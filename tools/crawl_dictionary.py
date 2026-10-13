#!/usr/bin/env python3
"""Discover missing dictionary entries using a resumable prefix/page cursor."""
import argparse,csv,fcntl,hashlib,io,json,re,string,subprocess,sys,time,uuid,zipfile
from pathlib import Path
import update_dictionary as core
ROOT=core.ROOT;WORK=ROOT/'collection/crawler';core.WORK=WORK

def device(config):
    adb=Path.home()/'Library/Android/sdk/platform-tools/adb'
    def run(args):return subprocess.run([str(adb),*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=30).stdout
    available=[s.split()[0] for s in run(['devices']).decode().splitlines()[1:] if s.endswith('\tdevice')]
    serial=config.get('serial') or (available[0] if len(available)==1 else '')
    if serial not in available:raise ValueError('Connect exactly one authorized device, or set serial in crawler-config.json')
    config['serial']=serial
    def read(path):return run(['-s',serial,'exec-out','run-as',core.PKG,'cat',path])
    exists=run(['-s',serial,'shell','run-as',core.PKG,'sh','-c','"if [ -f files/dictionary-v1.json ]; then echo yes; fi"']).strip()==b'yes'
    rows=json.loads(read('files/dictionary-v1.json')) if exists else json.loads((ROOT/'app/src/main/assets/dictionary.json').read_text())
    snapshot=WORK/'device-snapshot.json'
    if snapshot.exists():
        previous=json.loads(snapshot.read_text())
        if len(rows)<len(previous):
            raise ValueError(f'Device dictionary shrank from {len(previous)} to {len(rows)} entries. Stop and reconcile device data before continuing; crawler progress is not proof of device contents.')
    return rows

def choose(row,existing,heads):
    key=core.identity(row)
    if key in existing:
        old=existing[key];patch={'id':old['id']}
        for field in ('pronunciation','pronunciation_extra'):
            if row.get(field) and not old.get(field):patch[field]=row[field]
        if row.get('audio_urls') and not old.get('audio_file'):patch['audio_urls']=row['audio_urls']
        return (patch if len(patch)>1 else None),'existing'
    head=(row['mongolian'],row['transliteration'])
    if head in heads:return None,'identity_conflict'
    result=dict(row);result['id']='entry-'+str(uuid.uuid5(uuid.NAMESPACE_URL,core.BASE+'/p01/dic7/'+json.dumps(key,ensure_ascii=False)))
    return result,'new'

def page(prefix,number,config):
    path=WORK/'pages'/hashlib.sha256(prefix.encode()).hexdigest()/f'{number}.html'
    if not path.exists():
        form=dict(currentPage=number,searchRange=2,searchMethod=1,groupId=12,pageSize=30,keyword=prefix,dicIds=7)
        core.download(core.BASE+'/p01/mongol/listAction',path,max(1,config['request_delay_seconds']),core.urllib.parse.urlencode(form).encode())
    raw=path.read_text();rows,meta=core.extract(path)
    match=re.search(r'(?m)^\s*(\d+)\s+行\s*$',raw)
    if not match and not (not rows and 'Not Found' in raw):raise ValueError(f'Unrecognized result page {prefix}:{number}; preserved for inspection')
    total=int(match.group(1)) if match else 0
    if meta['form'].get('keyword')!=prefix or (total and meta['form'].get('currentPage')!=str(number)):raise ValueError('Wrong query/page returned')
    if len(rows)!=min(30,max(0,total-(number-1)*30)):raise ValueError('Truncated result page')
    if len(re.findall(r'<audio\b',raw))!=sum(bool(r['audio_urls']) for r in rows):raise ValueError('Audio parsing mismatch')
    if len(re.findall(r'href="[^"\n]*/p02/cyrillic/list\?[^"\n]*dicId=-1"',raw))!=sum(len(r['pronunciation_extra'].splitlines()) for r in rows):raise ValueError('Cyrillic parsing mismatch')
    return rows,total

def quarantine_invalid(row,prefix,number):
    missing=[k for k in ('mongolian','transliteration','definition') if not str(row.get(k) or '').strip()]
    damaged='\ufffd' in json.dumps(row,ensure_ascii=False)
    if not missing and not damaged:return None
    # Keep the complete source record separately before allowing the cursor to advance.
    record={'prefix':prefix,'page':number,'missing_fields':missing,'replacement_character':damaged,'record':row}
    key=hashlib.sha256(json.dumps(record,ensure_ascii=False,sort_keys=True).encode()).hexdigest()
    path=WORK/'quarantine'/f'{key}.json'
    core.save(path,record)
    print(f"[quarantine] {row.get('transliteration','?')}: missing={missing}, damaged={damaged}; saved {path.name}",flush=True)
    return str(path)

def package(rows,config,report,prefix='text'):
    """Package already prepared rows; networking belongs to the audio worker."""
    packages=[]
    for i in range(0,len(rows),100):
        chunk=rows[i:i+100];stream=io.StringIO(newline='');writer=csv.writer(stream);writer.writerow(core.COLS)
        for r in chunk:writer.writerow([json.dumps(r[c],ensure_ascii=False) if isinstance(r.get(c),list) else r.get(c,'') for c in core.COLS])
        p=WORK/f'{prefix}-package-{i//100+1:03}.zip'
        with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('entries.csv','\ufeff'+stream.getvalue())
            for name in sorted({r['audio_file'] for r in chunk if r.get('audio_file')}):
                z.write(WORK/name,name)
        with zipfile.ZipFile(p) as z:
            if z.testzip() or sum(x.file_size for x in z.infolist())>64*1024*1024:raise ValueError('Oversized or corrupt package')
        packages.append(p)
    return packages

def run_batch(argv=None, audio_queue=None):
    args=argparse.ArgumentParser();args.add_argument('--pages',type=int);args.add_argument('--collect-only',action='store_true');args.add_argument('--restart-scan',action='store_true');a=args.parse_args(argv)
    config=json.loads((ROOT/'tools/crawler-config.json').read_text());WORK.mkdir(parents=True,exist_ok=True)
    # Share the original updater lock so the two programs cannot write simultaneously.
    lockdir=ROOT/'collection/automatic';lockdir.mkdir(parents=True,exist_ok=True);lock=(lockdir/'run.lock').open('a');fcntl.flock(lock,fcntl.LOCK_EX if audio_queue is not None else fcntl.LOCK_EX|fcntl.LOCK_NB)
    report={'started_at':time.time(),'status':'running','ok':False,'fields':[],'conflicts':[],'pages':[],'new':0,'patches':0,'skipped_existing':0,'quarantined':[]}
    core.save(WORK/'report.json',report)
    try:
        current=device(config);report['device_entries_before']=len(current);report['phase']='scan'
        print(f'App currently contains {len(current)} entries. Scanning missing entries...',flush=True)
        core.save(WORK/'device-snapshot.json',current)
        if audio_queue is not None:audio_queue.enqueue(current)
        existing={core.identity(r):r for r in current};heads={(r['mongolian'],r['transliteration']) for r in current}
        statepath=WORK/'cursor.json';state=json.loads(statepath.read_text()) if statepath.exists() and not a.restart_scan else {'prefixes':list(string.ascii_lowercase+string.ascii_uppercase),'index':0,'page':1,'totals':{}}
        limit=a.pages if a.pages is not None else config['pages_per_run']
        selected={};seen=set();batch_keys=set();scanned=0
        while state['index']<len(state['prefixes']) and (limit==0 or scanned<limit):
            prefix=state['prefixes'][state['index']];number=state['page'];rows,total=page(prefix,number,config)
            previous=state['totals'].setdefault(prefix,total)
            if previous!=total:raise ValueError('Source total changed; inspect cache before restarting scan')
            signature=hashlib.sha256(json.dumps([core.identity(r) for r in rows],ensure_ascii=False).encode()).hexdigest()
            if signature in seen and rows:raise ValueError('Repeated result page')
            seen.add(signature)
            for row in rows:
                invalid=quarantine_invalid(row,prefix,number)
                if invalid:
                    report['quarantined'].append(invalid);continue
                key=core.identity(row)
                if key in batch_keys:continue
                patch,status=choose(row,existing,heads)
                if status=='identity_conflict':report['conflicts'].append(dict(row,reason='Same headword with different identity; manual review required'));continue
                if patch:
                    if patch['id'] not in selected:report['new' if status=='new' else 'patches']+=1
                    selected[patch['id']]=patch
                    batch_keys.add(key)
                    if status=='new':heads.add((row['mongolian'],row['transliteration']))
                else:report['skipped_existing']+=1
            report['pages'].append({'prefix':prefix,'page':number,'total':total,'rows':len(rows)})
            print(f'Scanned {prefix} page {number}: {len(rows)} rows; new {report["new"]}, patches {report["patches"]}, quarantined {len(report["quarantined"])}',flush=True)
            core.save(WORK/'report.json',report)
            scanned+=1
            if number*30>=total:state['index']+=1;state['page']=1
            else:state['page']+=1
        rows=list(selected.values());core.save(WORK/'pending-records.json',rows)
        core.save(WORK/'conflicts.json',report['conflicts'])
        # Never include downloaded audio in the text transaction. Audio URLs remain recoverable in the app.
        text_rows=[{k:v for k,v in row.items() if k not in ('audio_file','audio_source')} for row in rows]
        packages=package(text_rows,config,report);report['phase']='text_import';report['packages']=[str(p) for p in packages]
        core.save(WORK/'report.json',report)
        if not a.collect_only:
            if packages:core.install(packages,config,report)
            if audio_queue is not None:audio_queue.enqueue(rows)
            core.save(statepath,state)
            print(f"Text committed; queued audio separately. App total {report.get('imports',[{'total':len(current)}])[-1]['total']}",flush=True)
        report.update(ok=True,device_entries_before=len(current),scan_queue_complete=state['index']==len(state['prefixes']),next_cursor=state,cursor_saved=not a.collect_only)
        print(f'Completed: selected {report["new"]} new, {report["patches"]} patches; {len(report["conflicts"])} conflicts retained for review. Report: {WORK/"report.json"}',flush=True)
    except KeyboardInterrupt:
        report['error']='Stopped by user; last committed cursor retained.'
        raise
    except Exception as e:report['error']=str(e);print('Stopped: '+str(e),file=sys.stderr)
    finally:
        report['status']='completed' if report['ok'] else 'failed';report['finished_at']=time.time();core.save(WORK/'report.json',report);lock.close()
    return 0 if report['ok'] else 1
def continuous(batch_runner, batch_pages):
    if not 1 <= batch_pages <= 100:raise ValueError('pages_per_run must be between 1 and 100 in continuous mode')
    WORK.mkdir(parents=True,exist_ok=True)
    guard=(WORK/'continuous.lock').open('a');fcntl.flock(guard,fcntl.LOCK_EX|fcntl.LOCK_NB)
    progress={'status':'running','started_at':time.time(),'batches_completed':0,'new':0,'patches':0}
    try:
        while True:
            core.save(WORK/'progress.json',progress)
            code=batch_runner(['--pages',str(batch_pages)])
            report=json.loads((WORK/'report.json').read_text())
            with (WORK/'history.jsonl').open('a') as history:history.write(json.dumps(report,ensure_ascii=False)+'\n')
            if code:progress.update(status='failed',error=report.get('error'));return code
            progress['batches_completed']+=1;progress['new']+=report['new'];progress['patches']+=report['patches']
            progress['next_cursor']=report['next_cursor'];progress['last_batch_finished_at']=time.time()
            if report['scan_queue_complete']:progress['status']='completed';return 0
            print('Batch committed. Continuing from saved cursor...',flush=True)
            time.sleep(0.3)  # Allow a waiting audio import to acquire the shared device lock.
    except KeyboardInterrupt:
        progress['status']='stopped';print('Stopped by user; last committed cursor retained.',flush=True);return 130
    except Exception as error:
        progress.update(status='failed',error=str(error));raise
    finally:core.save(WORK/'progress.json',progress);guard.close()

def main():
    from audio_pipeline import run_pipeline
    return run_pipeline(sys.modules[__name__],sys.argv[1:])
if __name__=='__main__':sys.exit(main())
