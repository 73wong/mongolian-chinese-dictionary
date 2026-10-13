#!/usr/bin/env python3
"""Watch text commits and the independent audio queue without starting a crawler."""
import json,time
from pathlib import Path
work=Path(__file__).resolve().parents[1]/'collection/crawler'
def read(name):
    try:return json.loads((work/name).read_text())
    except FileNotFoundError:return {}
last=None
try:
    while True:
        r=read('report.json');a=read('audio-report.json');q=a.get('queue',{})
        imports=r.get('imports',[]);audio_imports=a.get('imports',[])
        totals=[i['total'] for i in imports+audio_imports if 'total' in i]
        total=max(totals) if totals else r.get('device_entries_before','?')
        text=(f"[text] {r.get('status','waiting')} {r.get('phase','')} | "
              f"batch scanned={sum(p['rows'] for p in r.get('pages',[]))} "
              f"quarantined={len(r.get('quarantined',[]))} selected new={r.get('new',0)} committed new={sum(i.get('added',0) for i in imports)} | app total={total}\n"
              f"[audio] {a.get('status','waiting')} {a.get('phase','')} | "
              f"processed={a.get('processed',0)} downloaded={a.get('downloaded',0)} cached={a.get('cached',0)} "
              f"unavailable={a.get('failed',0)} imported={a.get('imported',0)} | "
              f"queue completed={q.get('done',0)}/{q.get('total',0)} deferred={q.get('deferred',0)}")
        errors='\n'.join(str(x['error']) for x in (r,a) if x.get('error'))
        if errors:text+='\n'+errors
        if text!=last:print(time.strftime('%H:%M:%S')+'\n'+text,flush=True);last=text
        time.sleep(2)
except KeyboardInterrupt:pass
