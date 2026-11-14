#!/usr/bin/env python3
"""Host-only input, rollback, cleanup and restart-recovery regression checks."""
import os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
java=Path(os.environ['JAVA_HOME'])/'bin'
base=ROOT/'variants/pixel-android17'
pkg='io/github/lichen/locationbridge'
out=ROOT/'build/manual-position-tests';out.mkdir(parents=True,exist_ok=True)
sources=[base/'src'/pkg/(name+'.java') for name in ['Coordinates','ManualPosition','ManualLocationController']]
sources.append(base/'tests'/pkg/'ManualPositionTest.java')
subprocess.run([str(java/'javac'),'--release','17','-d',str(out),*map(str,sources)],check=True)
subprocess.run([str(java/'java'),'-cp',str(out),'io.github.lichen.locationbridge.ManualPositionTest'],check=True)
