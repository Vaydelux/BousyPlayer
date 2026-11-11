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
for name in ('Restart', 'Start over', '−10', '+10', '−10s', '+10s'):
    assert re.search(r'ActionButton\("' + re.escape(name) + r'".*?enabled\s*=\s*screen\.canSeek\)', buttons, re.S), f'{name} must be disabled if media is not seekable'
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
