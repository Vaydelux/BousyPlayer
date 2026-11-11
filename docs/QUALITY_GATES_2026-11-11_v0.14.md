# Bousy Player v0.14 — Mandatory regression quality gates

Date: 2026-11-11.

## Changes

- Added `tools/gradle-session` module that compiles the exact `PlayerSession.kt` and `PlaybackPolicy.kt` production source with small Java-free Android/Compose stubs, then runs the deterministic `SessionSmokeKt` test runner.
- Added `tools/gradle-native` module that compiles exact `MpvEngine.kt` and `VlcEngine.kt` production source against fake Android/MPV/VLC surfaces, then runs `NativeSmokeKt` scenarios.
- Both new Gradle application targets are required by `tools/build-and-qualify.sh`. Their failure blocks the Android APK/release even if normal Android unit tests pass.
- Debug prereleases include SHA256SUMS.txt for the split ARM32/ARM64 debug APKs; the hashes are generated from copied release assets.
- Updated version metadata and release qualification documentation. The app's content/API behavior remains v0.13; this milestone enforces regressions rather than claiming device compatibility.

## Known limitations

The JVM harnesses test actual Kotlin session/adapter source but use fake Android/MPV/VideoLAN APIs. They cannot prove codec behavior, memory safety on ARM32, real native load/detach, seek correctness on physical files, D-pad remote focus or subtitle contrast. The next quality gate remains device testing *after* architecture and core stabilization.

## Evidence

- v0.13 baseline: [successful GitHub Actions #11](https://github.com/Vaydelux/BousyPlayer/actions/runs/42987656674).
- v0.14: check the new GitHub Actions run; only call it qualified if session/native Gradle tasks, Android tests, lint, ELF/ZIP verification and prerelease upload all succeed.
