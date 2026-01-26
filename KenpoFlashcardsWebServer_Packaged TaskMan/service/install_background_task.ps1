param(
  [Parameter(Mandatory=$true)][string]$AppDir,
  [Parameter(Mandatory=$true)][string]$Exe,
  [string]$TaskName = "AdvancedFlashcardsWebAppServer-Background"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Exe)) {
  throw "EXE not found: $Exe"
}

# schtasks /TR should contain a quoted exe path if it contains spaces
$taskRun = "`"$Exe`" --headless"

Write-Host "[INFO] Creating/Updating scheduled task: $TaskName"
Write-Host "[INFO] Task run: $taskRun"

schtasks.exe /Create /F /SC ONLOGON /TN $TaskName /TR $taskRun /RL LIMITED | Out-Host

Write-Host "[DONE] Scheduled task created/updated."
