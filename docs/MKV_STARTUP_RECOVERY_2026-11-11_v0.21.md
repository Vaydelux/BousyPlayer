# Bousy Player v0.21 — Bounded Silent Local MKV Startup Recovery

Date: November 11, 2026 · Private GitHub repository Vaydelux/BousyPlayer · main

## Delivered
1. **Silent Media3 MKV startup recovery**: 25 seconds of continuous active Play intent with no confirmed readiness can trigger exactly one Media3-to-LibVLC handoff, *only for automatically selected local content:// Matroska media*. This supplements the existing immediate fallback on initial Media3 error.
2. **Resume integrity**: The pending seek target is carried into VLC; the old decoder is released before the new decoder allocates resources.
3. **Readiness correctness**: Nonzero positions can be provisional while Media3 buffers a previously saved resume target. Only active playback, EOF, or non-buffering ready timeline confirms readiness.
4. **User intent and hardware safety**: Explicit Pause, audio-focus suspension, manual engine selection, HTTPS playback and an already-confirmed player never trigger the timeout. Pause-to-Play and focus-gain restart the active startup window. The fallback is one-way and one-time per queue item.
5. **CI regressions**: Pure policy tests, JUnit and actual PlayerSession fake-decoder checks cover timeout boundaries, pause/focus time accounting, provisional resumed timestamps and native release ordering.

## Qualification
Last green baseline: [v0.20 GitHub Actions #22](https://github.com/Vaydelux/BousyPlayer/actions/runs/43016568018). v0.21 requires a new successful CI run before publication claims. Real MKV codec compatibility, 25-second stall behavior, LibVLC native loading, D-pad, subtitles, background lifecycle and ARM32 sustained performance remain **not tested on physical devices**.

## Follow-up milestones
- Add a bounded, per-engine startup diagnostic timeline to investigate real TV decoder stalls without media URLs.
- Execute compiled ActivityScenario lifecycle tests on Android emulator/TV and validate real URI grant restoration.
- Profile actual MKV/TS decoder startup latency, repeat seeking, cache lifetime and ARM32 memory budget.
- Audit codec licenses, native C++ runtime compatibility, signing and privacy before production readiness.
