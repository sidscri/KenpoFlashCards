"""
AdvancedFlashcardsWebAppServerTrayLauncher
- Starts the Flask server (app.py) in the background
- Shows a system-tray icon with Open / Settings / Restart / Exit
- Reads server_config.json for host/port configuration

This is used for PyInstaller builds so the EXE behaves like Sonarr-style tray apps.
"""
import os
import sys
import time
import threading
import webbrowser
import socket
import traceback
import logging
import json
import subprocess
import zipfile
import shutil
from pathlib import Path
from urllib.request import urlopen


# -----------------------------------------------------------------------------
# Step 4: Data update rules + backups + startup options
# -----------------------------------------------------------------------------
APP_NAME = "Advanced Flashcards WebApp Server"
SERVICE_NAME = "AdvancedFlashcardsWebAppServer"
APPDATA_ROOT = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / APP_NAME
LOCAL_DATA_DIR = APPDATA_ROOT / "data"
BACKUP_DIR = APPDATA_ROOT / "DataBackups"
LOG_DIR = APPDATA_ROOT / "log" / f"{APP_NAME} logs"
LOG_DIR.mkdir(parents=True, exist_ok=True)

BACKUP_STATE_FILE = APPDATA_ROOT / "backup_state.json"
DEFAULT_AUTO_BACKUP_MINUTES = 6 * 60  # 6 hours

SAFE_OVERWRITE_FILES = {"kenpo_words.json"}

def _now_stamp():
    return time.strftime("%Y%m%d_%H%M%S")

def _load_json(path: Path, default):
    try:
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        pass
    return default

def _save_json(path: Path, data):
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception:
        pass

def _data_newest_mtime(root: Path) -> float:
    newest = 0.0
    try:
        for p in root.rglob("*"):
            if p.is_file():
                newest = max(newest, p.stat().st_mtime)
    except Exception:
        pass
    return newest

def _rotate_backups(kind: str, keep: int = 10):
    try:
        folders = sorted([p for p in BACKUP_DIR.glob(f"Data_{kind}_*_*") if p.is_dir()], key=lambda p: p.stat().st_mtime, reverse=True)
        for p in folders[keep:]:
            try:
                shutil.rmtree(p, ignore_errors=True)
            except Exception:
                pass
    except Exception:
        pass

def _backup_data_zip(kind: str, app_version: str, reason: str = ""):
    """Create a zip of LOCAL_DATA_DIR into BACKUP_DIR with rotation (keep last 10)."""
    try:
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        stamp = _now_stamp()
        folder = BACKUP_DIR / f"Data_{kind}_{stamp}_{app_version}"
        folder.mkdir(parents=True, exist_ok=True)
        zip_path = folder / "data.zip"
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as z:
            if LOCAL_DATA_DIR.exists():
                for f in LOCAL_DATA_DIR.rglob("*"):
                    if f.is_file():
                        z.write(f, arcname=str(f.relative_to(LOCAL_DATA_DIR)))
        _rotate_backups(kind, keep=10)
        _log(f"[BACKUP] Created {zip_path} {'(' + reason + ')' if reason else ''}")
        return zip_path
    except Exception as e:
        _log(f"[BACKUP][ERROR] {e}")
        return None

def _merge_packaged_seed_into_local(app_version: str):
    """Local wins. Copy missing files from packaged seed. Overwrite only SAFE_OVERWRITE_FILES when seed newer."""
    try:
        LOCAL_DATA_DIR.mkdir(parents=True, exist_ok=True)
        packaged_seed = Path(BASE_DIR) / "data"
        if not packaged_seed.exists():
            return
        changed = False
        backed_up = False
        for src in packaged_seed.rglob("*"):
            if not src.is_file():
                continue
            rel = src.relative_to(packaged_seed)
            dst = LOCAL_DATA_DIR / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            if not dst.exists():
                shutil.copy2(src, dst)
                changed = True
                _log(f"[DATA] Seeded missing file: {rel}")
            else:
                if rel.name in SAFE_OVERWRITE_FILES:
                    try:
                        if src.stat().st_mtime > dst.stat().st_mtime + 2:
                            if not backed_up:
                                _backup_data_zip("Updated", app_version, reason="pre-seed-overwrite")
                                backed_up = True
                            shutil.copy2(src, dst)
                            changed = True
                            _log(f"[DATA] Updated reference file (seed newer): {rel}")
                    except Exception:
                        pass
        if changed and not backed_up:
            _backup_data_zip("Updated", app_version, reason="seeded-missing-files")
        if changed:
            state = _load_json(BACKUP_STATE_FILE, {})
            state["last_data_mtime"] = _data_newest_mtime(LOCAL_DATA_DIR)
            _save_json(BACKUP_STATE_FILE, state)
    except Exception as e:
        _log(f"[DATA][ERROR] {e}")

