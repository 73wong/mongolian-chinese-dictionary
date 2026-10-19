"""Audit only exact, single-target standalone cross references; retain original data."""
import json,re
from pathlib import Path
from urllib.parse import urlparse,parse_qs
from collections import defaultdict,Counter
ROOT=Path(__file__).resolve().parents[1]
def audit(rows):
 index=defaultdict(list)
 for row in rows:index[row['mongolian']].append(row)
 edges={};issues=[];byid={r['id']:r for r in rows}
 for row in rows:
  definition=row['definition'].strip()
  if not definition.startswith(('⇒','→')):continue
  refs=row.get('references',[]);reason=None
  if len(refs)!=1:reason='reference_count'
  else:
   url=urlparse(refs[0]);query=parse_qs(url.query);target=query.get('keyword',[''])[0]
   tail=definition[1:].strip()
   if url.hostname!='hkuri.cneas.tohoku.ac.jp' or query.get('dicId')!=['7']:reason='unrecognized_source'
   elif not target or not (tail==target or re.fullmatch(re.escape(target)+r'\s*[（(](?:本段|上段|下段):[^()（）]+[)）]',tail)):reason='additional_text'
   elif len(index[target])!=1:reason='missing_target' if not index[target] else 'ambiguous_target'
   else:edges[row['id']]=index[target][0]['id']
  if reason:issues.append({'id':row['id'],'transliteration':row['transliteration'],'reason':reason})
 resolved=[]
 for source in edges:
  chain=[source];target=source
  while target in edges and edges[target] not in chain:
   target=edges[target];chain.append(target)
  terminal=byid[target]
  if target in edges or terminal['definition'].strip().startswith(('⇒','→')) or not re.search('[\u4e00-\u9fff]',terminal['definition']):
   issues.append({'id':source,'transliteration':byid[source]['transliteration'],'reason':'cycle_or_unresolved_chain'});continue
  resolved.append({'id':source,'original':byid[source]['definition'],'target_id':target,'target_definition':terminal['definition'],'target_transliteration':terminal['transliteration'],'chain':chain,'chain_definitions':[byid[x]['definition'] for x in chain]})
 return resolved,issues
if __name__=='__main__':
 rows=json.loads((ROOT/'app/src/main/assets/dictionary.json').read_text());resolved,issues=audit(rows)
 (ROOT/'app/src/main/assets/reference-resolutions.json').write_text(json.dumps(resolved,ensure_ascii=False,indent=2)+'\n')
 report={'resolved':len(resolved),'pending':len(issues),'reasons':dict(Counter(x['reason'] for x in issues)),'issues':issues}
 (ROOT/'data/reference-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print({k:v for k,v in report.items() if k!='issues'})
