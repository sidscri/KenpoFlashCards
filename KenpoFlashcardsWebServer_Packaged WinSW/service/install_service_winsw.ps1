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
$logFile = Join-Path $logRoot "service_winsw_install.log"

function Log([string]$msg) {
  $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
  "$ts  $msg" | Tee-Object -FilePath $logFile -Append | Out-Null
}

$svcDir = Join-Path $AppDir "service"
Ensure-Dir $svcDir

$wrapper = Join-Path $svcDir "AdvancedFlashcardsWebAppServerService.exe"
$config  = Join-Path $svcDir "AdvancedFlashcardsWebAppServerService.xml"

if (-not (Test-Path $wrapper)) {
  $tmp = Join-Path $env:TEMP ("winsw_" + [guid]::NewGuid().ToString())
  Ensure-Dir $tmp
  $dl = Join-Path $tmp "WinSW-x64.exe"
  $url = "https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe"
  Log "Downloading WinSW from $url"
  Invoke-WebRequest -Uri $url -OutFile $dl -UseBasicParsing
  Copy-Item $dl $wrapper -Force
  Log "WinSW saved to $wrapper"
} else {
  Log "WinSW already present: $wrapper"
}

$xml = @"
<service>
  <id>$ServiceName</id>
  <name>$DisplayName</name>
  <description>$DisplayName (v$Version) - Windows Service (WinSW)</description>
  <executable>$Exe</executable>
  <arguments>--headless</arguments>
  <workingdirectory>$AppDir</workingdirectory>
  <startmode>Automatic</startmode>
  <logpath>$logRoot</logpath>
  <log mode="roll"></log>
  <onfailure action="restart" delay="10 sec" />
</service>
"@

Set-Content -Path $config -Value $xml -Encoding UTF8
Log "Wrote WinSW config: $config"

# Remove existing service if present (ignore errors)
try { & $wrapper stop | Out-Null } catch {}
try { & $wrapper uninstall | Out-Null } catch {}

Log "Installing service via WinSW..."
& $wrapper install | Out-Null
& $wrapper start | Out-Null
Log "Service started: $ServiceName"
