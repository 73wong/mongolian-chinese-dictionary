import hashlib,io,json,tarfile,tempfile,unittest
from pathlib import Path
from backup_policy import validate,prune

class BackupTests(unittest.TestCase):
    def make(self,root,n,bad=False):
        p=root/f'20261012-{n:06d}'/'app-data.tar';p.parent.mkdir()
        audio=b'recording';name=hashlib.sha256(audio).hexdigest()+'.mp3'
        with tarfile.open(p,'w') as t:
            for key,value in [('files/dictionary-v1.json',json.dumps([{'id':'one','audio_file':name}]).encode()),('files/audio/'+name,b'wrong' if bad else audio)]:
                m=tarfile.TarInfo(key);m.size=len(value);t.addfile(m,io.BytesIO(value))
        return p
    def test_retention(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);paths=[self.make(root,n) for n in range(6)]
            (paths[0].parent/'KEEP').touch()
            result=prune(root)
            self.assertEqual(len(result['retained']),4)
            self.assertEqual([p.exists() for p in paths],[True,False,False,True,True,True])
    def test_bad_newest_preserves_everything(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);paths=[self.make(root,n,bad=n==4) for n in range(5)]
            with self.assertRaises(ValueError):prune(root)
            self.assertTrue(all(p.exists() for p in paths))
    def test_empty_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'empty.tar';p.touch()
            with self.assertRaises(ValueError):validate(p)

if __name__=='__main__':unittest.main()
