"""Text-first crawler with a durable audio queue and two rate-limited workers."""
import concurrent.futures as futures
from contextlib import contextmanager
from email.utils import parsedate_to_datetime
import fcntl,hashlib,json,threading,time
import update_dictionary as core

class RequestGate:
    """One request-start budget shared by HTML and both audio workers."""
    def __init__(self,stop):
        self.stop=stop;self.lock=threading.Lock();self.next_start=0
    def wait(self,delay):
        while True:
            if self.stop.is_set():raise InterruptedError('Download stopped; queue retained')
            with self.lock:
                remaining=self.next_start-time.monotonic()
                if remaining<=0:
                    self.next_start=time.monotonic()+max(1,float(delay));return
            self.stop.wait(min(remaining,0.2))
    def backoff(self,value):
        seconds=30
        if value:
            try:seconds=max(30,float(value))
            except ValueError:
                try:seconds=max(30,parsedate_to_datetime(value).timestamp()-time.time())
                except (ValueError,TypeError,OverflowError):pass
        with self.lock:self.next_start=max(self.next_start,time.monotonic()+seconds)
        print(f'[network] Server busy; all requests paused for {seconds:.0f}s',flush=True)

class AudioQueue:
    def __init__(self,work):
        self.path=work/'audio-queue.json';self.lock=threading.Lock()
        self.jobs=json.loads(self.path.read_text()) if self.path.exists() else {}
    def enqueue(self,rows):
        with self.lock:
            for row in rows:
                urls=list(dict.fromkeys(row.get('audio_urls',[])))
                if not urls or row.get('audio_file'):continue
                old=self.jobs.get(row['id'])
                if old is None:
                    self.jobs[row['id']]={'id':row['id'],'word':row.get('transliteration',row['id']),
                        'urls':urls,'status':'pending','retry_at':0}
                elif set(urls)-set(old['urls']):
                    old['urls']=list(dict.fromkeys(old['urls']+urls));old.update(status='pending',retry_at=0)
            core.save(self.path,self.jobs)
    def ready(self,limit):
        with self.lock:
            return [dict(j) for j in self.jobs.values() if j['status']!='done' and j.get('retry_at',0)<=time.time()][:limit]
    def update(self,id,**changes):
        with self.lock:
            self.jobs[id].update(changes);core.save(self.path,self.jobs)
    def counts(self):
        with self.lock:
            jobs=list(self.jobs.values());now=time.time()
            return {'total':len(jobs),'done':sum(j['status']=='done' for j in jobs),
                'ready':sum(j['status']!='done' and j.get('retry_at',0)<=now for j in jobs),
                'deferred':sum(j['status']!='done' and j.get('retry_at',0)>now for j in jobs)}

@contextmanager
def device_lock(crawler,stop):
    path=crawler.ROOT/'collection/automatic/run.lock';path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('a') as lock:
        while True:
            try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);break
            except BlockingIOError:
                if stop.wait(0.2):raise InterruptedError('Stopped before device import')
        yield

