[Setup]
AppId={{D0B0A5F1-5E0E-4E1A-9C30-1C7D9A8B4B12}
AppName={#MyAppName}
; --- REQUIRED (fixes your compile error) ---
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion} (build {#MyAppBuild})
; --- STRONGLY RECOMMENDED (makes Apps & Features show correct version reliably) ---
VersionInfoVersion={#MyAppVersion}
VersionInfoProductVersion={#MyAppVersion}
; Optional but typical:
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir=output
OutputBaseFilename=AdvancedFlashcardsWebAppServer-{#MyAppVersion}
Compression=lzma
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
DisableProgramGroupPage=yes
SetupIconFile=..\Kenpo_Vocabulary_Study_Flashcards.ico
UninstallDisplayIcon={app}\{#MyAppExeName}

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"
Name: "startup"; Description: "Start with Windows (current user)"

Name: "service_winsw"; Description: "Run server as a Windows Service (WinSW wrapper - advanced)"; Flags: checkedonce
[Files]
; Copy entire dist folder output from PyInstaller (one-folder build)
Source: "..\dist\AdvancedFlashcardsWebAppServer\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

Source: "..\service\install_service_winsw.ps1"; DestDir: "{app}\service"; Flags: ignoreversion
Source: "..\service\uninstall_service_winsw.ps1"; DestDir: "{app}\service"; Flags: ignoreversion
Source: "..\service\SERVICE_WINSW_README.txt"; DestDir: "{app}\service"; Flags: ignoreversion
[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "AdvancedFlashcardsWebAppServer"; ValueData: """{app}\{#MyAppExeName}"""; Tasks: startup; Flags: uninsdeletevalue

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\service\install_service_winsw.ps1"" -AppDir ""{app}"" -Exe ""{app}\{#MyAppExeName}"" -ServiceName ""AdvancedFlashcardsWebAppServer"" -DisplayName ""Advanced Flashcards WebApp Server"" -Version ""{#MyAppVersion}"""; Tasks: service_winsw; Flags: runhidden waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent


[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\service\uninstall_service_winsw.ps1"" -AppDir ""{app}"" -ServiceName ""AdvancedFlashcardsWebAppServer"""; Tasks: service_winsw; Flags: runhidden waituntilterminated

[Code]
