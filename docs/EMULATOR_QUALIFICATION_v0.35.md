# BousyPlayer v0.35 — Android emulator instrumentation qualification

Date: November 12, 2026

## Purpose

The project had three Android instrumentation classes, but GitHub Actions previously compiled `assembleDebugAndroidTest` only. This did not prove ActivityScenario, Android ContentResolver/SharedPreferences or actual Android Lifecycle APIs worked at runtime. v0.35 adds a **real emulator device test** rather than inferring results from compilation.

## Job design

`.github/workflows/android.yml` adds `emulator-instrumentation` on pull requests and manual dispatch:

1. Starts a separate read-only Ubuntu 24.04 job using Java 17 and Gradle 8.13, installs SDK platform/build-tools 36, and grants KVM access to the emulator.
2. Launches an Android 35 Google APIs x86_64 emulator via `reactivecircus/android-emulator-runner@v2`.
3. Runs `gradle --no-daemon --stacktrace :app:connectedDebugAndroidTest -PciEmulatorAbi=true`, installing the Android debug app and test APK and requiring successful JUnit instrumentation execution.
4. The `ciEmulatorAbi` Gradle property enables an additional **ephemeral** x86_64 debug APK for emulator installation. It is absent from default builds and from `.github/workflows/release.yml`. Published APKs remain ARM32/ARM64 only; the checksum/manifest still expects exactly two APKs and seven other assets.
5. An emulator exception, timeout, installation failure, assertion failure or missing SDK/KVM fails the PR job. No test is silently skipped as “passed.”

## Runtime test scope

- `MainActivityLifecycleTest`: ActivityScenario launch/recreate and Android memory trim callbacks without a loaded decoder.
- `PlayerSessionLifecycleInstrumentedTest`: real PlayerSession and Android framework with injected fake decoder; checks idempotent trim/release, explicit foreground Play, native snapshot failure tolerance, engine handoff, and an unconfirmed seek preserved across UI-hidden trim without fabricating native status.
- `RecentStorePrivacyInstrumentedTest`: Android SharedPreferences/ContentResolver-based local history sanitization and clear, without persisting network stream credentials.

These checks complement, **not replace**, Kotlin JVM smoke, native-adapter fakes, Maven dependency scanning, native ELF inspection and signing consistency.

## Limitations

The emulator uses x86_64 and fake decoders. It does not qualify ARM32 hardware, physical Android TV D-pad, native VLC/mpv codec decoding, real MPEG-TS/MKV seeks or captions, vendor lifecycle ordering, background memory pressure under sustained streaming, Play Store signing, signed build provenance or native-library license/CVE provenance. Do not claim hardware certification based on passing instrumentation.
