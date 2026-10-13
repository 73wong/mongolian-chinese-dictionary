"""Parse source dictionary HTML without dependencies on local sample files."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin
import json, re, collections
BASE='http://hkuri.cneas.tohoku.ac.jp'
def clean_url(url): return re.sub(r';jsessionid=[^?/#;]+', '', url, flags=re.I)
class Parser(HTMLParser):
    def __init__(self):
        super().__init__(); self.active=False; self.rows=[]; self.row=None; self.cell=None; self.fields={}; self.skip=0; self.cyrillic=False
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        if tag=='input' and a.get('name') in ('currentPage','keyword'): self.fields[a['name']]=a.get('value')
        if tag=='table' and 'table-hover' in a.get('class',''): self.active=True
        if not self.active: return
        if tag=='tr': self.row=[]
        if tag=='td':
            self.cell={'parts':[], 'links':[], 'audio':[], 'buttons':[], 'cyrillic':[]}; self.row.append(self.cell)
        if self.cell is None: return
        if tag=='a' and not self.skip and '/p02/cyrillic/list?' in a.get('href','') and re.search(r'[?&]dicId=-1(?:&|$)',a.get('href','')):
            self.cyrillic=True; self.cell['cyrillic'].append('')
        if tag=='ul': self.skip+=1
        if tag=='br' and not self.skip: self.cell['parts'].append('\n')
        if tag=='a' and not self.skip and a.get('href','').startswith('http'): self.cell['links'].append(clean_url(a['href']))
        if tag=='source': self.cell['audio'].append(clean_url(urljoin(BASE,a['src'])))
        if tag=='input': self.cell['buttons'].append(a.get('value',''))
    def handle_endtag(self, tag):
        if not self.active: return
        if tag=='ul' and self.skip: self.skip-=1
        if tag=='a': self.cyrillic=False
        if tag=='td': self.cell=None; self.cyrillic=False
        if tag=='tr' and self.row:
            self.rows.append(self.row); self.row=None
        if tag=='table': self.active=False
    def handle_data(self, data):
        if self.cell is not None and not self.skip:
            # Strip HTML indentation, preserving internal Unicode spacing and controls.
            s=re.sub(r'[ \t\r\n\f]+', ' ', data)
            if s:
                if self.cyrillic:self.cell['cyrillic'][-1]+=s
                else:self.cell['parts'].append(s)
def extract(path):
    raw=path.read_text(encoding='utf-8'); p=Parser(); p.feed(raw); records=[]
    for r in p.rows:
        if len(r)!=6: raise ValueError(f'Unexpected row: {len(r)} cells')
        t=lambda i: re.sub(r' *\n *', '\n', re.sub(r' +', ' ', ''.join(r[i]['parts']))).strip(' \n')
        phon=t(3).split('\n',1)
        records.append(dict(mongolian=t(1),transliteration=t(2),pronunciation=t(3),pronunciation_extra='\n'.join(x.strip() for x in r[3]['cyrillic']),definition=t(4),references=r[4]['links'],audio_urls=r[3]['audio'],source_page=''.join(r[5]['buttons']),saved_page=p.fields.get('currentPage')))
    return records, dict(file=path.name,form=p.fields,count=len(records),replacement_characters=raw.count('\ufffd'))
