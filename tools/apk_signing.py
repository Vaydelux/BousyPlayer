#!/usr/bin/env python3
"""Require valid, consistently debug-signed ARM32 and ARM64 APKs.

apksigner checks Android signatures and both signer cert fingerprints.
A matching DEBUG certificate is not production signing or provenance.
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

CERT = re.compile(r"^Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", re.M)


def certificate_from_output(output):
    matches = CERT.findall(output)
    if len(matches) != 1 or matches[0][0] != "1":
        raise ValueError("Expected exactly one readable APK signing certificate")
    # Android 26+ must not rely on v1 signature alone.
    modern = any(re.search(r"^Verified using v[234] scheme.*:\s*true\s*$", output, re.M)
                 for _ in (0,))
    if not modern:
        raise ValueError("APK is not verified with v2+ Android signing")
    return matches[0][1].lower()


def verify_one(apksigner, apk):
    if not apk.is_file() or apk.is_symlink():
        raise ValueError("Missing or symlinked APK")
    result = subprocess.run([str(apksigner), "verify", "--verbose", "--print-certs",
                             str(apk)], text=True, capture_output=True, timeout=120)
    if result.returncode != 0:
        raise ValueError("Android APK signature verification failed")
    return certificate_from_output(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--arm32", type=Path, required=True)
    parser.add_argument("--arm64", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path)
    args = parser.parse_args()
    try:
        sdk = os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
        signer = args.apksigner or (Path(sdk) / "build-tools/36.0.0/apksigner" if sdk else None)
        if signer is None or not signer.is_file():
            raise ValueError("Android SDK 36 apksigner not found")
        results = {
            "armeabi-v7a": verify_one(signer, args.arm32),
            "arm64-v8a": verify_one(signer, args.arm64)
        }
        if len(set(results.values())) != 1:
            raise ValueError("ARM32 and ARM64 APKs use different signing certificates")
        report = {
            "schema": "bousyplayer.debug-signing-check/v1",
            "certificates_sha256_by_abi": results,
            "verification": "apksigner verify succeeded using v2+ for both APKs",
            "scope": "Matching debug signer only. Not a production key, APK attestation or certificate trust proof"
        }
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        print("PASS: both ARM APKs are v2+ signed and use the same SHA-256 signing certificate")
    except (ValueError, subprocess.TimeoutExpired, OSError) as ex:
        sys.exit(f"FAIL: {ex}")


if __name__ == "__main__":
    main()
