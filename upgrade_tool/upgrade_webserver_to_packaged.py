#!/usr/bin/env python3
"""
KenpoFlashcards Web Server Upgrade Tool
=======================================

Safely applies updates from the main KenpoFlashcardsWebServer project 
to the KenpoFlashcardsWebServer_Packaged project.

PROTECTED FILES (never overwritten):
- packaging/ folder (PyInstaller specs, Inno Setup, batch files)
- windows_service/ folder  
- windows_tray/ folder
- KenpoFlashcardsTrayLauncher.py
- Kenpo_Vocabulary_Study_Flashcards.ico
- server_config.json
- INSTALL_WINDOWS.md
- RUN_AS_WINDOWS_SERVICE.md
- PATCH_README.txt
- build_data/ folder (kenpo_words.json, etc.)
- *.lnk shortcut files
- Version-WebServerPackaged-*.txt (has its own versioning)

SYNCED FILES (copied from web server):
- app.py (core application)
- static/ folder (UI files)
- data/ folder (JSON data files - merged, not replaced)
- requirements.txt
- CHANGELOG.md
- README.md (web server changes noted)
- LICENSE
- BRANDING_NOTE.md
- ic_launcher.png

Usage:
    python upgrade_webserver_to_packaged.py <webserver_zip> <packaged_folder>
    
Example:
    python upgrade_webserver_to_packaged.py KenpoFlashcardsWebServer-v6_1_0_v32.zip ./KenpoFlashcardsWebServer_Packaged/
"""

import os
import sys
import json
import shutil
import zipfile
import argparse
from datetime import datetime
from pathlib import Path


