# BousyPlayer v0.26 — Enterprise privacy & release-hardening milestone

Date: November 12, 2026

## Implemented

1. **Untrusted metadata boundaries.** Media providers may expose very long or misleading filenames. Titles are capped at 140 characters; control characters and Unicode bidi override/isolation characters are removed on display/persistence. RecentStore rejects content identifiers beyond 2,048 characters and JSON above 65,536 characters, clamps progress/duration, and deduplicates document IDs on read.
2. **User-controlled history.** A confirm-first Clear History button removes the app's Continue Watching progress. No source media is deleted. Android SAF read grants are deliberately not revoked.
3. **HTTPS input hardening.** Reject malformed or out-of-range ports, raw controls, encoded CR/LF, fragments, userinfo, and URLs longer than 8,192 characters, while allowing standard signed HTTPS query strings.
4. **Least-privilege CI.** PR/feature branch qualification uses read-only repository credentials and cannot publish. A separate main-only write-scoped workflow repeats all qualification checks and publishes ARM32 and ARM64 APKs with SHA-256 checksums.
5. **Regression coverage.** Android/JVM model smoke and JUnit tests, UI source guards and compile-only RecentStore Android instrumentation ensure changes remain covered.

## Acceptance and limitations

- Passing GitHub Actions is evidence of compile, lint, JVM fake-engine, unit and APK architecture tests. Android instrumentation is compiled, **not executed** without a device or emulator.
- The APKs are debug builds. Do not present them as enterprise production releases.
- Security review remains open for libVLC/libmpv native redirects, TLS, SBOM/provenance, dependency audit, production signing and Android crash/ANR monitoring.
- Real MKV/TS seek, subtitle rendering, low-memory ARM32 endurance and TV remote focus require device validation.
