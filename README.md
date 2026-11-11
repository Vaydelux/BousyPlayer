# Bousy Player v0.21 — Android TV / phone / tablet

A separate, Bousygram-inspired Android media-player source project written in Kotlin and Jetpack Compose. Supports opening user-chosen local SAF documents and HTTPS streams. The original Bousygram repository is not modified. No Telegram account, TDLib, embedding tools, DRM bypass, or proprietary streaming service is required.

> **Qualification status:** v0.20 passed [GitHub Actions #22](https://github.com/Vaydelux/BousyPlayer/actions/runs/43016568018), with 84 session ownership/recovery checks, native-adapter simulations, Android unit tests and lint, compiled lifecycle instrumentation and two verified ABI debug APKs. v0.21 adds a bounded silent-stall fallback for automatic local MKV playback. A new passing CI build is required before treating v0.21 as build-qualified; physical playback remains unverified.

## v0.13 core hardening

- Automatically selected **Media3** can hand off a **local MKV** to **LibVLC** once on synchronous initialization failure or asynchronous startup codec error. It preserves Continue Watching and releases the old engine before allocating the next one.
- A manual engine choice, remote URL, explicit Pause or mid-playback failure never triggers this automatic compatibility handoff.
- The file picker caps the active queue at **96** items to avoid eagerly creating thousands of queue buttons on low-memory Android TVs; the user receives a visible notice if more files were selected. Only chosen files receive persisted URI permission requests.
- Continue Watching parser accepts at most 20 entries and clamps invalid saved timing/title lengths; stream URLs are still never persisted.
- CI still produces independent ARM32/ARM64 APKs, with lint, unit tests, packaging and ELF verification. **Actual MKV decoding/codec compatibility remains untested on devices.**

## v0.14 enterprise quality gates

- Two isolated Gradle JVM smoke modules now compile the **real `PlayerSession.kt`** with test doubles and the **real libmpv/libVLC adapters** with native-surface fakes. These tests run on every Android CI build; they no longer depend on a manually installed Kotlin command-line compiler.
- A passing build now requires the deeper session ownership/Play-Pause/seek/fallback tests and native-surface release/startup tests in addition to Android unit tests, lint, split-ABI APK checks and ELF architecture checks.
- Debug prereleases include a `SHA256SUMS.txt` for both APK files. The files are test builds, not runtime- or device-qualified releases.
- v0.13 recovery fixes and bounded playback queue behavior are retained.

## v0.15 — Playback and TV navigation foundation

- **Lazy playlist drawer:** Replaces eager rendering of up to 96 queue buttons with `LazyColumn` and stable indexed items. Opening the drawer scrolls to the currently playing item and targets its focus for Android TV D-pad navigation. It avoids nested unbounded scroll containers.
- **libmpv delayed-resume recovery:** A saved resume is retried for a bounded 3-second seekability window (12 × 250 ms) after the native FILE_LOADED event. A subsequent native `seekable` notification also triggers an immediate attempt. Pending callbacks are bound to their media generation and cancelled at release.
- **Explicit seek wins:** A user-triggered ±10-second/timeline seek cancels the old pending saved-resume seek after the native command succeeds. A stale delayed callback may never override the user's chosen position.
- **Accurate hidden HUD seek feedback:** The on-screen seek indicator reflects the requested target, not an early native 0:02 timestamp.
- **CI enforcement:** The real MPV engine source and PlayerSession source now run through expanded JVM native/session smoke tests, along with Kotlin/Android lint, unit tests and split-ABI APK checks. **Physical remote control and real MKV/TS behavior remain unverified.**

## v0.16 — Crash-resilient playback and safe diagnostics

- On Android Activity recreation, restore only the last open **persistently granted SAF document** from bounded saved-instance state, and resume in **Pause**, never automatically playing sound/video. Continue Watching provides the saved timeline position. No HTTPS URLs, expired file grants, external VIEW-only documents, or large playlists are preserved in the state bundle. Closing the player clears that restoration.
- The playback error screen now displays a deterministic support code and a fixed actionable explanation rather than raw decoder exception text, URLs or private file paths.
- Audio/subtitle track requests reject stale track IDs and provide failure/request feedback. External subtitle URIs must come from the document picker; selecting a subtitle requests playback through Media3 without claiming immediate track success.
- Retains v0.15 lazy TV queue, bounded MPV seek retry and ARM32/ARM64 split APKs. **Android lifecycle and physical-device behavior remain untested.**

## v0.17 — Subtitle accessibility and bounded session health

- **TV-readable subtitles:** Size presets (Small, Standard, Large) and white, yellow, and black-on-light high-contrast palettes are available in the Audio & Subtitles drawer. Media3 applies CaptionStyleCompat; libmpv applies native subtitle font/color/border/background properties. The session carries the preferences through engine switches without overriding Pause. Live subtitle styling is **not supported by LibVLC** in this version, and the UI states that clearly.
- **Privacy-safe session health:** The Options menu displays coarse sampled buffering percentage, longest buffering interval (capped at 120 seconds), error events, and engine handoff count. All metrics remain in memory, are bounded, and contain **no titles, URLs, filenames, credentials, or remote telemetry**; counters reset with a new player session.
- **Memory safeguards:** Media3, mpv, and LibVLC limit exposed track counts and label lengths to protect low-memory Android TV devices from unusually large metadata. Cached track lists are released where appropriate.
- **Regression gates:** Android-free recorder policies, Gradle-run actual libmpv adapter tests, session ownership tests, JUnit policy tests, Android lint, and two ABI-specific APK checks. Hardware subtitle rendering and actual long-session memory use remain unverified.

## v0.18 — Native lifecycle and long-session resource discipline

- **SAF descriptor ownership:** libVLC now treats failure of the previous decoder's `stop()` as a real error and does not close the active document file descriptor prematurely. libmpv stops before native destroy and ensures descriptors are closed even when teardown calls fail.
- **Manual seek wins:** libVLC clears an older, deferred Continue Watching seek only after a successful user-initiated seek, preventing a later native `Playing` event from jumping back to the prior timestamp.
- **Repeatable native lifecycle tests:** Gradle's real-adapter fake-Android suite tracks open/close counts for local document descriptors during rapid source replacement, failed native stops and repeated `release()`. It checks that failure preserves the original descriptor and that release tears native resources down only once.
- **On-demand resource snapshot:** Player Options can sample approximate Java heap, native heap and open file-descriptor count, with no background polling, media URLs, filenames or network reporting. Counts can be compared during later Android TV endurance qualification.
- **Release safety:** ARM32 and ARM64 debug APKs remain CI-gated by session tests, native adapter simulations, JUnit, Android lint, ZIP/ELF validation and SHA-256 hashes. No claim is made about native codec or hardware compatibility until device testing.

## v0.19 — Native surface lifecycle stress & manual memory trends

- **Surface ownership and idempotency:** Repeated `surfaceCreated` callbacks no longer attach another libmpv/LibVLC video output. VLC refreshes its geometry on reattachment, and all native views clear keep-screen-awake during release.
- **Recoverable surface failures:** A transient native attach failure is reported; a later successful surface attach clears only the specific surface error rather than hiding unrelated decoding failures.
- **Mandatory native stress regressions:** The Gradle native adapter suite now recreates each surface 20 times, confirms a single mpv media load, preserves LibVLC's explicit Pause, checks geometry changes and verifies released decoders ignore late surface callbacks. Simulated attach failures must recover without leaked SAF descriptors.
- **On-demand resource trends:** The Options menu now compares Java heap, native heap and open-FD counts against the first manual observation in the current session. Only bounded numeric counters are retained in memory; no background polling, log retention, file path inspection, or external telemetry. Growth **does not by itself prove a leak**.
- **Qualification boundary:** Kotlin/native fake tests, Android lint, JUnit, ABI-specific debug APK and ELF validation are required before a prerelease. These tests do not replace real ARM32 endurance or video rendering qualification.

## v0.20 — Decoder retry and low-memory lifecycle hardening

- **Bounded manual recovery:** At most three same-source decoder restarts within 30 seconds. Repeated Retry/Play taps after a fatal decoder error do not spin up additional native libraries. The gate resets when the window expires, after selecting another engine, or when moving to another playlist item. A clear message explains the temporary limit.
- **Background native resource release:** Android `onTrimMemory` forwards UI-hidden and background trim signals to the active PlayerSession. The owner captures Continue Watching, pauses, frees the native player and its descriptors, drops audio focus, and never restarts automatically when the Activity becomes visible. Foreground low-memory warning levels do not forcibly interrupt video playback. Pressing Play explicitly opens a clean decoder at the saved position.
- **Privacy-safe startup failures:** Failed decoder initialization no longer displays a media title or native exception text in the UI.
- **Android lifecycle instrumentation source:** An `androidTest` ActivityScenario test covers recreation and memory-trim callbacks without active media. CI compiles its test APK but does **not** run it without a device or emulator.
- **Qualification boundary:** Existing 75+ session/adapter regressions, native surface and FD checks, Android tests, lint, ARM32/ARM64 ELF/APK validation and SHA-256 checksums remain mandatory. No real Android TV endurance or codec tests have been performed.

## v0.21 — Silent MKV startup recovery

- **Bounded Media3 → LibVLC compatibility fallback:** An automatically chosen Media3 decoder that has not established playback for a local MKV after **25 continuous active seconds** can switch to LibVLC once, even if Media3 never reports an error. Its saved Continue Watching offset is transferred, and old decoder resources are released before the new decoder loads.
- **Pause and focus protection:** Time spent in explicit Pause or suspended audio focus does not consume the MKV startup budget. The timer restarts on explicit Play or audio-focus gain. This feature never silently changes a manually chosen engine, an HTTPS stream, or video already confirmed ready.
- **Correct readiness:** A buffered Media3 snapshot can initially display its requested starting position before the first frame. The timer will not treat that provisional timestamp alone as confirmed playback, preventing perpetual buffering from disabling the fallback.
- **Regression coverage:** Pure policy/JUnit tests and Gradle-run actual PlayerSession fake-decoder tests cover 24,999/25,000ms boundaries, saved-offset preservation, one-time release/hand-off ordering, resumed Play, focus loss/gain, and provisional timelines.
- **Scope:** Debug APKs still require Android compilation, session and native adapter smoke tests, lint, per-ABI APK/ELF validation and checksums. This is **not** physical MKV/codec certification.

## Current source capabilities

- Cinematic dark UI with responsive phone/tablet/TV layout and D-pad keys.
- One active playback owner; Media3 (default), libmpv (auto for TS/M2TS/MTS), and manually selectable libVLC.
- Playback: local SAF video/audio files, HTTPS streams, Media3 HLS/DASH URL hints, speed, fit/zoom/stretch, embedded audio/subtitles, external subtitles (via Media3), queue navigation, local Continue Watching.
- Live/nonseekable duration is displayed without fabricated seek positions.
- Native playback waits for the SurfaceView to be attached. A new file releases the previous native decoder before replacement. Early engine switches preserve the pending resume position even before the decoder reports its first timeline.
- Manual Pause survives rebuffer, audio-focus events, and engine switches. End-of-file and fatal errors release audio focus. Explicit Play after EOF replaces the finished engine and starts cleanly from 0:00.
- D-pad ±10s seeking during a pending saved resume is relative to the intended saved position, not a provisional 0:00/0:02 decoder timestamp. Provisional playback before the saved seek does not block a qualifying AUTO startup fallback.
- The 20-second native startup timeout is restarted after an explicit Play resume or a transient audio-focus restoration. Time spent paused, in the background, or waiting for focus cannot cause an immediate fallback. A fatal decoder error issues one defensive Pause before abandoning audio focus.
- AndroidX BackHandler alone owns Back navigation, avoiding duplicate KEYCODE_BACK handling in the Compose remote input layer.
- Completed playback is saved authoritatively even if the native decoder emits EndReached before its final TimeChanged notification.
- When automatically chosen libmpv fails **before becoming ready** for a local `content://` source, the session may try Media3 once. An automatic local MPV startup that has not progressed after **20 seconds** can also fall back once, even if the native library failed to report an error. User Pause, audio-focus suspension, manual engine selection, and HTTPS sources never trigger a timeout handoff. A timeout is a recovery policy, not proof that the file is broken.
- Failed startup Retry retains the original saved playback offset even before a native timeline is available. Fatal playback errors disarm the stale Pause state; Play or Retry replaces the failed decoder with a fresh instance instead of calling `play()` on a dead engine. Explicit Start Over also waits for the decoder's updated timeline before committing new progress.
- Android keeps the display awake only while playback is requested (not after Pause, EOF or an error).
- Visible Seek ±10s and Start Over controls are disabled while the demuxer reports that the current item cannot be sought; unknown durations never display guessed timestamps.
- The exported external HTTPS media intent needs a media MIME type, so the app will not appear as a handler for every regular web page. Paste arbitrary HTTPS media links into the in-app stream field.
- Content-only app permissions through the Android document picker. Saved resume entries require persisted SAF grants; HTTPS URLs are never saved in Continue Watching.

## Source-level verification

`bash tools/check-source.sh` runs the following **without an Android SDK**, provided `kotlinc`, Java and Python 3 are installed:

| Check | Evidence |
| --- | --- |
| Playback policy | 47 pure-Kotlin checks |
| Actual `PlayerSession.kt` with test doubles | 57 checks, including pause/focus-safe startup timeouts, fatal-error silence and bounded native startup timeout, explicit fatal-error retry, relative seeks during pending resume, EOF replay, metadata-only resume preservation, explicit start-over, retry offset, EOS focus release, asynchronous startup fallback and release order |
| Actual `MediaSource.kt` with Android/MIME stubs | 16 URL and time checks |
| Actual libmpv and LibVLC Kotlin engine adapters with native library stubs | 2 simulated surface-startup scenarios |
| Compact player UI guard | 6 static checks (not UI instrumentation) |
| External VIEW intent policy | 1 static manifest assertion: no generic HTTPS browser interception |
| Release metadata | 1 static version/CI artifact naming check |
| XML parsing | 4 Android XML resources |

**Limitations:** Library stubs do not certify real API signatures, Gradle dependency resolution, native ABI interoperability, actual video output, real codec support, audio focus behavior or on-device CEC/D-pad behavior.

## Build with Android tooling

Prerequisites: JDK 17, Gradle 8.13, Android SDK Platform 36 + Build Tools 36.0.0, and network access to Gradle repositories. The archive includes a wrapper properties file, but **not the runnable Gradle wrapper scripts/JAR**. Install Gradle or use the included GitHub Actions workflow.

```bash
# Optional source checks (requires Kotlin CLI):
bash tools/check-source.sh

export ANDROID_HOME=/path/to/Android/Sdk
bash tools/build-and-qualify.sh
```

The build script runs `:session-smoke:run :native-smoke:run :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, then validates the two outputs: `app-armeabi-v7a-debug.apk` (ARM32) and `app-arm64-v8a-debug.apk` (ARM64). Each APK is checked for only its intended native ABI, native mpv/VLC ELF architecture, ZIP integrity and DEX. This is not an installation or playback test.

The [Android debug qualification workflow](.github/workflows/android.yml) runs on each push and pull request. A passing `main` build publishes both per-ABI debug APKs as a **private-repository prerelease**, avoiding the account's currently full Actions artifact storage. Previous v0.11 run #8 passed; refer to the latest Actions run for v0.21 results.

## Scope / known limitations

- Native codecs can fail on individual devices, networks, hardware decoders, DRM-protected files and broken or non-indexed containers; "all formats" is a compatibility goal, not a guarantee.
- Android TV devices without a working system document provider may require a dedicated MediaStore/USB file browser in a future milestone.
- Native engine HTTPS redirects have not been security audited; do not rely on them for untrusted or DRM-protected sources. Automatic fallback is deliberately restricted to local content; it does not retry arbitrary remote streams.
- Physical ARM32/ARM64 tests, 2-hour low-RAM soak tests, external subtitle styling across all engines, real headset/Bluetooth switching, and device-specific Android runtime qualification remain to be completed.

See `docs/ARCHITECTURE.md`, `docs/QUALIFICATION.md` and the v0.21 progress report.

## Repository and downloads

- Repository: [Vaydelux/BousyPlayer](https://github.com/Vaydelux/BousyPlayer) (private, main branch).
- Debug APKs: [GitHub prereleases](https://github.com/Vaydelux/BousyPlayer/releases). Select `armeabi-v7a` for a 32-bit ARM TV or `arm64-v8a` for a 64-bit ARM device. Android ABI is not always the same as the CPU's advertised 64-bit capability.
- Latest source changes and CI checks: [GitHub Actions](https://github.com/Vaydelux/BousyPlayer/actions).
- Device qualification is still pending; no universal codec-support claim.