class WebServerUpgrader:
    """Handles upgrading the packaged project from web server updates."""
    
    # Files/folders that should NEVER be touched
    PROTECTED_ITEMS = [
        'packaging',
        'windows_service', 
        'windows_tray',
        'build_data',
        'KenpoFlashcardsTrayLauncher.py',
        'Kenpo_Vocabulary_Study_Flashcards.ico',
        'server_config.json',
        'INSTALL_WINDOWS.md',
        'RUN_AS_WINDOWS_SERVICE.md',
        'PATCH_README.txt',
        '.sync_backups',
    ]
    
    # Patterns for protected files
    PROTECTED_PATTERNS = [
        '*.lnk',
        'Version-WebServerPackaged-*.txt',
    ]
    
    # Files that should be synced from web server
    SYNC_FILES = [
        'app.py',
        'requirements.txt',
        'CHANGELOG.md',
        'LICENSE',
        'BRANDING_NOTE.md',
        'ic_launcher.png',
        '.gitattributes',
    ]
    
    # Folders that should be synced (contents replaced)
    SYNC_FOLDERS = [
        'static',
    ]
    
    # Data folder requires special handling (merge, not replace)
    DATA_FOLDER = 'data'
    
    def __init__(self, webserver_source: Path, packaged_dest: Path, dry_run: bool = False):
        self.webserver_source = Path(webserver_source)
        self.packaged_dest = Path(packaged_dest)
        self.dry_run = dry_run
        self.backup_dir = None
        self.changes = []
        
    def log(self, msg: str, level: str = "INFO"):
        """Log a message with timestamp."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        prefix = {"INFO": "ℹ️", "WARN": "⚠️", "ERROR": "❌", "SUCCESS": "✅", "SKIP": "⏭️"}
        print(f"[{timestamp}] {prefix.get(level, '')} {msg}")
        
    def create_backup(self):
        """Create a backup of files that will be modified."""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.backup_dir = self.packaged_dest / ".sync_backups" / timestamp
        
        if self.dry_run:
            self.log(f"Would create backup at: {self.backup_dir}", "INFO")
            return
            
        self.backup_dir.mkdir(parents=True, exist_ok=True)
        self.log(f"Created backup directory: {self.backup_dir}", "INFO")
        
        # Backup files that will be modified
        for filename in self.SYNC_FILES:
            src = self.packaged_dest / filename
            if src.exists():
                dst = self.backup_dir / filename
                shutil.copy2(src, dst)
                self.log(f"Backed up: {filename}", "INFO")
                
    def is_protected(self, path: str) -> bool:
        """Check if a path is protected from modification."""
        path_parts = Path(path).parts
        
        # Check exact matches
        if path_parts[0] in self.PROTECTED_ITEMS:
            return True
            
        # Check patterns
        filename = Path(path).name
        for pattern in self.PROTECTED_PATTERNS:
            if pattern.startswith('*'):
                if filename.endswith(pattern[1:]):
                    return True
            elif '*' in pattern:
                import fnmatch
                if fnmatch.fnmatch(filename, pattern):
                    return True
                    
        return False
        
    def sync_file(self, filename: str):
        """Sync a single file from web server to packaged project."""
        src = self.webserver_source / filename
        dst = self.packaged_dest / filename
        
        if not src.exists():
            self.log(f"Source file not found: {filename}", "WARN")
            return False
            
        if self.dry_run:
            if dst.exists():
                self.log(f"Would update: {filename}", "INFO")
            else:
                self.log(f"Would create: {filename}", "INFO")
            return True
            
        # Create parent directories if needed
        dst.parent.mkdir(parents=True, exist_ok=True)
        
        # Copy file
        shutil.copy2(src, dst)
        self.changes.append(f"Updated: {filename}")
        self.log(f"Synced: {filename}", "SUCCESS")
        return True
        
    def sync_folder(self, foldername: str):
        """Sync an entire folder from web server to packaged project."""
        src = self.webserver_source / foldername
        dst = self.packaged_dest / foldername
        
        if not src.exists():
            self.log(f"Source folder not found: {foldername}", "WARN")
            return False
            
        if self.dry_run:
            self.log(f"Would sync folder: {foldername}/", "INFO")
            return True
            
        # Remove existing and copy fresh
        if dst.exists():
            shutil.rmtree(dst)
            
        shutil.copytree(src, dst)
        self.changes.append(f"Synced folder: {foldername}/")
        self.log(f"Synced folder: {foldername}/", "SUCCESS")
        return True
        
    def sync_data_folder(self):
        """
        Sync the data folder with special handling.
        - JSON files are merged (web server version takes priority for structure)
        - User data in data/users/ is preserved
        - breakdowns.json is merged
        """
        src_data = self.webserver_source / self.DATA_FOLDER
        dst_data = self.packaged_dest / self.DATA_FOLDER
        
        if not src_data.exists():
            self.log(f"Source data folder not found", "WARN")
            return False
            
        if self.dry_run:
            self.log(f"Would merge data folder", "INFO")
            return True
            
        dst_data.mkdir(parents=True, exist_ok=True)
        
        # Files to copy directly (not user-specific)
        direct_copy = ['helper.json', 'admin_users.json', 'api_keys.enc', 'secret_key.txt']
        
        for filename in direct_copy:
            src_file = src_data / filename
            dst_file = dst_data / filename
            if src_file.exists():
                shutil.copy2(src_file, dst_file)
                self.log(f"Synced data/{filename}", "SUCCESS")
                
        # Merge profiles.json (preserve existing users, add new structure)
        self._merge_json_file(
            src_data / 'profiles.json',
            dst_data / 'profiles.json',
            merge_strategy='preserve_dest_users'
        )
        
        # Merge breakdowns.json (preserve existing, add new)
        self._merge_json_file(
            src_data / 'breakdowns.json',
            dst_data / 'breakdowns.json',
            merge_strategy='merge_dicts'
        )
        
        self.changes.append("Merged data/ folder")
        return True
        
    def _merge_json_file(self, src: Path, dst: Path, merge_strategy: str):
        """Merge two JSON files based on strategy."""
        if not src.exists():
            return
            
        try:
            with open(src, 'r', encoding='utf-8') as f:
                src_data = json.load(f)
        except Exception as e:
            self.log(f"Error reading {src}: {e}", "ERROR")
            return
            
        if dst.exists():
            try:
                with open(dst, 'r', encoding='utf-8') as f:
                    dst_data = json.load(f)
            except:
                dst_data = {}
        else:
            dst_data = {}
            
        # Apply merge strategy
        if merge_strategy == 'preserve_dest_users':
            # Keep destination data, only update structure if needed
            merged = dst_data
        elif merge_strategy == 'merge_dicts':
            # Merge dictionaries, destination values preserved for existing keys
            merged = {**src_data, **dst_data}
        else:
            merged = src_data
            
        with open(dst, 'w', encoding='utf-8') as f:
            json.dump(merged, f, indent=2, ensure_ascii=False)
            
        self.log(f"Merged: {dst.name}", "SUCCESS")
        
    def update_packaged_version(self, web_version: dict):
        """Update the packaged project's version.json with web server version info."""
        version_file = self.packaged_dest / 'version.json'
        
        if self.dry_run:
            self.log(f"Would update version.json", "INFO")
            return
            
        # Read existing packaged version
        if version_file.exists():
            with open(version_file, 'r') as f:
                pkg_version = json.load(f)
        else:
            pkg_version = {}
            
        # Update with web server version, but keep packaged-specific fields
        pkg_version['webserver_version'] = web_version.get('version', '')
        pkg_version['webserver_build'] = web_version.get('build', 0)
        pkg_version['last_sync'] = datetime.now().isoformat()
        
        with open(version_file, 'w') as f:
            json.dump(pkg_version, f, indent=2)
            
        self.log(f"Updated version.json with web server v{web_version.get('version', '?')}", "SUCCESS")
        
    def run(self):
        """Execute the upgrade process."""
        print("\n" + "="*60)
        print("  KenpoFlashcards Web Server → Packaged Upgrade Tool")
        print("="*60 + "\n")
        
        if self.dry_run:
            self.log("DRY RUN MODE - No changes will be made", "WARN")
            print()
            
        # Validate paths
        if not self.webserver_source.exists():
            self.log(f"Web server source not found: {self.webserver_source}", "ERROR")
            return False
            
        if not self.packaged_dest.exists():
            self.log(f"Packaged destination not found: {self.packaged_dest}", "ERROR")
            return False
            
        # Read web server version
        ws_version_file = self.webserver_source / 'version.json'
        if ws_version_file.exists():
            with open(ws_version_file, 'r') as f:
                ws_version = json.load(f)
            self.log(f"Web Server Version: {ws_version.get('version', '?')} (build {ws_version.get('build', '?')})", "INFO")
        else:
            ws_version = {}
            self.log("Web server version.json not found", "WARN")
            
        # Read packaged version
        pkg_version_file = self.packaged_dest / 'version.json'
        if pkg_version_file.exists():
            with open(pkg_version_file, 'r') as f:
                pkg_version = json.load(f)
            ws_v = pkg_version.get('webserver_version', pkg_version.get('version', '?'))
            self.log(f"Packaged Current Version: {ws_v}", "INFO")
        else:
            self.log("Packaged version.json not found", "WARN")
            
        print()
        
        # Create backup
        self.log("Creating backup of current files...", "INFO")
        self.create_backup()
        print()
        
        # Sync individual files
        self.log("Syncing files...", "INFO")
        for filename in self.SYNC_FILES:
            self.sync_file(filename)
        print()
        
        # Sync folders
        self.log("Syncing folders...", "INFO")
        for foldername in self.SYNC_FOLDERS:
            self.sync_folder(foldername)
        print()
        
        # Sync data folder with merging
        self.log("Merging data folder...", "INFO")
        self.sync_data_folder()
        print()
        
        # Update version
        self.log("Updating version info...", "INFO")
        self.update_packaged_version(ws_version)
        print()
        
        # Summary
        print("="*60)
        print("  UPGRADE SUMMARY")
        print("="*60)
        
        if self.dry_run:
            print("\n  DRY RUN - No changes were made")
        else:
            print(f"\n  ✅ Upgrade complete!")
            print(f"  📁 Backup created at: {self.backup_dir}")
            print(f"  📝 Changes made: {len(self.changes)}")
            
        print("\n  Protected (unchanged):")
        for item in self.PROTECTED_ITEMS[:5]:
            print(f"    ⏭️  {item}/")
        print(f"    ... and {len(self.PROTECTED_ITEMS) - 5} more")
        
        print("\n  Next steps:")
        print("    1. Test the packaged project")
        print("    2. Update Version-WebServerPackaged-*.txt if needed")
        print("    3. Run packaging/2. build_exe.bat to rebuild")
        print("    4. Run packaging/3. build_installer_inno.bat for installer")
        
        print("\n" + "="*60 + "\n")
        
        return True


