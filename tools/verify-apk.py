#!/usr/bin/env python3
"""Statically qualify one ABI-specific APK; does not certify device playback."""
import sys
import zipfile
from pathlib import Path

ARCH = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183)}

def verify(abi: str, filename: str) -> None:
    if abi not in ARCH:
        raise ValueError(f"Unsupported expected ABI: {abi}")
    path = Path(filename)
    if not path.is_file() or not path.stat().st_size:
        raise ValueError(f"Missing or empty APK: {path}")
    with zipfile.ZipFile(path) as z:
        corrupt = z.testzip()
        if corrupt:
            raise ValueError(f"Corrupt APK ZIP member: {corrupt}")
        names = set(z.namelist())
        required = {"AndroidManifest.xml", "classes.dex"}
        if not required.issubset(names):
            raise ValueError(f"Missing APK entries: {sorted(required - names)}")
        libs = sorted(n for n in names if n.startswith("lib/") and n.endswith(".so"))
        found = {n.split("/")[1] for n in libs}
        if found != {abi}:
            raise ValueError(f"Expected exactly {abi} native ABI, found {sorted(found)}")
        expected_class, expected_machine = ARCH[abi]
        for lib in libs:
            with z.open(lib) as binary:
                header = binary.read(20)
            if len(header) < 20 or header[:4] != b"\x7fELF":
                raise ValueError(f"Not an ELF shared library: {lib}")
            if header[4] != expected_class or header[5] != 1:
                raise ValueError(f"Wrong ELF class/endianness for {abi}: {lib}")
            if int.from_bytes(header[18:20], "little") != expected_machine:
                raise ValueError(f"Wrong ELF architecture for {abi}: {lib}")
        for token in ("mpv", "vlc"):
            if not any(token in name.lower() for name in libs):
                raise ValueError(f"Missing native {token} library for {abi}")
    print(f"PASS: {path.name} — {abi}, {len(libs)} native ELF libraries, {path.stat().st_size:,} bytes")
    print("NOTE: JNI loading, codec playback, TV remote behavior and memory are not device-verified")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Usage: python3 tools/verify-apk.py <armeabi-v7a|arm64-v8a> <APK>")
    try:
        verify(sys.argv[1], sys.argv[2])
    except (ValueError, zipfile.BadZipFile, OSError) as exc:
        sys.exit(f"FAIL: {exc}")
