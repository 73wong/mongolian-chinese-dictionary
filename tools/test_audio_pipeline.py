import json,tempfile,threading,time,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
from concurrent.futures import ThreadPoolExecutor
import audio_pipeline as a
import crawl_dictionary as c

class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.work=self.root/'work';self.work.mkdir()
        (self.root/'tools').mkdir();c.core.save(self.root/'tools/crawler-config.json',{'pages_per_run':1,'request_delay_seconds':1})
        self.patches=[patch.object(c,'ROOT',self.root),patch.object(c,'WORK',self.work),patch.object(c.core,'WORK',self.work)]
        for p in self.patches:p.start()
    def tearDown(self):
        for p in reversed(self.patches):p.stop()
        self.temp.cleanup()
    def worker(self,queue):
        done=threading.Event();done.set()
        return a.AudioWorker(c,{'request_delay_seconds':1,'audio_batch_size':10},queue,threading.Event(),done)
    def test_queue_survives_restart_and_deduplicates(self):
        q=a.AudioQueue(self.work);row={'id':'id','audio_urls':['url','url']}
        q.enqueue([row,row]);q.update('id',status='deferred',retry_at=time.time()+900)
        q=a.AudioQueue(self.work);q.enqueue([row]);self.assertEqual(q.ready(10),[])
        self.assertEqual(q.counts()['total'],1)
        q.enqueue([dict(row,audio_urls=['new'])]);self.assertEqual(len(q.ready(10)),1)
    def test_shared_url_downloaded_once_and_zip_member_unique(self):
        q=a.AudioQueue(self.work);w=self.worker(q)
        jobs=[{'id':str(i),'urls':['http://example.test/soundmp3?a']} for i in range(2)]
        def download(url,path,*args,**kwargs):
            time.sleep(.02);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'ID3test')
        with patch.object(c.core,'download',side_effect=download) as request,ThreadPoolExecutor(2) as pool:
            rows=list(pool.map(w.fetch,jobs))
            self.assertEqual(request.call_count,1)
            self.assertEqual({r[2] for r in rows},{'downloaded','cached'})
        packages=c.package([r[1] for r in rows],{}, {},prefix='audio')
        with zipfile.ZipFile(packages[0]) as z:self.assertEqual(len(z.namelist()),2)
    def test_text_commits_before_queue_and_has_no_audio_payload(self):
        row={'id':'source','mongolian':'m','transliteration':'t','definition':'d','source_page':'1','audio_urls':['url']}
        q=a.AudioQueue(self.work);events=[]
        def install(packages,config,report):
            self.assertEqual(q.counts()['total'],0)
            with zipfile.ZipFile(packages[0]) as z:self.assertEqual(z.namelist(),['entries.csv'])
            events.append('text');report['imports']=[{'added':1,'total':1}]
        with patch.object(c,'device',return_value=[]),patch.object(c,'page',return_value=([row],1)),patch.object(c.core,'install',side_effect=install),patch.object(c.core,'download') as download:
            self.assertEqual(c.run_batch(['--pages','1'],q),0);download.assert_not_called()
        self.assertEqual(events,['text']);self.assertEqual(q.counts()['ready'],1)
        self.assertTrue((self.work/'cursor.json').exists())
    def test_failed_import_does_not_advance_cursor(self):
        row={'id':'source','mongolian':'m','transliteration':'t','definition':'d','source_page':'1','audio_urls':['url']}
        q=a.AudioQueue(self.work)
        with patch.object(c,'device',return_value=[]),patch.object(c,'page',return_value=([row],1)),patch.object(c.core,'install',side_effect=ValueError('failed')):
            self.assertEqual(c.run_batch(['--pages','1'],q),1)
        self.assertFalse((self.work/'cursor.json').exists())
    def test_audio_failure_deferred_success_is_minimal_patch(self):
        q=a.AudioQueue(self.work)
        rows=[{'id':'good','audio_urls':['good']},{'id':'bad','audio_urls':['bad']},{'id':'already','audio_urls':['old']}]
        q.enqueue(rows);w=self.worker(q)
        current=rows[:2]+[dict(rows[2],audio_file='existing.mp3')]
        def download(url,path,*args,**kwargs):
            if url=='bad':raise ValueError('HTTP Error 404: Not Found')
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'ID3test')
        def install(packages,config,report,**kwargs):
            with zipfile.ZipFile(packages[0]) as z:
                import csv,io
                imported=list(csv.DictReader(io.StringIO(z.read('entries.csv').decode('utf-8-sig'))))
                self.assertEqual(imported[0]['id'],'good');self.assertEqual(imported[0]['definition'],'')
            report['imports']=[{'updated':1,'total':3}]
        with patch.object(c,'device',return_value=current),patch.object(c.core,'download',side_effect=download),patch.object(c.core,'install',side_effect=install):w.run()
        self.assertIsNone(w.error);self.assertEqual(q.counts()['done'],2)
        self.assertEqual(q.counts()['deferred'],1);self.assertEqual(w.report['imported'],1)
    def test_gate_shared_spacing_and_retry_after(self):
        gate=a.RequestGate(threading.Event())
        with patch.object(a.time,'monotonic',return_value=10):
            gate.wait(1.5);self.assertEqual(gate.next_start,11.5)
            gate.backoff('45');self.assertEqual(gate.next_start,55)
        gate.stop.set()
        with self.assertRaises(InterruptedError):gate.wait(1)
    def test_interrupted_download_remains_pending(self):
        q=a.AudioQueue(self.work);q.enqueue([{'id':'x','audio_urls':['u']}]);w=self.worker(q)
        with patch.object(c,'device',return_value=[{'id':'x'}]),patch.object(w,'fetch',side_effect=InterruptedError('stop')):
            # Real cancellation sets the shared stop flag. Cancel after the first fetch.
            def cancel(job):w.stop.set();raise InterruptedError('stop')
            w.fetch.side_effect=cancel;w.run()
        self.assertEqual(q.counts()['ready'],1)

if __name__=='__main__':unittest.main()
