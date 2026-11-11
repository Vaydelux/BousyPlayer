#!/usr/bin/env python3
"""Minimal independent ABI-validator regression tests; no Android toolchain needed."""
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
ARCH = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183)}

def fixture(path: Path, native_abi: str, machine: int, include_other: bool = False):
    klass = ARCH[native_abi][0]
    header = bytearray(20)
    header[0:4] = b"\x7fELF"
    header[4] = klass
    header[5] = 1
    header[18:20] = machine.to_bytes(2, "little")
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"manifest")
        z.writestr("classes.dex", b"dex\n")
        for name in ("libmpv.so", "libvlc.so"):
            z.writestr(f"lib/{native_abi}/{name}", header)
        if include_other:
            z.writestr("lib/x86_64/libother.so", header)

with tempfile.TemporaryDirectory() as directory:
    file = Path(directory) / "test.apk"
    for abi, (_, machine) in ARCH.items():
        fixture(file, abi, machine)
        passed = subprocess.run([sys.executable, str(ROOT / "verify-apk.py"), abi, str(file)],
                                capture_output=True, text=True)
        assert passed.returncode == 0, (abi, passed.stdout, passed.stderr)
        # A binary with the right filename but the wrong ELF machine is invalid.
        fixture(file, abi, 999)
        invalid = subprocess.run([sys.executable, str(ROOT / "verify-apk.py"), abi, str(file)],
                                 capture_output=True, text=True)
        assert invalid.returncode != 0 and "architecture" in invalid.stderr
        fixture(file, abi, machine, include_other=True)
        cross = subprocess.run([sys.executable, str(ROOT / "verify-apk.py"), abi, str(file)],
                               capture_output=True, text=True)
        assert cross.returncode != 0 and "exactly" in cross.stderr
print("PASS: ABI validator accepts both ARM variants and rejects wrong-machine/cross-ABI APKs")