def _run_elevated(cmdline: str) -> bool:
    """Run a command line elevated (UAC). Returns True if ShellExecute succeeded."""
    if sys.platform != "win32":
        return False
    try:
        rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", "cmd.exe", f'/c {cmdline}', None, 1)
        return rc > 32
    except Exception as e:
        _log(f"[UAC][ERROR] {e}")
        return False

def restart_service_admin(_icon=None, _item=None):
    """Restart the Windows Service with a UAC prompt."""
    if not _service_exists(SERVICE_NAME):
        try:
            ctypes.windll.user32.MessageBoxW(0, "Service is not installed.", APP_NAME, 0x40)
        except Exception:
            pass
        return

    svc_dir = Path(BASE_DIR) / "service"
    if "nssm" == "nssm":
        nssm = svc_dir / "nssm.exe"
        if nssm.exists():
            cmd = f'"{nssm}" restart "{SERVICE_NAME}"'
            if _run_elevated(cmd):
                return
    elif "nssm" == "winsw":
        winsw = svc_dir / "AdvancedFlashcardsWebAppServerService.exe"
        if winsw.exists():
            cmd = f'"{winsw}" restart'
            if _run_elevated(cmd):
                return

    cmd = f'sc stop "{SERVICE_NAME}" && timeout /t 2 /nobreak >nul && sc start "{SERVICE_NAME}"'
    _run_elevated(cmd)

def restart_app_only(_icon=None, _item=None):
    """Restart only the tray app (does not touch service)."""
    try:
        if sys.platform == "win32":
            exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
            ctypes.windll.shell32.ShellExecuteW(None, "open", exe, "", None, 1)
        else:
            exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
            subprocess.Popen([exe])
    except Exception as e:
        _log(f"Restart failed to relaunch: {e}")
    os._exit(0)

def _service_exists(name: str) -> bool:
    if sys.platform != "win32":
        return False
    try:
        r = subprocess.run(["sc", "query", name], capture_output=True, text=True)
        return r.returncode == 0
    except Exception:
        return False

def _restart_windows_service(name: str) -> bool:
    """Best-effort restart. Returns True if restart attempted successfully."""
    if sys.platform != "win32":
        return False
    try:
        # Prefer wrapper-specific restart if wrapper is present
        svc_dir = Path(BASE_DIR) / "service"
        if "nssm" == "nssm":
            nssm = svc_dir / "nssm.exe"
            if nssm.exists():
                r = subprocess.run([str(nssm), "restart", name], capture_output=True, text=True)
                if r.returncode == 0:
                    _log(f"[SERVICE] Restarted via NSSM: {name}")
                    return True
                _log(f"[SERVICE] NSSM restart failed: {r.stdout} {r.stderr}")
        elif "nssm" == "winsw":
            winsw = svc_dir / "AdvancedFlashcardsWebAppServerService.exe"
            if winsw.exists():
                r = subprocess.run([str(winsw), "restart"], capture_output=True, text=True)
                if r.returncode == 0:
                    _log(f"[SERVICE] Restarted via WinSW: {name}")
                    return True
                _log(f"[SERVICE] WinSW restart failed: {r.stdout} {r.stderr}")

        # Fallback: sc stop/start
        subprocess.run(["sc", "stop", name], capture_output=True, text=True)
        # wait a bit for stop
        for _ in range(20):
            q = subprocess.run(["sc", "query", name], capture_output=True, text=True)
            if "STOPPED" in (q.stdout or ""):
                break
            time.sleep(0.5)
        subprocess.run(["sc", "start", name], capture_output=True, text=True)
        _log(f"[SERVICE] Restarted via SC: {name}")
        return True
    except Exception as e:
        _log(f"[SERVICE][ERROR] Restart failed: {e}")
        return False

