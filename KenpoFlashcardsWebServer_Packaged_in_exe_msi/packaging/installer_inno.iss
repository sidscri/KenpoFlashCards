; Inno Setup script for KenpoFlashcardsWebServer
; Sonarr-style install: binaries + writable data under ProgramData.

#define MyAppName "Kenpo Flashcards Web"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Sidscri"

#define TrayExeName "KenpoFlashcardsTray.exe"
#define ServerExeName "KenpoFlashcardsWebServer.exe"

[Setup]
AppId={{D0B0A5F1-5E0E-4E1A-9C30-1C7D9A8B4B12}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
; Install under ProgramData so services and background tasks can write files
DefaultDirName={commonappdata}\KenpoFlashcardsWebServer
DefaultGroupName={#MyAppName}
OutputDir=output
OutputBaseFilename=KenpoFlashcardsWebSetup
Compression=lzma
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
DisableProgramGroupPage=yes

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"; Flags: unchecked
Name: "autostart"; Description: "Start the Tray app with Windows"; GroupDescription: "Startup:"; Flags: unchecked

[Dirs]
Name: "{app}\data"; Permissions: users-modify
Name: "{app}\data\users"; Permissions: users-modify
Name: "{app}\logs"; Permissions: users-modify

[Files]
; Binaries (PyInstaller one-folder output)
Source: "..\dist\KenpoFlashcardsTray\*"; DestDir: "{app}\bin"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\dist\KenpoFlashcardsWebServer\*"; DestDir: "{app}\bin"; Flags: ignoreversion recursesubdirs createallsubdirs

; Seed defaults (only if they don't exist yet)
Source: "..\data\breakdowns.json"; DestDir: "{app}\data"; Flags: onlyifdoesntexist
Source: "..\data\profiles.json"; DestDir: "{app}\data"; Flags: onlyifdoesntexist
Source: "..\data\admin_users.json"; DestDir: "{app}\data"; Flags: onlyifdoesntexist

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\bin\{#TrayExeName}"
Name: "{group}\Open Web UI"; Filename: "{cmd}"; Parameters: "/c start http://127.0.0.1:8009"; WorkingDir: "{app}\bin"; IconFilename: "{app}\bin\{#TrayExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\bin\{#TrayExeName}"; Tasks: desktopicon

[Registry]
; Optional: start tray at login
Root: HKLM; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "KenpoFlashcardsTray"; ValueData: '"{app}\bin\{#TrayExeName}"'; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\bin\{#TrayExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Keep user data on uninstall (Sonarr-style). Only clean logs.
Type: filesandordirs; Name: "{app}\logs"
