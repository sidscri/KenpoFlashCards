# -*- mode: python ; coding: utf-8 -*-

import os

block_cipher = None

project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
assets_dir = os.path.join(project_root, 'assets')
static_dir = os.path.join(project_root, 'static')

# Writable data lives in ProgramData for packaged builds (see app.py),
# so we only bundle assets/static.

datas = []
if os.path.isdir(assets_dir):
    datas.append((assets_dir, 'assets'))
if os.path.isdir(static_dir):
    datas.append((static_dir, 'static'))

a = Analysis(
    [os.path.join(project_root, 'KenpoFlashcardsServerRunner.py')],
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
    name='KenpoFlashcardsWebServer',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=os.path.join(assets_dir, 'ic_launcher.ico') if os.path.exists(os.path.join(assets_dir, 'ic_launcher.ico')) else None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='KenpoFlashcardsWebServer',
)
