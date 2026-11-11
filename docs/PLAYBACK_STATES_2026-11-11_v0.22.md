# Bousy Player v0.22 — Playback Stage, Seek Feedback and Progress Integrity

2026-11-11 · Private GitHub repository Vaydelux/BousyPlayer · main branch

## Implemented source changes
1. **Explicit playback phases:** Pure Kotlin PlaybackPhaseTracker distinguishes initial startup, playback, rebuffering, pending explicit seek, delayed seek (12 seconds), Pause, waiting, EOF and decoder errors. It keeps only bounded numeric phase durations and contains no stream URLs, media titles, file names or credentials.
2. **No uncontrolled recovery:** Seek delays are displayed nonfatally. The tracker does not command the player, restart a decoder, alter engine preference or persist any event log. The existing manual Retry gate and single native/MKV startup fallbacks remain unchanged.
3. **Backwards seek correctness:** Earlier resume acknowledgement logic could treat a stale timestamp far ahead of an explicitly requested backwards seek as a success. Explicit seeks now require the reported native position to be within 5 seconds of the chosen target; saved resume acknowledgement has its existing separate policy. Continue Watching is not overwritten by a provisional stale position.
4. **Pause/focus protection:** Seek delay measurement restarts on user Play or focus gain. Paused and focus-suspended states never show an active seek warning.
5. **UI:** Separate 'Starting playback' from 'Rebuffering', show a delayed-seek instruction with no hidden automatic engine switch, and expose the current stage/duration in the Options drawer.
6. **Mandatory tests:** Android-free pure JVM policy tests, Android JUnit tests, actual PlayerSession fake decoder tests for stale backward seek and time-bound warning, native adapter simulations, Android lint and per-ABI package verification.

## Qualification
Last qualified baseline: v0.21 [GitHub Actions #23](https://github.com/Vaydelux/BousyPlayer/actions/runs/43021579698). v0.22 requires its own green CI run and matching ARM32/ARM64 prerelease to be considered build-qualified. The suite cannot demonstrate visual decoder output, native DTS/AC3, actual seeking on unindexed TS, Android TV focus or 2-hour ARM32 stability.

## Next work
- Run on-device lifecycle instrumentation and compare actual seek behavior for MPV, LibVLC and Media3 on indexed/non-indexed MKV/TS.
- Compare measured startup-to-first-frame and rebuffer durations against app phase reports.
- Verify FD/heap trends under actual memory-pressure conditions and long viewing.
- Complete codec security/licensing and signed-production-release verification.