def _get_startup_reg_name():
    return "AdvancedFlashcardsWebAppServer"

def _is_startup_enabled() -> bool:
    if sys.platform != "win32":
        return False
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Run", 0, winreg.KEY_READ) as k:
            try:
                winreg.QueryValueEx(k, _get_startup_reg_name())
                return True
            except FileNotFoundError:
                return False
    except Exception:
        return False

def _set_startup_enabled(enable: bool):
    if sys.platform != "win32":
        return
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Run", 0, winreg.KEY_SET_VALUE) as k:
            if enable:
                exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
                winreg.SetValueEx(k, _get_startup_reg_name(), 0, winreg.REG_SZ, f'"{exe}"')
                _log("[STARTUP] Enabled startup (HKCU Run).")
            else:
                try:
                    winreg.DeleteValue(k, _get_startup_reg_name())
                except FileNotFoundError:
                    pass
                _log("[STARTUP] Disabled startup (HKCU Run).")
    except Exception as e:
        _log(f"[STARTUP][ERROR] {e}")

def _schedule_task_enabled() -> bool:
    if sys.platform != "win32":
        return False
    try:
        name = "AdvancedFlashcardsWebAppServer-Background"
        r = subprocess.run(["schtasks", "/Query", "/TN", name], capture_output=True, text=True)
        return r.returncode == 0
    except Exception:
        return False

def _set_schedule_task(enable: bool):
    if sys.platform != "win32":
        return
    name = "AdvancedFlashcardsWebAppServer-Background"
    try:
        exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
        if enable:
            subprocess.run(["schtasks", "/Create", "/F",
                            "/SC", "ONLOGON",
                            "/TN", name,
                            "/TR", f'"{exe}" --headless',
                            "/RL", "LIMITED"], check=False)
            _log("[SERVICE] Enabled background mode via Scheduled Task.")
        else:
            subprocess.run(["schtasks", "/Delete", "/F", "/TN", name], check=False)
            _log("[SERVICE] Disabled background mode via Scheduled Task.")
    except Exception as e:
        _log(f"[SERVICE][ERROR] {e}")

def _get_auto_backup_minutes() -> int:
    cfg = _load_json(_get_config_path(), {})
    m = cfg.get("auto_backup_minutes", DEFAULT_AUTO_BACKUP_MINUTES)
    try:
        return int(m)
    except Exception:
        return DEFAULT_AUTO_BACKUP_MINUTES

def _set_auto_backup_minutes(minutes: int):
    cfg = _load_json(_get_config_path(), {})
    cfg["auto_backup_minutes"] = minutes
    _save_json(_get_config_path(), cfg)

def _auto_backup_loop(app_version: str, stop_event: threading.Event):
    last_backup_time = 0.0
    while not stop_event.is_set():
        try:
            mins = _get_auto_backup_minutes()
            if mins <= 0:
                time.sleep(5)
                continue
            interval = mins * 60
            newest = _data_newest_mtime(LOCAL_DATA_DIR)
            state = _load_json(BACKUP_STATE_FILE, {})
            last_data = float(state.get("last_data_mtime", 0.0))
            now = time.time()
            if newest > last_data + 1 and (now - last_backup_time) >= interval:
                _backup_data_zip("Auto", app_version, reason="auto-change-detected")
                last_backup_time = now
                state["last_data_mtime"] = newest
                _save_json(BACKUP_STATE_FILE, state)
        except Exception:
            pass
        time.sleep(10)

# PyInstaller base dir - determine this FIRST before any app imports
if getattr(sys, 'frozen', False):
    # Running as frozen exe
    if hasattr(sys, '_MEIPASS'):
        # PyInstaller onefile mode - _MEIPASS is the temp extraction folder
        BASE_DIR = sys._MEIPASS
    else:
        # PyInstaller onefolder mode - use executable's directory
        BASE_DIR = os.path.dirname(sys.executable)
