"""Restore the versioned offline data Release asset into a source checkout."""
import argparse,hashlib,json,re,shutil,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('archive',type=Path);a=p.parse_args()
 manifest=json.loads((ROOT/'data/manifest.json').read_text())
 if digest(a.archive)!=manifest['archive_sha256']:raise ValueError('Data archive checksum mismatch')
 assets=ROOT/'app/src/main/assets';assets.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='data-stage-',dir=assets) as tmp:
  stage=Path(tmp)
  with zipfile.ZipFile(a.archive) as z:
   names=z.namelist()
   if len(names)!=len(set(names)):raise ValueError('Duplicate archive paths')
   if len(names)!=manifest['audio_files']+1:raise ValueError('Unexpected file count')
   for info in z.infolist():
    name=info.filename
    if name!='dictionary.json' and not re.fullmatch(r'audio/[a-f0-9]{64}\.(mp3|wav|m4a|ogg)',name):raise ValueError('Unexpected archive path')
    if info.file_size>(100*1024*1024 if name=='dictionary.json' else 20*1024*1024):raise ValueError('Oversized member')
    dest=stage/name;dest.parent.mkdir(parents=True,exist_ok=True)
    with z.open(info) as src,dest.open('wb') as dst:shutil.copyfileobj(src,dst)
    expected=manifest['dictionary_sha256'] if name=='dictionary.json' else dest.stem
    if digest(dest)!=expected:raise ValueError('File checksum mismatch: '+name)
  rows=json.loads((stage/'dictionary.json').read_text())
  if len(rows)!=manifest['entries']:raise ValueError('Wrong entry count')
  for row in rows:
   if row.get('audio_file') and not (stage/'audio'/row['audio_file']).is_file():raise ValueError('Missing audio reference')
  (assets/'audio').mkdir(exist_ok=True)
  for source in (stage/'audio').iterdir():source.replace(assets/'audio'/source.name)
  (stage/'dictionary.json').replace(assets/'dictionary.json')
 print('Restored',manifest['entries'],'entries and',manifest['audio_entries'],'entries with offline audio.')
if __name__=='__main__':main()
