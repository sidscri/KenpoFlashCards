"""KenpoFlashcardsServerRunner

Service-friendly entrypoint (no tray).

Use with a service wrapper (e.g., NSSM) to run like Sonarr.

Environment variables:
  - KENPO_WEB_PORT (default 8009)
  - KENPO_HOST     (default 127.0.0.1)
"""

import os
import app as kenpo_app

HOST = os.environ.get("KENPO_HOST", "127.0.0.1")
PORT = int(os.environ.get("KENPO_WEB_PORT", "8009"))


def main() -> None:
    # When this file is used as entrypoint, app.py is imported (not executed as __main__).
    # Keep reloader off for service usage.
    try:
        # Some versions expose this helper for key loading.
        kenpo_app._load_api_keys_on_startup()  # type: ignore[attr-defined]
    except Exception:
        pass

    kenpo_app.app.run(host=HOST, port=PORT, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
