# Bousy Player v0.15 — Lazy TV Queue and libmpv Seek-Race Hardening

Date: 2026-11-11.

## Delivered source improvements

1. The playlist menu uses a Compose LazyColumn instead of allocating eager button controls for every queue entry. On menu open or index change, scroll to the selected item then restore remote focus. The vertical scroll parent is disabled for the lazy list to avoid unconstrained height.
2. The native libmpv adapter handles async seekability: after FILE_LOADED, a saved seek is attempted if available or retried up to 12 delayed times at 250ms; native seekable=true property changes also trigger a resume attempt.
3. Every retry is associated with the current source generation; user-requested seeks clear the pending automatic resume **after the native seek succeeds**, protecting D-pad and timeline intent.
4. Hidden remote seek hints display the last requested position while native time reports a provisional 0:02.
5. The CI-enforced fake libmpv tests now simulate late seekability, single-load surface detach/reattach and explicit-seek-vs-resume races. The real PlayerSession smoke suite checks seek-hint accuracy.

## Validation boundary

CI regression tests use actual production Kotlin source against fake Android/native APIs, followed by real Android Gradle compilation, lint and APK verification. They do not establish native event scheduling under load, actual MPEG-TS/MKV seek correctness, subtitle readability or D-pad remote behavior on devices.

## Follow-on architecture priorities

- Better structured typed playback diagnostics instead of user-facing raw engine errors.
- Per-engine codec-support matrix and selection telemetry retained only locally (no URLs or personal media names).
- Examine screen lifecycle process-death recovery and grant persistence.
- Add Compose UI instrumentation tests for queue scrolling and real focus when emulator/device automation is available.
- Decode/rebuffer profiling and ARM32 2-hour memory qualification before release.
