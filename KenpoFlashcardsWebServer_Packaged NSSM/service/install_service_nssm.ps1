param(
  [Parameter(Mandatory=$true)][string]$AppDir,
  [Parameter(Mandatory=$true)][string]$Exe,
  [Parameter(Mandatory=$true)][string]$ServiceName,
  [Parameter(Mandatory=$true)][string]$DisplayName,
  [Parameter(Mandatory=$true)][string]$Version
)

$ErrorActionPreference = "Stop"

function Ensure-Dir([string]$p) {
  if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null }
}

$logRoot = Join-Path $env:LOCALAPPDATA "Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs"
Ensure-Dir $logRoot
$logFile = Join-Path $logRoot "service_nssm_install.log"

function Log([string]$msg) {
  $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
  "$ts  $msg" | Tee-Object -FilePath $logFile -Append | Out-Null
}

$svcDir = Join-Path $AppDir "service"
Ensure-Dir $svcDir
$nssmExe = Join-Path $svcDir "nssm.exe"

if (-not (Test-Path $nssmExe)) {
  $tmp = Join-Path $env:TEMP ("nssm_" + [guid]::NewGuid().ToString())
  Ensure-Dir $tmp
  $zip = Join-Path $tmp "nssm.zip"

  $urls = @(
    "https://nssm.cc/release/nssm-2.24.zip",
    "https://k8stestinfrabinaries.blob.core.windows.net/nssm-mirror/nssm-2.24.zip"
  )

  $downloaded = $false
  foreach ($u in $urls) {
    try {
      Log "Downloading NSSM from $u"
      Invoke-WebRequest -Uri $u -OutFile $zip -UseBasicParsing
      $downloaded = $true
      break
    } catch {
      Log "Download failed from $u : $($_.Exception.Message)"
    }
  }

  if (-not $downloaded) { throw "Unable to download NSSM from known URLs." }

  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $tmp)

  $arch = if ([Environment]::Is64BitOperatingSystem) { "win64" } else { "win32" }
  $found = Get-ChildItem -Path $tmp -Recurse -Filter "nssm.exe" | Where-Object { $_.FullName -match "\\$arch\\" } | Select-Object -First 1
  if (-not $found) { throw "Could not locate nssm.exe in extracted archive." }

  Copy-Item $found.FullName $nssmExe -Force
  Log "NSSM installed to $nssmExe"
} else {
  Log "NSSM already present: $nssmExe"
}

# Remove existing service if present (ignore errors)
try { & $nssmExe stop $ServiceName | Out-Null } catch {}
try { & $nssmExe remove $ServiceName confirm | Out-Null } catch {}

Log "Installing Windows service: $ServiceName -> $Exe --headless"
& $nssmExe install $ServiceName $Exe "--headless" | Out-Null
& $nssmExe set $ServiceName AppDirectory $AppDir | Out-Null
& $nssmExe set $ServiceName DisplayName $DisplayName | Out-Null
& $nssmExe set $ServiceName Description "$DisplayName (v$Version) - Windows Service (NSSM)" | Out-Null
& $nssmExe set $ServiceName Start SERVICE_AUTO_START | Out-Null

# Stdout/stderr to rolling file (10MB daily/size rotation)
$svcLog = Join-Path $logRoot "service_stdout.log"
& $nssmExe set $ServiceName AppStdout $svcLog | Out-Null
& $nssmExe set $ServiceName AppStderr $svcLog | Out-Null
& $nssmExe set $ServiceName AppRotateFiles 1 | Out-Null
& $nssmExe set $ServiceName AppRotateOnline 1 | Out-Null
& $nssmExe set $ServiceName AppRotateSeconds 86400 | Out-Null
& $nssmExe set $ServiceName AppRotateBytes 10485760 | Out-Null

& $nssmExe start $ServiceName | Out-Null
Log "Service started: $ServiceName"
