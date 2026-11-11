# Bousy Player v0.24 — Native Decoder Ownership and Playback Status Resilience

Date: November 11, 2026 · Repository: private Vaydelux/BousyPlayer · branch: main

## Changes
1. **Safe decoder handoff:** `PlayerSession.open()` now calls the new engine's initial `snapshot()` before publishing it as `engine`. A thrown native snapshot releases only the candidate in the existing startup exception handler. The session cannot retain a reference to a released decoder after all startup attempts fail.
2. **No false Play intent after total startup failure:** If no candidate loads successfully, disarm `wantsPlay`, abandon audio focus and report a safe recovery message without media titles, file paths, signed URLs, or raw exceptions.
3. **Bounded status-read errors:** `PlayerSession.refresh()` tolerates the first two consecutive `snapshot()` exceptions and temporarily hides stale seek/track controls. A successful status read clears the consecutive count. The third consecutive exception produces a fatal playback error, stops playback intent, requests a pause and abandons audio focus. No automatic engine changes, retries or decoder reallocation occur.
4. **Local-only diagnostics:** The player Options drawer shows capped total and consecutive snapshot fault counts. Only numeric counts exist; no media identifiers, raw exception messages or network telemetry are collected or persisted.
5. **Mandatory regressions:** Tests for first snapshot failure, exact native release count, two transient status exceptions, successful recovery, three consecutive failures and user-controlled Retry. Android instrumentation sources exercise a fake decoder and are compiled but not executed on GitHub hosted runners.

## Evidence and quality boundary
- v0.23 baseline passed: [GitHub Actions #25](https://github.com/Vaydelux/BousyPlayer/actions/runs/43036121213) with 108 session checks, Android builds and verified APKs.
- v0.24 is not considered qualified until its new CI run succeeds and publishes both ABI APKs.
- Runtime behavior under genuine libmpv/LibVLC/Media3 failures, Android TV remote controls, TS/MKV seeking and ARM32 endurance require physical device testing.

## Next milestones
Execute Android emulator/device instrumentation, profile real native decoder errors and audio focus, verify memory pressure and seek under TS/MKV samples, then review codecs/licenses and production signing.
