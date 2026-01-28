#!/usr/bin/env python3
"""
KenpoFlashcards Web Server → Packaged Sync Tool (with AI Documentation Update)
===============================================================================

Safely syncs updates from the main KenpoFlashcardsWebServer project folder
to the KenpoFlashcardsWebServer_Packaged project folder.

FEATURES:
- Works directly with project folders (no zip files)
- Automatically increments packaged version based on upgrade level
- AI-assisted README.md and CHANGELOG.md updates
- Creates backups in the tools/sync_backups/ folder
- Dry-run mode for previewing changes

UPGRADE LEVELS:
  1 (Low/Patch)   - Bug fixes, minor tweaks           → v1.3.0 → v1.3.1
  2 (Medium/Minor) - New features, improvements        → v1.3.0 → v1.4.0
  3 (High/Major)   - Breaking changes, major features  → v1.3.0 → v2.0.0

PROTECTED FILES (never overwritten):
- packaging/ folder
- windows_service/ folder  
- windows_tray/ folder
- tools/ folder
- KenpoFlashcardsTrayLauncher.py
- server_config.json
- INSTALL_WINDOWS.md, RUN_AS_WINDOWS_SERVICE.md, PATCH_README.txt
- *.lnk shortcut files
- Version-WebServerPackaged-*.txt

SYNCED FILES:
- app.py, static/ (mirrored with safe excludes), requirements.txt, LICENSE, BRANDING_NOTE.md
- data/ folder (merged, not replaced)

AI-UPDATED FILES:
- README.md (What's new section updated)
- CHANGELOG.md (New version entry added)
- version.json (Version numbers updated)

Usage:
    python sync_webserver_to_packaged.py <webserver_folder> <packaged_folder>
    
Example:
    python sync_webserver_to_packaged.py C:\\Projects\\KenpoFlashcardsWebServer C:\\Projects\\KenpoFlashcardsWebServer_Packaged
"""

import os
import sys
import json
import shutil
import argparse
import re
from datetime import datetime
from pathlib import Path
from typing import Optional, Tuple, List, Dict


class VersionBumper:
    """Handles semantic versioning bumps."""
    
    @staticmethod
    def parse_version(version_str: str) -> Tuple[int, int, int]:
        """Parse a version string like '1.3.0' into (major, minor, patch)."""
        match = re.match(r'v?(\d+)\.(\d+)\.(\d+)', version_str)
        if match:
            return int(match.group(1)), int(match.group(2)), int(match.group(3))
        return 0, 0, 0
    
    @staticmethod
    def bump_version(current: str, level: int) -> str:
        """
        Bump version based on level:
        1 = patch (x.y.Z)
        2 = minor (x.Y.0)
        3 = major (X.0.0)
        """
        major, minor, patch = VersionBumper.parse_version(current)
        
        if level == 1:  # Patch
            patch += 1
        elif level == 2:  # Minor
            minor += 1
            patch = 0
        elif level == 3:  # Major
            major += 1
            minor = 0
            patch = 0
            
        return f"{major}.{minor}.{patch}"


class ChangelogAnalyzer:
    """Analyzes web server changelog to extract changes since last sync."""
    
    @staticmethod
    def extract_changes_since_version(changelog_content: str, since_version: str) -> List[Dict]:
        """
        Extract all changelog entries newer than the specified version.
        Returns list of dicts with version info and changes.
        """
        changes = []
        
        # Parse version sections from changelog
        # Pattern matches: ## 7.0.1 (build 34) — 2026-01-23 or ## v7.0.1 (build 34)
        version_pattern = r'##\s+v?(\d+\.\d+\.\d+)\s+\(build\s+(\d+)\)[^\n]*'
        
        sections = re.split(version_pattern, changelog_content)
        
        # sections will be: [intro, version1, build1, content1, version2, build2, content2, ...]
        i = 1
        while i < len(sections) - 2:
            version = sections[i]
            build = sections[i + 1]
            content = sections[i + 2] if i + 2 < len(sections) else ""
            
            # Check if this version is newer than our last sync
            if ChangelogAnalyzer._is_newer_version(version, since_version):
                changes.append({
                    'version': version,
                    'build': int(build),
                    'content': content.strip()
                })
            
            i += 3
            
        return changes
    
    @staticmethod
    def _is_newer_version(v1: str, v2: str) -> bool:
        """Check if v1 is newer than v2."""
        def parse(v):
            match = re.match(r'v?(\d+)\.(\d+)\.(\d+)', v)
            if match:
                return tuple(int(x) for x in match.groups())
            return (0, 0, 0)
        return parse(v1) > parse(v2)
    
    @staticmethod
    def summarize_changes(changes: List[Dict]) -> str:
        """Create a human-readable summary of changes."""
        if not changes:
            return "No new changes detected."
        
        summary_lines = []
        for change in changes:
            summary_lines.append(f"### Web Server v{change['version']} (build {change['build']})")
            summary_lines.append(change['content'])
            summary_lines.append("")
        
        return "\n".join(summary_lines)


