#!/usr/bin/env python3
"""Check release checksums and inspect decompressed entries for forbidden values."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--forbid-value-file",action="append",type=Path,default=[],help="Private value to reject; file contents are never printed.")
    a=p.parse_args()
    forbidden=[f.read_bytes().strip() for f in a.forbid_value_file]
    if any(len(v)<16 for v in forbidden):raise SystemExit("Forbidden values must have at least 16 bytes.")
    def check(data,label):
        if any(v in data for v in forbidden):raise SystemExit("Private value found in "+label)
    names=subprocess.check_output(["git","ls-files","-co","--exclude-standard","-z"],cwd=ROOT).decode().split("\0")
    for name in filter(None,names):
        path=ROOT/name
        if path.suffix.lower() in [".p12",".jks",".keystore",".db",".sqlite",".apk"] or ".private" in path.parts:
            raise SystemExit("Private/binary file unexpectedly included in source tree: "+name)
        check(path.read_bytes(),name)
    apks=sorted((ROOT/"dist").glob("*.apk"))
    if len(apks)!=2:raise SystemExit("Expected exactly two signed public release APKs.")
    sums=[]
    for apk in apks:
        meta=json.loads(Path(str(apk)+".json").read_text())
        digest=hashlib.sha256(apk.read_bytes()).hexdigest()
        if digest!=meta["sha256"] or meta.get("embedded_baidu_key") is not False:raise SystemExit("Artifact metadata mismatch.")
        if not meta.get("signer_certificate_sha256"):raise SystemExit("Release APK is unsigned.")
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.namelist():check(archive.read(entry),apk.name+":"+entry)
        sums.append(digest+"  "+apk.name)
    (ROOT/"dist/SHA256SUMS").write_text("\n".join(sums)+"\n")
    print("PASS: source tree, both signed APKs and decompressed contents checked; checksums match.")

if __name__=="__main__":main()
