"""
KenpoFlashcardsTrayLauncher
- Starts the Flask server (app.py) in the background
- Shows a system-tray icon with Open / Restart / Exit

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

PORT = int(os.environ.get("KENPO_PORT", "8009"))
HOST = os.environ.get("KENPO_HOST", "127.0.0.1")
URL = f"http://{HOST}:{PORT}"

def _get_log_path():
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Kenpo Flashcards" / "logs"
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
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind((HOST, preferred))
        s.close()
        return preferred
    except OSError:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind((HOST, 0))
        p = s.getsockname()[1]
        s.close()
        return p

# Use a writable data dir and a stable base dir for templates/static
DATA_DIR = str(Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "Kenpo Flashcards" / "data")
os.environ.setdefault("KENPO_DATA_DIR", DATA_DIR)

# Pick a free port if 8009 is already in use
PORT = _pick_free_port(PORT)
os.environ["KENPO_PORT"] = str(PORT)
URL = f"http://{HOST}:{PORT}"


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
        _log(f"Starting server on {URL} (base={os.environ.get('KENPO_WEBAPP_BASE_DIR')}, data={os.environ.get('KENPO_DATA_DIR')})")
        from app import app as kenpo_app
        kenpo_app.run(host=HOST, port=PORT, debug=False, use_reloader=False)
    except Exception:
        tb = traceback.format_exc()
        _log("SERVER ERROR:\n" + tb)
        _popup("Kenpo Flashcards - Server Error", "The web server failed to start.\n\nDetails were written to:\n" + str(_get_log_path()) + "\n\n" + tb)
def main():
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
        webbrowser.open(URL)
        server_thread.join()
        return

    icon_path = os.path.join(BASE_DIR, "assets", "ic_launcher.png")
    try:
        image = Image.open(icon_path)
    except Exception:
        image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))

    def open_ui(_icon=None, _item=None):
        if _wait_for_server(10):
            webbrowser.open(URL)
        else:
            webbrowser.open(URL)

    def restart(_icon=None, _item=None):
        # Best-effort restart: exit; Windows Service / tray manager can relaunch
        os._exit(0)

    def quit_app(_icon=None, _item=None):
        os._exit(0)

    menu = pystray.Menu(
        pystray.MenuItem("Open Kenpo Flashcards", open_ui, default=True),
        pystray.MenuItem("Restart", restart),
        pystray.MenuItem("Exit", quit_app),
    )

    icon = pystray.Icon("KenpoFlashcards", image, "Kenpo Flashcards", menu)

    # Auto-open UI once
    _wait_for_server(15)
    try:
        webbrowser.open(URL)
    except Exception:
        pass

    icon.run()

if __name__ == "__main__":
    main()
