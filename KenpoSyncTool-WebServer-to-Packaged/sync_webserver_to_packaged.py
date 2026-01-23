#!/usr/bin/env python3
"""
Sync KenpoFlashcardsWebServer -> KenpoFlashcardsWebServer_Packaged safely.

Copies only server/web code and assets while protecting packaging scripts/specs.
Default behavior is DRY-RUN. Use --apply to actually copy files.
"""

from __future__ import annotations
import argparse, datetime, fnmatch, hashlib, json, os, shutil
from pathlib import Path

DEFAULT_SOURCE = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer"
DEFAULT_TARGET = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer_Packaged"

# Target paths we NEVER touch (protect packaging & build output)
PROTECT_DIRS = {
    "packaging", "dist", "build", "__pycache__", ".git", ".github", ".venv", "venv",
    ".idea", ".vscode", "logs", "output", ".sync_backups",
}

# Target files we NEVER overwrite by default (packaged has its own versioning)
PROTECT_FILE_PATTERNS = [
    "version.json",
    "Version-*.txt",
    "KenpoFlashcardsTrayLauncher.py",  # packaged-only entry point for tray app
]

# What to sync from source -> target (safe defaults)
SYNC_PATTERNS = [
    "app.py",
    "requirements.txt",
    "README.md",
    "CHANGELOG.md",
    "LICENSE",
    "BRANDING_NOTE.md",
    ".gitattributes",
    "static/**",
]

# Extra safety: do not sync runtime/user data by default (handled by pre_build.bat)
SKIP_SOURCE_DIRS = {"data", "build_data"}

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def is_under_protected_dir(rel: Path) -> bool:
    parts = {p.lower() for p in rel.parts}
    return any(p in parts for p in (d.lower() for d in PROTECT_DIRS))

def matches_any(name: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(name, pat) for pat in patterns)

def iter_source_files(src_root: Path, patterns: list[str]) -> list[Path]:
    """Return absolute paths of matching files under src_root for the given patterns."""
    out: set[Path] = set()
    for pat in patterns:
        for p in src_root.glob(pat):
            if p.is_file():
                rel = p.relative_to(src_root)
                if any(part in SKIP_SOURCE_DIRS for part in rel.parts):
                    continue
                out.add(p)
    return sorted(out)

def safe_copy(src: Path, dst: Path, backup_root: Path, tgt_root: Path, dry_run: bool) -> dict:
    """Copy src -> dst with backup if overwriting. Returns action dict."""
    action = {"src": str(src), "dst": str(dst), "status": None, "detail": None}
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        # Backup existing target file
        backup_path = backup_root / dst.relative_to(tgt_root)
        backup_path.parent.mkdir(parents=True, exist_ok=True)
        action["detail"] = f"backup -> {backup_path}"
        if not dry_run:
            shutil.copy2(dst, backup_path)
    if not dry_run:
        shutil.copy2(src, dst)
    action["status"] = "copied" if not dry_run else "would_copy"
    return action

def main() -> int:
    ap = argparse.ArgumentParser(
        description="Safely sync KenpoFlashcardsWebServer changes into KenpoFlashcardsWebServer_Packaged without touching packaging code."
    )
    ap.add_argument("--source", default=DEFAULT_SOURCE, help="Path to KenpoFlashcardsWebServer (source).")
    ap.add_argument("--target", default=DEFAULT_TARGET, help="Path to KenpoFlashcardsWebServer_Packaged (target).")
    ap.add_argument("--apply", action="store_true", help="Actually copy files (default is dry-run).")
    ap.add_argument("--include-version", action="store_true", help="Also sync version.json and Version-*.txt (NOT recommended).")
    ap.add_argument("--patterns", nargs="*", default=None, help="Override sync patterns (advanced).")
    ap.add_argument("--report", default="sync_report.json", help="Report filename to write in current directory.")
    args = ap.parse_args()

    src_root = Path(args.source).resolve()
    tgt_root = Path(args.target).resolve()

    if not src_root.exists():
        print(f"[ERROR] Source not found: {src_root}")
        return 2
    if not tgt_root.exists():
        print(f"[ERROR] Target not found: {tgt_root}")
        return 2

    patterns = args.patterns if args.patterns is not None else list(SYNC_PATTERNS)
    src_files = iter_source_files(src_root, patterns)
    dry_run = not args.apply

    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_root = tgt_root / ".sync_backups" / ts

    actions: list[dict] = []
    skipped: list[dict] = []

    for src_file in src_files:
        rel = src_file.relative_to(src_root)

        # Safety: never write into protected dirs in target
        if is_under_protected_dir(rel):
            skipped.append({"src": str(src_file), "rel": str(rel), "reason": "protected_target_dir"})
            continue

        # Protect certain target files unless explicitly allowed
        if (not args.include_version) and matches_any(rel.name, PROTECT_FILE_PATTERNS):
            skipped.append({"src": str(src_file), "rel": str(rel), "reason": "protected_target_file"})
            continue

        dst_file = tgt_root / rel

        # Compare content hashes when destination exists
        if dst_file.exists():
            try:
                if src_file.stat().st_size == dst_file.stat().st_size and sha256(src_file) == sha256(dst_file):
                    skipped.append({"src": str(src_file), "rel": str(rel), "reason": "identical"})
                    continue
            except Exception:
                # If comparison fails, fall through and treat as changed
                pass

        actions.append(safe_copy(src_file, dst_file, backup_root, tgt_root, dry_run=dry_run))

    report = {
        "timestamp": ts,
        "dry_run": dry_run,
        "source": str(src_root),
        "target": str(tgt_root),
        "patterns": patterns,
        "changed_count": len(actions),
        "actions": actions,
        "skipped_count": len(skipped),
        "skipped": skipped,
        "protected_dirs": sorted(PROTECT_DIRS),
        "protected_file_patterns": PROTECT_FILE_PATTERNS,
    }

    report_path = Path.cwd() / args.report
    try:
        report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    except Exception as e:
        print(f"[WARN] Could not write report to {report_path}: {e}")

    mode = "DRY-RUN" if dry_run else "APPLY"
    print("")
    print("============================================================")
    print(" Kenpo Sync: WebServer -> WebServer_Packaged")
    print("============================================================")
    print(f"Mode:   {mode}")
    print(f"Source: {src_root}")
    print(f"Target: {tgt_root}")
    print(f"Files to update: {len(actions)}")
    print(f"Skipped:         {len(skipped)}")
    if (not dry_run) and actions:
        print(f"Backups: {backup_root}")
    print(f"Report:  {report_path}")
    print("============================================================")

    preview = actions[:25]
    if preview:
        print("First changes:")
        for a in preview:
            rel_dst = Path(a["dst"]).resolve().relative_to(tgt_root)
            print(f"  - {rel_dst}")
        if len(actions) > len(preview):
            print(f"  ... +{len(actions) - len(preview)} more")
    else:
        print("No file changes detected for the configured patterns.")

    return 0

if __name__ == "__main__":
    raise SystemExit(main())