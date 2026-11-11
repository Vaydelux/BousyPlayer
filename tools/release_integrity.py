#!/usr/bin/env python3
"""Build and verify the exact debug release asset set from local bytes.

The unsigned SHA-256 manifest is useful integrity evidence, NOT an identity
signature, trusted SLSA provenance or a reproducible-build certification.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SCHEMA = "bousyplayer.debug-release-integrity/v1"
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
VERSION = re.compile(r"0\.\d+\.\d+\Z")
REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
CHECKSUM_LINE = re.compile(r"([0-9a-f]{64})  ([^\r\n/\\]+)\Z")


def require(ok, description):
    if not ok:
        raise ValueError(description)


def artifact_names(version):
    short = version.rsplit(".", 1)[0]
    prefix = f"BousyPlayer-v{short}-"
    return [
        prefix + "armeabi-v7a-debug.apk",
        prefix + "arm64-v8a-debug.apk",
        prefix + "maven-sbom.cdx.json",
        prefix + "native-file-sbom.cdx.json",
        prefix + "native-inventory.json",
        prefix + "osv-report.json",
        prefix + "signing-report.json",
    ]


def safe_file(directory, name):
    require(name == Path(name).name and not name.startswith("."),
            "Invalid artifact filename")
    p = directory / name
    require(p.is_file() and not p.is_symlink(), f"Missing/linked release artifact: {name}")
    size = p.stat().st_size
    require(0 < size <= 500 * 1024 * 1024, f"Unexpected artifact size: {name}")
    return p, size


def hash_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(part)
    return digest.hexdigest()


def metadata(args):
    require(VERSION.fullmatch(args.version) is not None, "Invalid release version")
    require(COMMIT.fullmatch(args.commit) is not None, "Invalid full source commit")
    require(REPOSITORY.fullmatch(args.repository) is not None, "Invalid repository name")
    require(args.run_id.isdigit() and int(args.run_id) > 0, "Invalid workflow run id")
    return {
        "schema": SCHEMA,
        "version": args.version,
        "source": {
            "repository": args.repository,
            "commit": args.commit,
            "workflow_run_id": args.run_id,
            "workflow_url": f"https://github.com/{args.repository}/actions/runs/{args.run_id}",
        },
        "claim_scope": "GitHub Actions debug asset bytes and build metadata; not a signed attestation, production key, or reproducible build",
    }


def expected_manifest(args):
    manifest = metadata(args)
    manifest["artifacts"] = []
    for name in artifact_names(args.version):
        path, size = safe_file(args.dist, name)
        manifest["artifacts"].append({
            "filename": name, "bytes": size, "sha256": hash_file(path)
        })
    return manifest


def checksum_data(directory):
    path, _ = safe_file(directory, "SHA256SUMS.txt")
    content = path.read_text(encoding="utf-8")
    require(content.endswith("\n"), "Checksum manifest lacks trailing newline")
    mapping = {}
    for line in content.splitlines():
        match = CHECKSUM_LINE.fullmatch(line)
        require(match is not None, "Malformed checksum line")
        digest, filename = match.groups()
        require(filename not in mapping, "Duplicate checksum entry")
        mapping[filename] = digest
    return mapping


def verify(args):
    require(args.dist.is_dir() and not args.dist.is_symlink(), "Missing release directory")
    expected = expected_manifest(args)
    manifest_path, _ = safe_file(args.dist, "release-manifest.json")
    loaded = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(loaded == expected, "Release manifest differs from actual artifact bytes, commit, or version")
    names = {x["filename"] for x in expected["artifacts"]}
    checksum_names = names | {"release-manifest.json"}
    expected_directory = checksum_names | {"SHA256SUMS.txt"}
    actual_directory = {x.name for x in args.dist.iterdir()}
    require(actual_directory == expected_directory,
            f"Unexpected release directory contents: {sorted(actual_directory ^ expected_directory)}")
    checksums = checksum_data(args.dist)
    require(set(checksums) == checksum_names, "Checksum list missing/extra release artifact")
    for name in checksum_names:
        p, _ = safe_file(args.dist, name)
        require(hash_file(p) == checksums[name], f"Checksum mismatch: {name}")
    print(f"PASS: release integrity verified {len(checksum_names)} checksummed files, "
          f"{len(names)} directly inventoried artifacts, exact commit {args.commit[:8]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("create", "verify"))
    parser.add_argument("--dist", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    try:
        if args.command == "create":
            require(args.dist.is_dir() and not args.dist.is_symlink(), "Missing release directory")
            result = expected_manifest(args)
            target = args.dist / "release-manifest.json"
            require(not target.is_symlink(), "Refusing symlink manifest")
            target.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n",
                              encoding="utf-8")
            print(f"PASS: generated debug release manifest for {len(result['artifacts'])} artifacts")
        else:
            verify(args)
    except (ValueError, OSError, json.JSONDecodeError) as ex:
        sys.exit(f"FAIL: {ex}")


if __name__ == "__main__":
    main()
