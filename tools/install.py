#!/usr/bin/env python3
"""Check the pinned environment and prepare this module without clearing data.

The default --check path is read-only. Installation requires --apk explicitly.
The script never unlocks, flashes, clears data, or reboots the phone.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import shutil
import subprocess

TARGETS={
    "pixel-android17":{"sdk":"37","device":"mustang","build":"CP3A.261005.005","gms":"3d281d7ec8467cb94b33ba350a25b2dfbb92f3918a2dbfca8925e496dc75abde"},
    "q25-android14":{"sdk":"34","device":"Q25","fingerprint":"Xelex/Xelex10_Ultra/Xelex10_Ultra:14/20240427/UP1v:user/release-keys","gms":"489ae6b16a2dc4aa69ecbaab3a8f1e3735247a6fe91b92ba270b6c30d7a02820"},
}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial",required=True)
    p.add_argument("--variant",required=True,choices=TARGETS)
    p.add_argument("--adb",default=shutil.which("adb") or str(Path.home()/"Library/Android/sdk/platform-tools/adb"))
    mode=p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check",action="store_true")
    mode.add_argument("--apk",type=Path)
    a=p.parse_args()
    def adb(*args):
        r=subprocess.run([a.adb,"-s",a.serial,*map(str,args)],capture_output=True,text=True,timeout=120)
        if r.returncode:raise RuntimeError((r.stdout+r.stderr).strip())
        return r.stdout.strip()
    def root(command):return adb("shell","-T","su","-c",shlex.quote(command))
    def prop(name):return adb("shell","getprop",name)
    expected=TARGETS[a.variant]
    actual={"sdk":prop("ro.build.version.sdk"),"device":prop("ro.product.device"),"build":prop("ro.build.id"),"fingerprint":prop("ro.build.fingerprint")}
    for key in ["sdk","device","build","fingerprint"]:
        if key in expected and expected[key]!=actual[key]:raise SystemExit("Unsupported system: "+key+" differs. No changes made.")
    if "uid=0" not in root("id"):raise SystemExit("Root access is required.")
    base=next((line[8:] for line in adb("shell","pm","path","--user","0","com.google.android.gms").splitlines() if line.endswith("/base.apk")),None)
    if not base:raise SystemExit("Google Play services is not installed.")
    actual["gms_apk_sha256"]=root("sha256sum "+shlex.quote(base)).split()[0]
    if actual["gms_apk_sha256"]!=expected["gms"]:raise SystemExit("Unsupported GMS APK fingerprint. No changes made; do not bypass the guard.")
    cli="/data/adb/modules/zygisk_vector/cli"
    health=json.loads(root(cli+" --json status"))
    if not health.get("success") or int(health.get("data",{}).get("API Version",0))<102:
        raise SystemExit("Vector with modern API 102 and system support is required.")
    print(json.dumps({"compatible":True,"variant":a.variant,**actual},ensure_ascii=False,indent=2))
    if a.check:return
    if not a.apk.is_file():raise SystemExit("APK not found.")
    metadata_path=Path(str(a.apk)+".json")
    if not metadata_path.is_file():raise SystemExit("Download the matching APK .json metadata file from the same release.")
    meta=json.loads(metadata_path.read_text())
    if meta.get("variant")!=a.variant or meta.get("sha256")!=hashlib.sha256(a.apk.read_bytes()).hexdigest():
        raise SystemExit("APK variant or checksum does not match its metadata. No changes made.")
    if not meta.get("signer_certificate_sha256"):raise SystemExit("Unsigned builds cannot be installed.")
    print(adb("install","--no-incremental","-r","--user","0",a.apk))
    package="io.github.lichen.locationbridge"
    for permission in ["ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION","ACCESS_BACKGROUND_LOCATION","POST_NOTIFICATIONS"]:
        root("pm grant --user 0 "+package+" android.permission."+permission)
    root("pm enable --user 0 "+package+"/.NetworkLocationService")
    root("cmd deviceidle whitelist +"+package)
    for args in [["scope","set",package,"system/0","com.google.android.gms/0"],["modules","enable",package]]:
        result=json.loads(root(cli+" --json "+shlex.join(args)))
        if not result.get("success"):raise RuntimeError("Vector configuration failed: "+str(result))
    for key in ["lichen_location_bridge_enabled","lichen_location_bridge_gms_enabled"]:
        root("settings put global "+key+" 1")
    print("Prepared. Open the app, enter your own Baidu AK and consent, run and stop the two-minute test, then reboot manually and unlock once.")
    print("No account/app data was cleared; no device reboot, unlock or flash was performed.")

if __name__=="__main__":main()
