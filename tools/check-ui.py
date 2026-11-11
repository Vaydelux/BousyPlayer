#!/usr/bin/env python3
"""Source-level guard for compact-player toggle and hidden remote focus.

This is intentionally NOT claimed as Android UI instrumentation.
"""
import re
from pathlib import Path

source = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/PlayerScreen.kt').read_text()
compact = re.search(r'if \(maxWidth < 680\.dp\)(.*?)(?=\n\s*} else \{)', source, re.S)
assert compact, 'compact transport layout not found'
button = re.search(r'ActionButton\(if \(session\.wantsPlay\).*?highlighted = true', compact.group(1), re.S)
assert button, 'compact play/pause control missing'
assert button.group(0).count('session.playPause()') == 1, 'compact Play/Pause must toggle exactly once'
assert 'Modifier.focusRequester(surfaceFocus).focusable()' in source, 'hidden TV HUD has no focus target'
assert 'screen.canSeek' in source, 'seek controls do not check seekability'
print('PASS: compact Play/Pause toggles only once')
print('PASS: hidden-HUD TV focus target present')
print('PASS: timeline seekability indicator present')
assert 'view.keepScreenOn = session.wantsPlay && !screen.ended && screen.error == null' in source, 'paused sessions must release keep-screen-on'
assert 'view.keepScreenOn = false' in source, 'view release must clear keep-screen-on'
print('PASS: paused/finished player screen releases keep-awake')

buttons = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/PlayerScreen.kt').read_text()
for name in ('−10', '+10', '−10s', '+10s'):
    assert re.search(r'ActionButton\("' + re.escape(name) + r'".*?enabled\s*=\s*screen\.canSeek\)', buttons, re.S), f'{name} must be disabled if media is not seekable'
for name in ('Restart', 'Start over'):
    assert re.search(r'ActionButton\("' + re.escape(name) + r'".*?enabled\s*=\s*screen\.canSeek\s*\|\|\s*screen\.ended\)', buttons, re.S), f'{name} must be available after EOF'
home = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/HomeScreen.kt').read_text()
assert 'enabled = enabled' in home, 'ActionButton must respect disabled state'
print('PASS: seek buttons disabled when native demuxer reports non-seekable media')

assert 'BackHandler { goBack() }' in source, 'AndroidX must own Back navigation'
assert not re.search(r'^\s*KeyEvent\.KEYCODE_BACK\s*->', source, re.M), 'Back must not be handled twice'
print('PASS: TV Back navigation has one owner')

assert 'LazyColumn(' in source and 'itemsIndexed(session.playlist' in source, 'queue must use lazy composition'
assert 'session.playlist.forEachIndexed' not in source, 'eager queue item composition is forbidden'
assert 'queueListState.scrollToItem(session.index)' in source, 'queue must scroll to selected item'
assert 'Modifier.focusRequester(queueFocus)' in source, 'queue must restore TV remote focus'
assert 'if (menu == PlayerMenu.QUEUE) Modifier' in source, 'LazyColumn must not be nested in unbounded vertical scroll'
assert 'session.seekDisplayPosition()' in source, 'remote seek preview must reflect pending target'
print('PASS: lazy queue TV focus, bounded scroll and pending-seek preview guards')

assert 'session.diagnostic' in source, 'error UI must render safe diagnostics'
assert 'Text(error,' not in source, 'raw decoder error text must not be rendered'
activity = Path('app/src/main/java/com/vaydelux/bousyplayer/MainActivity.kt').read_text()
assert 'rememberSaveable' in activity, 'active local document must survive Activity recreation'
assert 'SessionRestorePolicy.canRestore' in activity, 'restore needs persisted URI grant validation'
assert 'initialPlay = !restored' in activity, 'restored videos may not autoplay'
assert 'rememberActiveDocument(null)' in activity, 'leaving the player must clear saved restoration'
assert 'savedDocumentUri' in activity and 'savedDocumentTitle' in activity, 'save only one local document'
print('PASS: safe diagnostic display and paused/permission-bound activity restoration')

assert 'SubtitleSize.entries' in source, 'TV caption size options missing'
assert 'SubtitleColor.entries' in source, 'high-contrast caption presets missing'
assert 'Live subtitle styling is not yet supported by LibVLC' in source, 'VLC styling must disclose unsupported state'
assert 'session.playbackHealth' in source and 'on-device only' in source, 'private session-health UI missing'
assert 'subtitleAppearance' in Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text(), 'caption appearance not retained in session'
print('PASS: subtitle accessibility presets and bounded in-session health UI')