class AudioWorker:
    def __init__(self,crawler,config,queue,stop,text_done):
        self.crawler=crawler;self.config=dict(config);self.queue=queue;self.stop=stop;self.text_done=text_done
        self.url_locks={};self.url_guard=threading.Lock();self.error=None
        self.report={'status':'running','phase':'waiting','started_at':time.time(),'processed':0,
            'downloaded':0,'cached':0,'failed':0,'imported':0,'fields':[]}
        self.report_path=crawler.WORK/'audio-report.json'
    def publish(self):
        self.report['queue']=self.queue.counts();self.report['updated_at']=time.time()
        core.save(self.report_path,self.report)
    def fetch(self,job):
        errors=[]
        for url in sorted(job['urls'],key=lambda u:'soundmp3?' not in u):
            if self.stop.is_set():raise InterruptedError('Audio download cancelled')
            ext='.mp3' if 'soundmp3?' in url else '.wav'
            name=hashlib.sha256(url.encode()).hexdigest()+ext;path=self.crawler.WORK/'audio'/name
            with self.url_guard:lock=self.url_locks.setdefault(url,threading.Lock())
            try:
                with lock:
                    cached=path.exists()
                    if not cached:core.download(url,path,max(1,self.config['request_delay_seconds']),audio=True)
                return job,{'id':job['id'],'audio_file':'audio/'+name,'audio_source':url},'cached' if cached else 'downloaded',errors
            except InterruptedError:raise
            except Exception as error:errors.append(str(error))
        return job,None,'failed',errors
    def run(self):
        try:
            with futures.ThreadPoolExecutor(max_workers=2,thread_name_prefix='audio') as pool:
                while not self.stop.is_set():
                    jobs=self.queue.ready(self.config.get('audio_batch_size',50))
                    if not jobs:
                        self.report['phase']='waiting';self.publish()
                        if self.text_done.is_set():break
                        self.stop.wait(1);continue
                    # Reconcile already imported files before downloading, including interrupted runs.
                    with device_lock(self.crawler,self.stop):
                        current={r['id']:r for r in self.crawler.device(self.config)}
                    eligible=[]
                    for job in jobs:
                        row=current.get(job['id'])
                        if row and row.get('audio_file'):self.queue.update(job['id'],status='done',retry_at=0)
                        elif row:eligible.append(job)
                        else:self.queue.update(job['id'],status='deferred',retry_at=time.time()+900,error='Entry not on connected device')
                    if not eligible:continue
                    self.report['phase']='download';self.publish()
                    pending={pool.submit(self.fetch,j):j for j in eligible};patches=[]
                    while pending:
                        done,_=futures.wait(pending,timeout=5,return_when=futures.FIRST_COMPLETED)
                        if not done:
                            self.publish();print(f"[audio] Working; {len(pending)} remaining in batch | processed {self.report['processed']} | imported {self.report['imported']}",flush=True)
                        for future in done:
                            pending.pop(future)
                            try:job,patch,status,errors=future.result()
                            except InterruptedError:continue
                            self.report['processed']+=1;self.report[status]+=1
                            self.report['fields'].append({'id':job['id'],'status':status,'errors':errors})
                            if patch:patches.append(patch)
                            else:
                                permanent=bool(errors) and all('404' in e or '410' in e for e in errors)
                                self.queue.update(job['id'],status='deferred',retry_at=time.time()+(86400 if permanent else 900),errors=errors)
                            self.publish()
                            print(f"[audio] processed {self.report['processed']} | downloaded {self.report['downloaded']} | cached {self.report['cached']} | unavailable {self.report['failed']} | imported {self.report['imported']}",flush=True)
                    if self.stop.is_set():break
                    if patches:
                        self.report['phase']='import';self.publish()
                        # Serialize imports with text, and never overwrite an audio recording added meanwhile.
                        with device_lock(self.crawler,self.stop):
                            current={r['id']:r for r in self.crawler.device(self.config)}
                            safe=[r for r in patches if r['id'] in current and not current[r['id']].get('audio_file')]
                            if safe:
                                packages=self.crawler.package(safe,self.config,self.report,prefix='audio')
                                core.install(packages,self.config,self.report,report_path=self.report_path)
                                self.report['imported']+=sum(i.get('updated',0) for i in self.report.get('imports',[]))
                            for r in patches:
                                if r['id'] in current:self.queue.update(r['id'],status='done',retry_at=0)
                        self.publish()
                        print(f"[audio] Recordings imported this run: {self.report['imported']} | queue {self.queue.counts()}",flush=True)
                    self.report['fields']=self.report['fields'][-100:]
        except InterruptedError:pass
        except Exception as error:
            self.error=str(error);self.report['error']=self.error
            print('[audio] Stopped; queued work retained: '+self.error,flush=True)
        finally:
            self.report['status']='failed' if self.error else 'stopped' if self.stop.is_set() else 'completed'
            self.publish()

def run_pipeline(crawler,argv):
    config=json.loads((crawler.ROOT/'tools/crawler-config.json').read_text())
    crawler.WORK.mkdir(parents=True,exist_ok=True)
    # One owner for the queue, including standalone audio-only runs.
    with (crawler.WORK/'pipeline.lock').open('a') as guard:
        try:fcntl.flock(guard,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:
            print('Crawler already running. Use python3 tools/watch_crawler.py to observe.',flush=True);return 1
        # Reject legacy crawlers before changing reports or creating the worker.
        with (crawler.WORK/'continuous.lock').open('a') as old:
            try:fcntl.flock(old,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:
                print('Older crawler still running; stop it before starting this version.',flush=True);return 1
        legacy_lock=crawler.ROOT/'collection/automatic/run.lock'
        legacy_lock.parent.mkdir(parents=True,exist_ok=True)
        with legacy_lock.open('a') as old:
            try:fcntl.flock(old,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:
                print('Another updater is using the device; wait for it to finish.',flush=True);return 1
        stop=threading.Event();text_done=threading.Event();core.REQUEST_GATE=RequestGate(stop)
        queue=AudioQueue(crawler.WORK)
        worker=AudioWorker(crawler,config,queue,stop,text_done);thread=None;code=0
        try:
            if argv==['--audio-only']:
                with device_lock(crawler,stop):queue.enqueue(crawler.device(config))
                text_done.set();worker.run();return 1 if worker.error else 0
            if '--collect-only' not in argv:
                thread=threading.Thread(target=worker.run,name='audio-pipeline');thread.start()
            runner=lambda args:crawler.run_batch(args,audio_queue=queue if thread else None)
            code=crawler.continuous(runner,config['pages_per_run']) if not argv and config.get('continuous',False) else runner(argv)
            if code:stop.set()
        except KeyboardInterrupt:
            code=130;stop.set();print('Stopping; committed text and audio queue are retained.',flush=True)
        finally:
            text_done.set()
            if thread:
                while thread.is_alive():
                    try:thread.join(1)
                    except KeyboardInterrupt:stop.set();code=130
            core.REQUEST_GATE=None
        return code or (1 if worker.error else 0)
