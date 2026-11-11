# Bousy Player v0.19 — Surface Lifecycle & Long-Session Diagnostics

November 11, 2026 · Private repository: Vaydelux/BousyPlayer · main

## Changes
- Prevent duplicate mpv / LibVLC native vout attachment after redundant SurfaceHolder.created callbacks. Reinitialize VLC surface dimensions on every fresh attach. Clear keep-screen-on during release.
- Recover from an initial native video-attach failure upon a later surface recreation; only clear the relevant surface error, not decoding or source errors.
- Expand mandatory JVM native tests using actual MpvEngine/VlcEngine Kotlin with fake native/Android APIs: each surface cycles through destroy/create 20 times, receives duplicate created callbacks, and must preserve Pause, avoid duplicate mpv loadfile calls, update VLC output dimensions, keep descriptors balanced and ignore callbacks after release.
- Inject one-time native surface attachment failures to ensure a subsequent successful attachment unblocks the player without leaving stale surface errors or SAF handles.
- Add constant-memory `ResourceTrendRecorder` with explicit samples and first-sample deltas for Java heap, native heap and FD count; cap observation counters, preserve no media identifiers and do not infer a memory leak from a positive delta. The UI calls the probe only when requested.

## Build qualification
- Last qualified baseline: [v0.18 CI #19](https://github.com/Vaydelux/BousyPlayer/actions/runs/43001018061).
- v0.19: require a new green GitHub Actions job with native fake regression suite, 75 session tests, JUnit, lint and ABI-specific APK integrity + SHA-256 manifest. Do not declare v0.19 qualified until observed.

## Deferred real-device qualification
- Native engine release during SurfaceView recreation under Android hardware rendering.
- Actual MPV/VLC codec compatibility on TS/MKV, subtitle visibility, surface textures and repeated seeks.
- ARM32 two-hour media endurance, heap/FD trend and real memory-pressure signals.
- Final licensing/security checks, signed APK/App Bundle and release verification.
