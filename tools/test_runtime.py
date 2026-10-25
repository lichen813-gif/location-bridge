#!/usr/bin/env python3
"""Run memory-only checks on a connected compatible phone, without installing.

No installed APK, application data, provider, account, settings or root configuration changes.
Temporary read-only test archives in /data/local/tmp are removed afterwards.
"""
import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from build import ROOT,VARIANTS

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial",required=True);p.add_argument("--variant",required=True,choices=VARIANTS)
    p.add_argument("--adb",default=shutil.which("adb") or str(Path.home()/"Library/Android/sdk/platform-tools/adb"))
    a=p.parse_args();cfg=VARIANTS[a.variant]
    def adb(*args):return subprocess.run([a.adb,"-s",a.serial,*map(str,args)],capture_output=True,text=True,timeout=60,check=True).stdout.strip()
    if adb("shell","getprop","ro.build.version.sdk")!=str(cfg["min_api"]):raise SystemExit("Phone API does not match variant.")
    sdk=Path(os.environ.get("ANDROID_SDK_ROOT",str(Path.home()/"Library/Android/sdk")))
    java=Path(os.environ["JAVA_HOME"])/"bin";tools=sdk/"build-tools/37.0.0"
    android=sdk/"platforms/android-37.0/android.jar"
    if not android.exists():android=sdk/"platforms/android-37/android.jar"
    b=ROOT/"build"/a.variant;out=b/"runtime-tests";out.mkdir(exist_ok=True)
    sources=sorted((ROOT/"variants"/a.variant/"tests").rglob("*.java"))
    cp=os.pathsep.join(map(str,[android,b/"app.jar",b/"libxposed-api.jar"]))
    subprocess.run([str(java/"javac"),"--release","17","-cp",cp,"-d",str(out),*map(str,sources)],check=True)
    jar=out/"tests.jar";dex=out/"tests.dex.jar"
    subprocess.run([str(java/"jar"),"cf",str(jar),"-C",str(out),"io"],check=True)
    subprocess.run([str(tools/"d8"),"--min-api",str(cfg["dex_api"]),"--lib",str(android),"--classpath",str(b/"app.jar"),"--output",str(dex),str(jar)],check=True,capture_output=True)
    apk=ROOT/"dist"/("location-bridge-"+a.variant+"-"+cfg["version"]+".apk")
    remote_apk="/data/local/tmp/location-bridge-public-test.apk";remote_test="/data/local/tmp/location-bridge-public-test.jar"
    try:
        adb("push",apk,remote_apk);adb("push",dex,remote_test)
        adb("shell","chmod","0444",remote_apk,remote_test)
        for cls in ["PublicConfigPolicyTestMain","GmsFreshnessPolicyTestMain","ProtocolTestMain"]:
            cmd="CLASSPATH="+shlex.quote(remote_apk+":"+remote_test)+" /system/bin/app_process /system/bin io.github.lichen.locationbridge."+cls
            print(adb("shell",cmd))
    finally:adb("shell","rm","-f",remote_apk,remote_test)

if __name__=="__main__":main()
