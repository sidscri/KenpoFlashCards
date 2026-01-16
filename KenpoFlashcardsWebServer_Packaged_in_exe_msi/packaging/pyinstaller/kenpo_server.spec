# -*- mode: python ; coding: utf-8 -*-
import os

block_cipher = None

# PyInstaller may exec() the spec without __file__ defined in some environments.
# "specpath" is provided by PyInstaller and points to the directory of this .spec.
_spec_dir = globals().get("specpath") or os.getcwd()

# project_root = ...\KenpoFlashcardsWebServer_Packaged_in_exe_msi
project_root = os.path.abspath(os.path.join(_spec_dir, "..", ".."))
assets_dir = os.path.join(project_root, "assets")
static_dir = os.path.join(project_root, "static")
templates_dir = os.path.join(project_root, "templates")

datas = []
if os.path.isdir(assets_dir):
    datas.append((assets_dir, "assets"))
if os.path.isdir(static_dir):
    datas.append((static_dir, "static"))
if os.path.isdir(templates_dir):
    datas.append((templates_dir, "templates"))

icon_path = os.path.join(assets_dir, "ic_launcher.ico")
if not os.path.exists(icon_path):
    icon_path = None

a = Analysis(
    [os.path.join(project_root, "KenpoFlashcardsServerRunner.py")],
    pathex=[project_root],
    binaries=[],
    datas=datas,
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="KenpoFlashcardsWebServer",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=icon_path,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="KenpoFlashcardsWebServer",
)