else:
    # Running from source
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Ensure app.py sees a stable project dir for static paths
# This MUST be set before importing app
os.environ["KENPO_WEBAPP_BASE_DIR"] = BASE_DIR

# Also add BASE_DIR to Python path so app.py can be imported
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

# --- Configuration file support ---
def _get_config_path():
    """Get config path - check user data dir first, fall back to app dir."""
    user_config = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Advanced Flashcards WebApp Server" / "server_config.json"
    app_config = Path(BASE_DIR) / "server_config.json"
    
    # Use user config if it exists, otherwise copy from app dir
    if user_config.exists():
        return user_config
    elif app_config.exists():
        # Copy to user location for editing
        try:
            user_config.parent.mkdir(parents=True, exist_ok=True)
            import shutil
            shutil.copy(app_config, user_config)
            return user_config
        except Exception:
            return app_config
    return user_config  # Return user path even if doesn't exist yet

def _load_config():
    """Load configuration from server_config.json."""
    config_path = _get_config_path()
    default_config = {
        "host": "0.0.0.0",
        "port": 8009,
        "open_browser": True,
        "browser_url": "http://localhost:8009"
    }
    
    try:
        if config_path.exists():
            with open(config_path, 'r') as f:
                loaded = json.load(f)
                # Merge with defaults
                for key, value in default_config.items():
                    if key not in loaded:
                        loaded[key] = value
                return loaded
    except Exception as e:
        _log(f"Error loading config: {e}")
    
    # Create default config if it doesn't exist
    try:
        config_path.parent.mkdir(parents=True, exist_ok=True)
        with open(config_path, 'w') as f:
            json.dump({
                "host": "0.0.0.0",
                "port": 8009,
                "open_browser": True,
                "browser_url": "http://localhost:8009",
                "_comment_host": "Use '127.0.0.1' for localhost only, '0.0.0.0' or '::' for all interfaces, or a specific IP like '192.168.0.129'",
                "_comment_browser_url": "The URL opened in your browser. Change to your Tailscale/LAN IP if accessing remotely"
            }, f, indent=2)
    except Exception:
        pass
    
    return default_config

# Load configuration
CONFIG = _load_config()
PORT = int(os.environ.get("KENPO_PORT", CONFIG.get("port", 8009)))
HOST = os.environ.get("KENPO_HOST", CONFIG.get("host", "0.0.0.0"))
BROWSER_URL = CONFIG.get("browser_url", f"http://localhost:{PORT}")
OPEN_BROWSER = CONFIG.get("open_browser", True)

# For health checks, we need to use localhost if binding to 0.0.0.0 or ::
HEALTH_CHECK_HOST = "127.0.0.1" if HOST in ("0.0.0.0", "::", "") else HOST
URL = f"http://{HEALTH_CHECK_HOST}:{PORT}"

def _get_log_path():
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Advanced Flashcards WebApp Server" / "log" / "Advanced Flashcards WebApp Server logs"
    try:
        base.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    return base / "tray.log"

