"""Produce a readable audit; never overwrite dictionary records."""
import json,collections
from pathlib import Path
from urllib.parse import urlparse,parse_qs
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'verification/reference-review'
rows=json.loads((ROOT/'app/src/main/assets/dictionary.json').read_text());byid={r['id']:r for r in rows}
issues=[i for i in json.loads((ROOT/'data/reference-audit.json').read_text())['issues'] if i['reason'] in ('missing_target','cycle_or_unresolved_chain')]
live={r['id']:r for r in json.loads((OUT/'results.json').read_text())};cached=json.loads((OUT/'cached-evidence.json').read_text());local=json.loads((OUT/'local-candidates.json').read_text())
confirmed={x['id']:x for x in json.loads((ROOT/'app/src/main/assets/definition-corrections.json').read_text())};summary=[];details=[];recovered={}
for number,i in enumerate(issues,1):
 r=byid[i['id']];target=parse_qs(urlparse(r['references'][0]).query)['keyword'][0];l=live.get(r['id'],{});exact=[x for x in cached.get(target,[]) if not x['definition'].startswith(('⇒','→'))];embedded=local[r['id']]
 if exact:
  status='保存网页有完整词条，App 缺收';candidates=exact
  for x in exact:recovered[(x['mongolian'],x['transliteration'],x['definition'])]=x
 elif embedded:status='本地有正文或带词性限定的候选，需核对义项';candidates=embedded
 elif i['reason']=='cycle_or_unresolved_chain':status='引用链未闭合或自指，保留原文';candidates=l.get('candidates',[])
 elif l.get('candidates'):status='原站找到候选，待核对';candidates=l['candidates']
 else:status='证据不足，不猜释义';candidates=[]
 if r['id'] in confirmed:status='用户已确认：'+confirmed[r['id']]['definition'];candidates=[]
 summary.append({'number':number,'id':r['id'],'word':r['transliteration'],'status':status,'live_status':l.get('status','尚未查询'),'candidate_count':len(candidates)})
 details += [f"## {number}. {r['transliteration']}",'',f"- 结论：{status}",f"- 原文：{r['definition']}",f"- 页段：{r['source_page']}",f"- 实时查询：{l.get('status','尚未查询')}",f"- [原站链接]({r['references'][0]})",'']
 for j,c in enumerate(candidates,1):
  details += [f"候选 {j}：**{c['transliteration']}**（{c['source_page']}）",'',c['mongolian'],'',c['definition'],'']
  if c.get('evidence'):details += [f"[保存网页]({(ROOT/c['evidence']).as_posix()})",'']
counts=dict(collections.Counter(r['status'] for r in summary));head=['# 77 条参见词条核对报告','','核对日期：2026-10-19。仅核对，未修改 App 词库。','', '## 结论','']+[f'- {k}：{v} 条' for k,v in counts.items()]
head+=['','## 阅读方法','','先看“App 缺收”，这些已有保存网页证据。正文候选可能包含多个义项或例句，不应整条照搬。证据不足的继续保留原文。','','实时查询采用转写前缀首 30 条；没有命中不证明原站不存在。蒙古文完全匹配请求返回 HTTP 500，已停止该方式。','','## 总表','','| 编号 | 转写 | 分类 |','|---|---|---|']+[f"| {x['number']} | {x['word']} | {x['status']} |" for x in summary]
(OUT/'review.md').write_text('\n'.join(head+['']+details)+'\n');(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n');(OUT/'recovered-candidates.json').write_text(json.dumps(list(recovered.values()),ensure_ascii=False,indent=2)+'\n');print(counts)
