# Bousy Player v0.20 — Recovery Guardrails & Low-Memory Lifecycle

November 11, 2026 · Private GitHub repository Vaydelux/BousyPlayer · main.

## Implemented core improvements

1. **Bounded decoder restarts**: A monotonic 30-second window allows up to three manual same-source Retry/Play engine recreations; further requests do not allocate or start any decoder. A user can choose another engine or playlist media, or retry after the interval. Automatic source-specific startup compatibility fallback is unchanged.
2. **Android OS lifecycle connection**: MainActivity binds the current PlayerSession and forwards `onTrimMemory(level)`. The PlayerScreen lifecycle observes ON_STOP/ON_START, pausing on Stop and never auto-resuming on Start.
3. **Background resource release**: On UI_HIDDEN or background trim (level >= 20), PlayerSession preserves the authoritative seek offset/Continue Watching, pauses, clears audio focus, releases the active native decoder and abandons any active output surfaces. It retains only lightweight session state. Foreground trim levels 5/10/15 do not force decoder release. Explicit Play on return recreates the decoder at the intended offset; duplicate trims do not double-release.
4. **Safe error reporting**: The on-screen fallback initialization failure removes raw media titles and internal exception messages.
5. **Tests**: Android-free JVM/JUnit gate policies and real PlayerSession + fake decoder tests cover retry limit/reopening, background-only release, no autoplay, duplicate release safety, and restored positions. A new Android ActivityScenario instrumentation test is compiled by Gradle CI, but is not run unless an Android emulator/device is provided.

## CI and qualification
- Last confirmed stable release: v0.19 [successful GitHub Actions #20](https://github.com/Vaydelux/BousyPlayer/actions/runs/43014678358).
- v0.20 requires a new successful GitHub Actions job with Gradle session/native smoke, JUnit, Android lint, debug APKs and ELF/ZIP validation plus SHA256 manifests. `assembleDebugAndroidTest` compiles instrumentation source but does not execute tests.
- Real-device Android lifecycle, codec playback, repeated fault behavior and RAM/FD trends remain **not yet tested**.

## Further core priorities
- Stateful runtime metrics for repeated decoder recovery across actual Android TV hardware.
- Run ActivityScenario tests on device/emulator and verify pause+read-grant restoration behavior end to end.
- Validate ARM32 low-memory/native out-of-memory behavior and 2-hour soak before release signing.
- Licensing, native codec security and Play Store packaging assessment.
