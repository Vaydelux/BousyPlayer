#!/usr/bin/env python3
"""Catch mismatched source versions and debug APK release names before publishing.

This is a static source gate, not Android compilation or release certification.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
gradle = (root / 'app/build.gradle.kts').read_text()
workflow = (root / '.github/workflows/android.yml').read_text()
readme = (root / 'README.md').read_text()

def required(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text, re.MULTILINE)
    if not match:
        raise AssertionError(f'{label} missing or invalid')
    return match.group(1)

version = required(r'^\s*versionName\s*=\s*"(\d+\.\d+\.\d+)"', gradle, 'versionName')
code = int(required(r'^\s*versionCode\s*=\s*(\d+)', gradle, 'versionCode'))
minor = int(version.split('.')[1])
assert code == minor, f'versionCode {code} does not match versionName {version}'
assert f'Bousy Player v{version.rsplit(".", 1)[0]}' in readme, 'README release title mismatch'
assert f'BousyPlayer-v{version.rsplit(".", 1)[0]}-debug.apk' in workflow, 'CI release APK filename mismatch'
assert f'debug-v{version.rsplit(".", 1)[0]}-' in workflow and 'gh release create' in workflow, 'CI prerelease publication missing'
print(f'PASS: source version, README, and CI artifact names match ({version}, code {code})')
