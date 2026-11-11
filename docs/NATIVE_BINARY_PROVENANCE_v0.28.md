# BousyPlayer v0.28 — Native ELF security and release traceability

Date: November 12, 2026

## Implementation and gates

1. **Packaged native identity:** Examine every final APK native `lib/<abi>/*.so` payload rather than assuming a Maven AAR corresponds to a single known native version. Record file SHA-256, length, ELF architecture/class, DT_NEEDED dependencies, GNU Build ID where present, GNU RELRO presence and executable-stack status.
2. **Fail closed on dangerous ELF properties:** Reject duplicate ZIP library entries, malformed/cross-ABI ELF, different native filenames across 32-bit and 64-bit variants, PT_LOAD segments with simultaneous writable+executable permissions, executable PT_GNU_STACK and DT_TEXTREL or DF_TEXTREL. Missing RELRO or GNU_STACK is reported for a separate vendor audit, not silently classified as safe.
3. **Native-file CycloneDX SBOM:** Emit a CycloneDX 1.5 inventory using `type=file` and SHA256 hashes (not a guessed package/version). Publish this and the richer per-ABI native JSON report with the debug APKs and Maven/OSV reports, all checked by SHA256SUMS.
4. **Independent regression tests:** Synthetic ELF32 and ELF64 binaries verify note parsing, architecture checks and rejection of executable stacks, RWX loads, text relocations, duplicate library entries and inconsistent ABI sets.
5. **Release isolation:** Branch/PR jobs remain read-only; main-only publisher runs the full Android build, Maven OSV gate, binary integrity checks and reports before writing releases.

## Open enterprise work

- **Native CVE and vendor provenance:** Raw ELF hashes/Build IDs do not prove C/C++ source version or security patch level. Audit upstream VLC, mpv, FFmpeg and other packaged native objects individually.
- **Licensing:** The LGPL and codec notices, source or object-file relinking rights, codec patent risks and third-party attributions need legal review and complete notices.
- **Production distribution:** Debug APKs are not production-signed, provenance-attested or reproducibly built. Credential security and signing should have independent approvals.
- **Physical qualification:** Android TV D-pad, HDMI/CEC, TS/MKV seeks, subtitle rendering, audio focus, memory/FD performance and ARM32 long-session soak tests are not executed by CI.
