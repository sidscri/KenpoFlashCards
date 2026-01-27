"""
Study Flashcards Web - System Tray helper

- Opens the web UI
- Starts/stops/restarts the Windows service (NSSM) if installed
- Reads server_config.json for host/port configuration
"""

import os
import sys
import webbrowser
import subprocess
import threading
import time
import json
from pathlib import Path

import pystray
from PIL import Image
import requests

ROOT = Path(__file__).resolve().parents[1]
ICON_PATH = Path(__file__).with_name("icon.png")
SERVICE_NAME = "StudyFlashcardsWeb"

def _get_config_path():
    """Get config path - check user data dir first, fall back to app dir."""
    user_config = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Study Flashcards" / "server_config.json"
    app_config = ROOT / "server_config.json"
    
    if user_config.exists():
        return user_config
    elif app_config.exists():
        return app_config
    return user_config

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
                for key, value in default_config.items():
                    if key not in loaded:
                        loaded[key] = value
                return loaded
    except Exception:
        pass
    
    return default_config

CONFIG = _load_config()

def _port() -> int:
    try:
        return int(os.environ.get("KENPO_WEB_PORT") or CONFIG.get("port", 8009))
    except Exception:
        return 8009

def _host() -> str:
    return os.environ.get("KENPO_HOST") or CONFIG.get("host", "0.0.0.0")

def base_url() -> str:
    """URL for health checks - always use localhost for local checks."""
    host = _host()
    # For wildcard bindings, check on localhost
    if host in ("0.0.0.0", "::", ""):
        return f"http://127.0.0.1:{_port()}"
    return f"http://{host}:{_port()}"

def browser_url() -> str:
    """URL to open in browser."""
    return CONFIG.get("browser_url", f"http://localhost:{_port()}")

def is_running() -> bool:
    try:
        r = requests.get(base_url() + "/api/health", timeout=0.8)
        return r.ok
    except Exception:
        return False

def _sc(*args: str) -> int:
    # Use sc.exe to control service
    cmd = ["sc"] + list(args)
    try:
        p = subprocess.run(cmd, capture_output=True, text=True)
        return p.returncode
    except Exception:
        return 1

def service_installed() -> bool:
    return _sc("query", SERVICE_NAME) == 0

def service_start():
    if service_installed():
        _sc("start", SERVICE_NAME)

def service_stop():
    if service_installed():
        _sc("stop", SERVICE_NAME)

def service_restart():
    # Restart via stop/start to be compatible even if "restart" isn't available
    if service_installed():
        _sc("stop", SERVICE_NAME)
        time.sleep(0.5)
        _sc("start", SERVICE_NAME)

def open_app():
    webbrowser.open_new_tab(browser_url())

def start_server_fallback():
    """
    If service isn't installed, start the dev server hidden as a fallback.
    """
    if is_running():
        return
    py = ROOT / ".venv" / "Scripts" / "pythonw.exe"
    if not py.exists():
        return
    env = os.environ.copy()
    env.setdefault("KENPO_WEB_PORT", str(_port()))
    env.setdefault("KENPO_HOST", _host())
    subprocess.Popen([str(py), str(ROOT / "app.py")], cwd=str(ROOT), env=env,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

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
    except Exception:
        pass

def _update_title(icon: pystray.Icon):
    while True:
        running = is_running()
        host = _host()
        port = _port()
        icon.title = f"Study Flashcards ({host}:{port}) - {'Running' if running else 'Stopped'}"
        time.sleep(2.0)

def on_start(icon, item):
    if service_installed():
        service_start()
    else:
        start_server_fallback()

def on_stop(icon, item):
    if service_installed():
        service_stop()
    # no safe pid kill fallback; user can close console or use Task Manager

def on_restart(icon, item):
    if service_installed():
        service_restart()
    else:
        # fallback: just start if not running
        start_server_fallback()

def on_settings(icon, item):
    _open_config()

def on_quit(icon, item):
    icon.stop()

def build_menu():
    return pystray.Menu(
        pystray.MenuItem("Open Study Flashcards", lambda i, it: open_app(), default=True),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Edit Settings", on_settings),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Start", on_start),
        pystray.MenuItem("Stop", on_stop),
        pystray.MenuItem("Restart", on_restart),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Quit Tray", on_quit),
    )

def main():
    image = Image.open(ICON_PATH)
    host = _host()
    port = _port()
    icon = pystray.Icon("StudyFlashcards", image, f"Study Flashcards ({host}:{port})", menu=build_menu())
    threading.Thread(target=_update_title, args=(icon,), daemon=True).start()
    icon.run()

if __name__ == "__main__":
    main()