resourceProbe = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/ResourceProbe.kt').read_text()
assert 'ResourceProbe.sample()' in source and 'Sample resource usage' in source, 'manual resource sampling missing'
assert 'File("/proc/self/fd").list()?.size' in resourceProbe, 'resource probe must only count FDs, not read link targets'
assert 'getNativeHeapAllocatedSize()' in resourceProbe, 'native heap usage missing'
# Inspect executable lines, not comments describing the privacy guarantee.
probeCode = '\n'.join(line for line in resourceProbe.splitlines()
    if not line.lstrip().startswith(('*', '//')))
assert 'uri' not in probeCode.lower() and 'https:' not in probeCode.lower(), 'resource probe must not retain URLs'
assert 'readSymbolicLink' not in probeCode and 'readlink' not in probeCode, 'do not inspect descriptor targets'
print('PASS: on-demand Android heap and descriptor-count sampling without media identifiers')

assert 'ResourceTrendRecorder()' in source and 'Since first manual sample' in source, 'manual resource baseline/delta missing'
assert 'not proof of a memory leak' in source, 'resource trend may not be presented as a leak verdict'
trendPolicy = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackPolicy.kt').read_text()
assert 'class ResourceTrendRecorder' in trendPolicy and '1_024' in trendPolicy, 'resource trend must have bounded state'
print('PASS: manual resource baseline/delta and no automatic leak verdict')
 
activitySource = Path('app/src/main/java/com/vaydelux/bousyplayer/MainActivity.kt').read_text()
assert 'override fun onTrimMemory(level: Int)' in activitySource, 'Activity must forward OS memory trims'
assert 'activePlayerSession?.onMemoryPressure(level)' in activitySource, 'OS memory trim must reach active session'
assert 'activity.bindPlayerSession(session)' in activitySource, 'Activity session lifecycle binding missing'
assert 'activity.unbindPlayerSession(session)' in activitySource, 'Activity session lifecycle cleanup missing'
assert 'Lifecycle.Event.ON_START -> session.onForeground()' in source, 'foreground lifecycle reset missing'
assert 'Lifecycle.Event.ON_STOP -> session.onBackground()' in source, 'background lifecycle capture missing'
instrumentation = Path('app/src/androidTest/java/com/vaydelux/bousyplayer/MainActivityLifecycleTest.kt')
assert instrumentation.is_file() and 'scenario.recreate()' in instrumentation.read_text(), 'Android lifecycle instrumentation source missing'
print('PASS: activity memory-trim binding, foreground reentry and instrumentation source')

assert 'PlaybackPhase.REBUFFERING' in source, 'loading UI must distinguish rebuffer from startup'
assert 'PlaybackPhase.SEEK_DELAYED' in source, 'nonfatal seek-delay guidance missing'
assert 'session.playbackStatus.phase.label' in source, 'player options must show playback phase'
assert 'phaseTracker.explicitSeekRequested(clockMs())' in Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text(), 'explicit seek watchdog missing'
print('PASS: startup/rebuffer/seek-delay UI and safe playback phase diagnostics')

assert 'ActionButton("Retry pending seek"' in source, 'manual stalled-seek recovery control missing'
assert 'session.retryPendingSeek()' in source, 'manual retry must not rebuild decoder'
assert 'fun retryPendingSeek(): Boolean' in Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text(), 'bounded pending-seek retry API missing'
print('PASS: explicit EOF replay and user-triggered seek recovery controls')

instrumentedSession = Path('app/src/androidTest/java/com/vaydelux/bousyplayer/PlayerSessionLifecycleInstrumentedTest.kt')
assert instrumentedSession.is_file(), 'device lifecycle regression test source missing'
instrumentedSource = instrumentedSession.read_text()
assert 'onMemoryPressure(20)' in instrumentedSource and 'onMemoryPressure(80)' in instrumentedSource, 'OS background trim lifecycle test missing'
assert 'assertNull(player.engine)' in instrumentedSource and 'assertFalse(player.wantsPlay)' in instrumentedSource, 'decoder release / paused reentry tests missing'
assert 'switchEngine(EngineKind.VLC)' in instrumentedSource, 'manual engine-switch lifecycle test missing'
print('PASS: compile-only device instrumentation for ownership, background trim and user Pause')

