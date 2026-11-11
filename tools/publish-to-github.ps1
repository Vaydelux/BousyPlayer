# Standalone new-repository publisher for Windows PowerShell.
# User review required: default is PUBLIC. Does not touch Vaydelux/Bousygram.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI (gh).' }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install Git.' }
gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Sign in with gh auth login.' }
$repo = 'Vaydelux/BousyPlayer'
$null = gh repo view $repo 2>$null
if ($LASTEXITCODE -eq 0) { throw "$repo already exists; do not overwrite without reviewing it." }
if (Test-Path .git) { throw 'Existing Git history detected; inspect it before publishing.' }
git init -b main
if ($LASTEXITCODE -ne 0) { throw 'git init failed.' }
git add .
if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
git commit -m 'feat: standalone Bousy Player v0.11 source prototype'
if ($LASTEXITCODE -ne 0) { throw 'git commit failed; set Git user.name and user.email.' }
gh repo create $repo --public --description 'Standalone Android TV, phone and tablet multi-engine player' --source=. --remote=origin --push
if ($LASTEXITCODE -ne 0) { throw 'GitHub repository creation or push failed.' }
Write-Host 'Source published. Inspect Actions > Android debug qualification for actual APK build results.'
