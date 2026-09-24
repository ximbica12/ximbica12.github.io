import json, struct, sys, re

path=sys.argv[1]
with open(path,'rb') as f:
    data=f.read()
magic,version,total=struct.unpack_from('<4sII',data,0)
assert magic==b'glTF', magic
off=12
doc=None
while off < len(data):
    clen,ctype=struct.unpack_from('<II',data,off); off += 8
    chunk=data[off:off+clen]; off += clen
    if ctype==0x4E4F534A:
        doc=json.loads(chunk.decode('utf-8').rstrip('\x00 \t\r\n'))
        break
if doc is None:
    raise RuntimeError('No JSON chunk')

print('GLTF version', version, 'bytes', total)
print('extensionsUsed', doc.get('extensionsUsed'))
nodes=doc.get('nodes',[])
print('nodes', len(nodes), 'meshes', len(doc.get('meshes',[])), 'skins', len(doc.get('skins',[])))

vrm=doc.get('extensions',{}).get('VRM',{})
meta=vrm.get('meta',{})
print('--- VRM meta ---')
for key in ('title','version','author','contactInformation','reference','allowedUserName','violentUssageName','sexualUssageName','commercialUssageName','licenseName','otherLicenseUrl'):
    if key in meta:
        print(key, '=', meta.get(key))

pat=re.compile(r'(bust|breast|mune|chest|upperchest|spine|胸|乳)', re.I)
print('--- candidate chest/bust nodes ---')
for i,n in enumerate(nodes):
    name=n.get('name','')
    if pat.search(name):
        print(i, name)

hum=vrm.get('humanoid',{}).get('humanBones',[])
print('--- humanoid chest mapping ---')
for h in hum:
    if h.get('bone') in ('chest','upperChest','spine','hips','neck','head'):
        idx=h.get('node')
        print(h.get('bone'), idx, nodes[idx].get('name','') if isinstance(idx,int) and idx<len(nodes) else '')

sec=vrm.get('secondaryAnimation',{})
bgs=sec.get('boneGroups',[])
print('spring bone groups', len(bgs))
for j,g in enumerate(bgs):
    roots=[nodes[i].get('name','') for i in g.get('bones',[]) if isinstance(i,int) and i<len(nodes)]
    print('group',j,'comment=',g.get('comment'),'stiff=',g.get('stiffiness'),'drag=',g.get('dragForce'),'bones=',roots[:16])

print('--- mesh summary ---')
for i,m in enumerate(doc.get('meshes',[])):
    print(i,m.get('name',''), 'prims', len(m.get('primitives',[])))
