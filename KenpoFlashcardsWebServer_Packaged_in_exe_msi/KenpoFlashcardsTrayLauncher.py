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
from urllib.request import urlopen

# PyInstaller base dir
BASE_DIR = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))

# Ensure app.py sees a stable project dir for static paths
os.environ.setdefault("KENPO_WEBAPP_BASE_DIR", BASE_DIR)

PORT = int(os.environ.get("KENPO_PORT", "8009"))
HOST = os.environ.get("KENPO_HOST", "127.0.0.1")
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
    # Import here so environment variables are set first
    import app as kenpo_app  # app.py
    # Turn off Flask reloader + debug in packaged mode
    kenpo_app.app.run(host=HOST, port=PORT, debug=False, use_reloader=False)

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
