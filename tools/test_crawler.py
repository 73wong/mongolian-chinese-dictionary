import unittest,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import crawl_dictionary as c
class Checks(unittest.TestCase):
 def setUp(self):
  self.row=dict(id='existing-id',mongolian='ᠨᠣᠮ',transliteration='nom',definition='书',source_page='1',pronunciation='nɔm',pronunciation_extra='ном',audio_urls=['example'])
 def test_existing_complete_is_skipped(self):
  old=dict(self.row,audio_file='local.mp3');patch,status=c.choose(self.row,{c.core.identity(old):old},set());self.assertIsNone(patch)
 def test_missing_fields_use_original_id(self):
  old=dict(self.row,pronunciation='',pronunciation_extra='',audio_urls=[]);patch,status=c.choose(self.row,{c.core.identity(old):old},set());self.assertEqual(patch['id'],'existing-id');self.assertEqual(patch['pronunciation_extra'],'ном');self.assertNotIn('definition',patch)
 def test_changed_definition_requires_review(self):
  other=dict(self.row,definition='different');patch,status=c.choose(other,{c.core.identity(self.row):self.row},{('ᠨᠣᠮ','nom')});self.assertEqual(status,'identity_conflict');self.assertIsNone(patch)
 def test_new_id_repeatable(self):
  one,status=c.choose(self.row,{},set());two,_=c.choose(self.row,{},set());self.assertEqual(status,'new');self.assertEqual(one['id'],two['id'])
 def test_multiline_ipa_and_cyrillic(self):
  html='''<table class="table-hover"><tr><td></td><td>ᠠ</td><td>a</td><td>ɑː～<br>ɔː<br><a href="/p02/cyrillic/list?dicId=-1">аа</a><br><a href="/p02/cyrillic/list?dicId=-1">оо</a></td><td>test</td><td></td></tr></table>'''
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'page.html';p.write_text(html);rows,_=c.core.extract(p)
  self.assertEqual(rows[0]['pronunciation'],'ɑː～\nɔː');self.assertEqual(rows[0]['pronunciation_extra'],'аа\nоо')
if __name__=='__main__':unittest.main()
