# Bousy Player v0.11 — Release qualification gates

**Status:** source-only prototype. Do not describe this as an installable, APK-ready, release-ready or universally compatible media player. The checks below need recorded passing build/device evidence.

| Gate | Required evidence | Status |
| --- | --- | --- |
| Kotlin source smoke | `bash tools/check-source.sh` | PASS: policy 47, session 57, model 16, native adapter scenarios 2, UI 6, external intent 1; XML 4 |
| Release metadata / CI artifact labels | `python3 tools/check-release-metadata.py` | PASS: version `0.11.0` and artifact labels agree |
| Android compile and unit tests | `testDebugUnitTest` with Android Gradle dependencies | NOT RUN |
| Lint | `lintDebug` | NOT RUN |
| Debug APK packaging | `assembleDebug` and `tools/verify-apk.py` | NOT RUN (no APK) |
| Native ELF/ABI | Actual MPV/VLC ELF objects for ARM32/ARM64; load order and libc++ compatibility | NOT RUN |
| Playback files | MP4 H.264/AAC, MKV HEVC/AC3, TS indexed/nonindexed, HLS live, DASH VOD | NOT RUN |
| Seek and pause | ±10s TV seeking, scrubbing, seeks before/after buffering, nonseekable truthfulness | NOT RUN |
| Audio/subtitles | Track selection, external SRT/VTT/ASS, readability, focus loss, Bluetooth/unplug | NOT RUN |
| Native lifecycle | 20 switches, repeated file intents, surface recreation, descriptor teardown | NOT RUN |
| TV input/low RAM | Remote D-pad/OK/Back, HDMI-CEC, ARM32 ~1–2 GB and ARM64, 2-hour soak | NOT RUN |
| Security/licensing | HTTPS redirect/downgrade behavior, privacy/history, OSS licenses | NOT RUN |

The Android CI workflow is **configured only**, not executed. Its `testDebugUnitTest lintDebug assembleDebug` task and APK validation must actually pass before a debug artifact can be supplied. Device qualification follows a compiled APK. This environment has Java/Kotlin CLI, but no Android SDK or Gradle and cannot resolve external download hosts.

The native-adapter Kotlin stubs are intentionally minimal and may have different signatures from published Android SDK/VideoLAN/mpv libraries. Passing source checks cannot prove real dependency resolution or native runtime behavior.

### Earlier source coverage retained

- Resume-preservation checks protect Continue Watching from native decoders reporting a temporary zero duration/position or merely an early 0:02 before applying a saved seek.
- An explicitly requested Start Over supersedes a pending automatic resume; the new position is saved only after the updated decoder timeline is confirmed.
- Metadata-only native startup errors still qualify for the one-shot automatic Media3 fallback (local files only).
- Static Android source guards ensure ordinary HTTPS links are not captured by the app and the video view no longer holds the device awake after Pause/EOF/error.

**Packaging is NOT build evidence.** Gradle/Android dependencies, APK binaries, real native lib signatures, physical seeking, codecs, HDMI-CEC, and ARM32 memory must still be verified.

### v0.9 source coverage retained

- D-pad seek during asynchronous pending resume uses the last requested seek target rather than provisional native time.
- Early native frames that predate acknowledgement of the saved resume do not count as a successful decoder startup for the one-shot local AUTO fallback gate.
- Explicit Play at EOF allocates a clean decoder at 0:00, with strict release-before-replacement behavior.
- Native end-of-file events persist completion even if the final timeline timestamp is delayed; fatal decoder errors release audio focus.
- The CI workflow now checks version labels, external VIEW intent filters and Android XML before running Gradle.

**CI has not been run** and no native Android SDK build/APK/install/device evidence has been obtained.

### v0.10 source improvements (not hardware-verified)

- An explicit Play/Retry after fatal decoder failure destroys the old playback engine, keeps the most recent valid resume position, re-arms Play, and avoids stale Pause UI state.
- AUTO local libmpv startup stalls without error for 20 seconds trigger at most one Media3 handoff; manual choices, Pause and focus suspension do not time out. The threshold may need adjustment after physical/cloud SAF provider measurements.
- Visible Restart/Start over and ±10-second buttons are disabled for non-seekable streams.
- Actual `PlayerSession.kt` smoke tests use an injected monotonic clock to test exact timeout boundaries; policy exclusions also have an Android JUnit source test.

All Gradle compilation, native ABI checks and device-gate statuses above are still pending.

### v0.11 source improvements (not hardware-verified)

- A manually paused or focus-suspended native startup cannot immediately exhaust the 20-second automatic local MPV startup window when Play resumes or focus returns. Session tests cover 60-second Pause and 90-second transient audio-focus loss followed by 19,999 ms and 20,000 ms deadline boundaries.
- A fatal decoder error issues one best-effort Pause before releasing audio focus; repeated UI refreshes do not send a continuous stream of Pause commands.
- AndroidX `BackHandler` owns TV Back navigation instead of competing with Compose's raw KEYCODE_BACK handler. This behavior is guarded statically, but **needs physical remote confirmation**.
- CI preflight additionally runs `tools/check-ui.py` before Android Gradle compilation. Native APK build, ARM ABIs, user-visible video playback, and remote correctness remain unverified.
