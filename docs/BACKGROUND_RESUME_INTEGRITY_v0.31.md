# BousyPlayer v0.31 — Background power and saved-position integrity

Date: November 12, 2026

## Findings and changes

1. **Hidden background native polling:** The UI's `LaunchedEffect(session)` calls `session.refresh()` every 400 ms while the Compose surface is alive. An Activity stopped behind another app can remain composed, so the old session could keep querying native decoder state and updating health even after explicit lifecycle Pause. `refresh()` now exits for background sessions. `onBackground()` performs the final checkpoint and Pause first, and only then marks the session backgrounded; `onForeground()` allows polling again without granting autoplay.
2. **Stale status accepted as an explicit seek:** `refresh()` previously called `acknowledgePosition(snapshot)` even after `readDecoderSnapshot` failed, potentially interpreting cached pre-seek timestamps as proof that the decoder completed a seek. The acknowledgement now requires `snapshotReadable`. The pending target and saved progress remain unconfirmed until a fresh successful native read.
3. **SAF history prematurely erased:** `RecentStore.readAccessible()` had used a single persisted-grant enumeration as evidence to delete all records without a currently visible grant. Cloud document providers can temporarily fail or change state without user intent to forget. Now it filters what the home screen displays but does not rewrite durable records. Grant restoration makes those cards visible again. The user-initiated `Clear history` action still removes stored checkpoints.

## Added regression coverage

- Native fake counters prove 100 repeated background `refresh()` calls perform no native snapshot reads or increment sample counts.
- Foreground reentry resumes status polling without autoplay.
- Injected status-read failure immediately after a nearby explicit seek cannot acknowledge the old timestamp or overwrite history; a subsequent fresh successful read can.
- Android unit tests cover granted, missing and unavailable persisted-read grant snapshots; device instrumentation source verifies querying accessible cards never deletes durable history.

## Qualification and limitations

The actual PlayerSession is compiled with fakes under Kotlin JVM session smoke. Hosted Android CI compiles and statically qualifies the app, Android unit tests, instrumentation source, and both ARM ABI debug packages, then enforces resolved Maven OSV scanning and native ELF analysis. These checks do not certify real long-session ARM32 memory, CPU power, decoder hardware behavior, Android TV remote controls, SAF cloud-provider quirks, native C/C++ CVEs or production release signing.

## Foreground permission reconciliation

The Home screen now refreshes the visible, grant-filtered Continue Watching list on the next Activity ON_START event while no player session is active. That lets temporarily inaccessible SAF documents reappear without a process restart or mutation of stored history. The lifecycle listener is unregistered when its Compose owner changes. This is a visibility refresh, not an automatic file reopen or autoplay.
