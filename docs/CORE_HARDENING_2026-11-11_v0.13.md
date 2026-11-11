# Bousy Player v0.13 — Core enterprise hardening

Date: November 11, 2026.

## Scope and rationale
1. **Bounded MKV compatibility fallback.** Media3 remains first choice for MKV. Synchronous constructor/load failures and asynchronous errors *before playback ever became ready* can move automatically to LibVLC exactly once. Local content:// sources only; never for user-selected engines, an explicit Pause, HTTPS, or mid-playback failures.
2. **Resume and resource ownership.** The Media3 decoder is released before the VLC instance is allocated. The saved position (including an as-yet unacknowledged resume target) is supplied to VLC.
3. **ARM32 memory safety at the selection boundary.** At most 96 files enter the active playlist, and the user sees a notice if their selection is larger. Persisted SAF grants are requested for included files only. A future lazy queue UI can increase the ceiling after profiling.
4. **Defensive history parsing.** Parse only the first 20 saved entries, clamp negative/corrupted timing, and bound text fields. No remote URL logging or stream-history persistence.
5. **Regression gates.** Pure policy/JUnit tests cover source restrictions, manual-vs-automatic choices and queue size. JVM session test doubles cover asynchronous and synchronous MKV fallback, pause protection, and decoder release order.

## Validation boundary
- Baseline v0.12: [successful CI build #10](https://github.com/Vaydelux/BousyPlayer/actions/runs/42984416569), verified 32/64-bit APKs.
- v0.13: consult triggered GitHub Actions run for compile, unit test, lint and native APK validation.
- **Not verified**: actual MKV/TS playback, subtitles, remote navigation, native ABI loading, 2-hour ARM32 memory tests, or network redirects.

## Next core milestones
- Migrate large queue UI to a lazy TV-friendly list with explicit D-pad focus restoration and an indexed search option.
- Add deterministic native adapter event generation tests under rapid source replacement, seek and surface recreation.
- Validate external subtitles using actual Media3 MIME/format handling, and surface unsupported-track state to the user.
- Add CI static Android security/privacy checks for URI permission and manifest changes.
- Revisit per-engine caches using profiling data; do not claim universal codec support.
