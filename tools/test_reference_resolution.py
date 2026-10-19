import unittest
from resolve_references import audit
from urllib.parse import quote

def row(id,word,definition,target=None):
 return dict(id=id,mongolian=word,transliteration=id,definition=definition,references=[] if target is None else ['http://hkuri.cneas.tohoku.ac.jp/p01/mongol/list?groupId=12&dicId=7&keyword='+quote(target)])
class ReferenceTests(unittest.TestCase):
 def test_chain(self):
  resolved,_=audit([row('a','甲','⇒ 乙','乙'),row('b','乙','→ 丙','丙'),row('c','丙','〔名〕解释。')]);self.assertEqual(len(resolved),2);self.assertEqual(resolved[0]['target_id'],'c')
 def test_ambiguous_and_cycle(self):
  resolved,issues=audit([row('a','甲','⇒ 乙','乙'),row('b','乙','解释一'),row('c','乙','解释二')]);self.assertFalse(resolved);self.assertEqual(issues[0]['reason'],'ambiguous_target')
  self.assertFalse(audit([row('a','甲','⇒ 乙','乙'),row('b','乙','⇒ 甲','甲')])[0])
 def test_additional_text(self):
  self.assertFalse(audit([row('a','甲','⇒ 乙，另有含义','乙'),row('b','乙','解释')])[0])
if __name__=='__main__':unittest.main()
