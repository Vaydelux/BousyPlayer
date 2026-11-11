# Bousy Player v0.17 — Subtitle Accessibility and Local Health Counters

Date: 2026-11-11. Repository: private Vaydelux/BousyPlayer main.

## Delivered in source
1. **Caption size and contrast:** Small, Standard, Large and white/yellow/black-on-light palettes in the TV Audio / CC panel. Media3 uses CaptionStyleCompat, libmpv uses native subtitle properties. The app discloses LibVLC's lack of live styling support and retains preferences only for the current session.
2. **Private local health:** Bounded in-memory buffering observation percentage, longest observed continuous buffering interval (capped at two minutes), error event count and decoder handoff count. Counters contain no URLs, filenames, device identifiers, raw decoder strings or user content; no transmission or persistence.
3. **ARM32 memory hygiene:** cap track menu metadata and labels in Media3/VLC/mpv. Release cached track lists at native teardown.
4. **Testable architecture:** Gradle native fake tests check actual mpv property application; session fake tests cover caption selection across engine changes and explicit Pause; pure JVM tests cover bounded, private health counters and accessibility presets.

## Quality boundary
v0.16 was qualified by [GitHub Actions #16](https://github.com/Vaydelux/BousyPlayer/actions/runs/42993922342). The v0.17 build must pass before its debug APKs are qualified. Real subtitle rendering, native ABI coexistence, codec compatibility, TV D-pad interaction and ARM32 long-session resource use are still **not tested on hardware**.

## Next architectural priorities
- Confirm native LibVLC subtitle-formatting API before implementing unsupported live style features.
- Add instrumentation tests for native engine surface lifecycle, subtitle contrast and Activity recreation.
- Profile file descriptors, heap growth, buffering/decoder stalls during long ARM32 viewing.
- Complete native codec licensing and security review before a signed production release.
