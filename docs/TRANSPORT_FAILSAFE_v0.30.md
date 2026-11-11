# BousyPlayer v0.30 — Fail-safe native playback controls

Date: November 12, 2026

## Observed failure modes and changes

- **Suppressed native transport errors:** Previous `PlayerSession.play()`, `pause()` and audio-focus callback paths silently swallowed native exceptions. An engine could remain referenced, potentially audible, while Play/Paused UI and audio focus ownership disagreed.
  - A centralized `failTransportCommand` path retains the last trusted checkpoint, releases the decoder, disarms Play, abandons audio focus and reports a stable generic diagnostic without leaking native error messages.
  - The next explicit Retry reconstructs the decoder through the existing rate limiter. No transport operation implicitly switches playback engines.
- **Stale recovery button activation:** The v0.29 long-rebuffer Options button used `retry()` directly. A visible control could remain tappable after Pause or buffering completion. `recoverStalledPlayback()` now rechecks active REBUFFERING, 15 seconds, Play intent, focus suspension, and error-free state *when pressed*, and returns whether the new decoder was successfully opened.
- **Invisible seek rejections:** User seek exceptions now produce neutral on-screen guidance without changing the existing acknowledged timeline or attempting automatic decoder recreation.

## Regression and safety evidence

JVM session smoke includes controlled throwing Play/Pause engines, failed audio-focus gain, trusted local progress preservation on failure, user-directed rebuffer restart/position continuity, and rejection of repeated or paused recovery attempts. The source-level UI guard requires recovery to go through the safe session API. Android unit tests, lint, native adapter regression, Maven OSV vulnerability gate, per-ABI APK/ELF checks, native binary inventory and both ABI APK builds remain mandatory before publishing.

## Limits

The JVM smoke suite mocks platform and native decoder behavior and cannot certify real JNI, device audio focus, Android TV D-pad semantics or codec support. Additional work includes hardware stress/low-memory runs, native C/C++ CVE and LGPL review, production signing, privacy-safe crash/ANR telemetry and rollout criteria.
