#!/usr/bin/env python3
"""Inventory every shipped ELF in the ARM32/ARM64 debug APKs.

Outputs deterministic per-file SHA256 and ELF metadata plus a CycloneDX 1.5
native-file SBOM. Does not infer native package versions or claim CVE coverage.
"""
import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

ARCH = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183)}
MAX_LIB_BYTES = 128 * 1024 * 1024
MAX_LIBS = 128
MAX_APK_BYTES = 500 * 1024 * 1024
PT_LOAD = 1
PT_DYNAMIC = 2
PT_NOTE = 4
PT_GNU_STACK = 0x6474E551
PT_GNU_RELRO = 0x6474E552
DT_TEXTREL = 22
DT_FLAGS = 30
DT_NEEDED = 1
DT_STRTAB = 5
DT_STRSZ = 10


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while block := source.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def inspect_elf(data, abi, path):
    expected_class, machine = ARCH[abi]
    require(len(data) >= 64 and data[:4] == b"\x7fELF", f"Invalid ELF: {path}")
    require(data[4] == expected_class and data[5] == 1 and data[6] == 1,
            f"Unexpected ELF class, byte order or version: {path}")
    require(struct.unpack_from("<H", data, 16)[0] == 3,
            f"Native file is not an ET_DYN shared object: {path}")
    require(struct.unpack_from("<H", data, 18)[0] == machine,
            f"Unexpected ELF machine for {abi}: {path}")
    is64 = expected_class == 2
    phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54 if is64 else 42)
    require(phentsize == (56 if is64 else 32) and 0 < phnum <= 256,
            f"Unusable ELF program-header table: {path}")
    require(phoff >= (64 if is64 else 52) and
            phoff + phentsize * phnum <= len(data),
            f"ELF program headers exceed binary: {path}")

    programs = []
    for number in range(phnum):
        pos = phoff + number * phentsize
        if is64:
            p_type, flags, offset, vaddr, _, size, memsize, _ = struct.unpack_from(
                "<IIQQQQQQ", data, pos)
        else:
            p_type, offset, vaddr, _, size, memsize, flags, _ = struct.unpack_from(
                "<IIIIIIII", data, pos)
        if p_type in (PT_LOAD, PT_DYNAMIC, PT_NOTE):
            require(offset <= len(data) and size <= len(data) - offset,
                    f"ELF segment extends beyond binary: {path}")
        programs.append((p_type, flags, offset, vaddr, size, memsize))

    # ELF security findings are enforced for the high-risk cases, not for
    # RELRO/Build-ID absence (prebuilt vendor binaries need separate review).
    require(not any(t == PT_LOAD and flags & 0x7 == 0x7
                    for t, flags, *_ in programs),
            f"Writable/executable (RWX) segment: {path}")
    stacks = [flags for t, flags, *_ in programs if t == PT_GNU_STACK]
    require(not any(flags & 1 for flags in stacks),
            f"Executable GNU_STACK: {path}")
    dynamic = []
    for t, _, offset, _, size, _ in programs:
        if t != PT_DYNAMIC:
            continue
        step = 16 if is64 else 8
        require(size % step == 0 and size // step <= 8192,
                f"Invalid ELF dynamic table: {path}")
        for pos in range(offset, offset + size, step):
            tag, value = struct.unpack_from("<qQ" if is64 else "<iI", data, pos)
            if tag == 0:
                break
            dynamic.append((tag, value))
    require(not any(tag == DT_TEXTREL or (tag == DT_FLAGS and value & 0x4)
                    for tag, value in dynamic),
            f"Native binary uses text relocations: {path}")

    build_id = None
    for t, _, offset, _, size, _ in programs:
        if t != PT_NOTE or build_id is not None:
            continue
        end = offset + size
        cursor = offset
        while cursor + 12 <= end:
            name_size, desc_size, note_type = struct.unpack_from("<III", data, cursor)
            cursor += 12
            aligned_name = (name_size + 3) & ~3
            aligned_desc = (desc_size + 3) & ~3
            if cursor + aligned_name + aligned_desc > end:
                break
            name = data[cursor:cursor + name_size]
            desc = data[cursor + aligned_name:cursor + aligned_name + desc_size]
            if name == b"GNU\x00" and note_type == 3 and 0 < desc_size <= 64:
                build_id = desc.hex()
                break
            cursor += aligned_name + aligned_desc

    needed = []
    needed_indexes = [value for tag, value in dynamic if tag == DT_NEEDED]
    if needed_indexes:
        strtab = next((value for tag, value in dynamic if tag == DT_STRTAB), None)
        strsize = next((value for tag, value in dynamic if tag == DT_STRSZ), None)
        require(strtab is not None and strsize is not None and strsize <= 1024 * 1024,
                f"Unusable dynamic library string table: {path}")
        resolved = next((offset + strtab - vaddr for t, _, offset, vaddr, size, mem in programs
                         if t == PT_LOAD and vaddr <= strtab < vaddr + mem
                         and strtab - vaddr < size), None)
        require(resolved is not None and resolved + strsize <= len(data),
                f"Missing dynamic library string table: {path}")
        require(len(needed_indexes) <= 256, f"Too many native dependencies: {path}")
        for index in needed_indexes:
            require(index < strsize, f"Invalid native dependency index: {path}")
            end = data.find(b"\x00", resolved + index, resolved + strsize)
            require(end >= 0 and end - (resolved + index) <= 256,
                    f"Invalid native dependency name: {path}")
            try:
                needed.append(data[resolved + index:end].decode("utf-8"))
            except UnicodeDecodeError as exc:
                raise ValueError(f"Invalid native dependency encoding: {path}") from exc

    return {
        "elf_class": "ELF64" if is64 else "ELF32",
        "elf_machine": machine,
        "gnu_build_id": build_id,
        "needed": sorted(set(needed)),
        "hardening": {
            "et_dyn": True,
            "no_rwx_load": True,
            "gnu_stack_executable": False if stacks else None,
            "gnu_relro_present": any(t == PT_GNU_RELRO for t, *_ in programs),
            "no_text_relocations": True,
        }
    }


