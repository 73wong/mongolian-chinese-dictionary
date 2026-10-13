import unittest,tempfile,urllib.error
from pathlib import Path
from unittest.mock import patch
import update_dictionary as core

class DownloadTests(unittest.TestCase):
    def test_404_once_and_cached(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'a.mp3'
            error=urllib.error.HTTPError('http://example.test/a',404,'Not Found',{},None)
            with patch.object(core.urllib.request,'urlopen',side_effect=error) as request,patch.object(core.time,'sleep'):
                with self.assertRaises(urllib.error.HTTPError):core.download('http://example.test/a',target,1,audio=True)
                self.assertEqual(request.call_count,1)
                with self.assertRaisesRegex(ValueError,'Cached HTTP 404'):core.download('http://example.test/a',target,1,audio=True)
                self.assertEqual(request.call_count,1)
                core.save(target.with_suffix('.mp3.missing.json'),{'time':0,'status':404})
                with self.assertRaises(urllib.error.HTTPError):core.download('http://example.test/a',target,1,audio=True)
                self.assertEqual(request.call_count,2)
    def test_transient_server_error_retries(self):
        with tempfile.TemporaryDirectory() as directory:
            error=urllib.error.HTTPError('http://example.test/a',503,'Unavailable',{},None)
            with patch.object(core.urllib.request,'urlopen',side_effect=error) as request,patch.object(core.time,'sleep'):
                with self.assertRaises(urllib.error.HTTPError):core.download('http://example.test/a',Path(directory)/'a',1)
                self.assertEqual(request.call_count,3)


class BatchTests(unittest.TestCase):
    def test_same_head_conflict_does_not_abort_batch(self):
        import crawl_dictionary as crawler,json
        row=dict(id='source',mongolian='m',transliteration='word',definition='first',source_page='1',audio_urls=[])
        other=dict(row,definition='second')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir()
            core.save(root/'tools/crawler-config.json',{'pages_per_run':1,'request_delay_seconds':1})
            work=root/'work'
            with patch.object(crawler,'ROOT',root),patch.object(crawler,'WORK',work),patch.object(core,'WORK',work),patch.object(crawler,'device',return_value=[]),patch.object(crawler,'page',return_value=([row,other,row],3)):
                self.assertEqual(crawler.run_batch(['--pages','1','--collect-only']),0)
                report=json.loads((work/'report.json').read_text())
                self.assertEqual(report['new'],1)
                self.assertEqual(len(report['conflicts']),1)
                self.assertEqual(report['conflicts'][0]['definition'],'second')
                self.assertEqual(report['phase'],'text_import')
                self.assertNotIn('audio_progress',report)
                self.assertFalse(report['cursor_saved'])

if __name__=='__main__':unittest.main()
