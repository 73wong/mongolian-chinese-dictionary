#!/usr/bin/env python3
"""Local, resumable dictionary pipeline. Python standard library only."""
import argparse,csv,hashlib,io,json,os,re,shutil,subprocess,sys,time,urllib.parse,urllib.request,urllib.error,uuid,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(Path(__file__).resolve().parent))
from verify_dictionary import extract
BASE='http://hkuri.cneas.tohoku.ac.jp'
PKG='org.example.mongoldictionary'
WORK=ROOT/'collection/automatic'
REQUEST_GATE=None
COLS=['id','mongolian','transliteration','definition','source_page','pronunciation','pronunciation_extra','audio_file','audio_source','family_id','saved_page','references','audio_urls']
def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_suffix(path.suffix+'.tmp');tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n');tmp.replace(path)
def identity(row):return tuple(row.get(k,'') for k in ('mongolian','transliteration','source_page','definition'))
def download(url,path,delay,data=None,audio=False):
    url=urllib.parse.quote(url,safe=":/?=&%'+,;@()")
    missing=path.with_suffix(path.suffix+'.missing.json')
    if audio and missing.exists():
        cached=json.loads(missing.read_text())
        if time.time()-cached['time']<86400:
            raise ValueError(f"Cached HTTP {cached['status']} (retry after 24 hours)")
    for attempt in range(3):
        try:
            if REQUEST_GATE is not None:REQUEST_GATE.wait(delay)
            else:time.sleep(delay)
            with urllib.request.urlopen(urllib.request.Request(url,data=data,headers={'User-Agent':'MongolDictionaryLocalPilot/0.2'}),timeout=30) as response:b=response.read(8*1024*1024+1)
            if len(b)>8*1024*1024:raise ValueError('Response too large')
            if audio and not (b.startswith(b'ID3') or b.startswith(b'RIFF') or len(b)>1 and b[0]==255 and b[1]&224==224):raise ValueError('Not MP3/WAV')
            path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_suffix('.partial');tmp.write_bytes(b);tmp.replace(path);return
        except urllib.error.HTTPError as error:
            if error.code in (429,503) and REQUEST_GATE is not None:
                REQUEST_GATE.backoff(error.headers.get('Retry-After') if error.headers else None)
            if error.code in (404,410) and audio:
                save(missing,{'time':time.time(),'status':error.code,'url':url})
            if 400<=error.code<500 and error.code not in (408,429):raise
            if attempt==2:raise
            time.sleep(2**attempt)
        except Exception:
            if attempt==2:raise
            time.sleep(2**attempt)
