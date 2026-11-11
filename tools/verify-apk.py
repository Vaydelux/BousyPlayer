#!/usr/bin/env python3
"""Only claim a debug APK exists after Android Gradle produced real bytes.

Checks ZIP integrity, DEX, native ELF presence for both requested ARM ABIs. It is
NOT an install test and cannot certify ABI compatibility or codec behavior.
"""
from pathlib import Path
import sys
import zipfile

if len(sys.argv) != 2:
    print('Usage: python3 tools/verify-apk.py <app-debug.apk>', file=sys.stderr)
    sys.exit(2)
apk = Path(sys.argv[1])
if not apk.is_file() or apk.stat().st_size == 0:
    print(f'FAIL: missing/nonempty APK: {apk}', file=sys.stderr)
    sys.exit(1)
with zipfile.ZipFile(apk) as z:
    bad = z.testzip()
    if bad:
        raise SystemExit(f'FAIL: corrupt APK entry {bad}')
    entries = set(z.namelist())
    for required in ('AndroidManifest.xml', 'classes.dex'):
        if required not in entries:
            raise SystemExit(f'FAIL: missing {required}')
    for abi in ('armeabi-v7a', 'arm64-v8a'):
        libs = [x for x in entries if x.startswith(f'lib/{abi}/') and x.endswith('.so')]
        for engine in ('mpv', 'vlc'):
            matches = [x for x in libs if engine in x.lower()]
            if not matches:
                raise SystemExit(f'FAIL: {engine} missing for {abi}')
            if not any(z.open(x).read(4) == b'\x7fELF' for x in matches):
                raise SystemExit(f'FAIL: {engine} {abi} is not an ELF binary')
        print(f'PASS: {abi} has native mpv and VLC ELF objects')
    print(f'PASS: APK archive integrity and mandatory entries ({apk.stat().st_size:,} bytes)')
print('NOTE: device playback, native loading and C++ ABI interoperability remain unverified')
