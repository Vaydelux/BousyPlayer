#!/usr/bin/env python3
"""Protect against accidental broad HTTPS link interception by the media player."""
from pathlib import Path
from xml.etree import ElementTree as ET
ANDROID = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(Path('app/src/main/AndroidManifest.xml')).getroot()
view_filters = []
for filter_node in root.findall('.//intent-filter'):
    actions = [x.get(f'{ANDROID}name') for x in filter_node.findall('action')]
    if 'android.intent.action.VIEW' not in actions:
        continue
    schemes = [x.get(f'{ANDROID}scheme') for x in filter_node.findall('data')]
    mime_types = [x.get(f'{ANDROID}mimeType') for x in filter_node.findall('data')]
    view_filters.append((schemes, mime_types))
https_filters = [types for schemes, types in view_filters if 'https' in schemes]
assert https_filters, 'HTTPS media intent-filter is missing'
for types in https_filters:
    assert any(t in ('video/*', 'audio/*') for t in types), 'HTTPS ACTION_VIEW must specify media MIME types'
    assert all(t is not None for t in types[1:]), 'Unrestricted web link intent filter detected'
print('PASS: HTTPS external VIEW requires media MIME; generic web URLs remain with browsers')
