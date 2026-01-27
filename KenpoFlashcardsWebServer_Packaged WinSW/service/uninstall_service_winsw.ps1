param(
  [Parameter(Mandatory=$true)][string]$AppDir,
  [Parameter(Mandatory=$true)][string]$ServiceName
)

$ErrorActionPreference = "SilentlyContinue"

$logRoot = Join-Path $env:LOCALAPPDATA "Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs"
if (-not (Test-Path $logRoot)) { New-Item -ItemType Directory -Path $logRoot -Force | Out-Null }
$logFile = Join-Path $logRoot "service_winsw_uninstall.log"

function Log([string]$msg) {
  "$((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))  $msg" | Out-File -FilePath $logFile -Append -Encoding utf8
}

$wrapper = Join-Path (Join-Path $AppDir "service") "AdvancedFlashcardsWebAppServerService.exe"
if (Test-Path $wrapper) {
  Log "Stopping/uninstalling service via WinSW."
  & $wrapper stop | Out-Null
  & $wrapper uninstall | Out-Null
} else {
  Log "Wrapper not found; attempting sc stop/delete."
  sc.exe stop $ServiceName | Out-Null
  sc.exe delete $ServiceName | Out-Null
}
