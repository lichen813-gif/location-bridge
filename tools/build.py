#!/usr/bin/env python3
"""Build either pinned adapter using verified dependencies and standard SDK tools.

No API key is accepted by this build: public APKs obtain the user's key at runtime.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = {
    "pixel-android17": {"min_api": 37, "dex_api": 36, "version": "0.5.4-public", "code": 12},
    "q25-android14": {"min_api": 34, "dex_api": 34, "version": "0.6.1-public", "code": 11},
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=[*VARIANTS, "all"], default="all")
    signing = parser.add_mutually_exclusive_group(required=True)
    signing.add_argument("--keystore", type=Path)
    signing.add_argument("--unsigned", action="store_true")
    parser.add_argument("--key-alias", default="locationbridge")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", str(Path.home()/"Library/Android/sdk"))))
    java = Path(os.environ["JAVA_HOME"])/"bin" if os.environ.get("JAVA_HOME") else None
    javac = str(java/"javac") if java else shutil.which("javac")
    if not javac:
        parser.error("Set JAVA_HOME to a JDK 21 installation.")
    java_bin = Path(javac).parent
    platform = sdk/"platforms/android-37.0/android.jar"
    if not platform.exists(): platform = sdk/"platforms/android-37/android.jar"
    tools = sdk/"build-tools/37.0.0"
    if not platform.exists() or not (tools/"aapt2").exists():
        parser.error("Android SDK Platform 37 and Build-Tools 37.0.0 are required.")
    env = dict(os.environ)
    env["PATH"] = str(java_bin)+os.pathsep+env.get("PATH", "")
    def run(*command, capture=False):
        return subprocess.run(list(map(str, command)), env=env, check=True, capture_output=capture)
    cert_sha = ""
    if args.keystore:
        if not args.keystore.is_file(): parser.error("Keystore does not exist.")
        if not env.get("LB_KEYSTORE_PASSWORD"): parser.error("Set LB_KEYSTORE_PASSWORD privately; it is never embedded in the APK.")
        cert = run(java_bin/"keytool", "-exportcert", "-keystore", args.keystore,
                   "-storepass:env", "LB_KEYSTORE_PASSWORD", "-alias", args.key_alias, capture=True).stdout
        cert_sha = sha(cert)
    cache = ROOT/".cache/dependencies"
    cache.mkdir(parents=True, exist_ok=True)
    dependencies = json.loads((ROOT/"tools/dependencies.lock.json").read_text())
    for dependency in dependencies:
        path = cache/dependency["file"]
        if not path.exists():
            req = urllib.request.Request(dependency["url"], headers={"User-Agent":"LocationBridge-build"})
            with urllib.request.urlopen(req, timeout=60) as response: content=response.read()
            if sha(content) != dependency["sha256"]: raise RuntimeError("Dependency checksum mismatch: "+path.name)
            path.write_bytes(content)
        if sha(path.read_bytes()) != dependency["sha256"]: raise RuntimeError("Dependency checksum mismatch: "+path.name)
    selected = VARIANTS if args.variant == "all" else {args.variant:VARIANTS[args.variant]}
    dist = ROOT/"dist"
    dist.mkdir(exist_ok=True)
    for variant, config in selected.items():
        source = ROOT/"variants"/variant
        build = ROOT/"build"/variant
        if build.exists(): shutil.rmtree(build)
        for directory in ["classes", "dex", "generated/io/github/lichen/locationbridge"]:
            (build/directory).mkdir(parents=True, exist_ok=True)
        generated = build/"generated/io/github/lichen/locationbridge/BuildIdentity.java"
        generated.write_text('package io.github.lichen.locationbridge; final class BuildIdentity { static final String CERT_SHA256 = '+json.dumps(cert_sha)+'; }\n')
        api = build/"libxposed-api.jar"
        jars = []
        native = {}
        for dependency in dependencies:
            path = cache/dependency["file"]
            if dependency.get("compile_only"):
                with zipfile.ZipFile(path) as archive: api.write_bytes(archive.read("classes.jar"))
                continue
            if path.suffix == ".jar": jars.append(path); continue
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.endswith(".jar"):
                        dest=build/(path.stem+"-"+Path(name).name)
                        dest.write_bytes(archive.read(name));jars.append(dest)
                    if name.startswith("jni/arm64-v8a/") and name.endswith(".so"):
                        native["lib/arm64-v8a/"+Path(name).name]=archive.read(name)
        run(javac,"--release","17","-encoding","UTF-8","-cp",os.pathsep.join(map(str,[platform,api,*jars])),
            "-d",build/"classes",*sorted((source/"src").rglob("*.java")),generated)
        run(java_bin/"jar","cf",build/"app.jar","-C",build/"classes",".")
        # libxposed is supplied by Vector, so it must not be packaged into the app.
        run(tools/"d8","--lib",platform,"--classpath",api,"--min-api",config["dex_api"],
            "--output",build/"dex",build/"app.jar",*jars,capture=True)
        run(tools/"aapt2","compile","--dir",source/"res","-o",build/"resources.zip")
        run(tools/"aapt2","link","--manifest",source/"AndroidManifest.xml","-I",platform,
            "--min-sdk-version",config["min_api"],"--target-sdk-version",config["min_api"],
            "--version-code",config["code"],"--version-name",config["version"],
            "-o",build/"unsigned.apk",build/"resources.zip")
        with zipfile.ZipFile(build/"unsigned.apk","a",compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((build/"dex").glob("*.dex")): archive.write(path,path.name)
            for name,data in native.items(): archive.writestr(name,data)
            for notice in [ROOT/"LICENSE", ROOT/"THIRD_PARTY_NOTICES.md", *sorted((ROOT/"third_party").glob("*"))]:
                if notice.is_file(): archive.write(notice,"assets/licenses/"+notice.name)
            for path in sorted((source/"resources").rglob("*")):
                if path.is_file(): archive.write(path,path.relative_to(source/"resources").as_posix())
        run(tools/"zipalign","-P","16","-f","4",build/"unsigned.apk",build/"aligned.apk")
        suffix="-unsigned" if args.unsigned else ""
        apk=dist/("location-bridge-"+variant+"-"+config["version"]+suffix+".apk")
        if args.keystore:
            run(tools/"apksigner","sign","--ks",args.keystore,"--ks-key-alias",args.key_alias,
                "--ks-pass","env:LB_KEYSTORE_PASSWORD","--key-pass","env:LB_KEYSTORE_PASSWORD",
                "--out",apk,build/"aligned.apk")
            verification=run(tools/"apksigner","verify","--verbose","--print-certs",apk,capture=True).stdout
            (dist/(apk.name+".signature.txt")).write_bytes(verification)
        else: shutil.copy2(build/"aligned.apk",apk)
        metadata={"variant":variant,**config,"apk":apk.name,"sha256":sha(apk.read_bytes()),
                  "package":"io.github.lichen.locationbridge","signer_certificate_sha256":cert_sha,
                  "embedded_baidu_key":False,"api_key_configuration":"runtime, app-private SharedPreferences"}
        (dist/(apk.name+".json")).write_text(json.dumps(metadata,indent=2)+"\n")
        print("Built",apk.name,metadata["sha256"])

if __name__ == "__main__": main()
