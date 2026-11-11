# Bousy Player v0.18 — Native Resource Ownership and Diagnostics

Date: November 11, 2026. Repository: private Vaydelux/BousyPlayer, main branch.

## Source changes
1. **LibVLC source replacement:** Former code silently ignored `native.stop()` errors and closed the active local media file descriptor. The new path propagates that failure so PlayerSession can safely dispose of the broken engine; the descriptor is not closed until after native teardown.
2. **LibVLC explicit seeks:** After native `setTime()` succeeds, clear `resumePosition` so a delayed Playing callback does not overwrite the requested seek.
3. **libmpv teardown:** Stop native demuxing and detach surface before `destroy()`; close owned descriptors with defensive cleanup even after native teardown exceptions. Release remains idempotent.
4. **JVM native fake tests:** Track opened and closed SAF file descriptors and native destroy counts. Exercises repeated mpv/VLC media replacement, synthetic stop errors, explicit seek versus delayed VLC resume, and duplicate release. These tests run in mandatory Gradle CI.
5. **Opt-in performance hook:** Player Options exposes a manually sampled Java heap, native heap and /proc/self/fd entry count. File descriptor link targets and media identifiers are never read. No telemetry is persisted or transmitted.

## Evidence and limitations
- Baseline v0.17: [CI #17 passed](https://github.com/Vaydelux/BousyPlayer/actions/runs/42998083471).
- v0.18: Check its GitHub Actions run for tests, Android compilation, lint, per-ABI ELF verification and prerelease assets.
- The descriptor tests use fake Android/VideoLAN/libmpv APIs; they do NOT measure real native leaks or prove demuxer stop sequencing on Android TV.
- Live memory/FD probe is optional and requires physical-device testing to interpret trends; individual values are not diagnostic of a leak.

## Next steps after architectural stabilization
- Instrumentation for activity pause/recreation, surface attach/detach, repeated engine switches and actual media FD counts.
- Two-hour ARM32 soak testing with telemetry snapshots and representative MKV/TS files.
- Native codec security, license compliance and final release signing/policy gates before production.
