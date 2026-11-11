# Bousy Player v0.25 — Decoder-status integrity

Date: November 11, 2026 · Private repository: Vaydelux/BousyPlayer

## Problem addressed

Prior to v0.25, the third consecutive `snapshot()` exception generated the synthetic error `Playback state unavailable`. While the engine had not yet been marked ready, normal automatic MPV→Media3 or Media3→LibVLC error fallback could interpret this status-read failure as a codec failure. On a later successful refresh, the fatal message could also clear without an explicit user recovery action. The helper that captured current progress during engine switches polled `snapshot()` separately, without counting its exceptions.

## Fix

- Centralize decoder-status reads in `readDecoderSnapshot()` with bounded, private numeric counters.
- Quarantine stale seek/track/EOF state on a failed status read; preserve the last trusted timestamp and existing real decoder error, if any.
- While a read is invalid, skip automatic codec handoff, completion handling, and progress saving.
- On three consecutive failures, latch the fatal status. The session requests Pause, drops audio focus, and only a deliberate Retry or engine selection can rearm a new decoder.
- Reset the latch on decoder replacement and background release. Preserve an in-flight Continue Watching/seek target even if the old decoder cannot answer a final status read.
- Keep existing automatic fallback for genuine engine-reported startup errors or elapsed startup deadlines, not for synthetic status faults.

## Verification

New mandatory real-PlayerSession JVM smoke assertions cover temporary and fatal MPV status failures, no accidental LibVLC fallback for local MKV, no unrequested fatal recovery, retained Continue Watching, explicit Retry and counted switch-time status exceptions.

Qualification: **pending v0.25 GitHub Actions**. CI compiles Android instrumentation, but does not run it on an emulator/device. Physical MKV/TS playback, D-pad, subtitle rendering and ARM32 endurance are still outstanding.
