# Bousy Player v0.33 — Qualification gates

Scope: BousyPlayer private repository. v0.28 inventories actual native ELF objects in both ABI APKs and blocks executable stacks, writable/executable segments, text relocations, and cross-ABI mismatches. It does NOT determine native upstream versions or CVE status, device playback or production signing.

| Gate | Evidence and status |
|---|---|
| v0.11 whole-app build | PASS: [GitHub Actions build #8](https://github.com/Vaydelux/BousyPlayer/actions/runs/42982351716), Kotlin/Gradle, unit tests, lint, APK assembly, ARM native ELF presence |
| v0.13 core-hardening Android build | PASS: [CI #11](https://github.com/Vaydelux/BousyPlayer/actions/runs/42987656674) |
| v0.14 session + native Gradle smoke tests | PASS: [GitHub Actions #12](https://github.com/Vaydelux/BousyPlayer/actions/runs/42988411347), 64 session checks and native surface simulations |
| v0.15 delayed MPV resume and TV queue source gates | PASS: [GitHub Actions #14](https://github.com/Vaydelux/BousyPlayer/actions/runs/42989807568) |
| v0.16 permission-bound saved state and diagnostic/track gates | PASS: [GitHub Actions #16](https://github.com/Vaydelux/BousyPlayer/actions/runs/42993922342) |
| v0.17 subtitle/health/native regression gates | PASS: [GitHub Actions #17](https://github.com/Vaydelux/BousyPlayer/actions/runs/42998083471), 75 session regressions and both APKs |
| v0.18 libmpv/LibVLC descriptor and seek lifecycle gates | PASS: [GitHub Actions #19](https://github.com/Vaydelux/BousyPlayer/actions/runs/43001018061) |
| v0.19 repeated surface attach/detach + failure injection | PASS: [GitHub Actions #20](https://github.com/Vaydelux/BousyPlayer/actions/runs/43014678358) |
| v0.20 bounded decoder retries + background memory release | PASS: [GitHub Actions #22](https://github.com/Vaydelux/BousyPlayer/actions/runs/43016568018), 84 session checks and two verified APKs |
| v0.21 Media3 local MKV silent-stall fallback / position retention | PASS: [GitHub Actions #23](https://github.com/Vaydelux/BousyPlayer/actions/runs/43021579698), 97 session regressions |
| v0.22 playback phase and explicit backward seek regression gates | PASS: [GitHub Actions #24](https://github.com/Vaydelux/BousyPlayer/actions/runs/43026999771), 102 session checks |
| v0.23 stalled-seek retry and clean EOF restart | PASS: [GitHub Actions #25](https://github.com/Vaydelux/BousyPlayer/actions/runs/43036121213), 108 session checks and both ABIs |
| v0.24 decoder ownership, transient snapshot fault tolerance | PASS: [GitHub Actions run 43041613580](https://github.com/Vaydelux/BousyPlayer/actions/runs/43041613580) |
| v0.25 latched snapshot failure and no false engine fallback | PASS: [GitHub Actions #32](https://github.com/Vaydelux/BousyPlayer/actions/runs/43047420473) |
| v0.26 privacy controls, URL validation and isolated CI release | PASS: [main release #1](https://github.com/Vaydelux/BousyPlayer/actions/runs/43066908229) |
| v0.27 CycloneDX Maven inventory and fail-closed OSV | PASS: [main release #2](https://github.com/Vaydelux/BousyPlayer/actions/runs/43070243504) |
| v0.28 shipped ELF hashes/Build IDs, hardening checks, native file SBOM | PASS: [main release #3](https://github.com/Vaydelux/BousyPlayer/actions/runs/43076247076) |
| v0.29 shared themes, adaptive compact recovery UI, manual prolonged buffer handling | PASS: [main release #4](https://github.com/Vaydelux/BousyPlayer/actions/runs/43080232006) |
| v0.30 transport command exception safety, stale recovery guard, timeline continuity | PASS: [main release #5](https://github.com/Vaydelux/BousyPlayer/actions/runs/43087769302) |
| v0.31 background poll suspension, trusted seek snapshots, non-destructive SAF history | RELEASED: [v0.31 assets](https://github.com/Vaydelux/BousyPlayer/releases/tag/debug-v0.31-77d2d708); hardware tests not run |
| v0.32 split-ABI signer consistency and exact release asset hash/commit manifest | MERGED: [PR #8](https://github.com/Vaydelux/BousyPlayer/pull/8), release qualification queued |
| v0.33 native seek rejection, trusted timeline preservation and CI supersession | PENDING v0.33 CI, no hardware certification |
| v0.23/v0.24 PlayerSession Android lifecycle instrumentation | SOURCE + COMPILE ONLY; NOT RUN on device/emulator |
| v0.20 ActivityScenario instrumentation | SOURCE + COMPILE ONLY; NOT RUN on device/emulator |
| v0.19 manual in-memory resource trend | SOURCE IMPLEMENTED; device sampling NOT RUN |
| v0.18 Android on-demand memory/FD probe | SOURCE IMPLEMENTED; hardware sampling NOT RUN |
| v0.14 SHA-256 checksum manifest | PASS: [v0.14 prerelease](https://github.com/Vaydelux/BousyPlayer/releases/tag/debug-v0.14-743f04bc) |
| v0.12 ABI-split APK build | PASS: [GitHub Actions build #10](https://github.com/Vaydelux/BousyPlayer/actions/runs/42984416569), both debug APKs published |
| v0.12 per-ABI ZIP / DEX / ELF | PASS: 13 ELF objects per APK, 83.18 MB ARM32 and 89.52 MB ARM64 in CI #10 |
| ABI validator self-tests | Run `python3 tools/test-verify-apk.py` before Gradle; synthetic fixtures only |
| Installation on ARM32 | NOT RUN |
| Installation on ARM64 | NOT RUN |
| Native mpv and VLC loading | NOT RUN (ELF presence and architecture are not a JNI runtime test) |
| MPEG-TS startup/seek/rebuffer | NOT RUN (indexed and nonindexed samples on TV required) |
| MKV autoplay, repeated seek, subtitle selection | NOT RUN |
| D-pad focus, hidden HUD, Back, subtitle readability | NOT RUN on remote hardware |
| Resume, battery, headphones/Bluetooth, low-RAM endurance | NOT RUN |
| Security and third-party-license review | NOT COMPLETE |

## Testing on real Android hardware

Install the matching debug APK. Test Media3/MPV/VLC by selecting each engine, then MP4, MKV (with HEVC/AC3 and embedded captions), indexed and unindexed TS, HLS, and a seek-disabled live stream. On TV, test remote arrows, OK, Back, menu, caption selection and repeated menu exits. Pause intentionally during buffering, switch tracks, relaunch at 1:00+, and check that Continue Watching resumes correctly. Run a long playback session on a 32-bit low-memory device and watch for leaks/crashes.

Collect device model, Android version, ABI from `adb shell getprop ro.product.cpu.abilist`, engine, container, reproducible steps, and `adb logcat` without sharing sensitive URLs or tokens.

## Building locally

```bash
bash tools/build-and-qualify.sh
```

This requires JDK 17, Gradle 8.13, and Android SDK 36. Results:
- `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`
- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

Each APK is independently checked by `tools/verify-apk.py`. A successful build does not guarantee runtime codec or native-library compatibility.

## v0.26 additional gates

PR/feature branch Android qualification is read-only. Main-branch APK publishing is a separate write-scoped workflow that re-runs the full build-and-qualify script before release. The RecentStore privacy instrumentation is **compiled, not executed**, in hosted CI. Network redirects, native dependency vulnerabilities and real-device performance remain untested.

## v0.27 security evidence

The Gradle debug classpath is resolved to Maven module versions and exported as a CycloneDX 1.5 SBOM. An OSV querybatch scan includes pagination handling and fails closed on unavailable/incomplete scans or known advisories. Both reports and APKs are covered by release checksums. It is deliberately *not* an SBOM of all embedded native binaries, Gradle plugins, build tooling, or Android platform components. License, transitive native CVE, source provenance and release-signing evidence are outstanding.

The first v0.27 scan correctly **failed** on the Guava `33.3.1-android` advisory `GHSA-xxph-c9ww-hj94`. A patched `33.7.2-android` transitive version constraint was added. Do not interpret the earlier failing run as a qualified release; a fresh clean build and scan are required.

## v0.28 security verification

`tools/native_inventory.py` verifies every packaged ELF shared object directly from both debug APKs, rejecting invalid ABI/type/endianness, duplicate or asymmetric libraries, executable stack, RWX loads and text relocations. Outputs per-ABI SHA-256, GNU Build ID, DT_NEEDED, RELRO metadata and a CycloneDX file SBOM. `tools/test-native-inventory.py` exercises both ELF classes and negative hardening fixtures. Neither automation certifies native library CVE status, reproducibility or Android runtime behavior.

## v0.29 additional qualification

The same Maven advisory, native ELF, two APK, Android lint, JVM session/native smoke, and unit test gates apply. New JUnit and standalone JVM smoke checks require buffer recovery to be offered only when the user requested Play and REBUFFERING lasted at least 15 seconds; it never automatically calls Retry. Static UI guards verify shared persisted appearance and viewport-bounded recovery panels. Color contrast, D-pad traversal and small-screen layout remain to be validated on actual emulators/devices.

## v0.30 additional fail-safe qualification

Injected native Play/Pause exceptions must disarm Play intent, abandon audio focus, release the failing decoder, surface only an opaque UI error and keep Continue Watching's last trusted position. Audio-focus recovery must follow the same rule; delayed rebuffer recovery cannot restart once a user pauses. Native seek exceptions leave the previous valid seek/resume intact while notifying the user. Android CI runs these simulated ownership assertions against the real PlayerSession source, but the actual device/audio stack is not exercised.

## v0.31 regression requirements

The real PlayerSession source is exercised under the deterministic Kotlin JVM suite. A lifecycle ON_STOP must take one final checkpoint and pause, then all subsequent `refresh()` polls in the background must perform zero native decoder reads and zero health-counter updates. ON_START enables polling but never autoplay. When a decoder snapshot throws during an explicit seek, the previously captured timestamp cannot acknowledge the new target or replace Continue Watching; only a subsequent readable decoder status may confirm it. RecentStore must not mutate persistent history merely because a document-provider grant is absent from the current read-permission enumeration.

The existing Maven OSV and native ELF, Android unit/compile/lint and ABI split APK checks are unchanged. Android device instrumentation remains compiled but not executed in hosted CI.

## v0.32 integrity and signing-gate requirements

The APK signatures must pass Android SDK 36 `apksigner verify` for both ABIs, use a verified v2+ signature scheme and exactly one matching SHA-256 signing certificate. `debug-signing-report.json` is published alongside the other debug artifacts. This establishes only that the two APKs are cryptographically signed with a matching certificate; it does not establish trusted developer identity, release-key rotation, production signing, hardware installation, or proof of native code provenance.

`tools/release_integrity.py` records each release artifact's size and SHA-256 with the actual GitHub commit and main-branch Actions run. Immediately before publishing, it verifies the generated `release-manifest.json`, every file in `SHA256SUMS.txt`, and the exact expected artifact set. The manifest itself is SHA-256 checksummed. Negative regression tests reject changed bytes, forged commit metadata, invalid signing output, absent reports, duplicate checksum entries and unexpected payload files.

**Trust model:** The manifest/checksum are generated by the same GitHub workflow and are not independently signed. A compromised publishing workflow or token could replace them. Stronger provenance requires GitHub Artifact Attestations/Sigstore or an external trusted signing service; production APK distribution also needs a protected signing identity and device tests.

## v0.33 seekability race and continuous-integration safeguards

The source contract for Media3, LibVLC and libmpv now defines a user-issued seek as **rejected** when the native engine is no longer seekable at command time. The engine does not silently return to the session pretending the seek was accepted. The session catches the rejection, preserves the previously confirmed playback position, does not create a false pending target and displays stable guidance without media identifiers. Real mpv/LibVLC Kotlin adapter fakes test the command path; Android compilation validates the Media3 adaptation; fake PlayerSession tests verify saved-position continuity.

PR/push Android qualification now has event-and-ref-scoped cancellation for obsolete runs. This is an operational efficiency measure rather than a substitute for missing CI results, and does not change main-only APK publishing credentials. Hosted Actions can still be delayed by external queues. Real-device seeking remains to be qualified.