def inventory_apk(apk, abi):
    require(abi in ARCH, f"Unsupported ABI: {abi}")
    require(apk.is_file() and 0 < apk.stat().st_size <= MAX_APK_BYTES,
            f"Missing or overlarge APK: {apk}")
    with zipfile.ZipFile(apk) as archive:
        infos = [i for i in archive.infolist()
                 if i.filename.startswith("lib/") and i.filename.endswith(".so")]
        require(0 < len(infos) <= MAX_LIBS, f"Missing or excessive native binaries: {apk.name}")
        names = [i.filename for i in infos]
        require(len(names) == len(set(names)), f"Duplicate native ZIP member: {apk.name}")
        libraries = []
        for info in sorted(infos, key=lambda x: x.filename):
            path = info.filename
            require(re.fullmatch(r"lib/[^/]+/lib[A-Za-z0-9._+@-]+\.so", path) is not None,
                    f"Unexpected native library path: {path}")
            require(path.split("/")[1] == abi, f"Cross-ABI native library in {apk.name}: {path}")
            require(not info.flag_bits & 1 and 64 <= info.file_size <= MAX_LIB_BYTES,
                    f"Encrypted, empty or oversized native binary: {path}")
            # zipfile validates CRC as files are read; bound decompression by
            # the ZIP-declared uncompressed size before allocation.
            contents = archive.read(info)
            require(len(contents) == info.file_size, f"Truncated binary: {path}")
            libraries.append({
                "path": path,
                "name": path.rsplit("/", 1)[-1],
                "size": info.file_size,
                "sha256": hashlib.sha256(contents).hexdigest(),
                **inspect_elf(contents, abi, path)
            })
        require(any("mpv" in x["name"] for x in libraries) and
                any("vlc" in x["name"] for x in libraries),
                f"Required native playback libraries missing: {apk.name}")
    return {
        "abi": abi,
        "apk_name": apk.name,
        "apk_size": apk.stat().st_size,
        "apk_sha256": sha_file(apk),
        "libraries": libraries,
    }


def collect(apks, version):
    require(bool(re.fullmatch(r"0\.\d+\.\d+", version)), "Invalid version")
    collected = [inventory_apk(apks[abi], abi) for abi in ARCH]
    # Require a uniform shipped feature set on ARM32 and ARM64.
    sets = [{item["name"] for item in entry["libraries"]} for entry in collected]
    require(sets[0] == sets[1], "ARM32 and ARM64 contain different native library names")
    return {
        "schema_version": 1,
        "app_version": version,
        "scope": "Packaged ELF shared objects from both debug APKs; exact native component versions and CVEs not established",
        "apks": collected,
        "summary": {
            "native_files": sum(len(item["libraries"]) for item in collected),
            "unique_library_names": len(sets[0]),
            "libraries_with_build_id": sum(
                bool(lib["gnu_build_id"]) for item in collected for lib in item["libraries"]),
            "libraries_missing_relro": sum(
                not lib["hardening"]["gnu_relro_present"]
                for item in collected for lib in item["libraries"])
        }
    }


def cyclonedx(document):
    components = []
    for apk in document["apks"]:
        for lib in apk["libraries"]:
            components.append({
                "type": "file",
                "name": lib["path"],
                "bom-ref": lib["path"],
                "hashes": [{"alg": "SHA-256", "content": lib["sha256"]}],
                "properties": [
                    {"name": "bousyplayer:abi", "value": apk["abi"]},
                    {"name": "bousyplayer:elf-class", "value": lib["elf_class"]},
                    {"name": "bousyplayer:gnu-build-id", "value": lib["gnu_build_id"] or "not-present"},
                ]
            })
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {
            "component": {"type": "application", "name": "BousyPlayer",
                          "version": document["app_version"]},
            "properties": [
                {"name": "bousyplayer:inventory-scope",
                 "value": "Packaged native ELF files only; no inferred native component versions or CVE status"}
            ]
        },
        "components": components
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--arm32", type=Path, required=True)
    parser.add_argument("--arm64", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--sbom", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = collect({"armeabi-v7a": args.arm32, "arm64-v8a": args.arm64},
                         args.version)
    except (ValueError, OSError, zipfile.BadZipFile, struct.error, EOFError) as error:
        print(f"FAIL: native inventory security gate: {error}", file=sys.stderr)
        return 1
    for target, content in ((args.inventory, report), (args.sbom, cyclonedx(report))):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(content, sort_keys=True, indent=2) + "\n",
                          encoding="utf-8")
    print("PASS: native inventory validated",
          report["summary"]["native_files"], "packaged ELF objects;",
          report["summary"]["libraries_with_build_id"], "GNU Build IDs;",
          report["summary"]["libraries_missing_relro"], "without GNU RELRO (review)")
    print("NOTE: ELF inventory and hardening flags do not establish native versions or CVE-free status")
    return 0


if __name__ == "__main__":
    sys.exit(main())
