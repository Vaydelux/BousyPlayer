# Bousy Player v0.23 — Explicit Seek Recovery and Native-Safe Replay

Date: November 11, 2026. Repository: private Vaydelux/BousyPlayer, main branch.

## Implemented
1. **Manual pending-seek retry:** For an explicit seek that has been unconfirmed for 12 active seconds, the Options drawer now offers a Retry pending seek action. It retries the same target on the existing decoder, re-arms the seek warning clock only when the native seek request succeeds, and preserves Continue Watching until native timeline acknowledgement. Failure reports an actionable, privacy-safe message. No automatic seek reissue, background work, new decoder or engine change is introduced.
2. **Clean EOF restart:** For a completed video, user-triggered Start Over now reconstructs the decoder with a fresh 0:00 load and explicit play intent, even if native seeking has become unavailable. Non-EOF starts still use ordinary in-place seeking. No automatic replay.
3. **JVM regression tests:** Actual PlayerSession fake engine checks cover seek retries after a 12-second delay, phase reset on success, failure preserving target, native allocation counts, no unexpected playback change, and an EOF decoder with nonseekable timeline starting cleanly.
4. **Android instrumentation source:** A new PlayerSessionLifecycleInstrumentedTest uses Android Context + fake playback engine to validate idempotent background memory trim, no foreground autoplay, user-driven recovery at retained position and explicit Pause across engine changes. It compiles in CI, but requires an emulator/phone/TV to execute.

## Qualification
- Last successful baseline is v0.22 [GitHub Actions #24](https://github.com/Vaydelux/BousyPlayer/actions/runs/43026999771), with 102 session regressions and two verified APKs.
- v0.23 requires a successful GitHub Actions run for its expanded JVM native/session smoke checks, JUnit, Android lint, androidTest source compilation, per-ABI APK ELF integrity and checksum manifest. Do not treat source-only checks as device/runtime certification.

## Deferred validation
- Execute on-device Android instrumentation using an emulator or Android TV and validate actual media rendering across Media3, libmpv and LibVLC.
- Investigate real unindexed MPEG-TS seeking, MKV/HEVC/AC3 subtitles and 2-hour ARM32 memory/FD stability.
- Security, third-party codec license compliance and signed production release gates are not complete.