def collect(config,report):
    known={}
    for path in [ROOT/'app/src/main/assets/dictionary.json',ROOT/'collection/batch-01/records.json',WORK/'registry.json']:
        if path.exists():
            for row in json.loads(path.read_text()):known[identity(row)]=row
    selected={};delay=max(1,float(config['request_delay_seconds']))
    for word in config['keywords']:
        rows=[];expected=None;page=1
        while True:
            path=WORK/'pages'/hashlib.sha256(word.encode()).hexdigest()/f'{page}.html'
            seed=ROOT/'collection/batch-01'/f'{word}-{page}.html' if re.fullmatch('[A-Za-z]+',word) else None
            if not path.exists() and seed and seed.exists():path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(seed,path)
            if config['refresh_pages'] or not path.exists():
                form=dict(currentPage=page,searchRange=2,searchMethod=1,isAmbiguous=1,groupId=12,pageSize=30,keyword=word,dicIds=7)
                download(BASE+'/p01/mongol/listAction',path,delay,urllib.parse.urlencode(form).encode())
            raw=path.read_text();match=re.search(r'(?m)^\s*(\d+)\s+行\s*$',raw)
            if not match:raise ValueError('Missing total: '+word)
            count=int(match.group(1))
            if expected is None:expected=count
            if count!=expected:raise ValueError('Pagination changed: '+word+'; refresh pages')
            if count>config['max_entries']:raise ValueError('Query exceeds max_entries: '+word)
            batch,meta=extract(path)
            if meta['form'].get('keyword')!=word or meta['form'].get('currentPage')!=str(page):raise ValueError('Wrong page')
            if len(batch)!=min(30,max(0,count-(page-1)*30)):raise ValueError('Incomplete page')
            # Independently audit every source audio element and Cyrillic headword link.
            if len(re.findall(r'<audio\b',raw))!=sum(bool(r['audio_urls']) for r in batch):raise ValueError('Audio parse omission')
            if len(re.findall(r'href="[^"\n]*/p02/cyrillic/list\?[^"\n]*dicId=-1"',raw))!=sum(bool(r['pronunciation_extra']) for r in batch):raise ValueError('Cyrillic parse omission')
            rows.extend(batch)
            if len(rows)==count:break
            page+=1
        if len({identity(r) for r in rows})!=expected:raise ValueError('Duplicate page/row: '+word)
        for row in rows:
            key=identity(row)
            if any(not row[k] for k in ('mongolian','transliteration','definition')) or '\ufffd' in json.dumps(row,ensure_ascii=False):raise ValueError('Invalid text')
            row['id']=known[key]['id'] if key in known else 'entry-'+str(uuid.uuid5(uuid.NAMESPACE_URL,BASE+'/p01/dic7/'+json.dumps(key,ensure_ascii=False)))
            head=re.split(r'[\s-]',row['transliteration'],maxsplit=1)[0]
            for root in ('mori','usu'):
                if head in (root,root+'n',root+'(n)'):row['family_id']=root
            selected[key]=row;known[key]=row
        if len(selected)>config['max_entries']:raise ValueError('Combined queries exceed max_entries')
        save(WORK/'registry.json',list(known.values()));report['queries'].append({'keyword':word,'count':count,'pages':page})
        print(f'已检查 {word}: {count} 条',flush=True)
    rows=list(selected.values())
    for row in rows:
        urls=sorted(row.get('audio_urls',[]),key=lambda u:'soundmp3?' not in u)
        status='source_page_has_no_audio';errors=[]
        for url in urls:
            ext='.mp3' if 'soundmp3?' in url else '.wav';path=WORK/'audio'/(hashlib.sha256(url.encode()).hexdigest()+ext)
            seed=ROOT/'collection/batch-01/audio'/(row['id']+ext)
            if not path.exists() and seed.exists():path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(seed,path)
            try:
                if not path.exists():download(url,path,delay,audio=True)
                row['audio_file']='audio/'+path.name;row['audio_source']=url;status='downloaded';break
            except Exception as error:errors.append(str(error));status='download_failed'
        report['fields'].append({'id':row['id'],'word':row['transliteration'],'ipa':bool(row['pronunciation']),'cyrillic':bool(row['pronunciation_extra']),'audio':status,'errors':errors})
    save(WORK/'records.json',rows)
    if any(r['audio']=='download_failed' for r in report['fields']):raise ValueError('Audio downloads failed; cached progress retained. Rerun to retry; no automatic import.')
    packages=[]
    for offset in range(0,len(rows),100):
        chunk=rows[offset:offset+100];out=io.StringIO(newline='');w=csv.writer(out);w.writerow(COLS)
        for row in chunk:w.writerow([json.dumps(row[c],ensure_ascii=False) if isinstance(row.get(c),list) else row.get(c,'') for c in COLS])
        path=WORK/f'package-{offset//100+1:03}.zip'
        with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('entries.csv','\ufeff'+out.getvalue())
            for row in chunk:
                if row.get('audio_file'):z.write(WORK/row['audio_file'],row['audio_file'])
        with zipfile.ZipFile(path) as z:
            if z.testzip() or sum(i.file_size for i in z.infolist())>64*1024*1024:raise ValueError('Invalid or oversized package')
        packages.append(path)
    report.update(entries=len(rows),audio=sum(bool(r.get('audio_file')) for r in rows),packages=[str(p) for p in packages]);return packages

