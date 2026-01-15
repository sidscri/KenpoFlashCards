param(
  [string]$Source = "",
  [string]$Dest = "",
  [switch]$BuildExe,
  [switch]$BuildInstaller
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
  # tools\ -> KenpoFlashcardsWebServer_Packaged_in_exe_msi\ -> repo root
  return (Resolve-Path (Join-Path $PSScriptRoot "..\.." )).Path
}

function Ensure-Dir($p) {
  if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p | Out-Null }
}

function RoboMirror($src, $dst, [string[]]$excludeDirs=@(), [string[]]$excludeFiles=@()) {
  if (-not (Test-Path $src)) { return }
  Ensure-Dir $dst

  $xd = @()
  foreach($d in $excludeDirs){ $xd += "/XD"; $xd += (Join-Path $src $d) }

  $xf = @()
  foreach($f in $excludeFiles){ $xf += "/XF"; $xf += $f }

  $args = @($src, $dst, "/MIR", "/NFL", "/NDL", "/NJH", "/NJS", "/NP", "/R:2", "/W:1") + $xd + $xf
  & robocopy @args | Out-Null
}

function Copy-File($src, $dst) {
  Ensure-Dir (Split-Path $dst -Parent)
  Copy-Item -Force $src $dst
}

function Patch-AppForProgramData($appPath) {
  $txt = Get-Content -Raw -Encoding UTF8 $appPath

  if ($txt -match "KENPO_DATA_ROOT" -and $txt -match "PROGRAMDATA") {
    Write-Host "[OK] app.py already ProgramData-aware."
    return
  }

  if ($txt -notmatch "^\s*import\s+sys\s*$"m) {
    $txt = $txt -replace "(?m)^(import\s+[^\r\n]+\r?\n)", "`$1import sys`r`n"
  }

  $needle = "APP_DIR = os.path.dirname(os.path.abspath(__file__))`r`n`r`nDATA_DIR = os.path.join(APP_DIR, \"data\")"
  if ($txt -notlike "*APP_DIR = os.path.dirname(os.path.abspath(__file__))*") {
    Write-Host "[WARN] Could not find expected APP_DIR line to patch."
    return
  }

  $block = @"
# --- Runtime base directories ---
# For packaged installs (EXE/MSI), store writable data under ProgramData, similar to Sonarr.
# You can override with:
#   - KENPO_WEBAPP_BASE_DIR (where static/assets live)
#   - KENPO_DATA_ROOT (root folder for writable data)
#   - KENPO_WEB_PORT / KENPO_HOST

def _default_programdata_root() -> str:
    if os.name != \"nt\":
        return os.path.dirname(os.path.abspath(__file__))
    pd = os.environ.get(\"PROGRAMDATA\") or r\"C:\\ProgramData\"
    return os.path.join(pd, \"KenpoFlashcardsWebServer\")

# Where the web app's static/assets are located (important for PyInstaller).
APP_DIR = (
    os.environ.get(\"KENPO_WEBAPP_BASE_DIR\")
    or getattr(sys, \"_MEIPASS\", None)
    or os.path.dirname(os.path.abspath(__file__))
)

# Root folder for writable data (breakdowns, users, profiles, logs, keys).
# - Frozen/packaged builds default to ProgramData\\KenpoFlashcardsWebServer
# - Source/dev defaults to APP_DIR (project-local)
DATA_ROOT = (
    os.environ.get(\"KENPO_DATA_ROOT\")
    or (_default_programdata_root() if getattr(sys, \"frozen\", False) else APP_DIR)
)

DATA_DIR = os.path.join(DATA_ROOT, \"data\")
LOGS_DIR = os.path.join(DATA_ROOT, \"logs\")
STATIC_DIR = os.path.join(APP_DIR, \"static\")
"@

  # Replace the first APP_DIR/DATA_DIR pair if present
  $txt = $txt -replace "APP_DIR\s*=\s*os\.path\.dirname\(os\.path\.abspath\(__file__\)\)\s*\r?\n\s*\r?\n\s*DATA_DIR\s*=\s*os\.path\.join\(APP_DIR,\s*\"data\"\)", $block

  Set-Content -Encoding UTF8 -Path $appPath -Value $txt
  Write-Host "[OK] Patched app.py for ProgramData layout."
}

$repo = Resolve-RepoRoot
if ([string]::IsNullOrWhiteSpace($Dest))   { $Dest   = Join-Path $repo "KenpoFlashcardsWebServer_Packaged_in_exe_msi" }
if ([string]::IsNullOrWhiteSpace($Source)) { $Source = Join-Path $repo "KenpoFlashcardsWebServer" }

if (-not (Test-Path $Source)) { throw "Source not found: $Source" }
if (-not (Test-Path $Dest))   { throw "Dest not found: $Dest" }

Write-Host "Source: $Source"
Write-Host "Dest  : $Dest"
Write-Host ""

# app.py
Copy-File (Join-Path $Source "app.py") (Join-Path $Dest "app.py")

# static/assets/templates (if present)
RoboMirror (Join-Path $Source "static")    (Join-Path $Dest "static")    @("__pycache__") @()
RoboMirror (Join-Path $Source "assets")    (Join-Path $Dest "assets")    @("__pycache__") @()
RoboMirror (Join-Path $Source "templates") (Join-Path $Dest "templates") @("__pycache__") @()

# Seed data defaults (copy safe top-level json, skip secrets and user progress)
$srcData = Join-Path $Source "data"
$dstData = Join-Path $Dest "data"
Ensure-Dir $dstData
Ensure-Dir (Join-Path $dstData "users")

if (Test-Path $srcData) {
  Get-ChildItem -Path $srcData -File | ForEach-Object {
    $name = $_.Name.ToLowerInvariant()
    if ($name -eq "secret_key.txt") { return }
    if ($name -eq "api_keys.enc")   { return }
    if ($name -like "*.enc")        { return }
    if ($name -like "*.log")        { return }
    Copy-Item -Force $_.FullName (Join-Path $dstData $_.Name)
  }
}

# Ensure we don't ship secrets in the packaged project
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $dstData "secret_key.txt")
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $dstData "api_keys.enc")

# Apply ProgramData patch (best-effort)
Patch-AppForProgramData (Join-Path $Dest "app.py")

Write-Host ""
Write-Host "[DONE] Sync complete."

if ($BuildExe) {
  Write-Host ""
  Write-Host "[BUILD] packaging\\build_exe.bat"
  Push-Location $Dest
  $env:CI = "true"
  & cmd /c "packaging\\build_exe.bat"
  Pop-Location
}

if ($BuildInstaller) {
  Write-Host ""
  Write-Host "[BUILD] packaging\\build_installer_inno.bat"
  Push-Location $Dest
  $env:CI = "true"
  & cmd /c "packaging\\build_installer_inno.bat"
  Pop-Location
}
