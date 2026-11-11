# BousyPlayer v0.27 — Maven SBOM & OSV vulnerability gating

Date: November 12, 2026

## Implementation
1. `app:generateDebugSbom` resolves the Android `debugRuntimeClasspath` and inventories all version-resolved Maven modules in CycloneDX 1.5 JSON. This avoids confusing declared BOM constraints with resolved versions.
2. `tools/supply_chain.py` validates unique package URLs and coordinates, queries OSV `/v1/querybatch` in batches of 100, follows paginated results per individual package, and rejects partial responses.
3. `--enforce` blocks qualification and publication if OSV is unreachable or any advisory is returned. No vulnerability is silently allowlisted; fixes require dependency upgrades or an explicit, reviewed policy change.
4. Offline `tools/test_supply_chain.py` checks malformed inventories, duplicate packages, clean/advisory responses, pagination and partial-response rejection.
5. The main-only release workflow repeats all Android tests, APK/ELF architecture checks and the supply-chain gate before uploading both debug APKs, Maven SBOM, OSV JSON report and a checksum manifest covering those assets.

## Security and qualification caveats

This inventory is **Maven only**, and is not a full Android binary bill of materials. Embedded native C/C++ libraries may be vulnerable independently of their Maven wrappers. OSV results depend on the provider's database coverage; a clean result is not proof of absence of vulnerabilities. The workflow cannot prove application runtime behavior, codec patents, TV D-pad interaction, device longevity or production signing.

Separate follow-up milestones: inventory embedded ELF/shared-library versions, review native library licenses, generate verifiable build provenance, implement signed release distribution, and run Android TV/ARM32 hardware soak tests.

## First enforced vulnerability remediation

The first v0.27 scan resolved 131 Maven modules and identified `com.google.guava:guava:33.3.1-android` as affected by `GHSA-xxph-c9ww-hj94` / `CVE-2026-102554`, a deserialization-related denial-of-service advisory. Rather than waive the finding, `app/build.gradle.kts` now constrains its transitive Guava dependency to the upstream-patched `33.7.2-android` variant. Each build must still prove that Gradle actually resolves the patched version and that the OSV scan is clean. See the [upstream advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94).