def install(packages,config,report,report_path=None):
    report_path=report_path or WORK/'report.json'
    adb=Path.home()/'Library/Android/sdk/platform-tools/adb'
    def run(args,data=None):
        completed=subprocess.run([str(adb),*args],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
        if completed.returncode:raise RuntimeError((completed.stderr+completed.stdout).decode(errors='replace')[-2000:])
        return completed.stdout
    devices=[line.split()[0] for line in run(['devices']).decode().splitlines()[1:] if line.endswith('\tdevice')]
    serial=config.get('serial')
    if not serial:
        if len(devices)!=1:raise ValueError('请连接或启动一个设备；多个设备时在配置填写 serial。')
        serial=devices[0]
    if serial not in devices:raise ValueError('Configured device not connected/authorized')
    def device(args,data=None):return run(['-s',serial,*args],data)
    # Requires installed debug app; never reinstall/uninstall the user's app automatically.
    device(['shell','run-as',PKG,'id'])
    helper=ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if not helper.exists():helper=ROOT/'automation/local-import-helper.apk'
    if not helper.exists():raise ValueError('Missing import helper; run ./gradlew assembleDebugAndroidTest first')
    print('Preparing device import and full backup...',flush=True)
    device(['install','-r',str(helper)])
    device(['shell','am','force-stop',PKG])
    backup=WORK/'backups'/(time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);backup.mkdir(parents=True)
    from backup_policy import validate,prune,archives
    previous=archives(WORK/'backups')
    required=max(512*1024*1024,max((p.stat().st_size for p in previous),default=0)*12//10)+256*1024*1024
    if shutil.disk_usage(WORK).free<required:raise OSError('Insufficient disk space for verified backup; no import attempted')
    partial=backup/'app-data.tar.partial';final=backup/'app-data.tar'
    try:
        with partial.open('wb') as out:
            completed=subprocess.run([str(adb),'-s',serial,'exec-out','run-as',PKG,'tar','-cf','-','files','shared_prefs'],stdout=out,stderr=subprocess.PIPE,timeout=120)
        if completed.returncode:raise RuntimeError(completed.stderr.decode(errors='replace'))
        validate(partial);partial.replace(final)
    except Exception:
        partial.unlink(missing_ok=True)
        raise
    retention=prune(WORK/'backups',keep=3)
    save(WORK/'backup-retention.json',retention)
    print(f"Verified backup; retained {len(retention['retained'])} restore points, reclaimed {retention['freed_bytes']//(1024*1024)} MiB",flush=True)
    def protected():
        result={}
        for file in ['files/study-v1.json','shared_prefs/favorites.xml']:
            exists=device(['shell','run-as',PKG,'sh','-c',f'"if [ -f {file} ]; then echo yes; fi"']).strip()==b'yes'
            result[file]=device(['exec-out','run-as',PKG,'cat',file]) if exists else None
        return result
    before=protected();report['backup']=str(backup/'app-data.tar');report['imports']=[]
    for index,package in enumerate(packages,1):
        print(f'Importing package {index}/{len(packages)}...',flush=True)
        token=uuid.uuid4().hex
        device(['shell','run-as',PKG,'sh','-c','"cat > files/local-import.zip"'],package.read_bytes())
        output=device(['shell','am','instrument','-w','-e','token',token,PKG+'.test/org.example.mongoldictionary.RenderingChecks'])
        result=json.loads(device(['exec-out','run-as',PKG,'cat','files/local-import-result.json']))
        if result.get('token')!=token or not result.get('ok'):raise ValueError('Import failed: '+output.decode())
        if protected()!=before:raise ValueError('Study/favorites changed; stop and inspect backup')
        report.setdefault('imports',[]).append(result);save(report_path,report)
        print(f"Imported: added {result.get('added',0)}, updated {result.get('updated',0)}; app total {result.get('total','?')}",flush=True)
    device(['shell','am','start','-n',PKG+'/.MainActivity'])
    print('设备更新完成，学习记录和收藏校验未变。',flush=True)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--collect-only',action='store_true');parser.add_argument('--config',type=Path,default=ROOT/'tools/update-config.json');args=parser.parse_args()
    config=json.loads(args.config.read_text());WORK.mkdir(parents=True,exist_ok=True)
    import fcntl
    lock=(WORK/'run.lock').open('w');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    report={'queries':[],'fields':[],'ok':False}
    try:
        packages=collect(config,report)
        if config.get('install_to_device') and not args.collect_only:install(packages,config,report)
        report['ok']=True;print(f"完成：{report['entries']} 条，{report['audio']} 段语音。报告：{WORK/'report.json'}")
    except Exception as error:report['error']=str(error);print('未完成：'+str(error),file=sys.stderr)
    finally:save(WORK/'report.json',report)
    return 0 if report['ok'] else 1
if __name__=='__main__':sys.exit(main())
