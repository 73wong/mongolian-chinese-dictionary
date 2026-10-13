import hashlib,json,shutil,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import crawl_dictionary as c

class QuarantineTests(unittest.TestCase):
    def test_page_keeps_29_valid_records_and_archives_one(self):
        source=Path(__file__).resolve().parent/'fixtures/incomplete-page.html'
        with tempfile.TemporaryDirectory() as d:
            work=Path(d);target=work/'pages'/hashlib.sha256(b'k').hexdigest()/'75.html'
            target.parent.mkdir(parents=True);shutil.copyfile(source,target)
            with patch.object(c,'WORK',work):
                rows,total=c.page('k',75,{'request_delay_seconds':1})
                paths=[c.quarantine_invalid(row,'k',75) for row in rows]
                self.assertEqual(len(rows),30);self.assertEqual(total,3210)
                self.assertEqual(sum(p is None for p in paths),29)
                p=next(p for p in paths if p);record=json.loads(Path(p).read_text())
                self.assertEqual(record['record']['transliteration'],'kOmUnci～kUmUnci')
                self.assertEqual(record['missing_fields'],['definition'])
                self.assertEqual(c.quarantine_invalid(record['record'],'k',75),p)
                self.assertEqual(len(list((work/'quarantine').glob('*.json'))),1)
    def test_whitespace_missing_and_unicode_damage(self):
        with tempfile.TemporaryDirectory() as d,patch.object(c,'WORK',Path(d)):
            self.assertIsNotNone(c.quarantine_invalid({'mongolian':'m','transliteration':'t','definition':'  '},'x',1))
            self.assertIsNotNone(c.quarantine_invalid({'mongolian':'m','transliteration':'t','definition':'bad\ufffd'},'x',1))
            self.assertIsNone(c.quarantine_invalid({'mongolian':'m','transliteration':'t','definition':'good'},'x',1))

if __name__=='__main__':unittest.main()
