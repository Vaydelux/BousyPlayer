#!/usr/bin/env bash
# Requires user-authenticated GitHub CLI. Creates a NEW standalone repository.
# Never touches the Bousygram repository or existing repositories.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
repo="Vaydelux/BousyPlayer"
command -v gh >/dev/null || { echo "Install the GitHub CLI (gh) first." >&2; exit 2; }
command -v git >/dev/null || { echo "Install git first." >&2; exit 2; }
gh auth status >/dev/null
if gh repo view "$repo" >/dev/null 2>&1; then
  echo "$repo already exists. Refusing to overwrite it. Inspect and push manually." >&2
  exit 3
fi
if [[ -e .git ]]; then
  echo "This folder already contains Git history. Review it before publishing." >&2
  exit 4
fi
git init -b main
git add .
git commit -m 'feat: standalone Bousy Player v0.11 source prototype'
gh repo create "$repo" --public --description 'Standalone Android TV, phone and tablet multi-engine player' --source=. --remote=origin --push
printf '\nRepository created. The Android GitHub Actions workflow can now run.\n'
