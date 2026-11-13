#!/usr/bin/env python3
"""Build a reproducible, indexed runtime dictionary from the audited source assets."""
import json, sqlite3, unicodedata, hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
SOURCES=['dictionary.json','supplemental-entries.json','headword-corrections.json','pronunciation-corrections.json','reference-resolutions.json','definition-corrections.json']
def read(name): return json.loads((ASSETS/name).read_text())
def prepare(s): return unicodedata.normalize('NFC',s.strip(''.join(map(chr,range(33)))))
def fuzzy(s): return s.translate(str.maketrans({'u':'o','U':'O','d':'t','g':'k'}))
def build():
    rows=read('dictionary.json'); ids={r['id'] for r in rows}; heads={(r.get('mongolian'),r.get('transliteration')) for r in rows}
    for r in read('supplemental-entries.json'):
        head=(r.get('mongolian'),r.get('transliteration'))
        if r['id'] not in ids and head not in heads: rows.append(r);ids.add(r['id']);heads.add(head)
    byid={r['id']:r for r in rows}
    for c in read('pronunciation-corrections.json'):
        r=byid.get(c['id'])
        if r and r.get('transliteration')==c['transliteration'] and r.get('pronunciation')==c['from']:r['pronunciation']=c['to']
    for c in read('headword-corrections.json'):
        r=byid.get(c['id'])
        if r and r.get('mongolian')==c['original_mongolian'] and r.get('transliteration')==c['original_transliteration']:
            r.update(mongolian=c['mongolian'],transliteration=c['transliteration'])
    for r in rows:r.pop('resolved_definition',None);r.pop('definition_reference',None)
    for c in read('reference-resolutions.json'):
        r,t=byid.get(c['id']),byid.get(c['target_id'])
        if r and t and r.get('definition')==c['original'] and t.get('definition')==c['target_definition'] and all(byid.get(k,{}).get('definition')==d for k,d in zip(c['chain'],c['chain_definitions'])):
            r.update(resolved_definition=t['definition'],definition_reference=t.get('transliteration',''))
    for c in read('definition-corrections.json'):
        r=byid.get(c['id'])
        if r and r.get('transliteration')==c['transliteration'] and r.get('definition')==c['original']:
            r['resolved_definition']=c['definition'];r.pop('definition_reference',None)
    for old,keep in [('entry-1b99bb94-7b3d-526b-81b2-9657f0014505','entry-7a0b1cc0-12ee-5b61-a9c7-da654c9c8eee'),('entry-34f37f2a-a4d8-5577-bdf5-79dd1684271c','entry-f655db43-3670-55ae-b3f6-3307a1a9dee7')]:
        if keep in byid:rows=[r for r in rows if r['id']!=old]
    target=ASSETS/'dictionary.db';temp=target.with_suffix('.db.tmp');temp.unlink(missing_ok=True)
    db=sqlite3.connect(temp)
    db.executescript('CREATE TABLE entries(pos INTEGER PRIMARY KEY,id TEXT UNIQUE NOT NULL,payload TEXT NOT NULL);CREATE TABLE search_values(pos INTEGER,field INTEGER,n TEXT,c TEXT,f TEXT,fc TEXT);')
    for pos,r in enumerate(rows):
        db.execute('INSERT INTO entries VALUES(?,?,?)',(pos,r['id'],json.dumps(r,ensure_ascii=False,separators=(',',':'))))
        for field,name in enumerate(['mongolian','transliteration','pronunciation_extra','definition'],1):
            v=prepare(r.get(name,''))
            for n in (v.split('\n') if field==3 else [v]):
                c=n.lower();db.execute('INSERT INTO search_values VALUES(?,?,?,?,?,?)',(pos,field,n,c,fuzzy(n) if field==2 else None,fuzzy(c) if field==2 else None))
    for col in ['n','c','f','fc']:db.execute(f'CREATE INDEX search_{col} ON search_values({col},field,pos)' + (' WHERE field=2' if col in ['f','fc'] else ''))
    db.commit();db.close();temp.replace(target)
    digest=hashlib.sha256(target.read_bytes()).hexdigest();(ASSETS/'dictionary-db-version.txt').write_text(digest+'\n')
    print(f'Built {len(rows)} entries, {target.stat().st_size} bytes, sha256 {digest}')
if __name__=='__main__':build()
