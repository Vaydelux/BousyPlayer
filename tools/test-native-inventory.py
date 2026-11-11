#!/usr/bin/env python3
"""Offline native APK/ELF inventory regressions; no Android SDK required."""
import importlib.util
import struct
import tempfile
import warnings
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "native_inventory", Path(__file__).with_name("native_inventory.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def elf(abi, *, stack_exec=False, rwx=False, textrel=False, with_note=True):
    klass, machine = module.ARCH[abi]
    is64 = klass == 2
    blob = bytearray(1024)
    blob[:7] = b"\x7fELF" + bytes((klass, 1, 1))
    struct.pack_into("<H", blob, 16, 3)
    struct.pack_into("<H", blob, 18, machine)
    struct.pack_into("<Q" if is64 else "<I", blob, 32 if is64 else 28,
                     64 if is64 else 52)
    struct.pack_into("<HH", blob, 54 if is64 else 42, 56 if is64 else 32, 4)
    headers = [
        (module.PT_LOAD, 7 if rwx else 5, 0, 0x1000, 1024),
        (module.PT_GNU_STACK, 7 if stack_exec else 6, 0, 0, 0),
        (module.PT_GNU_RELRO, 4, 300, 0x112c, 16),
        (module.PT_NOTE, 4, 400, 0x1190, 28 if with_note else 0),
    ]
    # In an optional dynamic-table test, make the 3rd program header PT_DYNAMIC.
    if textrel:
        headers[2] = (module.PT_DYNAMIC, 4, 300, 0x112c, 16)
        struct.pack_into("<qQ" if is64 else "<iI", blob, 300, module.DT_TEXTREL, 1)
    for index, (kind, flags, offset, vaddr, size) in enumerate(headers):
        pos = (64 if is64 else 52) + index * (56 if is64 else 32)
        if is64:
            struct.pack_into("<IIQQQQQQ", blob, pos, kind, flags, offset, vaddr,
                             0, size, size, 4)
        else:
            struct.pack_into("<IIIIIIII", blob, pos, kind, offset, vaddr, 0,
                             size, size, flags, 4)
    if with_note:
        struct.pack_into("<III", blob, 400, 4, 8, 3)
        blob[412:416] = b"GNU\x00"
        blob[416:424] = b"BUILD-ID"
    return bytes(blob)


def fixture(path, abi, *, stack_exec=False, rwx=False, textrel=False,
            extra_name=False, duplicate=False):
    data = elf(abi, stack_exec=stack_exec, rwx=rwx, textrel=textrel)
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"manifest")
        z.writestr("classes.dex", b"dex")
        for name in ("libmpv.so", "libvlc.so"):
            z.writestr(f"lib/{abi}/{name}", data)
        if extra_name:
            z.writestr(f"lib/{abi}/libextra.so", data)
        if duplicate:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                z.writestr(f"lib/{abi}/libvlc.so", data)


def fail(callback, needle):
    try:
        callback()
    except ValueError as e:
        assert needle in str(e), (needle, str(e))
        return
    raise AssertionError(f"Did not reject {needle}")


def run():
    with tempfile.TemporaryDirectory() as temp:
        a = Path(temp) / "arm32.apk"
        b = Path(temp) / "arm64.apk"
        fixture(a, "armeabi-v7a")
        fixture(b, "arm64-v8a")
        result = module.collect({"armeabi-v7a": a, "arm64-v8a": b}, "0.28.0")
        assert result["summary"]["native_files"] == 4
        assert result["summary"]["libraries_with_build_id"] == 4
        assert result["summary"]["libraries_missing_relro"] == 0
        sbom = module.cyclonedx(result)
        assert sbom["bomFormat"] == "CycloneDX"
        assert len(sbom["components"]) == 4
        assert all(len(x["hashes"][0]["content"]) == 64 for x in sbom["components"])
        assert sbom["components"][0]["type"] == "file"
        fail(lambda: module.inspect_elf(elf("armeabi-v7a"), "arm64-v8a", "wrong"),
             "ELF class")
        fail(lambda: module.inspect_elf(elf("armeabi-v7a", stack_exec=True),
                                        "armeabi-v7a", "bad"), "Executable GNU_STACK")
        fail(lambda: module.inspect_elf(elf("arm64-v8a", rwx=True),
                                        "arm64-v8a", "bad"), "RWX")
        fail(lambda: module.inspect_elf(elf("arm64-v8a", textrel=True),
                                        "arm64-v8a", "bad"), "text relocations")
        fail(lambda: module.inspect_elf(b"not ELF", "armeabi-v7a", "bad"), "Invalid ELF")
        fixture(a, "armeabi-v7a", duplicate=True)
        fail(lambda: module.inventory_apk(a, "armeabi-v7a"),
             "Duplicate native ZIP member")
        fixture(a, "armeabi-v7a", extra_name=True)
        fail(lambda: module.collect({"armeabi-v7a": a, "arm64-v8a": b}, "0.28.0"),
             "different native library names")
        fail(lambda: module.collect({"armeabi-v7a": a, "arm64-v8a": b},
                                    "bad-version"), "Invalid version")
    print("PASS: ELF32/ELF64 metadata, Build IDs, CycloneDX, RWX/stack/textrel guards, ZIP and ABI mismatch regression tests")


if __name__ == "__main__":
    run()