class DocumentationUpdater:
    """Handles AI-assisted documentation updates."""
    
    def __init__(self, packaged_dir: Path, dry_run: bool = False):
        self.packaged_dir = packaged_dir
        self.dry_run = dry_run
        
    def generate_changelog_entry(self, 
                                  new_version: str, 
                                  new_build: int,
                                  ws_version: str,
                                  ws_build: int,
                                  old_ws_version: str,
                                  old_ws_build: int,
                                  ws_changes: List[Dict],
                                  upgrade_level: int) -> str:
        """Generate a new CHANGELOG.md entry for the packaged version."""
        
        today = datetime.now().strftime("%Y-%m-%d")
        
        # Build the changelog entry
        entry_lines = [
            f"## v{new_version} (build {new_build}) — {today}",
            "",
            "### Changed",
            f"- **Updated bundled Web Server to v{ws_version} (build {ws_build})** (from v{old_ws_version} build {old_ws_build}), including:"
        ]
        
        # Extract key features from web server changes
        for change in ws_changes:
            content = change['content']
            
            # Extract Added items
            added_match = re.search(r'### Added\n(.*?)(?=###|\Z)', content, re.DOTALL)
            if added_match:
                items = re.findall(r'- \*\*([^*]+)\*\*', added_match.group(1))
                for item in items[:6]:  # Limit to 6 items per version
                    entry_lines.append(f"  - **{item.strip()}**")
            
            # Extract Changed items  
            changed_match = re.search(r'### Changed\n(.*?)(?=###|\Z)', content, re.DOTALL)
            if changed_match:
                items = re.findall(r'- ([^\n]+)', changed_match.group(1))
                for item in items[:3]:  # Limit to 3 items
                    if not item.startswith('**'):
                        entry_lines.append(f"  - {item.strip()}")
        
        entry_lines.append("")
        
        return "\n".join(entry_lines)
    
    def update_changelog(self, new_entry: str) -> bool:
        """Insert new entry at the top of CHANGELOG.md."""
        changelog_path = self.packaged_dir / "CHANGELOG.md"
        
        if not changelog_path.exists():
            print("  ⚠️  CHANGELOG.md not found")
            return False
            
        with open(changelog_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find the first version entry (## v...)
        match = re.search(r'^## v\d+\.\d+\.\d+', content, re.MULTILINE)
        if match:
            insert_pos = match.start()
            new_content = content[:insert_pos] + new_entry + "\n" + content[insert_pos:]
        else:
            # No existing versions, append after header
            new_content = content + "\n" + new_entry
        
        if self.dry_run:
            print(f"  📝 Would update CHANGELOG.md with new v{new_entry.split()[1]} entry")
            return True
            
        with open(changelog_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
            
        print("  ✅ Updated CHANGELOG.md")
        return True
    
    def generate_readme_whats_new(self,
                                   new_version: str,
                                   new_build: int,
                                   ws_version: str,
                                   ws_build: int,
                                   old_ws_version: str,
                                   old_ws_build: int,
                                   ws_changes: List[Dict]) -> str:
        """Generate the 'What's new' section for README.md."""
        
        lines = [
            f"## What's new in v{new_version} (build {new_build})",
            "",
            f"- **Bundled Web Server updated to v{ws_version} (build {ws_build})** (from v{old_ws_version} build {old_ws_build}), bringing:"
        ]
        
        # Extract key features from ALL web server changes
        for change in ws_changes:
            content = change['content']
            
            # Extract Added items (main features)
            added_match = re.search(r'### Added\n(.*?)(?=###|\Z)', content, re.DOTALL)
            if added_match:
                items = re.findall(r'- \*\*([^*]+)\*\*[^-\n]*([^\n]*)?', added_match.group(1))
                for item in items[:5]:
                    feature_name = item[0].strip()
                    # Get the rest of the line after the bold part if present
                    desc = item[1].strip() if len(item) > 1 else ""
                    if desc.startswith(':') or desc.startswith('—') or desc.startswith('-'):
                        desc = desc[1:].strip()
                    if desc:
                        lines.append(f"  - **{feature_name}** — {desc[:80]}")
                    else:
                        lines.append(f"  - **{feature_name}**")
        
        lines.append("")
        
        return "\n".join(lines)
    
    def update_readme(self, new_whats_new: str, new_version: str, new_build: int, 
                      ws_version: str, ws_build: int) -> bool:
        """Update README.md with new version info and What's new section."""
        readme_path = self.packaged_dir / "README.md"
        
        if not readme_path.exists():
            print("  ⚠️  README.md not found")
            return False
            
        with open(readme_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Update version numbers at top
        # Pattern: - **Packaged Version:** **v1.3.0 (build 8)**
        content = re.sub(
            r'(\*\*Packaged Version:\*\*\s*\*\*)v?\d+\.\d+\.\d+\s*\(build\s*\d+\)(\*\*)',
            f'\\1v{new_version} (build {new_build})\\2',
            content
        )
        
        # Pattern: - **Bundled Web Server:** **v6.1.0 (build 32)**
        content = re.sub(
            r'(\*\*Bundled Web Server:\*\*\s*\*\*)v?\d+\.\d+\.\d+\s*\(build\s*\d+\)(\*\*)',
            f'\\1v{ws_version} (build {ws_build})\\2',
            content
        )
        
        # Find and replace the first "What's new" section
        # Keep existing What's new sections but insert new one at top
        whats_new_pattern = r'(## What\'s new in v\d+\.\d+\.\d+ \(build \d+\))'
        match = re.search(whats_new_pattern, content)
        
        if match:
            # Insert new What's new before the first existing one
            insert_pos = match.start()
            content = content[:insert_pos] + new_whats_new + "\n" + content[insert_pos:]
        else:
            # No existing What's new, add after ## What you get section
            get_match = re.search(r'(## What you get.*?)(?=##)', content, re.DOTALL)
            if get_match:
                insert_pos = get_match.end()
                content = content[:insert_pos] + "\n" + new_whats_new + "\n" + content[insert_pos:]
        
        if self.dry_run:
            print(f"  📝 Would update README.md with new v{new_version} info")
            return True
            
        with open(readme_path, 'w', encoding='utf-8') as f:
            f.write(content)
            
        print("  ✅ Updated README.md")
        return True
    
    def update_version_json(self, new_version: str, new_build: int,
                            ws_version: str, ws_build: int) -> bool:
        """Update version.json with new version info."""
        version_path = self.packaged_dir / "version.json"
        
        if version_path.exists():
            with open(version_path, 'r', encoding='utf-8') as f:
                version_data = json.load(f)
        else:
            version_data = {"app_name": "KenpoFlashcardsWebServer_Packaged"}
        
        version_data['version'] = new_version
        version_data['build'] = new_build
        version_data['release_date'] = datetime.now().strftime("%Y-%m-%d")
        version_data['notes'] = f"Synced to web server v{ws_version}."
        version_data['webserver_version'] = ws_version
        version_data['webserver_build'] = ws_build
        version_data['last_sync'] = datetime.now().isoformat()
        
        if self.dry_run:
            print(f"  📝 Would update version.json to v{new_version} (build {new_build})")
            return True
            
        with open(version_path, 'w', encoding='utf-8') as f:
            json.dump(version_data, f, indent=2)
            
        print("  ✅ Updated version.json")
        return True
    
    def update_version_txt(self, new_version: str, new_build: int, 
                           old_version: str, old_build: int) -> bool:
        """Rename the Version-WebServerPackaged-*.txt file WITHOUT changing its contents."""

        # Find existing version file (keep contents)
        old_file = None
        for f in self.packaged_dir.glob("Version-WebServerPackaged-*.txt"):
            old_file = f
            break

        if old_file is None:
            print("  ⏭️  No Version-WebServerPackaged-*.txt found (skipping)")
            return True

        new_filename = f"Version-WebServerPackaged-v{new_version} v{new_build}.txt"
        new_path = self.packaged_dir / new_filename

        if self.dry_run:
            print(f"  📝 Would rename version file: {old_file.name} → {new_filename}")
            return True

        # If target exists, remove it so rename succeeds on Windows
        if new_path.exists():
            new_path.unlink()

        old_file.rename(new_path)

        print(f"  ✅ Renamed version file to {new_filename} (contents preserved)")
        return True


class WebServerSyncer:
    """Main sync tool that coordinates all operations."""
    
    PROTECTED_ITEMS = [
        'packaging', 'windows_service', 'windows_tray', 'build_data', 'tools',
        'KenpoFlashcardsTrayLauncher.py', 'Kenpo_Vocabulary_Study_Flashcards.ico',
        'server_config.json', 'INSTALL_WINDOWS.md', 'RUN_AS_WINDOWS_SERVICE.md',
        'PATCH_README.txt', '.sync_backups', 'README.md', 'CHANGELOG.md', 'version.json'
    ]
    
    PROTECTED_PATTERNS = ['*.lnk', 'Version-WebServerPackaged-*.txt']
    
    SYNC_FILES = ['app.py', 'requirements.txt', 'LICENSE', 'BRANDING_NOTE.md', '.gitattributes']
    
    SYNC_FOLDERS = ['static']

    # Static sync rules:
    # - Mirror WebServer/static into Packaged/static (overwrite from source)
    # - Preserve Packaged-only Windows assets and user-uploaded content:
    #   * static/res/webappservericons/**   (Windows EXE/tray/icons)
    #   * static/res/decklogos/user/**      (user-uploaded deck logos)
    STATIC_EXCLUDE_SUBDIRS = [
        Path('res') / 'webappservericons',
        Path('res') / 'decklogos' / 'user',
    ]
    
    def __init__(self, ws_source: Path, pkg_dest: Path, tools_dir: Path, dry_run: bool = False, upgrade_level: Optional[int] = None):
        self.ws_source = Path(ws_source)
        self.pkg_dest = Path(pkg_dest)
        self.tools_dir = Path(tools_dir)
        self.dry_run = dry_run
        self.upgrade_level = upgrade_level
        self.backup_dir = None
        self.changes = []
        
    def log(self, msg: str, level: str = "INFO"):
        """Log with timestamp and emoji prefix."""
        ts = datetime.now().strftime("%H:%M:%S")
        prefix = {"INFO": "ℹ️", "WARN": "⚠️", "ERROR": "❌", "SUCCESS": "✅", "SKIP": "⏭️"}
        print(f"[{ts}] {prefix.get(level, '')} {msg}")
    
    def prompt_upgrade_level(self) -> int:
        """Prompt user for upgrade level."""
        print("\n" + "="*60)
        print("  SELECT UPGRADE LEVEL")
        print("="*60)
        print("""
  1 = Low (Patch)    - Bug fixes, minor tweaks       → x.y.Z+1
  2 = Medium (Minor) - New features, improvements    → x.Y+1.0
  3 = High (Major)   - Breaking changes, major features → X+1.0.0
""")
        
        while True:
            try:
                choice = input("  Enter upgrade level (1/2/3): ").strip()
                level = int(choice)
                if level in [1, 2, 3]:
                    level_names = {1: "Patch", 2: "Minor", 3: "Major"}
                    print(f"\n  Selected: {level_names[level]} upgrade\n")
                    return level
            except ValueError:
                pass
            print("  ⚠️  Please enter 1, 2, or 3")
    
    def create_backup(self, new_version: str, new_build: int):
        """Backup files that will be modified to the tools/sync_backups folder."""
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.backup_dir = self.tools_dir / "sync_backups" / f"v{new_version}_b{new_build}_{ts}"
        
        if self.dry_run:
            self.log(f"Would create backup at: {self.backup_dir}")
            return
            
        self.backup_dir.mkdir(parents=True, exist_ok=True)
        
        # Backup sync files and doc files
        files_to_backup = self.SYNC_FILES + ['README.md', 'CHANGELOG.md', 'version.json']
        for filename in files_to_backup:
            src = self.pkg_dest / filename
            if src.exists():
                shutil.copy2(src, self.backup_dir / filename)
        
        # Backup static folder
        static_src = self.pkg_dest / 'static'
        if static_src.exists():
            shutil.copytree(static_src, self.backup_dir / 'static')
        
        # Backup version txt file
        for f in self.pkg_dest.glob("Version-WebServerPackaged-*.txt"):
            shutil.copy2(f, self.backup_dir / f.name)
                
        self.log(f"Created backup at: {self.backup_dir}")
    
    def sync_file(self, filename: str) -> bool:
        """Copy a file from web server to packaged."""
        src = self.ws_source / filename
        dst = self.pkg_dest / filename
        
        if not src.exists():
            self.log(f"Source not found: {filename}", "WARN")
            return False
            
        if self.dry_run:
            self.log(f"Would sync: {filename}")
            return True
            
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        self.changes.append(f"Synced: {filename}")
        self.log(f"Synced: {filename}", "SUCCESS")
        return True
    
    def sync_folder(self, foldername: str) -> bool:
        """Sync an entire folder from web server."""
        if foldername == 'static':
            return self.sync_static_folder()

        src = self.ws_source / foldername
        dst = self.pkg_dest / foldername

        if not src.exists():
            self.log(f"Source folder not found: {foldername}", "WARN")
            return False

        if self.dry_run:
            self.log(f"Would sync folder: {foldername}/")
            return True

        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        self.changes.append(f"Synced folder: {foldername}/")
        self.log(f"Synced folder: {foldername}/", "SUCCESS")
        return True

    def sync_static_folder(self) -> bool:
        """Mirror static/ from web server into packaged, preserving certain Packaged-only subfolders."""
        src_static = self.ws_source / 'static'
        dst_static = self.pkg_dest / 'static'

        if not src_static.exists():
            self.log('Source folder not found: static', 'WARN')
            return False

        if self.dry_run:
            self.log('Would mirror folder: static/ (with safe excludes)')
            self.log('Static excludes (preserved in destination):', 'INFO')
            for ex in self.STATIC_EXCLUDE_SUBDIRS:
                self.log(f"  - static/{ex.as_posix()}/**", 'INFO')
            return True

        dst_static.mkdir(parents=True, exist_ok=True)

        # 1) Copy/update everything from source → destination
        for src_file in src_static.rglob('*'):
            if src_file.is_dir():
                continue
            rel = src_file.relative_to(src_static)
            if self._is_static_excluded(rel):
                continue
            dst_file = dst_static / rel
            dst_file.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src_file, dst_file)

        # 2) Remove destination files/dirs not present in source (mirror), BUT preserve excluded subtrees
        src_set = {p.relative_to(src_static) for p in src_static.rglob('*')}

        # Remove files first
        for dst_file in sorted([p for p in dst_static.rglob('*') if p.is_file()], reverse=True):
            rel = dst_file.relative_to(dst_static)
            if self._is_static_excluded(rel):
                continue
            if rel not in src_set:
                try:
                    dst_file.unlink()
                except Exception as e:
                    self.log(f"Failed to remove extra file static/{rel}: {e}", 'WARN')

        # Then remove empty dirs (excluding preserved dirs)
        for dst_dir in sorted([p for p in dst_static.rglob('*') if p.is_dir()], reverse=True):
            rel = dst_dir.relative_to(dst_static)
            if self._is_static_excluded(rel):
                continue
            try:
                if not any(dst_dir.iterdir()):
                    dst_dir.rmdir()
            except Exception:
                pass

        self.changes.append('Mirrored folder: static/ (safe excludes preserved)')
        self.log('Mirrored folder: static/ (safe excludes preserved)', 'SUCCESS')
        return True

    def _is_static_excluded(self, rel_path: Path) -> bool:
        """Return True if rel_path (relative to static/) should be preserved in destination.

        IMPORTANT: must be separator- and case-insensitive on Windows.
        """
        # Normalize both the candidate path and exclude prefixes to forward-slash, lowercase strings
        rp = str(rel_path).replace('\\', '/').lstrip('/').lower()
        for ex in self.STATIC_EXCLUDE_SUBDIRS:
            exs = str(ex).replace('\\', '/').lstrip('/').lower()
            if rp == exs or rp.startswith(exs + '/'):
                return True
        return False

    
    def sync_data_folder(self) -> bool:
        """Merge data folder (preserve user data)."""
        src_data = self.ws_source / 'data'
        dst_data = self.pkg_dest / 'data'
        
        if not src_data.exists():
            self.log("Source data folder not found", "WARN")
            return False
            
        if self.dry_run:
            self.log("Would merge data folder")
            return True
            
        dst_data.mkdir(parents=True, exist_ok=True)
        
        # Direct copy files
        for filename in ['helper.json', 'admin_users.json', 'api_keys.enc', 'secret_key.txt']:
            src = src_data / filename
            if src.exists():
                shutil.copy2(src, dst_data / filename)
                
        self.changes.append("Merged data/ folder")
        self.log("Merged data/ folder", "SUCCESS")
        return True
    
    def run(self):
        """Execute the full sync process."""
        print("\n" + "="*60)
        print("  KenpoFlashcards Web Server → Packaged Sync Tool")
        print("  (with AI Documentation Update)")
        print("="*60 + "\n")
        
        if self.dry_run:
            self.log("DRY RUN MODE - No changes will be made", "WARN")
            print()
        
        # Validate paths
        if not self.ws_source.exists():
            self.log(f"Web server source not found: {self.ws_source}", "ERROR")
            return False
        if not self.pkg_dest.exists():
            self.log(f"Packaged destination not found: {self.pkg_dest}", "ERROR")
            return False
        
        # Read versions
        ws_version_file = self.ws_source / 'version.json'
        pkg_version_file = self.pkg_dest / 'version.json'
        
        if ws_version_file.exists():
            with open(ws_version_file, 'r') as f:
                ws_ver = json.load(f)
            self.log(f"Web Server: v{ws_ver.get('version', '?')} (build {ws_ver.get('build', '?')})")
        else:
            self.log("Web server version.json not found", "ERROR")
            return False
        
        if pkg_version_file.exists():
            with open(pkg_version_file, 'r') as f:
                pkg_ver = json.load(f)
            old_pkg_version = pkg_ver.get('version', '0.0.0')
            old_pkg_build = pkg_ver.get('build', 0)
            old_ws_version = pkg_ver.get('webserver_version', '0.0.0')
            old_ws_build = pkg_ver.get('webserver_build', 0)
            self.log(f"Packaged: v{old_pkg_version} (build {old_pkg_build}) [bundled WS v{old_ws_version}]")
        else:
            old_pkg_version = "1.0.0"
            old_pkg_build = 0
            old_ws_version = "0.0.0"
            old_ws_build = 0
        
        print()
        
        # Prompt for upgrade level
        upgrade_level = self.upgrade_level if self.upgrade_level in (1,2,3) else self.prompt_upgrade_level()
        
        # Calculate new version
        new_version = VersionBumper.bump_version(old_pkg_version, upgrade_level)
        new_build = old_pkg_build + 1
        ws_version = ws_ver.get('version', '0.0.0')
        ws_build = ws_ver.get('build', 0)
        
        self.log(f"New packaged version: v{new_version} (build {new_build})")
        print()
        
        # Read web server changelog to extract changes
        ws_changelog_path = self.ws_source / 'CHANGELOG.md'
        if ws_changelog_path.exists():
            with open(ws_changelog_path, 'r', encoding='utf-8') as f:
                ws_changelog = f.read()
            ws_changes = ChangelogAnalyzer.extract_changes_since_version(ws_changelog, old_ws_version)
            self.log(f"Found {len(ws_changes)} web server version(s) to include")
        else:
            ws_changes = []
            self.log("Web server CHANGELOG.md not found", "WARN")
        
        print()
        
        # Create backup (in tools/sync_backups/)
        self.log("Creating backup...")
        self.create_backup(new_version, new_build)
        print()
        
        # Sync files
        self.log("Syncing files...")
        for filename in self.SYNC_FILES:
            self.sync_file(filename)
        print()
        
        # Sync folders
        self.log("Syncing folders...")
        for foldername in self.SYNC_FOLDERS:
            self.sync_folder(foldername)
        print()
        
        # Sync data
        self.log("Merging data folder...")
        self.sync_data_folder()
        print()
        
        # Update documentation
        self.log("Updating documentation (AI-assisted)...")
        doc_updater = DocumentationUpdater(self.pkg_dest, self.dry_run)
        
        # Generate and apply changelog entry
        changelog_entry = doc_updater.generate_changelog_entry(
            new_version, new_build,
            ws_version, ws_build,
            old_ws_version, old_ws_build,
            ws_changes, upgrade_level
        )
        doc_updater.update_changelog(changelog_entry)
        
        # Generate and apply README update
        readme_whats_new = doc_updater.generate_readme_whats_new(
            new_version, new_build,
            ws_version, ws_build,
            old_ws_version, old_ws_build,
            ws_changes
        )
        doc_updater.update_readme(readme_whats_new, new_version, new_build, ws_version, ws_build)
        
        # Update version.json
        doc_updater.update_version_json(new_version, new_build, ws_version, ws_build)
        
        # Update version txt file
        doc_updater.update_version_txt(new_version, new_build, old_pkg_version, old_pkg_build)
        
        print()
        
        # Summary
        print("="*60)
        print("  SYNC COMPLETE")
        print("="*60)
        
        if self.dry_run:
            print("\n  DRY RUN - No changes were made")
        else:
            print(f"\n  ✅ Sync complete!")
            print(f"  📦 New version: v{new_version} (build {new_build})")
            print(f"  🌐 Bundled web server: v{ws_version} (build {ws_build})")
            print(f"  📁 Backup: {self.backup_dir}")
        
        print("\n  Updated files:")
        print("    ✅ app.py, static/, data/, requirements.txt")
        print("    ✅ README.md (version + What's new)")
        print("    ✅ CHANGELOG.md (new entry)")
        print("    ✅ version.json")
        print("    ✅ Version-WebServerPackaged-*.txt")
        
        print("\n  Next steps:")
        print("    1. Review README.md and CHANGELOG.md")
        print("    2. Test the packaged project")
        print("    3. Run packaging/build_exe.bat")
        print("    4. Run packaging/build_installer_inno.bat")
        
        print("\n" + "="*60 + "\n")
        
        return True


def main():
    parser = argparse.ArgumentParser(
        description='Sync KenpoFlashcardsWebServer to Packaged with AI doc updates',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python sync_webserver_to_packaged.py C:\\Projects\\WebServer C:\\Projects\\Packaged
  python sync_webserver_to_packaged.py ..\\..\\KenpoFlashcardsWebServer ..
  python sync_webserver_to_packaged.py source dest --dry-run
        """
    )
    
    parser.add_argument('source', help='Web server project folder')
    parser.add_argument('destination', help='Packaged project folder')
    parser.add_argument('--dry-run', '-n', action='store_true',
                        help='Preview changes without applying')
    parser.add_argument('--level', type=int, choices=[1,2,3],
                        help='Upgrade level: 1=patch, 2=minor, 3=major (skips interactive prompt)')
    parser.add_argument('--output', choices=['inplace','synced'], default='inplace',
                        help='Where to write results: inplace=modify destination folder; synced=create sibling <destination>-synced and write there')
    
    args = parser.parse_args()
    
    source = Path(args.source).resolve()
    destination = Path(args.destination).resolve()
    
    # Tools dir is where this script is located
    tools_dir = Path(__file__).parent.resolve()

    # Optional output mode: create sibling "<destination>-synced" and write there
    if args.output == 'synced':
        base_destination = destination
        destination = base_destination.parent / (base_destination.name + '-synced')

        if args.dry_run:
            print(f"[DRY-RUN] Output mode: synced -> would write into: {destination}")
        else:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            backup_root = tools_dir / 'sync_backups' / 'synced_outputs'
            backup_root.mkdir(parents=True, exist_ok=True)

            if destination.exists():
                backup_path = backup_root / f"{destination.name}_{timestamp}"
                print(f"[INFO] Existing synced folder found. Moving to backup: {backup_path}")
                shutil.move(str(destination), str(backup_path))

            print(f"[INFO] Creating fresh output folder from base Packaged: {base_destination} -> {destination}")
            # Copy base Packaged folder into the new output folder; ignore caches/backups
            shutil.copytree(
                str(base_destination),
                str(destination),
                ignore=shutil.ignore_patterns('__pycache__', '*.pyc', 'sync_backups'),
                dirs_exist_ok=False
            )

    # Run sync
    syncer = WebServerSyncer(source, destination, tools_dir, dry_run=args.dry_run, upgrade_level=args.level)
    success = syncer.run()
    
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
