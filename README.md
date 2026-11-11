# Bousy Player v0.13 — Android TV / phone / tablet

A separate, Bousygram-inspired Android media-player source project written in Kotlin and Jetpack Compose. Supports opening user-chosen local SAF documents and HTTPS streams. The original Bousygram repository is not modified. No Telegram account, TDLib, embedding tools, DRM bypass, or proprietary streaming service is required.

> **Qualification status:** v0.11 compiled and passed Android unit tests, lint, APK assembly and ARM32/ARM64 native ELF checks in [CI build #8](https://github.com/Vaydelux/BousyPlayer/actions/runs/42982351716). v0.12 produced passing **separate ARM32 and ARM64 APKs** in [CI build #10](https://github.com/Vaydelux/BousyPlayer/actions/runs/42984416569). v0.13 adds source-level hardening and requires new CI qualification. Physical Android TV and phone playback is not yet tested.

## v0.13 core hardening

- Automatically selected **Media3** can hand off a **local MKV** to **LibVLC** once on synchronous initialization failure or asynchronous startup codec error. It preserves Continue Watching and releases the old engine before allocating the next one.
- A manual engine choice, remote URL, explicit Pause or mid-playback failure never triggers this automatic compatibility handoff.
- The file picker caps the active queue at **96** items to avoid eagerly creating thousands of queue buttons on low-memory Android TVs; the user receives a visible notice if more files were selected. Only chosen files receive persisted URI permission requests.
- Continue Watching parser accepts at most 20 entries and clamps invalid saved timing/title lengths; stream URLs are still never persisted.
- CI still produces independent ARM32/ARM64 APKs, with lint, unit tests, packaging and ELF verification. **Actual MKV decoding/codec compatibility remains untested on devices.**

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

The build script runs `testDebugUnitTest lintDebug assembleDebug`, then validates the two outputs: `app-armeabi-v7a-debug.apk` (ARM32) and `app-arm64-v8a-debug.apk` (ARM64). Each APK is checked for only its intended native ABI, native mpv/VLC ELF architecture, ZIP integrity and DEX. This is not an installation or playback test.

The [Android debug qualification workflow](.github/workflows/android.yml) runs on each push and pull request. A passing `main` build publishes both per-ABI debug APKs as a **private-repository prerelease**, avoiding the account's currently full Actions artifact storage. Previous v0.11 run #8 passed; refer to the latest Actions run for v0.13 results.

## Scope / known limitations

- Native codecs can fail on individual devices, networks, hardware decoders, DRM-protected files and broken or non-indexed containers; "all formats" is a compatibility goal, not a guarantee.
- Android TV devices without a working system document provider may require a dedicated MediaStore/USB file browser in a future milestone.
- Native engine HTTPS redirects have not been security audited; do not rely on them for untrusted or DRM-protected sources. Automatic fallback is deliberately restricted to local content; it does not retry arbitrary remote streams.
- Physical ARM32/ARM64 tests, 2-hour low-RAM soak tests, external subtitle styling across all engines, real headset/Bluetooth switching, and device-specific Android runtime qualification remain to be completed.

See `docs/ARCHITECTURE.md`, `docs/QUALIFICATION.md` and the v0.13 progress report.

## Repository and downloads

- Repository: [Vaydelux/BousyPlayer](https://github.com/Vaydelux/BousyPlayer) (private, main branch).
- Debug APKs: [GitHub prereleases](https://github.com/Vaydelux/BousyPlayer/releases). Select `armeabi-v7a` for a 32-bit ARM TV or `arm64-v8a` for a 64-bit ARM device. Android ABI is not always the same as the CPU's advertised 64-bit capability.
- Latest source changes and CI checks: [GitHub Actions](https://github.com/Vaydelux/BousyPlayer/actions).
- Device qualification is still pending; no universal codec-support claim.
