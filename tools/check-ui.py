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