def _log(msg):
    try:
        logging.basicConfig(filename=str(_get_log_path()), level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
        logging.info(msg)
    except Exception:
        pass

def _popup(title, message):
    try:
        import ctypes
        ctypes.windll.user32.MessageBoxW(0, message, title, 0x10)
    except Exception:
        pass

def _pick_free_port(preferred: int) -> int:
    try:
        # For 0.0.0.0 or ::, test binding on the wildcard
        bind_host = HOST if HOST not in ("", "::") else "0.0.0.0"
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind((bind_host if bind_host != "::" else "", preferred))
        s.close()
        return preferred
    except OSError:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(("", 0))
        p = s.getsockname()[1]
        s.close()
        return p

# Use a writable data dir and a stable base dir for templates/static
DATA_DIR = str(Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Advanced Flashcards WebApp Server" / "data")
os.environ.setdefault("KENPO_DATA_DIR", DATA_DIR)


# App version (from version.json next to this script)
def _get_app_version():
    try:
        vpath = Path(BASE_DIR) / "version.json"
        if vpath.exists():
            return json.loads(vpath.read_text(encoding="utf-8")).get("version", "0.0.0")
    except Exception:
        pass
    return "0.0.0"

APP_VERSION = _get_app_version()

# Pick a free port if configured port is already in use
PORT = _pick_free_port(PORT)
os.environ["KENPO_PORT"] = str(PORT)
os.environ["KENPO_HOST"] = HOST

# Update URLs with actual port
URL = f"http://{HEALTH_CHECK_HOST}:{PORT}"
if BROWSER_URL == f"http://localhost:{CONFIG.get('port', 8009)}":
    BROWSER_URL = f"http://localhost:{PORT}"


def _wait_for_server(timeout_s: float = 10.0) -> bool:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            with urlopen(URL + "/api/health", timeout=1) as r:
                if r.status == 200:
                    return True
        except Exception:
            pass
        time.sleep(0.2)
    return False

def _run_server():
    try:
        _log(f"Starting server on {HOST}:{PORT} (base={os.environ.get('KENPO_WEBAPP_BASE_DIR')}, data={os.environ.get('KENPO_DATA_DIR')})")
        from app import app as kenpo_app
        kenpo_app.run(host=HOST, port=PORT, debug=False, use_reloader=False)
    except Exception:
        tb = traceback.format_exc()
        _log("SERVER ERROR:\n" + tb)
        _popup("Advanced Flashcards WebApp Server - Server Error", "The web server failed to start.\n\nDetails were written to:\n" + str(_get_log_path()) + "\n\n" + tb)

def _open_config():
    """Open the config file in the default editor."""
    config_path = _get_config_path()
    try:
        if sys.platform == 'win32':
            os.startfile(str(config_path))
        elif sys.platform == 'darwin':
            subprocess.run(['open', str(config_path)])
        else:
            subprocess.run(['xdg-open', str(config_path)])
    except Exception as e:
        _log(f"Error opening config: {e}")
        _popup("Advanced Flashcards WebApp Server", f"Config file location:\n{config_path}")

def _open_config_folder():
    """Open the config folder in file explorer."""
    config_path = _get_config_path().parent
    try:
        if sys.platform == 'win32':
            os.startfile(str(config_path))
        elif sys.platform == 'darwin':
            subprocess.run(['open', str(config_path)])
        else:
            subprocess.run(['xdg-open', str(config_path)])
    except Exception as e:
        _log(f"Error opening config folder: {e}")

def main():
    # Headless mode (service-like background)
    if any(a.lower()=='--headless' for a in sys.argv[1:]):
        _run_server()
        return

    # Step 4: ensure local data and seed missing files
    state = _load_json(BACKUP_STATE_FILE, {})
    if state.get("last_run_version") != APP_VERSION:
        _backup_data_zip("Updated", APP_VERSION, reason="app-version-change")
        state["last_run_version"] = APP_VERSION
        _save_json(BACKUP_STATE_FILE, state)

    _merge_packaged_seed_into_local(APP_VERSION)

    # Start server thread
    server_thread = threading.Thread(target=_run_server, daemon=True)
    server_thread.start()

    # Auto-backup watcher (runs in background)
    stop_evt = threading.Event()
    threading.Thread(target=_auto_backup_loop, args=(APP_VERSION, stop_evt), daemon=True).start()

    # Tray UI
    try:
        import pystray
        from PIL import Image
    except Exception as e:
        print("Tray dependencies missing:", e)
        print(f"Server should still be running at {URL}")
        _wait_for_server(15)
        if OPEN_BROWSER:
            webbrowser.open(BROWSER_URL)
        server_thread.join()
        return

    # Try multiple icon locations
    icon_paths = [
        os.path.join(BASE_DIR, "assets", "ic_launcher.png"),
        os.path.join(BASE_DIR, "windows_tray", "icon.png"),
        os.path.join(BASE_DIR, "ic_launcher.png"),
        os.path.join(BASE_DIR, "Kenpo Vovabulary Advanced Flashcards WebApp Server.png"),
    ]
    
    image = None
    for icon_path in icon_paths:
        try:
            if os.path.exists(icon_path):
                image = Image.open(icon_path)
                break
        except Exception:
            continue
    
    if image is None:
        image = Image.new("RGBA", (256, 256), (0, 128, 0, 255))  # Green fallback

    def open_ui(_icon=None, _item=None):
        if _wait_for_server(10):
            webbrowser.open(BROWSER_URL)
        else:
            webbrowser.open(BROWSER_URL)

    def edit_settings(_icon=None, _item=None):
        _open_config()

    def open_data_folder(_icon=None, _item=None):
        _open_config_folder()

    def restart(_icon=None, _item=None):
    # If installed as a Windows Service, restart the service.
    if _service_exists(SERVICE_NAME):
        ok = _restart_windows_service(SERVICE_NAME)
        if ok:
            return
        # If service restart failed (often permissions), fall back to relaunching UI app.
        try:
            ctypes.windll.user32.MessageBoxW(
                0,
                "Service restart failed. Try running the tray app as Administrator, or restart the service in Services.msc.\n\nFalling back to restarting the tray app.",
                APP_NAME,
                0x40
            )
        except Exception:
            pass

    # Restart tray app by relaunching this executable, then exiting
    try:
        if sys.platform == "win32":
            exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
            # ShellExecute is the most reliable for frozen apps
            ctypes.windll.shell32.ShellExecuteW(None, "open", exe, "", None, 1)
        else:
            exe = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
            subprocess.Popen([exe])
    except Exception as e:
        _log(f"Restart failed to relaunch: {e}")
    os._exit(0)

    def quit_app(_icon=None, _item=None):
        os._exit(0)

    def show_info(_icon=None, _item=None):
        info = f"Advanced Flashcards WebApp Server Web Server\n\nListening on: {HOST}:{PORT}\nBrowser URL: {BROWSER_URL}\nConfig: {_get_config_path()}"
        _popup("Advanced Flashcards WebApp Server - Server Info", info)

    menu = pystray.Menu(
        pystray.MenuItem("Open Advanced Flashcards WebApp Server", open_ui, default=True),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Server Info", show_info),
        pystray.MenuItem("Edit Settings", edit_settings),
        pystray.MenuItem("Open App Folder", open_data_folder),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Backup Now", lambda _i=None, _m=None: _backup_data_zip("OnDemand", APP_VERSION, reason="user")),
        pystray.MenuItem(
            "Auto Backup",
            pystray.Menu(
                pystray.MenuItem("Off", lambda *_: _set_auto_backup_minutes(0), checked=lambda _: _get_auto_backup_minutes()==0),
                pystray.MenuItem("Every 30 min", lambda *_: _set_auto_backup_minutes(30), checked=lambda _: _get_auto_backup_minutes()==30),
                pystray.MenuItem("Every 1 hour", lambda *_: _set_auto_backup_minutes(60), checked=lambda _: _get_auto_backup_minutes()==60),
                pystray.MenuItem("Every 2 hours", lambda *_: _set_auto_backup_minutes(120), checked=lambda _: _get_auto_backup_minutes()==120),
                pystray.MenuItem("Every 6 hours (default)", lambda *_: _set_auto_backup_minutes(360), checked=lambda _: _get_auto_backup_minutes()==360),
                pystray.MenuItem("Every 1 day", lambda *_: _set_auto_backup_minutes(1440), checked=lambda _: _get_auto_backup_minutes()==1440),
                pystray.MenuItem("Every 2 days", lambda *_: _set_auto_backup_minutes(2880), checked=lambda _: _get_auto_backup_minutes()==2880),
            ),
        ),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Start with Windows", lambda *_: _set_startup_enabled(not _is_startup_enabled()), checked=lambda _: _is_startup_enabled()),
        pystray.MenuItem("Run in background at login (Scheduled Task)", lambda *_: _set_schedule_task(not _schedule_task_enabled()), checked=lambda _: _schedule_task_enabled()),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Restart (Auto)", restart),
        pystray.MenuItem("Restart Service (Admin)", restart_service_admin),
        pystray.MenuItem("Restart App Only", restart_app_only),
        pystray.MenuItem("Exit", quit_app),
    )
icon = pystray.Icon("AdvancedFlashcardsWebAppServer", image, f"{APP_NAME} ({HOST}:{PORT})", menu)

    # Auto-open UI once
    _wait_for_server(15)
    if OPEN_BROWSER:
        try:
            webbrowser.open(BROWSER_URL)
        except Exception:
            pass

    icon.run()

if __name__ == "__main__":
    main()