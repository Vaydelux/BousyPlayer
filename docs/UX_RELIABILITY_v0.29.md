# BousyPlayer v0.29 — Adaptive UI and player recovery milestone

Date: November 12, 2026

## Goals

Deliver cohesive premium appearance throughout BousyPlayer, protect narrow phone displays from unreadable player actions, preserve Android TV focusability, and give users control when an established video stalls in mid-playback.

## Delivered

- Four named palettes (Ocean, Obsidian, Amethyst, Ember) provide semantic background, panel, text, muted text, secondary and accent colors through one Compose CompositionLocal. Both Home and Player Options change the same theme, with a safe default and persistent on-device SharedPreferences. No cloud account/telemetry.
- Compact errors present Retry and alternative engine actions in a vertical full-width stack; wider layouts use a row. The queue/options side panel is capped at the available viewport width and video header/transport padding scales with compact screens.
- `PlaybackRecoveryPolicy.offerManualRecovery` activates only after 15,000ms of continuous REBUFFERING with active Play intent and no fatal engine error. The control lives in Player Options. Tapping it uses the same bounded explicit decoder Retry semantics and last trusted playback position; **no automatic retry or engine change** is introduced.
- Kotlin JVM policy smoke, Android JUnit tests, and compile-time/static source checks verify threshold boundaries, Pause/error guards, theme propagation and responsive screen implementation.
- Versioned debug publishing continues to require Play/Seek/lifecycle regression, Android compilation/lint, OSV Maven scan, native ELF integrity and hardening checks, and both split-ABI APK outputs.

## Open requirements for enterprise shipping

- Real-device and emulator screenshots at 320/360/480dp portrait, tablet landscape and 720p/1080p TV; large text and accessibility contrast review; physical D-pad navigation.
- MKV/TS/HLS playback, subtitles, user Pause, seeking and audio-focus tests on ARM32 and ARM64 devices.
- Full native C/C++ CVE mapping and redistribution licenses, production signing, build provenance and crash/ANR service-level requirements. Debug builds cannot be called production-certified.
