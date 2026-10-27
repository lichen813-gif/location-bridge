#!/usr/bin/env python3
"""Host-only compatibility guard regression checks; no device changes."""
from pathlib import Path
import subprocess, tempfile, zipfile, json
import os, argparse
ROOT=Path(__file__).resolve().parents[1]
JAVA=Path(os.environ["JAVA_HOME"])
ENV=dict(os.environ)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--old-apk",required=True,type=Path)
parser.add_argument("--new-apk",required=True,type=Path)
args=parser.parse_args()

out=ROOT/'build/contract-test'
out.mkdir(parents=True,exist_ok=True)
subprocess.run([str(JAVA/'bin/javac'),'-d',str(out),str(ROOT/'variants/pixel-android17/src/io/github/lichen/locationbridge/DexContract.java')],env=ENV,check=True)
def check(apk,names):
 return subprocess.run([str(JAVA/'bin/java'),'-cp',str(out),'io.github.lichen.locationbridge.DexContract',str(apk),*names],env=ENV,capture_output=True,text=True)
passed=[]
for label,source in [('old',args.old_apk),('new',args.new_apk)]:
 expected=(ROOT/'variants/pixel-android17/tests/gms-contract'/(label+'.txt')).read_text()
 names=[line.split('=')[0] for line in expected.splitlines()]
 assert check(source,names).stdout==expected
 passed.append(label+': reviewed class fingerprints match')
 if label!='new':continue
 with tempfile.TemporaryDirectory() as tmp:
  temp=Path(tmp)
  with zipfile.ZipFile(source) as original:
   dex={n:original.read(n) for n in original.namelist() if n.endswith('.dex')}
  def write(path,entries,extra=False):
   with zipfile.ZipFile(path,'w') as z:
    for n,b in entries.items():z.writestr(n,b)
    if extra:z.writestr('assets/unrelated-update.txt',b'unrelated APK change')
  # Remove all resources and change unrelated APK metadata: same location code still passes.
  write(temp/'unrelated.apk',dex,True)
  assert check(temp/'unrelated.apk',names).stdout==expected
  passed.append('unrelated APK resources/metadata: accepted')
  changed=dict(dex)
  assert b'NlpRequestHelper' in changed['classes2.dex']
  changed['classes2.dex']=changed['classes2.dex'].replace(b'NlpRequestHelper\x00',b'MlpRequestHelper\x00')
  write(temp/'changed.apk',changed)
  result=check(temp/'changed.apk',names)
  assert result.returncode==0 and result.stdout!=expected
  passed.append('referenced string changed without changing instruction indices: rejected')
  write(temp/'missing.apk',{n:b for n,b in dex.items() if n!='classes2.dex'})
  assert check(temp/'missing.apk',names).returncode!=0
  passed.append('missing helper/receiver classes: rejected')
report={'result':'PASS','checks':passed,'scope':'host only; modified copies never installed'}

print(json.dumps(report,indent=2))
