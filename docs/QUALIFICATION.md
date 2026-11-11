# Bousy Player v0.17 — Qualification gates

Scope: standalone BousyPlayer private repository. v0.17 adds high-contrast subtitle presets (Media3/libmpv), in-memory bounded playback health and capped native track metadata; hardware remains unverified. This is an automated build qualification, **not** device or codec certification.

| Gate | Evidence and status |
|---|---|
| v0.11 whole-app build | PASS: [GitHub Actions build #8](https://github.com/Vaydelux/BousyPlayer/actions/runs/42982351716), Kotlin/Gradle, unit tests, lint, APK assembly, ARM native ELF presence |
| v0.13 core-hardening Android build | PASS: [CI #11](https://github.com/Vaydelux/BousyPlayer/actions/runs/42987656674) |
| v0.14 session + native Gradle smoke tests | PASS: [GitHub Actions #12](https://github.com/Vaydelux/BousyPlayer/actions/runs/42988411347), 64 session checks and native surface simulations |
| v0.15 delayed MPV resume and TV queue source gates | PASS: [GitHub Actions #14](https://github.com/Vaydelux/BousyPlayer/actions/runs/42989807568) |
| v0.16 permission-bound saved state and diagnostic/track gates | PASS: [GitHub Actions #16](https://github.com/Vaydelux/BousyPlayer/actions/runs/42993922342) |
| v0.17 subtitle/health/native regression gates | PENDING v0.17 GitHub Actions workflow |
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
