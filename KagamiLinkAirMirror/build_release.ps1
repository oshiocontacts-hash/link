$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

$appName = "KagamiLinkAirMirror"
$distDir = Join-Path $PSScriptRoot "dist"
$releaseDir = Join-Path $PSScriptRoot "release"
$zipPath = Join-Path $releaseDir "$appName-win64.zip"

Write-Host "[1/5] Installing build dependency (PyInstaller)..."
python -m pip install --upgrade pip pyinstaller | Out-Host

Write-Host "[2/5] Cleaning previous build outputs..."
if (Test-Path (Join-Path $PSScriptRoot "build")) { Remove-Item (Join-Path $PSScriptRoot "build") -Recurse -Force }
if (Test-Path $distDir) { Remove-Item $distDir -Recurse -Force }
if (Test-Path $releaseDir) { Remove-Item $releaseDir -Recurse -Force }
New-Item -ItemType Directory -Path $releaseDir | Out-Null

Write-Host "[3/5] Building executable..."
python -m PyInstaller `
  --noconfirm `
  --clean `
  --windowed `
  --name $appName `
  mirror_app.py | Out-Host

Write-Host "[4/5] Copying docs..."
$appOutDir = Join-Path $distDir $appName
Copy-Item (Join-Path $PSScriptRoot "README.md") (Join-Path $appOutDir "README.md") -Force

Write-Host "[5/5] Creating zip..."
Compress-Archive -Path "$appOutDir\*" -DestinationPath $zipPath -Force

Write-Host ""
Write-Host "Done."
Write-Host "EXE folder: $appOutDir"
Write-Host "ZIP file  : $zipPath"
