#!/usr/bin/env python3
"""Parse-only negative signature fixtures; no Android SDK/network required."""
from importlib import util
from pathlib import Path

spec = util.spec_from_file_location("apk_signing", Path(__file__).with_name("apk_signing.py"))
module = util.module_from_spec(spec)
spec.loader.exec_module(module)

def signature(hex_value="ab" * 32, modern=True):
    return ("Verifies\n"
            "Verified using v1 scheme (JAR signing): false\n"
            f"Verified using v2 scheme (APK Signature Scheme v2): {str(modern).lower()}\n"
            "Verified using v3 scheme (APK Signature Scheme v3): false\n"
            f"Signer #1 certificate SHA-256 digest: {hex_value}\n")


def rejects(output):
    try:
        module.certificate_from_output(output)
    except ValueError:
        return True
    return False


assert module.certificate_from_output(signature()) == "ab" * 32
assert module.certificate_from_output(signature("CD" * 32)) == "cd" * 32
assert rejects(signature(modern=False))
assert rejects(signature() + "Signer #2 certificate SHA-256 digest: " + "ab" * 32)
assert rejects(signature().replace("Signer #1", "Signer #7"))
assert rejects(signature().replace("ab" * 32, "not-sha256"))
assert rejects("Verifies")
print("PASS: signing parser rejects v1-only, absent, malformed and multiple signers")
