"""Validate local restore points and retain a bounded number of full backups."""
import hashlib,json,re,tarfile
from pathlib import Path

PATTERN=re.compile(r'^\d{8}-\d{6}(?:-[a-f0-9]{8})?$')
def archives(root):
    return sorted(p for p in Path(root).glob('*/app-data.tar') if PATTERN.fullmatch(p.parent.name) and not p.is_symlink() and not p.parent.is_symlink())
def validate(path):
    path=Path(path)
    if path.stat().st_size<1024:raise ValueError('Empty/incomplete backup: '+str(path))
    with path.open('rb') as f:
        f.seek(-1024,2)
        if any(f.read()):raise ValueError('Missing tar end marker: '+str(path))
    with tarfile.open(path,'r:') as archive:
        members=archive.getmembers();names={m.name for m in members}
        if len(names)!=len(members):raise ValueError('Duplicate archive member')
        rows=None
        for member in members:
            if member.isdir():continue
            if not member.isfile():raise ValueError('Unexpected backup member type')
            stream=archive.extractfile(member);digest=hashlib.sha256();size=0;payload=[]
            capture=member.name=='files/dictionary-v1.json'
            while True:
                chunk=stream.read(1024*1024)
                if not chunk:break
                size+=len(chunk);digest.update(chunk)
                if capture:payload.append(chunk)
            if size!=member.size:raise ValueError('Truncated member: '+member.name)
            if re.fullmatch(r'files/audio/[a-f0-9]{64}\.[a-z0-9]+',member.name):
                if digest.hexdigest()!=Path(member.name).stem:raise ValueError('Audio checksum mismatch: '+member.name)
            if capture:rows=json.loads(b''.join(payload))
        if not isinstance(rows,list) or not rows:raise ValueError('Missing dictionary')
        if len({r['id'] for r in rows})!=len(rows):raise ValueError('Duplicate dictionary IDs')
        for row in rows:
            audio=row.get('audio_file')
            if audio and 'files/audio/'+audio not in names:raise ValueError('Missing referenced recording: '+audio)
    return {'entries':len(rows),'audio_entries':sum(bool(r.get('audio_file')) for r in rows),'bytes':path.stat().st_size,'members':len(members)}

def prune(root,keep=3):
    if keep<2:raise ValueError('Keep at least two verified restore points')
    paths=archives(root);retained=[p for p in paths if (p.parent/'KEEP').exists()]
    candidates=[p for p in paths if p not in retained]
    retained+=candidates[-keep:]
    # Nothing is deleted unless all intended restore points validate.
    verified={str(p):validate(p) for p in retained}
    removed=[]
    for p in paths:
        if p in retained:continue
        size=p.stat().st_size;p.unlink();removed.append({'path':str(p),'bytes':size})
        if not any(p.parent.iterdir()):p.parent.rmdir()
    return {'retained':verified,'removed':removed,'freed_bytes':sum(r['bytes'] for r in removed)}