assert 'Decoder status reads failed:' in source and 'consecutivePlaybackSnapshotFaults' in source, 'bounded snapshot diagnostics missing'
playerSessionSource = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text()
assert 'val first = next.snapshot()' in playerSessionSource, 'decoder ownership must require successful initial snapshot'
assert playerSessionSource.index('val first = next.snapshot()') < playerSessionSource.index('engine = next'), 'do not own failed native decoder'
assert 'DecoderSnapshotFailurePolicy.nextConsecutive' in playerSessionSource, 'snapshot failures must be bounded and recoverable'
deviceTests = instrumentedSession.read_text()
assert 'failedFirstNativeSnapshotIsReleasedAndNotOwnedByTheSession' in deviceTests
assert 'transientSnapshotFaultDoesNotReallocateNativeEngine' in deviceTests
print('PASS: startup native ownership and bounded, private snapshot fault diagnostics')

# Privacy actions are deliberately user initiated; automated source checks are not UI tests.
assert 'onClearHistory: () -> Unit' in home, 'history privacy callback missing'
assert 'Clear Continue Watching?' in home and 'onClearHistory()' in home, 'clear-history confirmation missing'
assert 'recentsStore.clear()' in activity, 'home must erase saved local playback history'
assert 'HistoryPolicy.safeTitle(name)' in activity, 'provider filenames require sanitization'
print('PASS: explicit local-history clearing and bounded provider titles')

theme = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/PlayerTheme.kt').read_text()
assert 'staticCompositionLocalOf' in theme and 'CompositionLocalProvider' in theme, 'Themed colors must share one composition'
for item in ('OCEAN', 'OBSIDIAN', 'AMETHYST', 'EMBER'):
    assert item in theme, f'Missing theme: {item}'
assert 'fromStored(raw: String?)' in theme, 'Invalid saved theme must use a safe default'
assert 'appearance.edit().putString("theme", next.name).apply()' in activity, 'Theme choice must persist'
assert 'onThemeSelected: (CinemaTheme) -> Unit' in home and 'isThemeDialogOpen' in home, 'Home theme selector missing'
assert 'onThemeSelected(choice)' in source, 'Player settings must use shared appearance state'
assert 'BoxWithConstraints(Modifier.fillMaxSize()' in source, 'Playback controls must adapt to available width'
assert 'val compactOverlay = maxWidth < 600.dp' in source, 'Narrow-screen error layout missing'
assert 'width(maxWidth.coerceAtMost(390.dp))' in source, 'Drawer width must not exceed viewport'
assert 'PlaybackRecoveryPolicy.offerManualRecovery(' in source, 'Long rebuffer manual action missing'
assert 'ActionButton("Recover stalled playback"' in source, 'User-initiated stalled playback recovery missing'
print('PASS: persistent premium themes, adaptive compact errors and user-directed rebuffer recovery')

assert 'accessibilityLabel: String? = null' in home, 'ActionButton screen reader label extension missing'
assert 'Modifier.semantics { contentDescription = accessibilityLabel }' in home, 'Accessibility semantics not applied'
assert 'maxWidth < 380.dp' in home, 'Extra-narrow home header must reflow'
assert 'accessibilityLabel = "Back to library"' in source, 'Back icon needs readable label'
assert 'accessibilityLabel = "Close player menu"' in source, 'Close icon needs readable label'
assert 'accessibilityLabel = if (session.wantsPlay) "Pause playback" else "Play video"' in source, 'Compact transport must expose stateful semantics'
print('PASS: compact screen labels and extra-narrow header accessibility gates')

sessionSource = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text()
assert 'private fun failTransportCommand()' in sessionSource, 'Command failures must have centralized fail-safe'
assert 'fun recoverStalledPlayback(): Boolean' in sessionSource, 'Manual rebuffer recovery must revalidate state on activation'
assert 'session.recoverStalledPlayback()' in source, 'UI must not bypass rebuffer predicate via unconditional Retry'
assert 'Playback controls became unavailable. Press Retry' in sessionSource, 'Native command errors need safe user feedback'
assert 'Seek request failed. Try again' in sessionSource, 'Failed seek cannot be silently ignored'
assert 'throwing Play disarms intent' in Path('tools/session-smoke/SessionSmoke.kt').read_text(), 'Play failure injection test missing'
assert 'throwing Pause tears down decoder' in Path('tools/session-smoke/SessionSmoke.kt').read_text(), 'Pause failure injection test missing'
assert 'focus gain play exception safely tears down decoder' in Path('tools/session-smoke/SessionSmoke.kt').read_text(), 'Focus gain transport regression absent'
print('PASS: transport failure fail-safe and explicit stalled-recovery state validation')

