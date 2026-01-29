"""
AdvancedFlashcardsWebAppServerLauncher
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

# Prevent console window flashes when launching console tools (sc.exe) from a tray (GUI) app.
CREATE_NO_WINDOW = 0x08000000 if sys.platform == "win32" else 0

def _run_hidden(args, **kwargs):
    """Run a subprocess without flashing a console window on Windows."""
    if sys.platform == "win32":
        kwargs.setdefault("creationflags", CREATE_NO_WINDOW)
    return subprocess.run(args, **kwargs)

from pathlib import Path
from urllib.request import urlopen

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
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Advanced Flashcards WebApp Server" / "logs"
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


# ---------------------------------------------------------------------------
# Startup (Run at login) + optional Service (WinSW) helpers
# ---------------------------------------------------------------------------

SERVICE_NAME_DEFAULT = "AdvancedFlashcardsWebAppServer"
RUN_KEY_PATH = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_VALUE_NAME = "AdvancedFlashcardsWebAppServer"

def _get_launcher_settings_path():
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Advanced Flashcards WebApp Server"
    try:
        base.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    return base / "launcher_settings.json"

def _load_launcher_settings():
    p = _get_launcher_settings_path()
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}

def _save_launcher_settings(d: dict):
    p = _get_launcher_settings_path()
    try:
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")
    except Exception:
        pass

def _get_self_command():
    """Return the command list to relaunch this tray app."""
    if getattr(sys, "frozen", False):
        return [sys.executable]
    return [sys.executable, os.path.abspath(__file__)]

def _service_exists(service_name: str) -> bool:
    if sys.platform != "win32":
        return False
    try:
        r = _run_hidden(["sc", "query", service_name], capture_output=True, text=True)
        return r.returncode == 0 and "STATE" in (r.stdout or "")
    except Exception:
        return False

def _set_service_startup(service_name: str, automatic: bool):
    if sys.platform != "win32":
        return
    start_mode = "auto" if automatic else "demand"
    # Note: sc.exe requires "start=" with a trailing space.
    _run_hidden(["sc", "config", service_name, f"start= {start_mode}"], capture_output=True, text=True)

def _start_service(service_name: str):
    if sys.platform != "win32":
        return
    _run_hidden(["sc", "start", service_name], capture_output=True, text=True)

def _stop_service(service_name: str):
    if sys.platform != "win32":
        return
    _run_hidden(["sc", "stop", service_name], capture_output=True, text=True)

def _restart_service(service_name: str):
    if sys.platform != "win32":
        return
    _stop_service(service_name)
    time.sleep(1.0)
    _start_service(service_name)

def _is_run_at_login_enabled() -> bool:
    if sys.platform != "win32":
        return False
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY_PATH, 0, winreg.KEY_READ) as k:
            try:
                v, _ = winreg.QueryValueEx(k, RUN_VALUE_NAME)
                return bool(v)
            except FileNotFoundError:
                return False
    except Exception:
        return False

def _set_run_at_login(enabled: bool):
    if sys.platform != "win32":
        return
    try:
        import winreg
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, RUN_KEY_PATH) as k:
            if enabled:
                exe = _get_self_command()[0]
                winreg.SetValueEx(k, RUN_VALUE_NAME, 0, winreg.REG_SZ, f"\"{exe}\"")
            else:
                try:
                    winreg.DeleteValue(k, RUN_VALUE_NAME)
                except FileNotFoundError:
                    pass
    except Exception as e:
        _log(f"Run-at-login registry update failed: {e}")

def _apply_autostart_state(enabled: bool, service_name: str):
    """Enable/disable tray run-at-login and (if present) set service startup automatic/manual."""
    _set_run_at_login(enabled)
    if _service_exists(service_name):
        try:
            _set_service_startup(service_name, automatic=enabled)
            if enabled:
                _start_service(service_name)
        except Exception as e:
            _log(f"Service startup update failed: {e}")

def _ensure_default_autostart(service_name: str):
    """Default behavior: ON (first run only), but respect user choice afterwards."""
    s = _load_launcher_settings()
    if not s.get("autostart_initialized", False):
        s["autostart_initialized"] = True
        s["autostart_enabled"] = True
        _save_launcher_settings(s)
        _apply_autostart_state(True, service_name)
    else:
        # Keep system state in sync with the user's preference
        desired = bool(s.get("autostart_enabled", False))
        _apply_autostart_state(desired, service_name)

def _set_autostart_enabled(enabled: bool, service_name: str):
    s = _load_launcher_settings()
    s["autostart_initialized"] = True
    s["autostart_enabled"] = bool(enabled)
    _save_launcher_settings(s)
    _apply_autostart_state(bool(enabled), service_name)

def _get_autostart_enabled() -> bool:
    s = _load_launcher_settings()
    if not s.get("autostart_initialized", False):
        return True
    return bool(s.get("autostart_enabled", False))

def _relaunch_and_exit():
    """Relaunch the tray app and exit this instance (used for true restart)."""
    try:
        cmd = _get_self_command()
        subprocess.Popen(cmd, cwd=BASE_DIR)
    except Exception as e:
        _log(f"Failed to relaunch: {e}")
    os._exit(0)
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
    # Default: enable autostart on first run
    _ensure_default_autostart(SERVICE_NAME_DEFAULT)

    # Start server thread
    server_thread = threading.Thread(target=_run_server, daemon=True)
    server_thread.start()

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
        os.path.join(BASE_DIR, "assets", "AdvancedFlashcardsWebAppServer_tray.png"),
        os.path.join(BASE_DIR, "windows_tray", "icon.png"),
        os.path.join(BASE_DIR, "static", "res", "webappservericons", "AdvancedFlashcardsWebAppServer_tray.png"),
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

    def restart_server(_icon=None, _item=None):
        # True restart: relaunch this tray app (which restarts the embedded server)
        _relaunch_and_exit()

    def restart_service_only(_icon=None, _item=None):
        svc = SERVICE_NAME_DEFAULT
        if _service_exists(svc):
            _restart_service(svc)
            _popup("Advanced Flashcards WebApp Server", f"Service restarted: {svc}")
        else:
            _popup("Advanced Flashcards WebApp Server", "No Windows Service found to restart.")

    def restart_server_and_service(_icon=None, _item=None):
        svc = SERVICE_NAME_DEFAULT
        if _service_exists(svc):
            _restart_service(svc)
        _relaunch_and_exit()

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
        pystray.MenuItem("Open Data Folder", open_data_folder),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem(
            "Start service + tray with Windows",
            lambda _i=None, _m=None: _set_autostart_enabled(not _get_autostart_enabled(), SERVICE_NAME_DEFAULT),
            checked=lambda _i=None: _get_autostart_enabled()
        ),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Restart server (tray + web server)", restart_server),
        pystray.MenuItem("Restart Windows Service only", restart_service_only),
        pystray.MenuItem("Restart server + service", restart_server_and_service),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit", quit_app),
    )

    icon = pystray.Icon("StudyFlashcards", image, f"Advanced Flashcards WebApp Server ({HOST}:{PORT})", menu)

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