def extract_webserver_zip(zip_path: Path, extract_to: Path) -> Path:
    """Extract web server zip and return the inner folder path."""
    with zipfile.ZipFile(zip_path, 'r') as zf:
        zf.extractall(extract_to)
        
    # Find the extracted folder (should be KenpoFlashcardsWebServer)
    for item in extract_to.iterdir():
        if item.is_dir() and 'KenpoFlashcardsWebServer' in item.name:
            return item
            
    return extract_to


def main():
    parser = argparse.ArgumentParser(
        description='Upgrade KenpoFlashcardsWebServer_Packaged from web server updates',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python upgrade_webserver_to_packaged.py webserver.zip ./Packaged/
  python upgrade_webserver_to_packaged.py webserver.zip ./Packaged/ --dry-run
  python upgrade_webserver_to_packaged.py ./WebServer/ ./Packaged/
        """
    )
    
    parser.add_argument('source', help='Web server zip file or extracted folder')
    parser.add_argument('destination', help='Packaged project folder')
    parser.add_argument('--dry-run', '-n', action='store_true',
                       help='Show what would be done without making changes')
    
    args = parser.parse_args()
    
    source = Path(args.source)
    destination = Path(args.destination)
    
    # Handle zip file input
    if source.suffix.lower() == '.zip':
        print(f"Extracting {source.name}...")
        temp_dir = Path('/tmp/webserver_upgrade_temp')
        if temp_dir.exists():
            shutil.rmtree(temp_dir)
        temp_dir.mkdir(parents=True)
        source = extract_webserver_zip(source, temp_dir)
        print(f"Extracted to: {source}")
        
    # Run upgrader
    upgrader = WebServerUpgrader(source, destination, dry_run=args.dry_run)
    success = upgrader.run()
    
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
