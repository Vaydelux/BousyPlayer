#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
bash tools/check-policy.sh
bash tools/check-session.sh
bash tools/check-model.sh
bash tools/check-native.sh
python3 tools/check-ui.py
python3 tools/check-manifest.py
python3 tools/check-release-metadata.py
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
files = sorted(Path('app/src/main').rglob('*.xml'))
for f in files: ET.parse(f)
print(f'PASS: {len(files)} Android XML resources parsed')
PY
