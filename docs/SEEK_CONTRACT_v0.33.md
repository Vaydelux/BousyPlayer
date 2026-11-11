# BousyPlayer v0.33 — Native seek acknowledgement contract

Date: November 12, 2026

## Failure mode

The UI polls decoder seekability periodically. HLS/live TS streams and transitions can change seekability before the next explicit D-pad or timeline seek. Previously the adapters could return normally without issuing the native command (Media3/MPV/LibVLC). PlayerSession would assume successful issuance, show a new seek target and wait for a confirmation that could never arrive.

## Changes

- Media3 checks `native.isCurrentMediaItemSeekable` immediately before native `seekTo`; otherwise it throws a stable non-identifying `Seek unavailable` exception.
- mpv checks current `released`, `fileLoaded` and native `seekable` state; otherwise it throws. The saved-resume retry is invalidated **only if** native seek actually succeeds.
- LibVLC checks current `released` and `native.isSeekable`; otherwise it throws. The saved resume target is invalidated **only if** native setTime succeeds.
- PlayerSession already handles thrown seek attempts with a neutral user message and without creating pending seek state or advancing Continue Watching. This change makes the adapter contract truthful for rapidly changing stream state.
- The Android CI workflow cancels older, superseded push or PR runs for the same source ref/event; the main release job retains its stricter independent publishing flow.

## Regression evidence and limits

Native fake engines exercise mpv and VLC loss of seekability at command time and verify no native seek executes, while subsequent successful seek preserves the explicit user's priority over a delayed resume. The real PlayerSession is compiled and tested with a throwing seek fake to ensure no false displayed position, pending seek or persisted jump. Media3's new check is exercised through Android compilation and a source guard. Physical device stream behavior and production release qualification are still outstanding.

## Required acceptance before publication

The branch and PR workflows must independently pass all Kotlin session and native-adapter tests, Android debug compilation/unit tests/lint, Maven OSV scan, dual-ABI ELF and native SBOM gates, Android APK signer check and release integrity negative tests. After merge, only the main workflow may publish ARM32/ARM64 debug prerelease APKs and its associated validated hash/signing evidence.

## D-pad feedback and background safety

The previously hidden-HUD left/right overlay used `screen.canSeek` from the last status poll and could display `+10s` even after a failed native command. `PlayerSession.seekTo/seekBy` now return a Boolean representing **command accepted**, not completed playback. Hidden HUD shows `+10s requested` or `−10s requested` only after an issued command, and `Seek unavailable` if the decoder rejects it. Fake decoder tests cover rejection, acceptance, unknown seekability, and background suppression.

`play()`, `retry()` and seek commands now refuse stale user callbacks while the Activity is backgrounded. This prevents a backgrounded screen from recreating a decoder or issuing a new seek after `onBackground()`. Foreground Play remains an explicit user action.

## LibVLC native snapshot read integrity

LibVLC's previous `snapshot()` swallowed native `time`, `length`, `isPlaying` and `isSeekable` getter failures and substituted a valid-looking `0:00` or Paused/Not Seekable state. A status-read exception is not authoritative playback data. These four critical fields now propagate exceptions to `PlayerSession.readDecoderSnapshot()`, which allows two consecutive transient misses before a latched user-recoverable error and preserves the last trusted progress. Caption-track polling still remains best-effort. Native adapter regression fixtures inject individual getter faults and verify the decoder can recover on a subsequent valid read.
