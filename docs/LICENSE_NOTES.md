# Third-party license and redistribution review (incomplete)

Bousy Player's new code uses architectural ideas from [Bousygram](https://github.com/Vaydelux/Bousygram), licensed MIT, Copyright (c) 2026 Vaydelux.

- AndroidX Media3, Compose and other AndroidX libraries have Apache 2.0 or their published licenses. Review their notices.
- VideoLAN `org.videolan.android:libvlc-all:3.6.5` includes LGPL-2.1 libraries. Redistribution must satisfy license text, notice and applicable reverse-engineering/relinking requirements. Treat transitive native libraries individually.
- `dev.jdtech.mpv:libmpv:1.0.0` wrapper lists MIT; inspect mpv, FFmpeg and every built native dependency and enabled codec. The wrapper license alone does not license all packaged native bits.
- A release should produce an open-source attribution page and include license notices; independent legal review may be needed for codec patents and commercial distribution.

This document is an engineering checklist, not a legal clearance.

## v0.27 Maven inventory vs native-license boundary

The generated CycloneDX debug SBOM enumerates resolved Maven dependencies (including the AAR wrappers for libmpv and LibVLC). That does **not** enumerate transitive C/C++ FFmpeg/mpv/VLC shared libraries inside those archives, their codec patent obligations, LGPL notices or their exact linked versions. The OSV Maven query is not a native security scan. Before distributing a commercial production APK, collect native provenance, create a complete third-party notice bundle and obtain a redistribution/legal review.
