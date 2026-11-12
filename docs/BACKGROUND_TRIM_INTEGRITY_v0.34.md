# BousyPlayer v0.34 — Background timeline and native ownership integrity

Date: November 12, 2026

## Root cause

The playback session normally maintains the last trusted decoder position and a separate requested seek/resume target. A seek or saved resume can be *issued but not yet confirmed* by the native player. `onBackground()` pauses and attempts its final checkpoint. Previously, `onMemoryPressure()` then polled the native decoder **again** and unconditionally changed `resumePositionAcknowledged` to true. With a provisional native timestamp or a pending target, this can fabricate a confirmed resume position; a later `close()` could also re-poll and try to persist an unconfirmed status.

## Implemented invariants

1. When ON_STOP occurs, `onBackground()` pauses and takes the last native status checkpoint before entering background.
2. After entering background, the player does not poll status in memory trim, in repeated stale UI commands, or during Close.
3. Memory trim distinguishes `lastAttemptedPositionMs` (user's pending intent) from `snapshot.position` (last native-reported value). It keeps a pending target for user-triggered foreground reopening **without** acknowledging it or copying it into a saved checkpoint.
4. Once the engine has been released, `saveProgress()` refuses to use its cached snapshot as a newly verified timeline. The last persisted position can only be advanced by an active decoder's acknowledged status.
5. The decoder's internal `open()` method rejects calls while backgrounded. User-facing controls for EOF restart, engine switching, queue navigation, manual recovery, subtitle selection, speed/aspect and track changes also reject late callbacks.
6. The foreground remains explicitly paused after ON_START. Only a new user action may allocate/reopen a decoder or resume playback.

## Deterministic tests

The Gradle `:session-smoke:run` module compiles the **real PlayerSession source** against a fake engine, injects delayed seek acknowledgement and provisional local TS startup, and checks native poll/release counts, requested-vs-confirmed timestamps, persisted Continue Watching checkpoints, background callback isolation, close semantics and user-triggered reopen. These are source-level lifecycle simulations, **not** physical Android TV tests.

## Production qualification still required

Test ON_STOP and UI_HIDDEN ordering on actual OEM TVs, ARM32 low-memory trims, two-hour mixed-format playback, screen-off and system media-key callbacks, real subtitle tracks, native player readiness after repeated decoder release and reconstruction, production signing, native dependency licenses, and signed build provenance. Passing Kotlin smoke and Android APK signing checks alone does not establish hardware compatibility.
