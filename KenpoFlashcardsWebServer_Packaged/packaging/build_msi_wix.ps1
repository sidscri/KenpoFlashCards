# Build MSI using WiX Toolset (v3)
# Prereqs: WiX installed and on PATH (candle.exe, light.exe, heat.exe)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist\KenpoFlashcardsTray"
if (!(Test-Path $dist)) {
  Write-Host "[ERROR] EXE not built. Run packaging\build_exe.bat first." -ForegroundColor Red
  exit 1
}

$outDir = Join-Path $PSScriptRoot "output"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$tmp = Join-Path $PSScriptRoot "wix_tmp"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $tmp
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# Harvest files
$wxs = Join-Path $tmp "files.wxs"
heat dir "$dist" -nologo -gg -srd -sreg -dr INSTALLFOLDER -cg KenpoFiles -out "$wxs"

# Main product template
$product = Join-Path $tmp "product.wxs"
@"
<?xml version='1.0' encoding='UTF-8'?>
<Wix xmlns='http://schemas.microsoft.com/wix/2006/wi'>
  <Product Id='*' Name='Kenpo Flashcards' Language='1033' Version='1.0.0' Manufacturer='Sidscri' UpgradeCode='{D1BCEB2C-6DBA-4E0A-8F70-4C7E2A0A7C01}'>
    <Package InstallerVersion='200' Compressed='yes' InstallScope='perMachine' />
    <MajorUpgrade DowngradeErrorMessage='A newer version is already installed.' />
    <MediaTemplate />

    <Feature Id='MainFeature' Title='Kenpo Flashcards' Level='1'>
      <ComponentGroupRef Id='KenpoFiles' />
    </Feature>

    <Directory Id='TARGETDIR' Name='SourceDir'>
      <Directory Id='ProgramFilesFolder'>
        <Directory Id='INSTALLFOLDER' Name='Kenpo Flashcards' />
      </Directory>
    </Directory>

    <Property Id='WIXUI_INSTALLDIR' Value='INSTALLFOLDER' />
    <UIRef Id='WixUI_InstallDir' />
    <UIRef Id='WixUI_ErrorProgressText' />
  </Product>
</Wix>
"@ | Set-Content -Encoding UTF8 $product

# Compile + link
$candleOut = Join-Path $tmp "obj"
New-Item -ItemType Directory -Force -Path $candleOut | Out-Null

candle -nologo -out "$candleOut\" "$product" "$wxs"
$msi = Join-Path $outDir "KenpoFlashcardsWeb.msi"
light -nologo -out "$msi" (Join-Path $candleOut "product.wixobj") (Join-Path $candleOut "files.wixobj") -ext WixUIExtension

Write-Host "[DONE] MSI created: $msi" -ForegroundColor Green
