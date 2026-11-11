#!/usr/bin/env python3
"""No Android SDK needed: prove byte tampering/missing/wrong-commit failures."""
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VERSION = "0.32.0"
COMMIT = "a" * 40
REPO = "Vaydelux/BousyPlayer"
RUN = "123456"


def call(directory, action, commit=COMMIT):
    return subprocess.run([
        sys.executable, str(ROOT / "release_integrity.py"), action,
        "--dist", str(directory), "--version", VERSION,
        "--commit", commit, "--repository", REPO, "--run-id", RUN
    ], capture_output=True, text=True)


with tempfile.TemporaryDirectory() as directory:
    dist = Path(directory)
    from importlib import util
    spec = util.spec_from_file_location("release_integrity", ROOT / "release_integrity.py")
    module = util.module_from_spec(spec)
    spec.loader.exec_module(module)
    for filename in module.artifact_names(VERSION):
        (dist / filename).write_bytes(("artifact data:" + filename).encode("utf-8"))

    made = call(dist, "create")
    assert made.returncode == 0, made.stderr
    # Create SHA manifest the same way as main-only publisher (one entry per file).
    manifest_names = module.artifact_names(VERSION) + ["release-manifest.json"]
    (dist / "SHA256SUMS.txt").write_text(
        "".join(f"{module.hash_file(dist / name)}  {name}\n" for name in manifest_names),
        encoding="utf-8"
    )
    accepted = call(dist, "verify")
    assert accepted.returncode == 0, accepted.stderr
    assert call(dist, "verify", "b" * 40).returncode != 0, "Wrong source commit accepted"

    apk = dist / module.artifact_names(VERSION)[0]
    original_apk = apk.read_bytes()
    apk.write_bytes(original_apk + b"tampered")
    assert call(dist, "verify").returncode != 0, "Tampered APK accepted"
    apk.write_bytes(original_apk)
    assert call(dist, "verify").returncode == 0, "Clean APK rejected"

    manifest = dist / "release-manifest.json"
    manifest.write_text(manifest.read_text().replace("debug asset bytes", "FAKE DATA"))
    assert call(dist, "verify").returncode != 0, "Edited release manifest accepted"
    assert call(dist, "create").returncode == 0
    assert call(dist, "verify").returncode == 0

    report = dist / module.artifact_names(VERSION)[-1]
    report.unlink()
    assert call(dist, "verify").returncode != 0, "Missing signing report accepted"
    report.write_bytes(("artifact data:" + report.name).encode())
    (dist / "unexpected.txt").write_text("surprise")
    assert call(dist, "verify").returncode != 0, "Unexpected artifact accepted"
    (dist / "unexpected.txt").unlink()

    sums = dist / "SHA256SUMS.txt"
    sums.write_text(sums.read_text() + sums.read_text().splitlines()[0] + "\n")
    assert call(dist, "verify").returncode != 0, "Duplicate checksum entry accepted"
    print("PASS: deterministic release evidence; tampered bytes, metadata, missing/extra assets and duplicate checksums rejected")
