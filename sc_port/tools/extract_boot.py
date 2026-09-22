#!/usr/bin/env python3
from __future__ import annotations
import argparse, io, json, os, re, struct, zlib, zipfile
from pathlib import Path

KEY1=bytes([0x00,0x08,0x07,0x03,0x05,0x0C,0x0B,0x0A,0x09,0x01,0x02,0x0E,0x04,0x0D,0x06,0x0F])
KEY2=bytes([0x02,0x0E,0x04,0x0A,0x06,0x0B,0x03,0x00,0x09,0x0F,0x07,0x01,0x0D,0x08,0x0C,0x05])
KEY1_SUFFIXES=('.cg','.cgh','.dlp_d','.exe','.dll','.dlp','.webm')

def crypt(data,key,start=0,decrypt=True):
    b=bytearray(data)
    for i in range(len(b)):
        j=(i+start)&15; d=j*key[j]
        b[i]=(b[i]-d if decrypt else b[i]+d)&255
    return bytes(b)

def per_key(name):
    return KEY1 if name.lower().endswith(KEY1_SUFFIXES) else KEY2

def read_plain_str(f):
    n=f.read(1)[0]; return f.read(n).decode('utf-8','replace')

def read_enc_str(f):
    n=f.read(1)[0]; off=f.tell()
    return crypt(f.read(n),KEY2,off,True).decode('utf-8','replace')

class Pack:
    def __init__(self,path):
        self.path=Path(path); self.entries=[]; self.parse()
    def parse(self):
        with self.path.open('rb') as f:
            assert f.read(6)==b'BKNPAK'
            self.version=int.from_bytes(f.read(2),'big')
            self.table_offset=16+int.from_bytes(f.read(8),'little')
            marker=f.read(1)[0]
            if marker<=1:
                if marker: self.title=read_plain_str(f)
            else:
                self.title=f.read(marker).decode('utf8','replace')
            f.read(4); n=f.read(1)[0]; f.seek(n,1)
            self.inner_len=int.from_bytes(f.read(8),'little')
            self.inner_off=f.tell()
            f.seek(self.table_offset)
            count=int.from_bytes(f.read(4),'little')
            rows=[]
            for _ in range(count):
                name=read_enc_str(f).replace('\\','/')
                c=int.from_bytes(f.read(8),'little')
                o=int.from_bytes(f.read(8),'little')
                rows.append((name,c,o))
            self.payload_off=f.tell()
        off=self.payload_off
        for i,(name,c,o) in enumerate(rows):
            self.entries.append(dict(index=i,name=name,compressed_size=c,original_size=o,offset=off,compressed=c!=o))
            off+=c
    def inner_zip(self):
        with self.path.open('rb') as f:
            f.seek(self.inner_off); enc=f.read(self.inner_len-8)
        raw=b'PK\x03\x04\x14\x00\x00\x00'+crypt(enc,KEY1,self.inner_off,True)
        return zipfile.ZipFile(io.BytesIO(raw),'r')
    def extract_res(self,e,dest):
        with self.path.open('rb') as f:
            f.seek(e['offset']); data=f.read(e['compressed_size'])
        if e['compressed']: data=zlib.decompress(data)
        data=crypt(data,KEY2,0,True)
        out=dest/Path(e['name']); out.parent.mkdir(parents=True,exist_ok=True); out.write_bytes(data)
        return out

def strings(b):
    return [m.decode('utf8','ignore').strip() for m in re.findall(rb'[\x20-\x7e]{4,}',b)]

def norm(s):
    return re.sub(r'[^a-z0-9]+','',s.lower())

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('rbpack'); ap.add_argument('out')
    a=ap.parse_args()
    pack=Pack(a.rbpack); out=Path(a.out); meta=out/'meta'; assets=out/'assets'
    meta.mkdir(parents=True,exist_ok=True); assets.mkdir(parents=True,exist_ok=True)

    selected_maps={}
    with pack.inner_zip() as z:
        names=z.namelist()
        targets=[]
        for n in names:
            low=n.lower().replace('\\','/')
            if low.endswith('.rbr') and (low.startswith('map/menu_') or low.startswith('map/intro_') or low.startswith('map/start_')
                or low in ('gamesettings.rbr','layout.rbr','resourceitem.rbr','camera.rbr','cast.rbr','rendersettings.rbr')):
                targets.append(n)
        for n in targets:
            raw=crypt(z.read(n),per_key(n),0,True)
            p=meta/Path(n); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(raw)
            selected_maps[n]=strings(raw)
        (meta/'inner_names.json').write_text(json.dumps(names,ensure_ascii=False,indent=2),encoding='utf8')

    # Derive probable visible object/resource names from boot maps, then match pack paths.
    candidates=set()
    for n,ss in selected_maps.items():
        if not n.lower().startswith('map/'): continue
        for s in ss:
            if 3<=len(s)<=80 and not s.startswith(('guid','baseId','position','rotation','category','extra')):
                q=norm(s)
                if len(q)>=4: candidates.add(q)
    mandatory=[
      'airplain','airplanenoise','bgmmenu','logo','menupanel','intromarian','jayintro',
      'beach','palm','ep1terrain01','boat','bridge','water','sky','fog',
      'building','bush','tree','bench'
    ]
    selected=[]
    for e in pack.entries:
        n=e['name'].replace('\\','/'); nl=n.lower(); nn=norm(n)
        base=norm(Path(n).stem)
        hit=any(k in nn for k in mandatory)
        if not hit:
            for c in candidates:
                if len(c)>=5 and (c==base or c in nn):
                    hit=True; break
        if hit: selected.append(e)

    # Pull sibling textures/files for each selected FBX directory.
    dirs=set()
    for e in selected:
        if e['name'].lower().endswith('.fbx'):
            dirs.add(str(Path(e['name']).parent).replace('\\','/').lower()+'/')
    for e in pack.entries:
        nl=e['name'].replace('\\','/').lower()
        if any(nl.startswith(d) for d in dirs) and e not in selected: selected.append(e)

    # Keep the slice bounded: skip giant unrelated files selected by overly generic names.
    keep=[]
    for e in selected:
        nl=e['name'].lower()
        if e['original_size']>50*1024*1024 and not ('airplain' in nl or 'menu' in nl): continue
        keep.append(e)
    selected=sorted({e['index']:e for e in keep}.values(),key=lambda x:x['index'])

    total=0
    for i,e in enumerate(selected,1):
        print(f"[{i}/{len(selected)}] {e['name']} ({e['original_size']/1048576:.1f} MiB)")
        pack.extract_res(e,assets); total+=e['original_size']

    report={
      'rbpack_size':pack.path.stat().st_size,'resources_total':len(pack.entries),
      'selected_count':len(selected),'selected_original_bytes':total,
      'entries':selected,'boot_map_string_candidates':sorted(candidates)
    }
    (out/'boot_extract_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print('BOOT_EXTRACT_OK',len(selected),total)

if __name__=='__main__': main()
