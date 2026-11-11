#!/usr/bin/env python3
"""Catch mismatched source versions and debug APK release names before publishing.

This is a static source gate, not Android compilation or release certification.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
gradle = (root / 'app/build.gradle.kts').read_text()
qualification_workflow = (root / '.github/workflows/android.yml').read_text()
workflow = (root / '.github/workflows/release.yml').read_text()
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
# The release step expands the ABI filename dynamically inside its shell loop.
assert 'for abi in armeabi-v7a arm64-v8a' in workflow, 'CI must publish both ABI variants'
assert f'BousyPlayer-v{version.rsplit(".", 1)[0]}-$abi-debug.apk' in workflow, 'CI per-ABI APK filename template missing'
qualification = (root / 'tools/build-and-qualify.sh').read_text()
assert ':session-smoke:run' in qualification, 'Session regression gate missing from CI build'
assert ':native-smoke:run' in qualification, 'Native adapter regression gate missing from CI build'
assert 'SHA256SUMS.txt' in workflow, 'Release checksums missing from CI'
# Ordinary PR/feature builds never hold release write credentials.
assert 'contents: read' in qualification_workflow, 'Qualification must be read-only'
assert 'contents: write' not in qualification_workflow, 'PR builds must never have write access'
assert 'gh release create' not in qualification_workflow, 'PR builds must not publish'
assert 'pull_request:' in qualification_workflow, 'PR qualification trigger missing'
assert 'branches-ignore: [main]' in qualification_workflow, 'Main should not build twice'
assert 'on:\\n  push:\\n    branches: [main]'.replace('\\n','\n') in workflow, 'Release must trigger only on main'
assert 'pull_request:' not in workflow, 'Release must not trigger from pull requests'
assert 'contents: write' in workflow, 'Main-only publisher needs write scope'
assert 'bash tools/build-and-qualify.sh' in workflow, 'Publisher must rebuild and qualify APKs'
assert 'isUniversalApk = false' in gradle, 'Universal ABI APK must be disabled'
assert 'include("armeabi-v7a", "arm64-v8a")' in gradle, 'Expected ARM ABI split configuration missing'
assert f'debug-v{version.rsplit(".", 1)[0]}-' in workflow and 'gh release create' in workflow, 'CI prerelease publication missing'
print(f'PASS: source version, README, and CI artifact names match ({version}, code {code})')
