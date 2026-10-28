#!/usr/bin/env python3
"""Capture renderer stages and settings from the debuggable app. Emulator/device must be idle."""
import os, subprocess, time, shlex
from pathlib import Path
adb = os.environ.get('ADB', str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
root = Path(__file__).resolve().parents[1]
shots = root/'screenshots'; shots.mkdir(exist_ok=True)
def run(*args): return subprocess.run([adb,*args], check=True, capture_output=True)
for name,screen,progress in [
 ('01-initial','Preview',0), ('02-cut-25','Preview',.25), ('03-cut-50','Preview',.5),
 ('04-cut-75','Preview',.75), ('05-complete-wrong-cut','Preview',1),
 ('08-shortcut-settings','Secret shortcut',None), ('09-main-settings','Home',None),
 ('10-protected-apps','Protected applications',None), ('11-appearance','Appearance',None)]:
    args=['shell','am','start','-S','-W','-n','com.kawaiilock.app/.MainActivity','--es','screen',screen]
    if progress is not None: args+=['--ef','progress',str(progress)]
    run('shell', shlex.join(args[1:])); time.sleep(2.5)
    (shots/f'{name}.png').write_bytes(run('exec-out','screencap','-p').stdout)
print('Captured',len(list(shots.glob('*.png'))),'screenshots')
