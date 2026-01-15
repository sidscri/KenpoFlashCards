"""
Kenpo Flashcards Web - System Tray helper

- Opens the web UI
- Starts/stops/restarts the Windows service (NSSM) if installed
"""

import os
import sys
import webbrowser
import subprocess
import threading
import time
from pathlib import Path

import pystray
from PIL import Image
import requests

ROOT = Path(__file__).resolve().parents[1]
ICON_PATH = Path(__file__).with_name("icon.png")
SERVICE_NAME = "KenpoFlashcardsWeb"

def _port() -> int:
    try:
        return int(os.environ.get("KENPO_WEB_PORT") or 8009)
    except Exception:
        return 8009

def base_url() -> str:
    return f"http://127.0.0.1:{_port()}"

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
    webbrowser.open_new_tab(base_url())

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
    subprocess.Popen([str(py), str(ROOT / "app.py")], cwd=str(ROOT), env=env,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def _update_title(icon: pystray.Icon):
    while True:
        running = is_running()
        icon.title = f"Kenpo Flashcards ({'Running' if running else 'Stopped'})"
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

def on_quit(icon, item):
    icon.stop()

def build_menu():
    return pystray.Menu(
        pystray.MenuItem("Open Kenpo Flashcards", lambda i, it: open_app(), default=True),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Start", on_start),
        pystray.MenuItem("Stop", on_stop),
        pystray.MenuItem("Restart", on_restart),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Quit Tray", on_quit),
    )

def main():
    image = Image.open(ICON_PATH)
    icon = pystray.Icon("KenpoFlashcards", image, "Kenpo Flashcards", menu=build_menu())
    threading.Thread(target=_update_title, args=(icon,), daemon=True).start()
    icon.run()

if __name__ == "__main__":
    main()
