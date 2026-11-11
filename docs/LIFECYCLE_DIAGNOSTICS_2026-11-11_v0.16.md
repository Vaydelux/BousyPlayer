# Bousy Player v0.16 — Lifecycle, Subtitle, and Diagnostics Hardening

Date: November 11, 2026.

## Completed source changes
- **Safe Activity recreation:** rememberSaveable retains only one previously selected persistently granted content:// document URI and bounded title; no HTTPS stream URLs or large playlist state. On recreation, recheck Android persisted URI read grants, create a new player paused, preserve Continue Watching position; clear on Leave. This is not an always-on process death service.
- **Safe playback diagnostics:** return code BP-ENGINE-CATEGORY and fixed, actionable guidance. Do not surface raw native decoder exceptions, signed URLs, local file paths, or tokens in the error UI.
- **Subtitle/track safety:** reject stale or mismatched IDs before touching engine, handle track request failure, and show pending selection rather than falsely claiming the track is immediately active. External subtitle requests accept only Android content URIs.
- **Compatibility/fallback UX:** fixed synchronous candidate selection message to reflect actual fallback (Media3 or LibVLC), not always Media3.
- **Regression enforcement:** pure Kotlin/Android JUnit checks cover grants, bounds and diagnostics; actual PlayerSession fake-engine suite covers paused restore, retained offset, stale subtitle IDs, and private-path diagnostic safety; source UI gate guards restore and error display.

## Boundaries
Previously qualified v0.15: [Build #14](https://github.com/Vaydelux/BousyPlayer/actions/runs/42989807568). v0.16 build results must come from its new CI run. The Activity saved-state behavior has no physical lifecycle instrumentation yet; foreground process death by Android, codec-specific track behavior and TV remote correctness remain unverified.

## Next engineering priorities
- Real lifecycle instrumentation tests (kill/recreate Activity with saved state, retained SAF grant).
- Per-engine subtitle language/track labels and stable-selection verification on device.
- TV D-pad/focus UI tests through Compose instrumentation, and network redirect hardening.
- Strict permission and data-retention controls, cache budget profiling and native C++ ABI compatibility.
