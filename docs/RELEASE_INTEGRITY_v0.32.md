# BousyPlayer v0.32 — Debug release integrity and signing evidence

Date: November 12, 2026

## Release trust boundary

The two per-ABI APKs are standard Android **debug builds**. A valid Android signature is necessary to install an APK but does not prove the signer is the intended production publisher. A matching debug certificate merely confirms both ABI variants use the same signer. Neither `SHA256SUMS.txt` nor `release-manifest.json` is cryptographically signed by an independent trusted identity; this is traceable build evidence, not SLSA provenance.

## Gates

1. Android CI builds both APKs, runs Kotlin session/native smoke, Android unit tests, lint, resolves Maven dependencies and enforces an OSV vulnerability scan. ZIP/ELF inspections reject invalid or unexpectedly mixed native libraries.
2. `tools/apk_signing.py` runs Android SDK `apksigner verify --verbose --print-certs` independently on both split APKs, requires a modern v2+ scheme and exactly one SHA-256 signer, then ensures the certificate fingerprints match. Errors and unexpected signer differences fail the pipeline. `debug-signing-report.json` records only certificate fingerprints, version-independent validation claims and debug-signing limitations.
3. The main-only workflow copies the exact qualified APKs, Maven SBOM, OSV report, native inventory, native-file SBOM and signing report to `dist`. `tools/release_integrity.py create` records immutable properties of these seven local artifacts, the 40-character source Git commit, repository name and GitHub Actions workflow run ID/URL in `release-manifest.json`.
4. The workflow computes `SHA256SUMS.txt` over the 2 APKs and 6 JSON files (including the manifest), and calls `tools/release_integrity.py verify`. Verification recalculates every hash, size, name and release metadata, rejects a missing or extra asset, and checks there are no duplicate or incorrect checksums. Only then does it create the prerelease with **nine assets**.
5. Both offline security fixture suites run in push, PR and main-only release jobs, rejecting tampering, bad metadata, absent/extra artifacts and malformed signing results.

## Verifying a release locally

Download all nine published assets into the same directory, then compare the files against `SHA256SUMS.txt` with `sha256sum --check SHA256SUMS.txt` on an environment that provides GNU coreutils. You can also verify the complete expected asset set using:

```bash
python3 tools/release_integrity.py verify --dist ./dist \
  --version 0.32.0 --commit FULL_40_CHARACTER_COMMIT_SHA \
  --repository Vaydelux/BousyPlayer --run-id MAIN_PUBLISHING_ACTION_RUN_ID
```

The commit and run ID **must** come from GitHub's main-branch release workflow rather than from an untrusted downloaded file alone. Integrity only checks agreement with the chosen artifacts and metadata; it does not prove trusted publisher identity. For APK signature independent verification, use the Android SDK's `apksigner verify --print-certs --verbose` on each APK and compare certificates.

## Remaining production blockers

- Protected production signing keystore or Play App Signing configuration, key rotation/recovery, and non-debug production build settings.
- Signing independent build provenance, for example GitHub Artifact Attestations, under appropriate GitHub repository/account policy. Do not label the current JSON file a signed SLSA attestation.
- Native FFmpeg, mpv and VLC binary upstream version matching, CVE screening and license compliance.
- Real Android TV/ARM32/ARM64 hardware installation, codec/seek/caption/remote tests, 2-hour memory-soak and release approval criteria.