sessionPolicy = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text()
recentStore = Path('app/src/main/java/com/vaydelux/bousyplayer/data/RecentStore.kt').read_text()
assert 'if (closed || backgrounded) return' in sessionPolicy, 'Background native polling gate missing'
assert 'val timelineAcknowledged = snapshotReadable && acknowledgePosition(snapshot)' in sessionPolicy, 'Untrusted status must not acknowledge seeks'
assert 'backgrounded = true' in sessionPolicy and 'backgrounded = false' in sessionPolicy, 'Background lifecycle gate missing'
assert 'return all.filter {' in recentStore, 'History must hide unavailable cards without mutating disk'
assert 'if (accessible.size != all.size) write(accessible)' not in recentStore, 'Do not erase SAF history on missing grants'
assert 'isVisibleWithReadGrant' in recentStore, 'History filter needs explicit grant policy'
sessionSmoke = Path('tools/session-smoke/SessionSmoke.kt').read_text()
assert 'backgrounded Activity cannot poll a native decoder' in sessionSmoke, 'Background poll regression missing'
assert 'unreadable decoder snapshot cannot acknowledge a nearby explicit seek' in sessionSmoke, 'Stale seek acknowledgement regression missing'
print('PASS: background polling suspension, fresh seek acknowledgement and non-destructive history filter')

activitySourceV31 = Path('app/src/main/java/com/vaydelux/bousyplayer/MainActivity.kt').read_text()
assert 'event == Lifecycle.Event.ON_START && session == null' in activitySourceV31, 'Home must recheck SAF read permissions on foreground return'
assert 'recents = recentsStore.readAccessible()' in activitySourceV31, 'SAF grant restoration must make history cards visible again'
assert 'lifecycle.lifecycle.removeObserver(observer)' in activitySourceV31, 'Activity foreground history observer must be removed'
print('PASS: background-to-home SAF permission refresh without irreversible history loss')

media3 = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/Media3Engine.kt').read_text()
mpv = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/MpvEngine.kt').read_text()
vlc = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/VlcEngine.kt').read_text()
for engineName, engineSource in [('Media3', media3), ('libmpv', mpv), ('LibVLC', vlc)]:
    assert 'override fun seek(positionMs: Long)' in engineSource and 'throw IllegalStateException("Seek unavailable")' in engineSource, f'{engineName} must reject stale/unseekable seek commands'
smokeSource = Path('tools/native-smoke/NativeSmoke.kt').read_text()
assert 'libmpv silently accepted an unseekable user request' in smokeSource, 'Missing libmpv seek rejection regression'
assert 'LibVLC silently accepted an unseekable user request' in smokeSource, 'Missing LibVLC seek rejection regression'
assert 'native rejection cannot create a fake pending seek' in Path('tools/session-smoke/SessionSmoke.kt').read_text(), 'Missing session seek rejection case'
print('PASS: all decoders reject unseekable seeks and preserve truthfulness of session progress')

sourceV33 = Path('app/src/main/java/com/vaydelux/bousyplayer/ui/PlayerScreen.kt').read_text()
sessionV33 = Path('app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt').read_text()
assert 'val issued = session.seekBy(10_000)' in sourceV33 and 'val issued = session.seekBy(-10_000)' in sourceV33, 'HUD must react to actual seek command result'
assert '"Seek unavailable"' in sourceV33 and 'requested ·' in sourceV33, 'D-pad feedback must not misreport rejected seek'
assert 'fun seekTo(ms: Long): Boolean' in sessionV33 and 'fun seekBy(deltaMs: Long): Boolean' in sessionV33, 'Seek commands must expose acceptance'
assert 'if (closed || backgrounded || !snapshot.canSeek' in sessionV33, 'Seek rejected in background'
assert 'if (closed || backgrounded) return' in sessionV33, 'Background play/retry guard missing'
print('PASS: D-pad seek feedback tracks issued command and background transport is gated')
