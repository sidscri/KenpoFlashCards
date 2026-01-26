param(
  [string]$TaskName = "AdvancedFlashcardsWebAppServer-Background"
)

$ErrorActionPreference = "SilentlyContinue"

Write-Host "[INFO] Removing scheduled task: $TaskName"
schtasks.exe /Delete /F /TN $TaskName | Out-Host

Write-Host "[DONE] Scheduled task removed (or did not exist)."
