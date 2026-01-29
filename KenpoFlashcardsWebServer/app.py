import os
import json
import time
import hashlib
import re
import uuid
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

import requests

from flask import Flask, jsonify, request, send_from_directory, session, send_file
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename

# =========================================================
# Advanced Flashcards WebApp Server (Multi-user with Username/Password Auth)
# - Users create a profile with username/password
# - Progress + settings are stored per user on the server
# - Login from any device with the same credentials
# =========================================================

DEFAULT_KENPO_ROOT = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps"
# Fallback (only used if auto-discovery fails)
DEFAULT_KENPO_JSON_FALLBACK = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsProject-v2\app\src\main\assets\kenpo_words.json"


def _safe_int(value, default=0):
    """Best-effort int() conversion for sync timestamps (prevents 500s on bad data)."""
    try:
        if value is None:
            return default
        if isinstance(value, bool):
            return int(value)
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        if isinstance(value, str):
            s = value.strip()
            if not s or s.lower() in ('none', 'null', 'nan'):
                return default
            try:
                return int(s)
            except Exception:
                try:
                    return int(float(s))
                except Exception:
                    return default
        return int(value)
    except Exception:
        return default

def _resolve_kenpo_json_path() -> str:
    """Resolve the kenpo_words.json path.

    Priority:
      1) KENPO_JSON_PATH (explicit)
      2) Auto-discover under KENPO_ROOT (or DEFAULT_KENPO_ROOT)
      3) DEFAULT_KENPO_JSON_FALLBACK

    Auto-discovery picks the most recently modified match.
    """
    explicit = (os.getenv("KENPO_JSON_PATH") or "").strip()
    if explicit and os.path.exists(explicit):
        return explicit

    root = (os.getenv("KENPO_ROOT") or DEFAULT_KENPO_ROOT).strip() or DEFAULT_KENPO_ROOT
    try:
        root_path = Path(root)
        if root_path.exists():
            candidates = list(root_path.rglob("app/src/main/assets/kenpo_words.json"))
            if candidates:
                candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
                return str(candidates[0])
    except Exception:
        # fall through to fallback
        pass

    return DEFAULT_KENPO_JSON_FALLBACK


KENPO_JSON_PATH = _resolve_kenpo_json_path()

APP_DIR = os.path.dirname(os.path.abspath(__file__))

DATA_DIR = os.path.join(APP_DIR, "data")

BREAKDOWNS_PATH = os.path.join(DATA_DIR, "breakdowns.json")

# Canonical term->id helper (source of truth for IDs across devices)
HELPER_PATH = os.path.join(DATA_DIR, "helper.json")

# helper.json cache (term<->id mapping)
_helper_cache = {}
_helper_cache_mtime = -1.0
_ID16_RE = re.compile(r"^[0-9a-f]{16}$", re.I)

# Optional AI provider (server-side) for breakdown auto-fill.
# IMPORTANT: Keep API keys on the server (environment variables). Never put them in client-side JS.
OPENAI_API_KEY = (os.environ.get("OPENAI_API_KEY") or "").strip()
OPENAI_MODEL = (os.environ.get("OPENAI_MODEL") or "gpt-4o-mini").strip()
OPENAI_API_BASE = (os.environ.get("OPENAI_API_BASE") or "https://api.openai.com").rstrip("/")

# Gemini (Google AI) optional provider for breakdown autofill
GEMINI_API_KEY = (os.environ.get("GEMINI_API_KEY") or "").strip()
GEMINI_MODEL = (os.environ.get("GEMINI_MODEL") or "gemini-1.5-flash").strip()
GEMINI_API_BASE = (os.environ.get("GEMINI_API_BASE") or "https://generativelanguage.googleapis.com").rstrip("/")

def _init_api_keys_from_encrypted():
    """Load API keys from encrypted file if not set via environment."""
    global OPENAI_API_KEY, OPENAI_MODEL, GEMINI_API_KEY, GEMINI_MODEL
    
    # Only load from encrypted if not already set via environment
    if OPENAI_API_KEY and GEMINI_API_KEY:
        return
    
    try:
        keys = _load_encrypted_api_keys()
        if keys:
            if not OPENAI_API_KEY and keys.get("chatGptKey"):
                OPENAI_API_KEY = keys["chatGptKey"]
                print("[INIT] Loaded OpenAI API key from encrypted storage")
            if keys.get("chatGptModel"):
                OPENAI_MODEL = keys["chatGptModel"]
            if not GEMINI_API_KEY and keys.get("geminiKey"):
                GEMINI_API_KEY = keys["geminiKey"]
                print("[INIT] Loaded Gemini API key from encrypted storage")
            if keys.get("geminiModel"):
                GEMINI_MODEL = keys["geminiModel"]
    except Exception as e:
        print(f"[INIT] Could not load encrypted API keys: {e}")

# -------- Shared Breakdowns (global across all user profiles) --------
def _load_breakdowns() -> Dict[str, Any]:
    """
    Returns a dict keyed by card id (string) with breakdown payloads.
    Stored globally so all profiles can review the same saved breakdowns.
    """
    try:
        if not os.path.exists(BREAKDOWNS_PATH):
            return {}
        with open(BREAKDOWNS_PATH, "r", encoding="utf-8") as f:
            raw = json.load(f)
        if isinstance(raw, dict):
            return raw
    except Exception:
        pass
    return {}

def _save_breakdowns(data: Dict[str, Any]) -> None:
    os.makedirs(DATA_DIR, exist_ok=True)
    tmp = BREAKDOWNS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, BREAKDOWNS_PATH)

# A small curated set of auto-suggestions (optional) – users can edit and save.
AUTO_BREAKDOWNS: Dict[str, Dict[str, Any]] = {
    "taekwondo": {
        "parts": [
            {"part": "Tae", "meaning": "Foot"},
            {"part": "Kwon", "meaning": "Hand / Fist"},
            {"part": "Do", "meaning": "Way"},
        ],
        "literal": "The Way of the Foot and Fist"
    },
    "aikido": {
        "parts": [
            {"part": "Ai", "meaning": "Harmony"},
            {"part": "Ki", "meaning": "Energy / Spirit"},
            {"part": "Do", "meaning": "Way"},
        ],
        "literal": "The Way of Harmonizing Energy"
    },
    "judo": {
        "parts": [
            {"part": "Ju", "meaning": "Gentle / Yielding"},
            {"part": "Do", "meaning": "Way"},
        ],
        "literal": "The Gentle Way"
    },
    "karate": {
        "parts": [
            {"part": "Kara", "meaning": "Empty"},
            {"part": "Te", "meaning": "Hand"},
        ],
        "literal": "Empty Hand"
    }
}



def _extract_json_object(text: str) -> Optional[Dict[str, Any]]:
    """Best-effort extraction of a JSON object from a model response.

    Handles common cases like code fences and extra surrounding text.
    Uses a small brace-matching scanner so we don't accidentally capture
    multiple objects with a greedy regex.
    """
    if not text:
        return None
    text = text.strip()

    # Strip common code fences
    if "```" in text:
        # remove leading/trailing fences while keeping inner content
        text = re.sub(r"```(?:json)?", "", text, flags=re.IGNORECASE).replace("```", "").strip()

    # First try: whole string
    try:
        obj = json.loads(text)
        if isinstance(obj, dict):
            return obj
    except Exception:
        pass

    # Brace-matching scan for first balanced {...}
    start = text.find("{")
    if start == -1:
        return None

    in_str = False
    esc = False
    depth = 0
    end = None
    for i in range(start, len(text)):
        ch = text[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        else:
            if ch == '"':
                in_str = True
                continue
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break

    if end is None:
        return None

    snippet = text[start:end]
    try:
        obj = json.loads(snippet)
        if isinstance(obj, dict):
            return obj
    except Exception:
        return None
    return None


def _responses_output_text(data: dict) -> str:
    """Best-effort extraction of plain text from an OpenAI /v1/responses payload."""
    if not isinstance(data, dict):
        return ""
    if isinstance(data.get("output_text"), str):
        return data.get("output_text")
    out = []
    for item in (data.get("output") or []):
        if not isinstance(item, dict):
            continue
        for c in (item.get("content") or []):
            if not isinstance(c, dict):
                continue
            # Common shapes: {type:"output_text", text:"..."} or {type:"text", text:"..."}
            t = c.get("text")
            if isinstance(t, str) and t.strip():
                out.append(t)
    return "\n".join(out).strip()


def _openai_breakdown(term: str, meaning: str = "", group: str = "") -> Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]]]:
    """Call OpenAI server-side to propose a compound-term breakdown.

    Returns (result, error). result shape: {parts:[{part,meaning}], literal:"..."}
    error shape: {provider:"openai", status:int|None, message:str}
    """
    if not OPENAI_API_KEY:
        return None, {"provider": "openai", "status": None, "message": "OPENAI_API_KEY not set on server"}

    term = (term or "").strip()
    if not term:
        return None, {"provider": "openai", "status": None, "message": "missing term"}

    instructions = (
        "You are a martial-arts terminology assistant. "
        "Given a romanized term (often Japanese/Korean/Chinese), break it into meaningful components "
        "and provide brief English glosses. If you are uncertain about a component, leave its meaning empty "
        "rather than guessing. Output ONLY valid JSON with this shape: "
        "{\"parts\":[{\"part\":string,\"meaning\":string}],\"literal\":string}."
    )

    user_obj = {
        "term": term,
        "group": (group or "").strip(),
        "existing_meaning": (meaning or "").strip(),
        "instructions": "Return only JSON. Prefer 2-6 parts. Use title-case parts as written in the term when possible."
    }

    # Prefer the Responses API per current OpenAI guidance.
    url = f"{OPENAI_API_BASE}/v1/responses"
    payload = {
        "model": OPENAI_MODEL,
        "instructions": instructions,
        "input": json.dumps(user_obj, ensure_ascii=False),
        "max_output_tokens": 250,
    }

    try:
        r = requests.post(
            url,
            headers={"Authorization": f"Bearer {OPENAI_API_KEY}", "Content-Type": "application/json"},
            json=payload,
            timeout=25,
        )
        if r.status_code != 200:
            # Try to parse a helpful error message
            msg = ""
            try:
                j = r.json()
                if isinstance(j, dict):
                    err = j.get("error")
                    if isinstance(err, dict):
                        msg = str(err.get("message") or "")
                    else:
                        msg = str(j.get("message") or "")
            except Exception:
                msg = ""
            if not msg:
                msg = f"OpenAI request failed (HTTP {r.status_code})"
            return None, {"provider": "openai", "status": r.status_code, "message": msg}

        data = r.json() if r.content else {}
        content = _responses_output_text(data)
        obj = _extract_json_object(content or "")
        if not obj:
            return None, {"provider": "openai", "status": 200, "message": "Could not parse JSON from model output"}

        parts = obj.get("parts")
        if not isinstance(parts, list):
            return None, {"provider": "openai", "status": 200, "message": "Model output missing 'parts' list"}

        norm_parts = []
        for p2 in parts[:10]:
            if not isinstance(p2, dict):
                continue
            part = str(p2.get("part") or "").strip()
            mean = str(p2.get("meaning") or "").strip()
            if part or mean:
                norm_parts.append({"part": part, "meaning": mean})

        literal = str(obj.get("literal") or "").strip()
        return {"parts": norm_parts, "literal": literal}, None

    except Exception as e:
        return None, {"provider": "openai", "status": None, "message": f"OpenAI error: {e.__class__.__name__}"}


def _gemini_breakdown(term: str, meaning: str = "", group: str = "") -> Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]]]:
    """Call Gemini server-side to propose a compound-term breakdown.

    Returns (result, error). result shape: {parts:[{part,meaning}], literal:"..."}
    error shape: {provider:"gemini", status:int|None, message:str}
    """
    if not GEMINI_API_KEY:
        return None, {"provider": "gemini", "status": None, "message": "GEMINI_API_KEY not set on server"}

    term = (term or "").strip()
    if not term:
        return None, {"provider": "gemini", "status": None, "message": "missing term"}

    instructions = (
        "You are a martial-arts terminology assistant. "
        "Given a romanized term (often Japanese/Korean/Chinese), break it into meaningful components "
        "and provide brief English glosses. If you are uncertain about a component, leave its meaning empty "
        "rather than guessing. Output ONLY valid JSON with this shape: "
        '{"parts":[{"part":"<string>","meaning":"<string>"}],"literal":"<string>"}.'
    )

    user_obj = {
        "term": term,
        "group": (group or "").strip(),
        "existing_meaning": (meaning or "").strip(),
        "instructions": "Return only JSON. Prefer 2-6 parts. Use title-case parts as written in the term when possible."
    }

    # Gemini REST: generateContent
    url = f"{GEMINI_API_BASE}/v1beta/models/{GEMINI_MODEL}:generateContent"
    payload = {
        "contents": [{
            "role": "user",
            "parts": [{"text": instructions + "\n\nINPUT:\n" + json.dumps(user_obj, ensure_ascii=False)}]
        }]
    }

    try:
        r = requests.post(
            url,
            headers={"Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY},
            json=payload,
            timeout=25,
        )
        if r.status_code != 200:
            msg = ""
            try:
                j = r.json()
                if isinstance(j, dict):
                    err = j.get("error")
                    if isinstance(err, dict):
                        msg = str(err.get("message") or "")
                    else:
                        msg = str(j.get("message") or "")
            except Exception:
                msg = ""
            if not msg:
                msg = f"Gemini request failed (HTTP {r.status_code})"
            return None, {"provider": "gemini", "status": r.status_code, "message": msg}

        data = r.json() if r.content else {}
        text = ""
        try:
            cands = data.get("candidates") or []
            if cands and isinstance(cands[0], dict):
                content = cands[0].get("content") or {}
                parts = content.get("parts") or []
                if parts and isinstance(parts[0], dict):
                    text = str(parts[0].get("text") or "")
        except Exception:
            text = ""

        obj = _extract_json_object(text or "")
        if not obj:
            return None, {"provider": "gemini", "status": 200, "message": "Could not parse JSON from model output"}

        parts = obj.get("parts")
        if not isinstance(parts, list):
            return None, {"provider": "gemini", "status": 200, "message": "Model output missing 'parts' list"}

        norm_parts = []
        for p2 in parts[:10]:
            if not isinstance(p2, dict):
                continue
            part = str(p2.get("part") or "").strip()
            mean = str(p2.get("meaning") or "").strip()
            if part or mean:
                norm_parts.append({"part": part, "meaning": mean})

        literal = str(obj.get("literal") or "").strip()
        return {"parts": norm_parts, "literal": literal}, None

    except Exception as e:
        return None, {"provider": "gemini", "status": None, "message": f"Gemini error: {e.__class__.__name__}"}


def _call_openai_chat(prompt: str, model: str, api_key: str) -> str:
    """Simple OpenAI chat completion for generating text responses."""
    if not api_key:
        raise Exception("OpenAI API key not configured")
    
    url = f"{OPENAI_API_BASE}/v1/chat/completions"
    
    # Use higher token limit for card generation (detect from prompt)
    max_tokens = 4000 if "flashcard" in prompt.lower() or "cards" in prompt.lower() else 500
    
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": max_tokens,
        "temperature": 0.7
    }
    
    r = requests.post(
        url,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    
    if r.status_code != 200:
        try:
            err = r.json().get("error", {}).get("message", f"HTTP {r.status_code}")
        except:
            err = f"HTTP {r.status_code}"
        raise Exception(f"OpenAI error: {err}")
    
    data = r.json()
    choices = data.get("choices", [])
    if choices and isinstance(choices[0], dict):
        return choices[0].get("message", {}).get("content", "")
    return ""


def _call_gemini_chat(prompt: str, model: str, api_key: str) -> str:
    """Simple Gemini chat for generating text responses."""
    if not api_key:
        raise Exception("Gemini API key not configured")
    
    url = f"{GEMINI_API_BASE}/v1beta/models/{model}:generateContent"
    
    # Use higher token limit for card generation
    max_tokens = 4000 if "flashcard" in prompt.lower() or "cards" in prompt.lower() else 500
    
    payload = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "maxOutputTokens": max_tokens,
            "temperature": 0.7
        }
    }
    
    r = requests.post(
        url,
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        json=payload,
        timeout=60
    )
    
    if r.status_code != 200:
        try:
            err = r.json().get("error", {}).get("message", f"HTTP {r.status_code}")
        except:
            err = f"HTTP {r.status_code}"
        raise Exception(f"Gemini error: {err}")
    
    data = r.json()
    try:
        cands = data.get("candidates", [])
        if cands and isinstance(cands[0], dict):
            content = cands[0].get("content", {})
            parts = content.get("parts", [])
            if parts and isinstance(parts[0], dict):
                return parts[0].get("text", "")
    except:
        pass
    return ""


USERS_DIR = os.path.join(DATA_DIR, "users")
PROFILES_PATH = os.path.join(DATA_DIR, "profiles.json")
SECRET_PATH = os.path.join(DATA_DIR, "secret_key.txt")
API_KEYS_PATH = os.path.join(DATA_DIR, "api_keys.enc")  # Encrypted API keys storage
ADMIN_USERS_PATH = os.path.join(DATA_DIR, "admin_users.json")  # Admin users SoT

def _load_admin_usernames() -> set:
    """Load admin usernames from admin_users.json (Source of Truth)."""
    try:
        if os.path.exists(ADMIN_USERS_PATH):
            with open(ADMIN_USERS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                usernames = data.get("admin_usernames", [])
                return {u.lower() for u in usernames}
    except Exception as e:
        print(f"[WARN] Could not load admin_users.json: {e}")
    # Fallback to hardcoded default
    return {"sidscri"}

# Admin users who can manage API keys (loaded from admin_users.json)
ADMIN_USERNAMES = _load_admin_usernames()

PORT = int(os.environ.get("KENPO_WEB_PORT", "8009"))
app = Flask(__name__, static_folder="static")

os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(USERS_DIR, exist_ok=True)


def _load_or_create_secret() -> str:
    if os.path.exists(SECRET_PATH):
        try:
            with open(SECRET_PATH, "r", encoding="utf-8") as f:
                s = f.read().strip()
                if s:
                    return s
        except Exception:
            pass
    s = uuid.uuid4().hex + uuid.uuid4().hex
    with open(SECRET_PATH, "w", encoding="utf-8") as f:
        f.write(s)
    return s


# -------- API Key Encryption (for safe storage/git commit) --------
import base64
import hmac

def _derive_encryption_key(secret: str) -> bytes:
    """Derive a 32-byte key from the secret using SHA-256."""
    return hashlib.sha256(secret.encode('utf-8')).digest()

def _xor_encrypt(data: bytes, key: bytes) -> bytes:
    """Simple XOR encryption (sufficient for API keys with a strong secret)."""
    return bytes(d ^ key[i % len(key)] for i, d in enumerate(data))

def _encrypt_api_keys(keys_dict: dict, secret: str) -> str:
    """Encrypt API keys dict to a base64 string."""
    json_data = json.dumps(keys_dict, ensure_ascii=False)
    key = _derive_encryption_key(secret)
    encrypted = _xor_encrypt(json_data.encode('utf-8'), key)
    # Add HMAC for integrity check
    mac = hmac.new(key, encrypted, hashlib.sha256).digest()
    return base64.b64encode(mac + encrypted).decode('ascii')

def _decrypt_api_keys(encrypted_str: str, secret: str) -> Optional[dict]:
    """Decrypt API keys from base64 string. Returns None if invalid."""
    try:
        raw = base64.b64decode(encrypted_str)
        if len(raw) < 32:
            return None
        mac_stored = raw[:32]
        encrypted = raw[32:]
        key = _derive_encryption_key(secret)
        # Verify HMAC
        mac_computed = hmac.new(key, encrypted, hashlib.sha256).digest()
        if not hmac.compare_digest(mac_stored, mac_computed):
            return None
        decrypted = _xor_encrypt(encrypted, key)
        return json.loads(decrypted.decode('utf-8'))
    except Exception:
        return None

def _save_encrypted_api_keys(keys_dict: dict) -> bool:
    """Save encrypted API keys to file."""
    try:
        secret = _load_or_create_secret()
        encrypted = _encrypt_api_keys(keys_dict, secret)
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(API_KEYS_PATH, "w", encoding="utf-8") as f:
            f.write(encrypted)
        return True
    except Exception as e:
        print(f"[ERROR] Failed to save API keys: {e}")
        return False

def _load_encrypted_api_keys() -> dict:
    """Load encrypted API keys from file."""
    if not os.path.exists(API_KEYS_PATH):
        return {}
    try:
        secret = _load_or_create_secret()
        with open(API_KEYS_PATH, "r", encoding="utf-8") as f:
            encrypted = f.read().strip()
        if not encrypted:
            return {}
        result = _decrypt_api_keys(encrypted, secret)
        return result if result else {}
    except Exception as e:
        print(f"[ERROR] Failed to load API keys: {e}")
        return {}

def _is_admin_user(username: str) -> bool:
    """Check if username is an admin."""
    return username.lower() in {u.lower() for u in ADMIN_USERNAMES}

# Initialize API keys from encrypted storage at startup
_init_api_keys_from_encrypted()

app.secret_key = os.environ.get("KENPO_SECRET_KEY", "") or _load_or_create_secret()
# ----------------------------
# Version + request audit log
# ----------------------------
VERSION_FILE = os.path.join(app.root_path, "version.json")
_VERSION_CACHE = None
_VERSION_MTIME = 0.0

def get_version():
    """Load version.json with a tiny mtime-based cache."""
    global _VERSION_CACHE, _VERSION_MTIME
    try:
        st = os.stat(VERSION_FILE)
        if _VERSION_CACHE is None or st.st_mtime != _VERSION_MTIME:
            with open(VERSION_FILE, "r", encoding="utf-8") as f:
                _VERSION_CACHE = json.load(f)
            _VERSION_MTIME = st.st_mtime
    except Exception:
        return {"name": "KenpoFlashcardsWebServer", "version": "unknown", "build": "unknown"}
    return _VERSION_CACHE or {"name": "KenpoFlashcardsWebServer", "version": "unknown", "build": "unknown"}

# Optional allowlist: set env var KENPO_ALLOWED_IPS="1.2.3.4,5.6.7.8"
ALLOWED_IPS = {ip.strip() for ip in os.environ.get("KENPO_ALLOWED_IPS", "").split(",") if ip.strip()}

@app.before_request
def _access_log_and_optional_allowlist():
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    ua = (request.headers.get("User-Agent") or "").strip()
    uid = session.get("user_id")
    uname = (_get_user(uid) or {}).get("username") if uid else "-"
    if ALLOWED_IPS and ip not in ALLOWED_IPS:
        print(f"[BLOCK] ip={ip} user={uname} {request.method} {request.path} ua={ua[:120]}")
        return ("Forbidden", 403)
    print(f"[REQ] ip={ip} user={uname} {request.method} {request.path} ua={ua[:120]}")

_cards_cache: List[Dict[str, Any]] = []
_cards_cache_mtime: float = -1.0


def _now() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


def _stable_id(group: str, subgroup: str, term: str, meaning: str, pron: str) -> str:
    base = f"{group}||{subgroup}||{term}||{meaning}||{pron}".encode("utf-8")
    return hashlib.sha1(base).hexdigest()[:16]


def _load_json_file(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _get_first(d: Dict[str, Any], keys: List[str]) -> Any:
    for k in keys:
        if k in d and d[k] is not None:
            return d[k]
    return None


def _normalize_cards(raw: Any) -> List[Dict[str, Any]]:
    cards: List[Dict[str, Any]] = []

    def add_card(group: str, item: Dict[str, Any]):
        term = _get_first(item, ["term", "word", "vocab", "name", "kenpo", "title", "front"])
        meaning = _get_first(item, ["meaning", "definition", "desc", "description", "english", "translation", "details", "back"])
        pron = _get_first(item, ["pron", "pronunciation", "phonetic"]) or ""
        subgroup = _get_first(item, ["subgroup", "sub_group", "subCategory", "subcategory", "subSection", "subsection"]) or ""
        if not term or not meaning:
            return

        group_s = str(group).strip()
        subgroup_s = str(subgroup).strip()
        term_s = str(term).strip()
        meaning_s = str(meaning).strip()
        pron_s = str(pron).strip()

        cid = item.get("id") or _stable_id(group_s, subgroup_s, term_s, meaning_s, pron_s)

        cards.append({
            "id": str(cid),
            "group": group_s,
            "subgroup": subgroup_s,
            "term": term_s,
            "meaning": meaning_s,
            "pron": pron_s
        })

    if isinstance(raw, list):
        for item in raw:
            if isinstance(item, dict):
                group = _get_first(item, ["group", "section", "category", "stack"]) or "General"
                add_card(group, item)
        return cards

    if isinstance(raw, dict):
        if isinstance(raw.get("groups"), list):
            for g in raw["groups"]:
                if not isinstance(g, dict):
                    continue
                gname = _get_first(g, ["name", "group", "section", "category", "title"]) or "General"
                gcards = g.get("cards") or g.get("items") or g.get("words") or []
                if isinstance(gcards, list):
                    for item in gcards:
                        if isinstance(item, dict):
                            add_card(gname, item)
            return cards

        if isinstance(raw.get("sections"), dict):
            for gname, arr in raw["sections"].items():
                if isinstance(arr, list):
                    for item in arr:
                        if isinstance(item, dict):
                            add_card(gname, item)
            return cards

        dict_like_groups = 0
        for _, v in raw.items():
            if isinstance(v, list) and v and isinstance(v[0], dict):
                dict_like_groups += 1
        if dict_like_groups > 0:
            for gname, arr in raw.items():
                if isinstance(arr, list):
                    for item in arr:
                        if isinstance(item, dict):
                            add_card(gname, item)
            return cards

    return cards


def load_cards_cached() -> Tuple[List[Dict[str, Any]], str]:
    global _cards_cache, _cards_cache_mtime
    if not os.path.exists(KENPO_JSON_PATH):
        return [], f"kenpo_words.json not found at: {KENPO_JSON_PATH}"

    mtime = os.path.getmtime(KENPO_JSON_PATH)
    if mtime != _cards_cache_mtime:
        raw = _load_json_file(KENPO_JSON_PATH)
        _cards_cache = _normalize_cards(raw)
        _cards_cache_mtime = mtime
    return _cards_cache, "ok"

def _build_helper(cards: List[Dict[str, Any]], kenpo_mtime: float) -> Dict[str, Any]:
    """
    Build canonical mapping for cross-device ID consistency.

    term_key = lower(trim(term))

    If duplicate terms exist, term_to_id[term_key] becomes a list of ids.
    """
    term_to_id: Dict[str, Any] = {}
    id_to_term: Dict[str, str] = {}
    cards_by_id: Dict[str, Any] = {}

    for c in cards:
        term = str(c.get("term", "")).strip()
        if not term:
            continue
        term_key = term.lower()
        cid = str(c.get("id", "")).strip()
        if not cid:
            continue

        cards_by_id[cid] = {
            "id": cid,
            "group": c.get("group", ""),
            "subgroup": c.get("subgroup", ""),
            "term": term,
            "meaning": c.get("meaning", ""),
            "pron": c.get("pron", ""),
            "status": c.get("status", "active"),
        }
        id_to_term[cid] = term_key

        if term_key not in term_to_id:
            term_to_id[term_key] = cid
        else:
            existing = term_to_id[term_key]
            if isinstance(existing, list):
                if cid not in existing:
                    existing.append(cid)
            else:
                if cid != existing:
                    term_to_id[term_key] = [existing, cid]

    flat = []
    for k in sorted(term_to_id.keys()):
        v = term_to_id[k]
        if isinstance(v, list):
            flat.append(f"{k}=" + ",".join(sorted(v)))
        else:
            flat.append(f"{k}={v}")
    base = ("\n".join(flat) + f"\nkenpo_mtime={kenpo_mtime}").encode("utf-8")
    version = hashlib.sha1(base).hexdigest()[:12]

    return {
        "version": version,
        "kenpo_mtime": kenpo_mtime,
        "generated_at": int(time.time()),
        "term_to_id": term_to_id,
        "id_to_term": id_to_term,
        "cards": cards_by_id,
    }


def load_helper_cached() -> Tuple[Dict[str, Any], str]:
    """Cache helper.json and rebuild automatically when kenpo_words.json changes."""
    global _helper_cache, _helper_cache_mtime

    if not os.path.exists(KENPO_JSON_PATH):
        return {}, f"kenpo_words.json not found at: {KENPO_JSON_PATH}"

    kenpo_mtime = os.path.getmtime(KENPO_JSON_PATH)

    helper_exists = os.path.exists(HELPER_PATH)
    helper_mtime = os.path.getmtime(HELPER_PATH) if helper_exists else -1.0

    rebuild = (not helper_exists) or (_helper_cache_mtime != helper_mtime)

    if not rebuild and _helper_cache:
        stored = float(_helper_cache.get("kenpo_mtime", -1))
        if stored != kenpo_mtime:
            rebuild = True

    if rebuild:
        cards, status = load_cards_cached()
        if status != "ok":
            return {}, status
        helper = _build_helper(cards, kenpo_mtime)
        os.makedirs(DATA_DIR, exist_ok=True)
        tmp = HELPER_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(helper, f, ensure_ascii=False, indent=2)
        os.replace(tmp, HELPER_PATH)
        _helper_cache = helper
        _helper_cache_mtime = os.path.getmtime(HELPER_PATH)

    return _helper_cache, "ok"


def _canonical_id_for_term(term: str, group: str = "", subgroup: str = "") -> Optional[str]:
    """Resolve canonical id for a term using helper.json."""
    helper, status = load_helper_cached()
    if status != "ok":
        return None

    term_key = str(term or "").strip().lower()
    if not term_key:
        return None

    mapping = helper.get("term_to_id", {})
    v = mapping.get(term_key)
    if not v:
        return None

    if isinstance(v, list):
        g = str(group or "").strip()
        sg = str(subgroup or "").strip()
        if g or sg:
            cards = helper.get("cards", {})
            for cid in v:
                c = cards.get(cid) or {}
                if (str(c.get("group","")).strip() == g) and (str(c.get("subgroup","")).strip() == sg):
                    return cid
        return v[0]
    return str(v)


def _default_settings() -> Dict[str, Any]:
    return {
        "all": {
            "randomize": False,
            # Default OFF (user can enable to link Unlearned/Unsure/Learned study tabs)
            "link_randomize_study_tabs": False,
            "randomize_unlearned": False,
            "randomize_unsure": False,
            "randomize_learned_study": False,
            "show_group_label": False,
            "show_subgroup_label": False,
            "reverse_faces": False,
            # When enabled, study cards will show a saved term breakdown (parts + literal meaning)
            # on the definition side (meaning side). If a card has no saved breakdown, nothing is shown.
            "show_breakdown_on_definition": True,
            "breakdown_apply_all_tabs": False,
            "breakdown_remove_all_tabs": False,
            "breakdown_remove_unlearned": False,
            "breakdown_remove_unsure": False,
            "breakdown_remove_learned_study": False,
            "show_definitions_all_list": True,
            "show_definitions_learned_list": True,
            "all_list_show_unlearned_unsure_buttons": True,
            "learned_list_show_relearn_unsure_buttons": False,
            "learned_list_show_group_label": False,
            "all_mode": "flat",
            "group_order": "alpha",
            "card_order": "json",
            # Default to OpenAI only (Gemini optional)
            "breakdown_ai_provider": "openai",
            # Voice/Speech settings
            "speech_rate": 1.0,
            "speech_voice": "",
            "auto_speak_on_card_change": False,
            "speak_definition_on_flip": False,
            # Custom Set settings
            "custom_set_random_order": False,
            "custom_set_reverse_cards": False,
            "custom_set_show_group_label": False,
            "custom_set_show_breakdown": True,
        },
        "groups": {},
        # Custom Set card IDs (starred cards)
        "custom_set": [],
        # Custom Set internal status tracking
        "custom_set_status": {}
    }


# -------- Profiles / Users --------
def _load_profiles() -> Dict[str, Any]:
    if not os.path.exists(PROFILES_PATH):
        return {"users": {}}
    try:
        with open(PROFILES_PATH, "r", encoding="utf-8") as f:
            p = json.load(f)
        if not isinstance(p, dict):
            return {"users": {}}
        p.setdefault("users", {})
        # Remove old ip_map if present (no longer used)
        p.pop("ip_map", None)
        return p
    except Exception:
        return {"users": {}}


def _migrate_settings_obj(obj: Dict[str, Any]) -> Dict[str, Any]:
    """Migrate older settings keys to the current schema (in-place-ish)."""
    if not isinstance(obj, dict):
        return obj
    # Legacy: show_breakdown_on_definition (bool) -> new inverted per-tab remove flags
    if "show_breakdown_on_definition" in obj and "breakdown_apply_all_tabs" not in obj:
        show = bool(obj.get("show_breakdown_on_definition"))
        # Preserve legacy behavior: if it was OFF (False), hide breakdown everywhere; if ON, show everywhere.
        obj["breakdown_apply_all_tabs"] = True
        obj["breakdown_remove_all_tabs"] = (not show)
        obj["breakdown_remove_unlearned"] = (not show)
        obj["breakdown_remove_unsure"] = (not show)
        obj["breakdown_remove_learned_study"] = (not show)
        obj.pop("show_breakdown_on_definition", None)
    return obj


def _migrate_settings(settings: Dict[str, Any]) -> Dict[str, Any]:
    """Migrate stored __settings__ structure."""
    if not isinstance(settings, dict):
        return settings
    if "all" in settings and isinstance(settings["all"], dict):
        settings["all"] = _migrate_settings_obj(settings["all"])
    if "groups" in settings and isinstance(settings["groups"], dict):
        for k, v in list(settings["groups"].items()):
            if isinstance(v, dict):
                settings["groups"][k] = _migrate_settings_obj(v)
    return settings


def _save_profiles(p: Dict[str, Any]) -> None:
    with open(PROFILES_PATH, "w", encoding="utf-8") as f:
        json.dump(p, f, ensure_ascii=False, indent=2)


def _get_user(user_id: str) -> Optional[Dict[str, Any]]:
    profiles = _load_profiles()
    u = profiles.get("users", {}).get(user_id)
    if not isinstance(u, dict):
        return None
    out = dict(u)
    out["id"] = user_id
    # Don't expose password hash
    out.pop("password_hash", None)
    return out


def _get_user_by_username(username: str) -> Optional[Tuple[str, Dict[str, Any]]]:
    """Returns (user_id, user_dict) or None if not found."""
    profiles = _load_profiles()
    username_lower = username.lower()
    for uid, udata in profiles.get("users", {}).items():
        if isinstance(udata, dict) and udata.get("username", "").lower() == username_lower:
            return uid, udata
    return None


def _progress_path(user_id: str) -> str:
    udir = os.path.join(USERS_DIR, user_id)
    os.makedirs(udir, exist_ok=True)
    return os.path.join(udir, "progress.json")


def _ensure_user_progress(user_id: str) -> None:
    path = _progress_path(user_id)
    if not os.path.exists(path):
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"__settings__": _default_settings()}, f, ensure_ascii=False, indent=2)


def current_user_id() -> Optional[str]:
    uid = session.get("user_id")
    if uid and _get_user(uid):
        return uid
    return None


def require_user() -> Tuple[Optional[str], Optional[Dict[str, Any]]]:
    uid = current_user_id()
    if uid:
        return uid, None
    return None, None


# -------- Progress per user --------
def load_progress(user_id: str) -> Dict[str, Any]:
    path = _progress_path(user_id)
    if not os.path.exists(path):
        return {"__settings__": _default_settings()}

    try:
        with open(path, "r", encoding="utf-8") as f:
            p = json.load(f)
    except json.JSONDecodeError:
        p = {}

    for k, v in list(p.items()):
        if k.startswith("__"):
            continue
        if isinstance(v, dict) and "status" not in v and "learned" in v:
            p.setdefault(k, {})
            p[k]["status"] = "learned" if v.get("learned") else "active"
            if "learned_at" in v:
                p[k]["updated_at"] = v["learned_at"]

    if "__settings__" not in p or not isinstance(p["__settings__"], dict):
        p["__settings__"] = _default_settings()
    else:
        defaults = _default_settings()
        p["__settings__"].setdefault("all", defaults["all"])
        p["__settings__"].setdefault("groups", defaults["groups"])
        p["__settings__"]["all"].setdefault("show_group_label", False)
        p["__settings__"]["all"].setdefault("show_subgroup_label", False)
        p["__settings__"]["all"].setdefault("all_mode", "flat")
        p["__settings__"]["all"].setdefault("breakdown_ai_provider", "openai")

    return p


def save_progress(user_id: str, p: Dict[str, Any]) -> None:
    with open(_progress_path(user_id), "w", encoding="utf-8") as f:
        json.dump(p, f, ensure_ascii=False, indent=2)


def card_status(progress: Dict[str, Any], card_id: str) -> str:
    entry = progress.get(card_id)
    if isinstance(entry, dict):
        s = entry.get("status")
        if s in ("active", "unsure", "learned", "deleted"):
            return s
    return "active"


def set_card_status(progress: Dict[str, Any], card_id: str, status: str) -> None:
    progress.setdefault(card_id, {})
    progress[card_id]["status"] = status
    progress[card_id]["updated_at"] = _now()


# -------- Routes --------
@app.get("/")
def index():
    return send_from_directory("static", "index.html")


@app.get("/api/me")
def api_me():
    uid = current_user_id()
    if uid:
        return jsonify({"logged_in": True, "user": _get_user(uid)})
    return jsonify({"logged_in": False, "user": None})


@app.post("/api/register")
def api_register():
    data = request.get_json(force=True) or {}
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "")
    display_name = (data.get("display_name") or "").strip()

    if not username or not password:
        return jsonify({"error": "username_and_password_required"}), 400

    if len(username) < 3:
        return jsonify({"error": "username_too_short"}), 400

    if len(password) < 4:
        return jsonify({"error": "password_too_short"}), 400

    # Check if username already exists
    if _get_user_by_username(username):
        return jsonify({"error": "username_taken"}), 400

    profiles = _load_profiles()
    user_id = uuid.uuid4().hex[:12]
    
    profiles["users"][user_id] = {
        "username": username,
        "password_hash": generate_password_hash(password),
        "display_name": display_name or username,
        "created_at": _now()
    }

    _save_profiles(profiles)
    _ensure_user_progress(user_id)
    session["user_id"] = user_id
    return jsonify({"ok": True, "user": _get_user(user_id)})


@app.post("/api/login")
def api_login():
    data = request.get_json(force=True) or {}
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "")

    if not username:
        return jsonify({"error": "username_required"}), 400

    # Allow admin LAN/localhost login even with blank password (personal deployment convenience).
    # If you prefer strict security, set a password for the admin user in profiles.json.
    is_admin = _is_admin_user(username)
    remote_ip = request.remote_addr or ""
    from_private_net = remote_ip in ("127.0.0.1", "::1") or remote_ip.startswith("192.168.") or remote_ip.startswith("10.") or remote_ip.startswith("172.")

    result = _get_user_by_username(username)

    if not result:
        # If admin exists in admin_users.json but profile missing, bootstrap a profile on private networks.
        if is_admin and from_private_net:
            profiles = _load_profiles()
            user_id = uuid.uuid4().hex[:12]
            profiles.setdefault("users", {})[user_id] = {
                "user_id": user_id,
                "username": username,
                "password_hash": generate_password_hash(password) if password else "",
                "display_name": username,
                "created_at": _now(),
            }
            _save_profiles(profiles)
            _ensure_user_progress(user_id)
            session["user_id"] = user_id
            return jsonify({"ok": True, "user": _get_user(user_id), "bootstrap_admin": True})
        return jsonify({"error": "invalid_credentials"}), 401

    user_id, user_data = result
    stored_hash = (user_data.get("password_hash") or "").strip()

    # Admin LAN login with blank password: accept (even if a password_hash exists)
    if is_admin and from_private_net and password == "":
        _ensure_user_progress(user_id)
        session["user_id"] = user_id
        return jsonify({"ok": True, "user": _get_user(user_id), "admin_lan_override": True})

    # Normal password verification
    if not stored_hash:
        return jsonify({"error": "password_required"}), 401

    if not check_password_hash(stored_hash, password):
        log_activity("warn", f"Failed login attempt for user: {username}", "")
        return jsonify({"error": "invalid_credentials"}), 401

    _ensure_user_progress(user_id)
    session["user_id"] = user_id
    log_activity("info", f"User logged in: {username}", username)
    return jsonify({"ok": True, "user": _get_user(user_id)})


@app.post("/api/logout")
def api_logout():
    uid = current_user_id()
    user = _get_user(uid) if uid else None
    username = user.get("username", "") if user else ""
    session.pop("user_id", None)
    if username:
        log_activity("info", f"User logged out: {username}", username)
    return jsonify({"ok": True})


@app.get("/api/health")
def health():
    cards, status = load_cards_cached()
    v = get_version()
    kenpo_exists = os.path.exists(KENPO_JSON_PATH)
    return jsonify({
        "status": status, 
        "cards_loaded": len(cards), 
        "server_time": _now(), 
        "version": v.get("version"), 
        "build": v.get("build"),
        "kenpo_json_exists": kenpo_exists,
        "kenpo_json_path": KENPO_JSON_PATH
    })


@app.get("/api/version")
def api_version():
    return jsonify(get_version())
@app.get("/api/groups")
def api_groups():
    uid, _ = require_user()
    deck_id = request.args.get("deck_id", "")
    
    # Get active deck from settings if not specified
    if uid and not deck_id:
        progress = load_progress(uid)
        settings = progress.get("__settings__", _default_settings())
        deck_id = _get_active_deck_id(settings, "kenpo")
    
    if not deck_id or deck_id == "kenpo":
        # Built-in Kenpo deck
        cards, status = load_cards_cached()
        if status != "ok":
            return jsonify({"error": status}), 500
        all_cards = list(cards)
        # Also include user cards for kenpo deck
        if uid:
            user_cards = _load_user_cards(uid)
            kenpo_user_cards = [c for c in user_cards if c.get("deckId", "kenpo") == "kenpo"]
            all_cards = all_cards + kenpo_user_cards
    else:
        # User-created deck
        if not uid:
            return jsonify([])
        user_cards = _load_user_cards(uid)
        all_cards = [c for c in user_cards if c.get("deckId") == deck_id]
    
    # Get unique groups
    groups = sorted({c.get("group", "") for c in all_cards if c.get("group")})
    return jsonify(groups)

import datetime

@app.get("/about")
def about_page():
    return send_from_directory("static", "about.html")

@app.get("/admin")
def admin_page():
    return send_from_directory("static", "admin.html")

@app.get("/user-guide")
def user_guide_page():
    return send_from_directory("static", "user-guide.html")

@app.get("/user-guide.pdf")
def user_guide_pdf():
    """Serve a printable User Guide PDF (generated on demand)."""
    try:
        from reportlab.lib.pagesizes import letter
        from reportlab.pdfgen import canvas
        from reportlab.lib.units import inch
        from io import BytesIO
    except ImportError as e:
        # Return a helpful HTML page instead of a blank error
        return f"""
        <html><body style="font-family: sans-serif; padding: 40px; background: #1a1f2e; color: #fff;">
        <h1>PDF Generation Unavailable</h1>
        <p>The reportlab library is not installed on this server.</p>
        <p>To enable PDF downloads, run: <code>pip install reportlab</code></p>
        <p><a href="/user-guide" style="color: #5ca5ff;">← View User Guide (HTML)</a></p>
        <p><a href="/" style="color: #5ca5ff;">← Back to App</a></p>
        </body></html>
        """, 500

    try:
        v = get_version()
        title = f"Advanced Flashcards WebApp — User Guide  (v{v.get('version','')}, build {v.get('build','')})"
        lines = [
            "Created by Sidney Shelton (Sidscri@yahoo.com)",
            "",
            "Overview:",
            "• Study Kenpo vocabulary and track progress across devices.",
            "• Tabs: Unlearned / Unsure / Learned / All / Custom Set.",
            "• Group filtering and All Cards mode.",
            "• Sync: progress + breakdowns between Android and Web.",
            "• AI Breakdowns (optional): OpenAI/Gemini configured server-side.",
            "",
            "How to use:",
            "1) Choose a tab (Unlearned/Unsure/Learned/All).",
            "2) Use Group dropdown or All Cards button to set your filter.",
            "3) Use status buttons to move a card between states.",
            "4) Use Search to jump to a term quickly.",
            "5) Use Breakdown tools to view or generate a breakdown.",
            "6) Use Sync to push/pull progress and pull breakdowns on other devices.",
            "",
            "Keyboard Shortcuts:",
            "• Space/Enter: Flip card",
            "• Arrow keys: Navigate cards",
            "• 1/2/3: Mark as Didn't Get It / Unsure / Got It",
            "",
            "Troubleshooting:",
            "• If sync seems stuck: logout/login and pull again.",
            "• Ensure server is running and reachable from your device.",
            "• Visit /admin for diagnostics.",
        ]

        buf = BytesIO()
        c = canvas.Canvas(buf, pagesize=letter)
        width, height = letter

        x = 0.8 * inch
        y = height - 1.0 * inch
        c.setTitle("Advanced Flashcards WebApp User Guide")
        c.setFont("Helvetica-Bold", 14)
        c.drawString(x, y, title)
        y -= 0.4 * inch

        c.setFont("Helvetica", 11)
        for ln in lines:
            if y < 0.8 * inch:
                c.showPage()
                y = height - 1.0 * inch
                c.setFont("Helvetica", 11)
            c.drawString(x, y, ln)
            y -= 0.22 * inch

        c.showPage()
        c.save()
        buf.seek(0)

        return send_file(buf, mimetype="application/pdf", as_attachment=True, download_name="KenpoFlashcards_User_Guide.pdf")
    except Exception as e:
        return f"""
        <html><body style="font-family: sans-serif; padding: 40px; background: #1a1f2e; color: #fff;">
        <h1>PDF Generation Error</h1>
        <p>Error: {str(e)}</p>
        <p><a href="/user-guide" style="color: #5ca5ff;">← View User Guide (HTML)</a></p>
        <p><a href="/" style="color: #5ca5ff;">← Back to App</a></p>
        </body></html>
        """, 500

@app.get("/api/whoami")
def whoami():
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    return jsonify({
        "ip": ip,
        "ua": request.headers.get("User-Agent", "-"),
        "time": datetime.datetime.utcnow().isoformat() + "Z",
    })

def _get_active_deck_id(settings_obj: Any, fallback: str = "kenpo") -> str:
    """Return the active deck id from __settings__ in either the new schema (settings['all']) or legacy flat schema."""
    try:
        if isinstance(settings_obj, dict):
            if isinstance(settings_obj.get("all"), dict):
                v = (settings_obj.get("all") or {}).get("activeDeckId")
                if v:
                    return str(v)
            v = settings_obj.get("activeDeckId")
            if v:
                return str(v)
    except Exception:
        pass
    return fallback


def _set_active_deck_id(settings_obj: Dict[str, Any], deck_id: str) -> Dict[str, Any]:
    """Set activeDeckId in the new schema, preserving other settings."""
    settings_obj = _migrate_settings(settings_obj or _default_settings())
    if not isinstance(settings_obj, dict):
        settings_obj = _default_settings()
    if "all" not in settings_obj or not isinstance(settings_obj.get("all"), dict):
        settings_obj = _default_settings()
    settings_obj["all"]["activeDeckId"] = deck_id
    # Remove legacy top-level key if present
    settings_obj.pop("activeDeckId", None)
    return settings_obj

@app.get("/api/settings")
def api_settings_get():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    progress = load_progress(uid)
    scope = request.args.get("scope", "all")
    settings = _migrate_settings(progress.get("__settings__", _default_settings()))

    if scope == "all":
        # Ensure we always have a valid activeDeckId.
        # If missing/invalid, fall back to the user's default deck (isDefault) or Kenpo.
        try:
            decks = _load_decks(user_id=uid)
            accessible_ids = {d.get("id") for d in decks if d.get("id")}
            current_active = None
            if isinstance(settings, dict) and isinstance(settings.get("all"), dict):
                current_active = (settings.get("all") or {}).get("activeDeckId")

            if (not current_active) or (accessible_ids and current_active not in accessible_ids):
                default_id = None
                for d in decks:
                    if d.get("isDefault"):
                        default_id = d.get("id")
                        break
                if not default_id:
                    default_id = "kenpo" if ("kenpo" in accessible_ids or not accessible_ids) else next(iter(accessible_ids))
                settings = _set_active_deck_id(settings, default_id)
                progress["__settings__"] = settings
                save_progress(uid, progress)
        except Exception:
            # Never block settings read; just return what we have.
            pass

        return jsonify({"scope": "all", "settings": settings["all"]})

    return jsonify({"scope": scope, "settings": settings["groups"].get(scope) or {}})


@app.post("/api/settings")
def api_settings_set():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    data = request.get_json(force=True) or {}
    scope = data.get("scope", "all")
    patch = data.get("settings", {})
    if not isinstance(patch, dict):
        return jsonify({"error": "settings must be an object"}), 400

    progress = load_progress(uid)
    settings = _migrate_settings(progress.get("__settings__", _default_settings()))

    if scope == "all":
        settings["all"].update(patch)
    else:
        settings["groups"].setdefault(scope, {})
        settings["groups"][scope].update(patch)

    progress["__settings__"] = settings
    save_progress(uid, progress)
    return jsonify({"ok": True})


@app.post("/api/settings_reset")
def api_settings_reset():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    data = request.get_json(force=True) or {}
    scope = (data.get("scope") or "all").strip()

    progress = load_progress(uid)
    settings = _migrate_settings(progress.get("__settings__", _default_settings()))
    defaults = _default_settings()

    if scope == "all":
        # Reset ONLY the All-Groups settings (keep per-group overrides)
        settings["all"] = defaults["all"]
    else:
        # Reset ONLY that group's override (removes it)
        if "groups" in settings and isinstance(settings["groups"], dict):
            settings["groups"].pop(scope, None)

    progress["__settings__"] = settings
    save_progress(uid, progress)
    return jsonify({"ok": True})


@app.get("/api/counts")
def api_counts():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    group = request.args.get("group", "")
    deck_id = request.args.get("deck_id", "")
    
    # Get user's settings to check active deck
    progress = load_progress(uid)
    settings = progress.get("__settings__", _default_settings())
    active_deck_id = deck_id or _get_active_deck_id(settings, "kenpo")
    
    # Load cards based on deck
    if active_deck_id == "kenpo":
        # Built-in Kenpo deck
        cards, status = load_cards_cached()
        if status != "ok":
            return jsonify({"error": status}), 500
        all_cards = list(cards)
        # Also add any user cards assigned to kenpo deck
        _migrate_user_deck_cards(uid)
        user_cards = _load_user_cards(uid)
        kenpo_user_cards = [c for c in user_cards if c.get("deckId", "kenpo") == "kenpo"]
        all_cards = all_cards + kenpo_user_cards
    else:
        # Non-built-in deck (owned or shared)
        _migrate_user_deck_cards(uid)
        if not _user_can_access_deck(uid, active_deck_id):
            return jsonify({"error": "deck_not_accessible"}), 403

        deck = _get_deck_by_id(active_deck_id)
        owner_id = _deck_owner_id(deck)

        if not owner_id:
            # Legacy deck (no owner stored yet): cards are still per-user
            user_cards = _load_user_cards(uid)
            all_cards = [c for c in user_cards if c.get("deckId") == active_deck_id]
        else:
            all_cards = _load_deck_cards(active_deck_id)

    counts = {"active": 0, "unsure": 0, "learned": 0, "deleted": 0, "total": 0}
    for c in all_cards:
        if group and c.get("group", "") != group:
            continue
        s = card_status(progress, c["id"])
        counts["total"] += 1
        counts[s] += 1
    return jsonify(counts)


# ============ CUSTOM SET API ============

@app.get("/api/custom_set")
def api_custom_set_get():
    """Get custom set cards with their status."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    cards, status = load_cards_cached()
    if status != "ok":
        return jsonify({"error": status}), 500
    
    progress = load_progress(uid)
    settings = progress.get("__settings__", _default_settings())
    custom_set_ids = settings.get("custom_set", [])
    custom_set_status = settings.get("custom_set_status", {})
    
    # Build card lookup
    cards_by_id = {c["id"]: c for c in cards}
    
    out = []
    for cid in custom_set_ids:
        card = cards_by_id.get(cid)
        if card:
            cc = dict(card)
            cc["custom_status"] = custom_set_status.get(cid, "active")
            cc["main_status"] = card_status(progress, cid)
            out.append(cc)
    
    return jsonify({
        "cards": out,
        "counts": {
            "total": len(out),
            "active": sum(1 for c in out if c.get("custom_status") == "active"),
            "unsure": sum(1 for c in out if c.get("custom_status") == "unsure"),
            "learned": sum(1 for c in out if c.get("custom_status") == "learned"),
        }
    })


@app.post("/api/custom_set/add")
def api_custom_set_add():
    """Add a card to custom set."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    data = request.get_json(force=True) or {}
    cid = data.get("id")
    if not cid:
        return jsonify({"error": "id required"}), 400
    
    progress = load_progress(uid)
    settings = progress.setdefault("__settings__", _default_settings())
    custom_set = settings.setdefault("custom_set", [])
    
    if cid not in custom_set:
        custom_set.append(cid)
        save_progress(uid, progress)
    
    return jsonify({"ok": True, "in_custom_set": True})


@app.post("/api/custom_set/remove")
def api_custom_set_remove():
    """Remove a card from custom set."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    data = request.get_json(force=True) or {}
    cid = data.get("id")
    if not cid:
        return jsonify({"error": "id required"}), 400
    
    progress = load_progress(uid)
    settings = progress.setdefault("__settings__", _default_settings())
    custom_set = settings.setdefault("custom_set", [])
    custom_set_status = settings.setdefault("custom_set_status", {})
    
    if cid in custom_set:
        custom_set.remove(cid)
        custom_set_status.pop(cid, None)
        save_progress(uid, progress)
    
    return jsonify({"ok": True, "in_custom_set": False})


@app.post("/api/custom_set/toggle")
def api_custom_set_toggle():
    """Toggle a card in/out of custom set."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    data = request.get_json(force=True) or {}
    cid = data.get("id")
    if not cid:
        return jsonify({"error": "id required"}), 400
    
    progress = load_progress(uid)
    settings = progress.setdefault("__settings__", _default_settings())
    custom_set = settings.setdefault("custom_set", [])
    custom_set_status = settings.setdefault("custom_set_status", {})
    
    if cid in custom_set:
        custom_set.remove(cid)
        custom_set_status.pop(cid, None)
        in_set = False
    else:
        custom_set.append(cid)
        in_set = True
    
    save_progress(uid, progress)
    return jsonify({"ok": True, "in_custom_set": in_set})


@app.post("/api/custom_set/set_status")
def api_custom_set_set_status():
    """Set custom set internal status for a card."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    data = request.get_json(force=True) or {}
    cid = data.get("id")
    status = data.get("status")
    reflect_main = data.get("reflect_main", False)
    
    if not cid or status not in ("active", "unsure", "learned"):
        return jsonify({"error": "id and valid status required"}), 400
    
    progress = load_progress(uid)
    settings = progress.setdefault("__settings__", _default_settings())
    custom_set_status = settings.setdefault("custom_set_status", {})
    
    custom_set_status[cid] = status
    
    # Optionally reflect status change in main deck
    if reflect_main:
        set_card_status(progress, cid, status)
    
    save_progress(uid, progress)
    return jsonify({"ok": True})


@app.post("/api/custom_set/clear")
def api_custom_set_clear():
    """Clear entire custom set."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    progress = load_progress(uid)
    settings = progress.setdefault("__settings__", _default_settings())
    settings["custom_set"] = []
    settings["custom_set_status"] = {}
    save_progress(uid, progress)
    
    return jsonify({"ok": True})


# ============ ADMIN STATS API ============

@app.get("/api/admin/stats")
def api_admin_stats():
    """Get comprehensive admin statistics for dashboard."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    # Load all data
    cards, status = load_cards_cached()
    profiles = _load_profiles()
    breakdowns = _load_breakdowns()
    decks = _load_decks(include_all=True)  # Admin sees all decks
    
    # User stats with detailed progress
    users = profiles.get("users", {})
    total_users = len(users)
    user_list = []
    
    for user_id, udata in users.items():
        if isinstance(udata, dict):
            uname = udata.get("username", "")
            is_admin = _is_admin_user(uname)
            
            # Get user's progress
            try:
                progress = load_progress(user_id)
                settings = progress.get("__settings__", {})
                active_deck_id = _get_active_deck_id(settings, "kenpo")
                last_sync = progress.get("__last_sync__", 0)
                
                # Count statuses
                learned = 0
                unsure = 0
                active = 0
                for k, v in progress.items():
                    if k.startswith("__"):
                        continue
                    if isinstance(v, dict):
                        s = v.get("status", "active")
                        if s == "learned":
                            learned += 1
                        elif s == "unsure":
                            unsure += 1
                        elif s == "active":
                            active += 1
                
                total_cards_user = learned + unsure + active
                progress_pct = round((learned / total_cards_user * 100) if total_cards_user > 0 else 0, 1)
                
                # Get active deck name
                active_deck_name = "Kenpo Vocabulary"
                for d in decks:
                    if d.get("id") == active_deck_id:
                        active_deck_name = d.get("name", active_deck_id)
                        break
                
            except Exception:
                learned = unsure = active = 0
                progress_pct = 0
                active_deck_id = "kenpo"
                active_deck_name = "Kenpo Vocabulary"
                last_sync = 0
            
            user_list.append({
                "id": user_id,
                "username": uname,
                "is_admin": is_admin,
                "password_reset_required": udata.get("password_reset_required", False),
                "learned": learned,
                "unsure": unsure,
                "active": active,
                "progress_pct": progress_pct,
                "active_deck_id": active_deck_id,
                "active_deck_name": active_deck_name,
                "last_sync": last_sync
            })
    
    # Sort users by progress descending
    user_list.sort(key=lambda x: x["progress_pct"], reverse=True)
    
    # Card stats
    total_cards = len(cards) if status == "ok" else 0
    groups = set()
    if status == "ok":
        for c in cards:
            groups.add(c.get("group", ""))
    
    # Breakdown stats
    total_breakdowns = len(breakdowns)
    breakdowns_with_content = sum(1 for b in breakdowns.values() 
                                   if isinstance(b, dict) and 
                                   (b.get("parts") or b.get("literal")))
    
    # Breakdown IDs for frontend
    breakdown_ids = list(breakdowns.keys())
    
    # Progress stats across all users
    total_learned = sum(u["learned"] for u in user_list)
    total_unsure = sum(u["unsure"] for u in user_list)
    total_active = sum(u["active"] for u in user_list)
    
    # Deck stats
    total_decks = len(decks)
    user_decks = sum(1 for d in decks if not d.get("isBuiltIn"))
    
    # API key status
    keys = _load_encrypted_api_keys()
    
    return jsonify({
        "users": {
            "total": total_users,
            "admins": list(ADMIN_USERNAMES),
            "list": user_list
        },
        "cards": {
            "total": total_cards,
            "groups": len(groups),
            "group_list": sorted(groups)
        },
        "breakdowns": {
            "total": total_breakdowns,
            "with_content": breakdowns_with_content,
            "ids": breakdown_ids
        },
        "decks": {
            "total": total_decks,
            "user_created": user_decks
        },
        "progress": {
            "total_learned": total_learned,
            "total_unsure": total_unsure,
            "total_active": total_active
        },
        "ai": {
            "chatgpt_configured": bool(keys.get("chatGptKey")),
            "chatgpt_model": keys.get("chatGptModel", "gpt-4o"),
            "gemini_configured": bool(keys.get("geminiKey")),
            "gemini_model": keys.get("geminiModel", "gemini-1.5-flash")
        },
        "server": {
            "version": get_version().get("version", ""),
            "build": get_version().get("build", ""),
            "uptime": _now()
        }
    })


# Server activity log storage
ACTIVITY_LOG = []
MAX_LOG_ENTRIES = 500

def log_activity(level: str, message: str, user: str = ""):
    """Add an entry to the activity log."""
    global ACTIVITY_LOG
    entry = {
        "timestamp": _now(),
        "level": level,  # info, warn, error
        "message": message,
        "user": user
    }
    ACTIVITY_LOG.append(entry)
    # Keep only recent entries
    if len(ACTIVITY_LOG) > MAX_LOG_ENTRIES:
        ACTIVITY_LOG = ACTIVITY_LOG[-MAX_LOG_ENTRIES:]


@app.get("/api/admin/logs")
def api_admin_logs():
    """Get server activity logs (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    log_type = request.args.get("type", "all")
    limit = int(request.args.get("limit", 100))
    
    logs = ACTIVITY_LOG.copy()
    
    # Filter by type
    if log_type == "error":
        logs = [l for l in logs if l["level"] == "error"]
    elif log_type == "user":
        logs = [l for l in logs if l["user"]]
    elif log_type == "server":
        logs = [l for l in logs if not l["user"]]
    
    # Return most recent first, limited
    logs = logs[-limit:]
    logs.reverse()
    
    return jsonify({"logs": logs})


@app.post("/api/admin/logs/clear")
def api_admin_logs_clear():
    """Clear activity logs (admin only)."""
    global ACTIVITY_LOG
    
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    log_type = request.args.get("type", "all")
    
    if log_type == "all":
        ACTIVITY_LOG = []
    elif log_type == "error":
        ACTIVITY_LOG = [l for l in ACTIVITY_LOG if l["level"] != "error"]
    elif log_type == "user":
        ACTIVITY_LOG = [l for l in ACTIVITY_LOG if not l["user"]]
    elif log_type == "server":
        ACTIVITY_LOG = [l for l in ACTIVITY_LOG if l["user"]]
    
    log_activity("info", f"Logs cleared by {username}", username)
    
    return jsonify({"success": True})


@app.post("/api/admin/user/update")
def api_admin_user_update():
    """Update user admin status."""
    global ADMIN_USERNAMES
    
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    data = request.get_json() or {}
    target_user_id = data.get("user_id", "")
    is_admin = data.get("is_admin", False)
    
    if not target_user_id:
        return jsonify({"error": "user_id required"}), 400
    
    profiles = _load_profiles()
    target_user = profiles.get("users", {}).get(target_user_id)
    if not target_user:
        return jsonify({"error": "user not found"}), 404
    
    target_username = target_user.get("username", "")
    
    # Update admin_users.json
    admin_users_path = os.path.join(DATA_DIR, "admin_users.json")
    try:
        with open(admin_users_path, "r", encoding="utf-8") as f:
            admin_data = json.load(f)
    except Exception:
        admin_data = {"admins": list(ADMIN_USERNAMES)}
    
    admins = set(admin_data.get("admins", []))
    if is_admin:
        admins.add(target_username.lower())
    else:
        admins.discard(target_username.lower())
    
    admin_data["admins"] = list(admins)
    with open(admin_users_path, "w", encoding="utf-8") as f:
        json.dump(admin_data, f, ensure_ascii=False, indent=2)
    
    # Reload admin usernames
    ADMIN_USERNAMES = _load_admin_usernames()
    
    return jsonify({"success": True})


@app.post("/api/admin/user/reset_password")
def api_admin_user_reset_password():
    """Reset user password to default and require change on next login."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    data = request.get_json() or {}
    target_user_id = data.get("user_id", "")
    
    if not target_user_id:
        return jsonify({"error": "user_id required"}), 400
    
    profiles = _load_profiles()
    if target_user_id not in profiles.get("users", {}):
        return jsonify({"error": "user not found"}), 404
    
    # Set password to default and flag for reset
    default_password = "123456789"
    profiles["users"][target_user_id]["password_hash"] = generate_password_hash(default_password)
    profiles["users"][target_user_id]["password_reset_required"] = True
    
    _save_profiles(profiles)
    
    return jsonify({"success": True})


# ============ ADMIN DECK ACCESS MANAGEMENT ============

@app.get("/api/admin/deck-config")
def api_admin_get_deck_config():
    """Get global deck configuration (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    config = _load_deck_config()
    access = _load_deck_access()
    
    return jsonify({
        "config": config,
        "inviteCodes": access.get("inviteCodes", {}),
        "userUnlocks": access.get("userUnlocks", {}),
        "userBuiltInDisabled": access.get("userBuiltInDisabled", [])
    })


@app.post("/api/admin/deck-config")
def api_admin_update_deck_config():
    """Update global deck configuration (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    data = request.get_json() or {}
    config = _load_deck_config()
    
    if "newUsersGetBuiltInDecks" in data:
        config["newUsersGetBuiltInDecks"] = bool(data["newUsersGetBuiltInDecks"])
    if "allowNonAdminDeckEdits" in data:
        config["allowNonAdminDeckEdits"] = bool(data["allowNonAdminDeckEdits"])
    if "builtInDecks" in data and isinstance(data["builtInDecks"], list):
        config["builtInDecks"] = data["builtInDecks"]
    
    _save_deck_config(config)
    log_activity("info", f"Deck config updated by {username}", username)
    
    return jsonify({"success": True, "config": config})




@app.get("/api/admin/user/<target_user_id>/deck-access")
def api_admin_get_user_deck_access(target_user_id: str):
    """Get a user's owned decks and which of the ADMIN's decks they can access (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403

    profiles = _load_profiles()
    if target_user_id not in profiles.get("users", {}):
        return jsonify({"error": "user not found"}), 404

    decks_all = _load_decks(include_all=True)

    # Decks owned by the target user
    owned = []
    for d in decks_all:
        if d.get("isBuiltIn"):
            continue
        if _deck_owner_id(d) == target_user_id:
            owned.append({"id": d.get("id"), "name": d.get("name", d.get("id"))})

    # Decks owned by the current admin (shareable)
    shareable = []
    for d in decks_all:
        if d.get("isBuiltIn"):
            continue
        if _deck_owner_id(d) == uid:
            shareable.append({"id": d.get("id"), "name": d.get("name", d.get("id"))})

    access = _load_deck_access()
    current_unlocks = set(access.get("userUnlocks", {}).get(target_user_id, []) or [])
    granted = [d["id"] for d in shareable if d["id"] in current_unlocks]

    return jsonify({
        "userId": target_user_id,
        "ownedDecks": owned,
        "adminDecks": shareable,
        "grantedAdminDecks": granted
    })


@app.post("/api/admin/user/<target_user_id>/deck-access")
def api_admin_set_user_deck_access(target_user_id: str):
    """Set which of the ADMIN's decks the target user can access (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403

    profiles = _load_profiles()
    if target_user_id not in profiles.get("users", {}):
        return jsonify({"error": "user not found"}), 404

    data = request.get_json() or {}
    desired = data.get("grantedAdminDecks", [])
    if not isinstance(desired, list):
        return jsonify({"error": "grantedAdminDecks must be a list"}), 400
    desired = [str(x) for x in desired if str(x).strip()]

    decks_all = _load_decks(include_all=True)
    admin_deck_ids = set(
        d.get("id") for d in decks_all
        if (not d.get("isBuiltIn")) and (_deck_owner_id(d) == uid)
    )

    # Only allow granting decks owned by this admin
    desired = [d for d in desired if d in admin_deck_ids]

    access = _load_deck_access()
    unlocks = access.setdefault("userUnlocks", {})
    existing = set(unlocks.get(target_user_id, []) or [])

    # Preserve non-admin-owned unlocks; only manage the admin-owned subset
    preserved = set([d for d in existing if d not in admin_deck_ids])
    unlocks[target_user_id] = sorted(list(preserved.union(set(desired))))

    _save_deck_access(access)

    target_username = profiles.get("users", {}).get(target_user_id, {}).get("username", "")
    log_activity("info", f"Admin {username} updated deck access for {target_username}", username)

    return jsonify({"success": True, "grantedAdminDecks": desired})

@app.post("/api/admin/deck-invite-code")
def api_admin_create_invite_code():
    """Create an invite code for a deck (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    data = request.get_json() or {}
    deck_id = data.get("deckId", "")
    
    if not deck_id:
        return jsonify({"error": "deckId required"}), 400
    
    access = _load_deck_access()
    
    # Generate unique code
    code = _generate_invite_code()
    while code in access.get("inviteCodes", {}):
        code = _generate_invite_code()
    
    access.setdefault("inviteCodes", {})[code] = {
        "deckId": deck_id,
        "createdAt": int(time.time()),
        "createdBy": username,
        "uses": 0
    }
    
    _save_deck_access(access)
    log_activity("info", f"Invite code {code} created for deck {deck_id} by {username}", username)
    
    return jsonify({"success": True, "code": code})


@app.delete("/api/admin/deck-invite-code/<code>")
def api_admin_delete_invite_code(code: str):
    """Delete an invite code (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    access = _load_deck_access()
    
    if code in access.get("inviteCodes", {}):
        del access["inviteCodes"][code]
        _save_deck_access(access)
        log_activity("info", f"Invite code {code} deleted by {username}", username)
    
    return jsonify({"success": True})


@app.post("/api/admin/user-deck-access")
def api_admin_update_user_deck_access():
    """Update a user's deck access (admin only)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    if not _is_admin_user(username):
        return jsonify({"error": "admin_required"}), 403
    
    data = request.get_json() or {}
    target_user_id = data.get("userId", "")
    action = data.get("action", "")  # "unlock", "lock", "enableBuiltIn", "disableBuiltIn"
    deck_id = data.get("deckId", "")
    
    if not target_user_id:
        return jsonify({"error": "userId required"}), 400
    
    access = _load_deck_access()
    
    if action == "unlock" and deck_id:
        # Add deck to user's unlocked list
        access.setdefault("userUnlocks", {}).setdefault(target_user_id, [])
        if deck_id not in access["userUnlocks"][target_user_id]:
            access["userUnlocks"][target_user_id].append(deck_id)
            log_activity("info", f"Deck {deck_id} unlocked for user {target_user_id} by {username}", username)
    
    elif action == "lock" and deck_id:
        # Remove deck from user's unlocked list
        if target_user_id in access.get("userUnlocks", {}):
            access["userUnlocks"][target_user_id] = [
                d for d in access["userUnlocks"][target_user_id] if d != deck_id
            ]
            log_activity("info", f"Deck {deck_id} locked for user {target_user_id} by {username}", username)
    
    elif action == "disableBuiltIn":
        # Disable built-in decks for user
        access.setdefault("userBuiltInDisabled", [])
        if target_user_id not in access["userBuiltInDisabled"]:
            access["userBuiltInDisabled"].append(target_user_id)
            log_activity("info", f"Built-in decks disabled for user {target_user_id} by {username}", username)
    
    elif action == "enableBuiltIn":
        # Re-enable built-in decks for user
        access["userBuiltInDisabled"] = [
            u for u in access.get("userBuiltInDisabled", []) if u != target_user_id
        ]
        log_activity("info", f"Built-in decks enabled for user {target_user_id} by {username}", username)
    
    _save_deck_access(access)
    
    return jsonify({"success": True})


@app.post("/api/redeem-invite-code")
def api_redeem_invite_code():
    """Redeem an invite code to unlock a deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    data = request.get_json() or {}
    code = str(data.get("code", "")).strip()
    
    if not code:
        return jsonify({"error": "code required"}), 400
    
    access = _load_deck_access()
    
    if code not in access.get("inviteCodes", {}):
        return jsonify({"error": "Invalid invite code"}), 404
    
    code_data = access["inviteCodes"][code]
    deck_id = code_data.get("deckId")
    
    if not deck_id:
        return jsonify({"error": "Invalid invite code"}), 400
    
    # Check if already unlocked
    user_unlocks = access.get("userUnlocks", {}).get(uid, [])
    if deck_id in user_unlocks:
        return jsonify({"error": "Deck already unlocked"}), 400
    
    # Unlock the deck
    access.setdefault("userUnlocks", {}).setdefault(uid, [])
    access["userUnlocks"][uid].append(deck_id)
    
    # Increment use count
    access["inviteCodes"][code]["uses"] = code_data.get("uses", 0) + 1
    
    _save_deck_access(access)
    
    # Get deck name for response
    decks = _load_decks(include_all=True)
    deck_name = deck_id
    for d in decks:
        if d.get("id") == deck_id:
            deck_name = d.get("name", deck_id)
            break
    
    user = _get_user(uid)
    username = user.get("username", "") if user else ""
    log_activity("info", f"User {username} redeemed code {code} for deck {deck_name}", username)
    
    return jsonify({"success": True, "deckId": deck_id, "deckName": deck_name})


@app.post("/api/decks/<deck_id>/clear_default")
def api_clear_default_deck(deck_id: str):
    """Clear the default flag from a deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    decks = _load_decks(include_all=True)
    
    # Clear default flag from specified deck
    for d in decks:
        if d.get("id") == deck_id:
            d["isDefault"] = False
            break
    
    _save_decks(decks)
    
    return jsonify({"success": True})


@app.get("/api/cards")
def api_cards():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    status_filter = request.args.get("status", "active")
    if status_filter in ("all", "any", ""):
        status_filter = ""
    group = request.args.get("group", "")
    q = (request.args.get("q", "") or "").strip().lower()
    deck_id = request.args.get("deck_id", "")

    # Get user's settings to check active deck
    progress = load_progress(uid)
    settings = progress.get("__settings__", _default_settings())
    active_deck_id = deck_id or _get_active_deck_id(settings, "kenpo")
    
    # Load cards based on deck
    if active_deck_id == "kenpo":
        # Built-in Kenpo deck
        cards, status = load_cards_cached()
        if status != "ok":
            return jsonify({"error": status}), 500
        all_cards = list(cards)
        # Also add any user cards assigned to kenpo deck
        _migrate_user_deck_cards(uid)
        user_cards = _load_user_cards(uid)
        kenpo_user_cards = [c for c in user_cards if c.get("deckId", "kenpo") == "kenpo"]
        all_cards = all_cards + kenpo_user_cards
    else:
        # Non-built-in deck (owned or shared)
        _migrate_user_deck_cards(uid)
        if not _user_can_access_deck(uid, active_deck_id):
            return jsonify({"error": "deck_not_accessible"}), 403

        deck = _get_deck_by_id(active_deck_id)
        owner_id = _deck_owner_id(deck)

        if not owner_id:
            # Legacy deck (no owner stored yet): cards are still per-user
            user_cards = _load_user_cards(uid)
            all_cards = [c for c in user_cards if c.get("deckId") == active_deck_id]
        else:
            all_cards = _load_deck_cards(active_deck_id)
    
    # Get custom set IDs for this user
    custom_set_ids = set(settings.get("custom_set", []))
    
    out: List[Dict[str, Any]] = []
    for c in all_cards:
        if group and c.get("group", "") != group:
            continue

        s = card_status(progress, c["id"])
        if status_filter and s != status_filter:
            continue

        if q:
            hay = f"{c.get('term','')} {c.get('meaning','')} {c.get('pron','')}".lower()
            if q not in hay:
                continue

        cc = dict(c)
        cc["status"] = s
        cc["in_custom_set"] = c["id"] in custom_set_ids
        out.append(cc)

    return jsonify(out)


@app.post("/api/set_status")
def api_set_status():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    data = request.get_json(force=True) or {}
    cid = data.get("id")
    s = data.get("status")
    if not cid or s not in ("active", "unsure", "learned", "deleted"):
        return jsonify({"error": "id and valid status required"}), 400

    progress = load_progress(uid)
    set_card_status(progress, cid, s)
    save_progress(uid, progress)
    return jsonify({"ok": True})


@app.post("/api/bulk_set_status")
def api_bulk_set_status():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    data = request.get_json(force=True) or {}
    ids = data.get("ids", [])
    s = data.get("status")
    if not isinstance(ids, list) or s not in ("active", "unsure", "learned", "deleted"):
        return jsonify({"error": "ids[] and valid status required"}), 400

    progress = load_progress(uid)
    for cid in ids:
        if cid:
            set_card_status(progress, str(cid), s)
    save_progress(uid, progress)
    return jsonify({"ok": True})


@app.post("/api/reset")
def api_reset():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    save_progress(uid, {"__settings__": _default_settings()})
    return jsonify({"ok": True})


@app.get("/api/breakdown")
def api_breakdown_get():
    uid, user = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    card_id = (request.args.get("id") or "").strip()
    if not card_id:
        return jsonify({"error": "missing_id"}), 400

    data = _load_breakdowns()
    entry = data.get(card_id)
    return jsonify({"id": card_id, "breakdown": entry})


@app.post("/api/breakdown")
def api_breakdown_set():
    uid, user = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    payload = request.get_json(force=True, silent=True) or {}
    card_id = str(payload.get("id") or "").strip()
    term = str(payload.get("term") or "").strip()

    if not card_id:
        return jsonify({"error": "missing_id"}), 400

    parts = payload.get("parts") or []
    if not isinstance(parts, list):
        return jsonify({"error": "parts_must_be_list"}), 400

    norm_parts: List[Dict[str, str]] = []
    for p in parts:
        if not isinstance(p, dict):
            continue
        part = str(p.get("part") or "").strip()
        meaning = str(p.get("meaning") or "").strip()
        if part or meaning:
            norm_parts.append({"part": part, "meaning": meaning})

    entry = {
        "id": card_id,
        "term": term,
        "parts": norm_parts,
        "literal": str(payload.get("literal") or "").strip(),
        "notes": str(payload.get("notes") or "").strip(),
        "updated_at": int(time.time()),
        "updated_by": (user or {}).get("username") if isinstance(user, dict) else None,
    }

    data = _load_breakdowns()
    existing = data.get(card_id)

    # Only the admin user (sidscri) may overwrite an existing saved breakdown.
    username = ((user or {}).get('username') if isinstance(user, dict) else '') or ''
    is_admin = username.strip().lower() == 'sidscri'

    def _core(b):
        if not isinstance(b, dict):
            return None
        parts = b.get('parts') if isinstance(b.get('parts'), list) else []
        norm_parts = []
        for p in parts:
            if not isinstance(p, dict):
                continue
            part = str(p.get('part') or '').strip()
            meaning = str(p.get('meaning') or '').strip()
            if part or meaning:
                norm_parts.append({'part': part, 'meaning': meaning})
        return {
            'term': str(b.get('term') or '').strip(),
            'parts': norm_parts,
            'literal': str(b.get('literal') or '').strip(),
            'notes': str(b.get('notes') or '').strip(),
        }

    if existing and not is_admin:
        if _core(existing) != _core(entry):
            return jsonify({
                'error': 'overwrite_not_allowed',
                'message': 'Only admin (sidscri) can overwrite an existing breakdown.',
                'breakdown': existing,
            }), 403
        # No content changes; return existing without touching timestamps
        return jsonify({'ok': True, 'breakdown': existing})

    data[card_id] = entry
    _save_breakdowns(data)
    return jsonify({'ok': True, 'breakdown': entry})


@app.post("/api/breakdown_autofill")
def api_breakdown_autofill():
    uid, user = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    payload = request.get_json(force=True, silent=True) or {}
    term = str(payload.get("term") or "").strip()
    meaning = str(payload.get("meaning") or "").strip()
    group = str(payload.get("group") or "").strip()
    req_provider = str(payload.get("provider") or "").strip().lower()
    if not term:
        return jsonify({"error": "missing_term"}), 400

    # Determine provider: request param > per-user setting > auto
    prov = req_provider
    if not prov:
        try:
            prog = load_progress(uid)
            prov = str(prog.get("__settings__", {}).get("all", {}).get("breakdown_ai_provider") or "auto").strip().lower()
        except Exception:
            prov = "auto"
    if prov not in ("auto", "openai", "gemini", "off"):
        prov = "auto"

    ai_error = None

    def try_openai():
        nonlocal ai_error
        result, err = _openai_breakdown(term=term, meaning=meaning, group=group)
        if result and isinstance(result, dict):
            return {"ok": True, "suggestion": result, "source": "openai", "provider": "openai"}
        if err:
            ai_error = err
        return None

    def try_gemini():
        nonlocal ai_error
        result, err = _gemini_breakdown(term=term, meaning=meaning, group=group)
        if result and isinstance(result, dict):
            return {"ok": True, "suggestion": result, "source": "gemini", "provider": "gemini"}
        if err:
            ai_error = err
        return None

    # Try requested provider(s)
    if prov == "openai":
        out = try_openai()
        if out:
            return jsonify(out)
    elif prov == "gemini":
        out = try_gemini()
        if out:
            return jsonify(out)
    elif prov == "auto":
        # Prefer OpenAI, then Gemini
        out = try_openai()
        if out:
            return jsonify(out)
        out = try_gemini()
        if out:
            return jsonify(out)
    # prov == off -> skip AI

    # Fallback: curated + conservative split
    key = re.sub(r"\s+", "", term.lower())
    suggestion = AUTO_BREAKDOWNS.get(key)

    if not suggestion:
        parts = []
        for tok in re.split(r"[\s\-]+", term.strip()):
            t = tok.strip()
            if t:
                parts.append({"part": t, "meaning": ""})
        suggestion = {"parts": parts, "literal": ""}

    resp = {"ok": True, "suggestion": suggestion, "source": "curated", "provider": prov}
    if ai_error:
        # Provide a helpful message without leaking secrets.
        resp["ai_error"] = ai_error
    return jsonify(resp)


@app.get("/api/ai")
@app.get("/api/ai/status")
def api_ai_status():
    """Simple status so the UI can show if AI autofill is available."""
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    # selected provider (per-user setting)
    prog = load_progress(uid)
    prov = "auto"
    try:
        prov = (prog.get("__settings__", {}).get("all", {}).get("breakdown_ai_provider") or "auto").strip().lower()
    except Exception:
        prov = "auto"

    return jsonify({
        "ok": True,
        "selected_provider": prov,
        "openai_available": bool(OPENAI_API_KEY),
        "openai_model": OPENAI_MODEL if OPENAI_API_KEY else "",
        "gemini_available": bool(GEMINI_API_KEY),
        "gemini_model": GEMINI_MODEL if GEMINI_API_KEY else "",
    })


@app.get("/api/breakdowns")
def api_breakdowns_list():
    uid, user = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401

    q = (request.args.get("q") or "").strip().lower()
    data = _load_breakdowns()
    items = []
    for k, v in data.items():
        if not isinstance(v, dict):
            continue
        term = str(v.get("term") or "")
        if q and q not in term.lower():
            # also search inside parts
            hay = " ".join([term] + [str(p.get("part","")) + " " + str(p.get("meaning","")) for p in (v.get("parts") or [])])
            if q not in hay.lower():
                continue
        items.append(v)

    # newest first
    items.sort(key=lambda x: _safe_int(x.get("updated_at"), 0), reverse=True)
    return jsonify({"ok": True, "items": items})


@app.get("/api/breakdowns/ids")
def api_breakdowns_ids():
    """Get just the IDs of cards that have breakdowns (lightweight endpoint)."""
    data = _load_breakdowns()
    # Only return IDs of breakdowns with actual content
    ids_with_content = []
    for k, v in data.items():
        if isinstance(v, dict) and (v.get("parts") or v.get("literal")):
            ids_with_content.append(k)
    return jsonify({"ids": ids_with_content})




"""
ANDROID SYNC API ROUTES
=======================
Add these routes to your existing app.py file.

Copy everything below and paste it BEFORE the line:
    @app.get("/<path:filename>")
"""

# ============ ANDROID APP SYNC API ============

# In-memory token storage (simple approach - tokens expire on server restart)
# For production, consider using Redis or database storage
_android_tokens: Dict[str, Dict[str, Any]] = {}

def _generate_token() -> str:
    """Generate a secure random token."""
    return uuid.uuid4().hex + uuid.uuid4().hex

def _verify_android_token(token: str) -> Optional[Tuple[str, Dict]]:
    """Verify token and return (user_id, user_data) or None."""
    if not token:
        return None
    session_data = _android_tokens.get(token)
    if not session_data:
        return None
    # Check expiration (7 days)
    if time.time() > session_data.get('exp', 0):
        del _android_tokens[token]
        return None
    return session_data.get('uid'), session_data.get('user')

def android_auth_required(f):
    """Decorator for routes that require Android token auth."""
    from functools import wraps
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return jsonify({'error': 'No token provided'}), 401
        token = auth_header[7:]
        result = _verify_android_token(token)
        if not result:
            return jsonify({'error': 'Invalid or expired token'}), 401
        request.android_uid, request.android_user = result
        return f(*args, **kwargs)
    return decorated




def android_admin_required(f):
    """Decorator for routes that require Android token auth AND admin user."""
    from functools import wraps
    @wraps(f)
    @android_auth_required
    def decorated(*args, **kwargs):
        username = (request.android_user or {}).get('username', '')
        if not _is_admin_user(username):
            return jsonify({'error': 'admin_required'}), 403
        return f(*args, **kwargs)
    return decorated

@app.post("/api/sync/login")
def api_android_login():
    """Android app login endpoint.
    
    Expects JSON: {"username": "...", "password": "..."}
    Returns: {"token": "...", "userId": "...", "username": "..."}
    """
    payload = request.get_json(force=True, silent=True) or {}
    username = str(payload.get('username') or '').strip()
    password = str(payload.get('password') or '').strip()
    
    if not username or not password:
        return jsonify({'error': 'Username and password required'}), 400
    
    # Load profiles
    profiles = _load_profiles()
    users = profiles.get('users', {})
    
    # Find user by username (case-insensitive)
    found_uid = None
    found_user = None
    for uid, user_data in users.items():
        if user_data.get('username', '').lower() == username.lower():
            found_uid = uid
            found_user = user_data
            break
    
    if not found_user:
        return jsonify({'error': 'User not found'}), 401
    
    # Verify password
    password_hash = found_user.get('password_hash', '')
    if not check_password_hash(password_hash, password):
        return jsonify({'error': 'Invalid password'}), 401
    
    # Generate token
    token = _generate_token()
    _android_tokens[token] = {
        'uid': found_uid,
        'user': found_user,
        'exp': time.time() + (7 * 24 * 60 * 60)  # 7 days
    }
    
    return jsonify({
        'token': token,
        'userId': found_uid,
        'username': found_user.get('username', ''),
        'displayName': found_user.get('display_name', '')
    })

@app.post("/api/breakdowns")
@android_auth_required
def api_breakdowns_save_android():
    """
    Android expects POST /api/breakdowns with a Bearer token.
    Saves/updates a breakdown so other devices can pull it later.
    """
    payload = request.get_json(silent=True) or {}
    incoming_bid = str(payload.get("id") or "").strip()
    bid = incoming_bid
    term = str(payload.get("term") or "").strip()

    # Canonicalize breakdown id using server helper (term->id)
    canonical = _canonical_id_for_term(
        term,
        group=str(payload.get("group") or "").strip(),
        subgroup=str(payload.get("subgroup") or "").strip(),
    )
    if canonical:
        bid = canonical

    parts = payload.get("parts") or []
    literal = str(payload.get("literal") or "").strip()
    notes = str(payload.get("notes") or "").strip()

    if not bid or not term or not isinstance(parts, list):
        return jsonify({"error": "id, term, parts[] required"}), 400

    # normalize parts list
    norm_parts = []
    for p in parts:
        if not isinstance(p, dict):
            continue
        norm_parts.append({
            "part": str(p.get("part") or "").strip(),
            "meaning": str(p.get("meaning") or "").strip(),
        })
    data = _load_breakdowns()
    if incoming_bid and incoming_bid != bid and incoming_bid in data:
        try:
            del data[incoming_bid]
        except Exception:
            pass
    data[bid] = {
        "id": bid,
        "term": term,
        "parts": norm_parts,
        "literal": literal,
        "notes": notes,
        "updated_at": int(time.time()),
        "updated_by": str(getattr(request, "android_uid", "") or ""),
    }
    _save_breakdowns(data)

    return jsonify({"ok": True, "id": bid})


# ============ WEB SYNC ENDPOINTS (Session Auth) ============

@app.post("/api/web/sync/push")
def api_web_sync_push():
    """Push progress from web app to server (session auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    # Web app pushes the full progress from localStorage
    # For simplicity, just update last_sync timestamp
    progress = load_progress(uid)
    progress["__last_sync__"] = int(time.time())
    save_progress(uid, progress)
    
    return jsonify({"success": True, "message": "Sync complete"})


@app.get("/api/web/sync/pull")
def api_web_sync_pull():
    """Pull progress from server to web app (session auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    
    progress = load_progress(uid)
    
    # Extract card progress entries
    entries = {}
    for key, value in progress.items():
        if isinstance(key, str) and key.startswith('__'):
            continue
        if not isinstance(key, str):
            continue
        
        if isinstance(value, dict) and 'status' in value:
            status = str(value.get('status') or '').lower().strip()
            if status not in ('active', 'unsure', 'learned', 'deleted'):
                continue
            updated_at = _safe_int(value.get('updated_at'), 0)
            entries[key] = {'status': status, 'updated_at': updated_at}
        elif isinstance(value, str):
            status = value.lower().strip()
            if status not in ('active', 'unsure', 'learned', 'deleted'):
                continue
            entries[key] = {'status': status, 'updated_at': 0}
    
    return jsonify({'progress': entries})


@app.get("/api/sync/pull")
@android_auth_required
def api_sync_pull():
    """Pull progress from server to Android app.

    Returns canonical card IDs mapped to an object: {status, updated_at}.
    Backwards compatible: Android may still accept plain status strings.
    """
    uid = request.android_uid
    progress = load_progress(uid)

    # Extract just the card progress entries (not settings/internal keys).
    entries = {}
    for key, value in progress.items():
        if isinstance(key, str) and key.startswith('__'):
            continue
        if not isinstance(key, str):
            continue

        # Stored format on server is typically {status, updated_at}.
        if isinstance(value, dict) and 'status' in value:
            status = str(value.get('status') or '').lower().strip()
            if status not in ('active', 'unsure', 'learned', 'deleted'):
                continue
            updated_at = _safe_int(value.get('updated_at'), 0)
            entries[key] = {'status': status, 'updated_at': updated_at}
        elif isinstance(value, str):
            # Legacy server data (unlikely)
            status = value.lower().strip()
            if status not in ('active', 'unsure', 'learned', 'deleted'):
                continue
            entries[key] = {'status': status, 'updated_at': 0}

    return jsonify({'progress': entries})


@app.post("/api/sync/push")
@android_auth_required
def api_sync_push():
    """Push progress from Android app to server.

    Accepts BOTH formats for backwards compatibility:
      - Legacy: progress[id] = "learned"
      - New:    progress[id] = {"status": "learned", "updated_at": 1737042271}

    Merge rule (per card): keep the entry with the newer updated_at.
    If legacy format is provided, server assigns updated_at = now.
    """
    uid = request.android_uid
    payload = request.get_json(force=True, silent=True) or {}
    incoming_progress = payload.get('progress', {})

    if not isinstance(incoming_progress, dict):
        return jsonify({'error': 'Invalid progress data'}), 400

    current = load_progress(uid)

    now = int(time.time())
    applied = 0
    skipped_unknown = 0
    skipped_older = 0

    def _parse_incoming(v):
        # returns (status, updated_at) or (None, None)
        if isinstance(v, str):
            st = v.lower().strip()
            if st not in ('active', 'unsure', 'learned', 'deleted'):
                return None, None
            return st, now
        if isinstance(v, dict):
            st = str(v.get('status') or '').lower().strip()
            if st not in ('active', 'unsure', 'learned', 'deleted'):
                return None, None
            try:
                ua = _safe_int(v.get('updated_at'), 0)
            except Exception:
                ua = 0
            if ua <= 0:
                ua = now
            return st, ua
        return None, None

    for card_key, v in incoming_progress.items():
        ck = card_key if isinstance(card_key, str) else str(card_key)

        status_lower, incoming_updated_at = _parse_incoming(v)
        if not status_lower:
            continue

        # Canonicalize key to the 16-hex ID used by the web UI.
        canonical_id = ck.lower() if _ID16_RE.match(ck) else (_canonical_id_for_term(ck) or "")
        if not canonical_id or not _ID16_RE.match(canonical_id):
            skipped_unknown += 1
            continue
        canonical_id = canonical_id.lower()

        cur = current.get(canonical_id)
        cur_status = None
        cur_updated = 0
        if isinstance(cur, dict) and 'status' in cur:
            cur_status = str(cur.get('status') or '').lower().strip()
            try:
                cur_updated = _safe_int(cur.get('updated_at'), 0)
            except Exception:
                cur_updated = 0
        elif isinstance(cur, str):
            cur_status = cur.lower().strip()
            cur_updated = 0

        if incoming_updated_at >= cur_updated:
            current[canonical_id] = {
                'status': status_lower,
                'updated_at': int(incoming_updated_at)
            }
            applied += 1
        else:
            skipped_older += 1

    save_progress(uid, current)
    return jsonify({
        'success': True,
        'message': 'Progress synced',
        'applied': applied,
        'skipped_unknown': skipped_unknown,
        'skipped_older': skipped_older
    })


@app.get("/api/sync/helper")
def api_sync_helper():
    """
    Public helper endpoint used by Android + Web to agree on canonical card IDs.
    (No token required; contains only vocabulary metadata.)
    """
    helper, status = load_helper_cached()
    if status != "ok":
        return jsonify({"error": status}), 404
    return jsonify(helper)

@app.get("/api/sync/breakdowns")
def api_sync_breakdowns():
    """Get all breakdowns for Android app (no auth required for read)."""
    data = _load_breakdowns()
    return jsonify({'breakdowns': data})


@app.post("/api/sync/customset")
@android_auth_required
def api_sync_customset():
    """Sync custom study set from Android."""
    uid = request.android_uid
    payload = request.get_json(force=True, silent=True) or {}
    custom_set = payload.get('customSet', [])
    
    if not isinstance(custom_set, list):
        return jsonify({'error': 'Invalid custom set data'}), 400
    
    # Store in user's progress file under special key
    progress = load_progress(uid)
    progress['__custom_set__'] = list(custom_set)
    save_progress(uid, progress)
    
    return jsonify({'success': True})


@app.get("/api/sync/customset")
@android_auth_required
def api_get_customset():
    """Get custom study set for Android."""
    uid = request.android_uid
    progress = load_progress(uid)
    custom_set = progress.get('__custom_set__', [])
    return jsonify({'customSet': custom_set})


# ============ ADMIN API KEY MANAGEMENT ============

@app.post("/api/admin/apikeys")
@android_auth_required
def api_admin_save_apikeys():
    """
    Save encrypted API keys and models (admin only).
    
    Expects JSON: {"chatGptKey": "...", "chatGptModel": "...", "geminiKey": "...", "geminiModel": "..."}
    Returns: {"success": true} or {"error": "..."}
    """
    # Check if user is admin
    username = request.android_user.get('username', '')
    if not _is_admin_user(username):
        return jsonify({'error': 'Admin access required'}), 403
    
    payload = request.get_json(force=True, silent=True) or {}
    chat_gpt_key = str(payload.get('chatGptKey') or '').strip()
    chat_gpt_model = str(payload.get('chatGptModel') or 'gpt-4o').strip()
    gemini_key = str(payload.get('geminiKey') or '').strip()
    gemini_model = str(payload.get('geminiModel') or 'gemini-1.5-flash').strip()
    
    # Load existing keys and update
    keys = _load_encrypted_api_keys()
    if chat_gpt_key:
        keys['chatGptKey'] = chat_gpt_key
    elif 'chatGptKey' in payload and not chat_gpt_key:  # Explicit empty means remove
        keys.pop('chatGptKey', None)
    
    keys['chatGptModel'] = chat_gpt_model
    
    if gemini_key:
        keys['geminiKey'] = gemini_key
    elif 'geminiKey' in payload and not gemini_key:  # Explicit empty means remove
        keys.pop('geminiKey', None)
    
    keys['geminiModel'] = gemini_model
    
    # Save encrypted
    if _save_encrypted_api_keys(keys):
        # Also update environment variables for current session
        global OPENAI_API_KEY, GEMINI_API_KEY, OPENAI_MODEL, GEMINI_MODEL
        if chat_gpt_key:
            OPENAI_API_KEY = chat_gpt_key
        OPENAI_MODEL = chat_gpt_model
        if gemini_key:
            GEMINI_API_KEY = gemini_key
        GEMINI_MODEL = gemini_model
        
        print(f"[ADMIN] API keys updated by {username}")
        return jsonify({'success': True, 'message': 'API keys saved and encrypted'})
    else:
        return jsonify({'error': 'Failed to save API keys'}), 500


@app.get("/api/admin/apikeys")
@android_auth_required
def api_admin_get_apikeys():
    """
    Get decrypted API keys and models (admin only).
    
    Returns: {"chatGptKey": "...", "chatGptModel": "...", "geminiKey": "...", "geminiModel": "..."} or {"error": "..."}
    """
    # Check if user is admin
    username = request.android_user.get('username', '')
    if not _is_admin_user(username):
        return jsonify({'error': 'Admin access required'}), 403
    
    keys = _load_encrypted_api_keys()
    
    # Return the keys (or empty strings/defaults if not set)
    return jsonify({
        'chatGptKey': keys.get('chatGptKey', ''),
        'chatGptModel': keys.get('chatGptModel', 'gpt-4o'),
        'geminiKey': keys.get('geminiKey', ''),
        'geminiModel': keys.get('geminiModel', 'gemini-1.5-flash'),
        'hasKeys': bool(keys.get('chatGptKey') or keys.get('geminiKey'))
    })


@app.get("/api/sync/apikeys")
@android_auth_required
def api_sync_get_apikeys():
    """
    Get API keys for any authenticated user (read-only).
    This allows all users to use AI features without admin access.
    
    Returns: {"chatGptKey": "...", "chatGptModel": "...", "geminiKey": "...", "geminiModel": "...", "hasKeys": bool}
    """
    keys = _load_encrypted_api_keys()
    
    # Return the keys (or empty strings/defaults if not set)
    return jsonify({
        'chatGptKey': keys.get('chatGptKey', ''),
        'chatGptModel': keys.get('chatGptModel', 'gpt-4o'),
        'geminiKey': keys.get('geminiKey', ''),
        'geminiModel': keys.get('geminiModel', 'gemini-1.5-flash'),
        'hasKeys': bool(keys.get('chatGptKey') or keys.get('geminiKey'))
    })


@app.get("/api/admin/status")
@android_auth_required
def api_admin_status():
    """
    Get admin status for current user.
    
    Returns: {"isAdmin": true/false, "username": "..."}
    """
    username = request.android_user.get('username', '')
    is_admin = _is_admin_user(username)
    
    return jsonify({
        'isAdmin': is_admin,
        'username': username,
        'hasApiKeys': bool(_load_encrypted_api_keys()) if is_admin else None
    })



# ===============================
# ANDROID TOKEN ADMIN ROUTES (GEN8)
# Namespace: /api/sync/admin/...
# ===============================

@app.get("/api/sync/admin/deck-config")
@android_admin_required
def api_sync_admin_get_deck_config():
    """Token-admin: get global deck config."""
    return jsonify(_load_deck_config())


@app.post("/api/sync/admin/deck-config")
@android_admin_required
def api_sync_admin_set_deck_config():
    """Token-admin: set global deck config."""
    data = request.get_json(force=True, silent=True) or {}
    cfg = _load_deck_config()
    if "newUsersGetBuiltInDecks" in data:
        cfg["newUsersGetBuiltInDecks"] = bool(data.get("newUsersGetBuiltInDecks"))
    if "allowNonAdminDeckEdits" in data:
        cfg["allowNonAdminDeckEdits"] = bool(data.get("allowNonAdminDeckEdits"))
    if "builtInDecks" in data and isinstance(data.get("builtInDecks"), list):
        cfg["builtInDecks"] = [str(x) for x in data.get("builtInDecks") if str(x).strip()]
    _save_deck_config(cfg)
    username = (request.android_user or {}).get("username", "")
    log_activity("info", "Deck config updated via Android token admin", username)
    return jsonify({"success": True, "config": cfg})


@app.post("/api/sync/admin/deck-invite-code")
@android_admin_required
def api_sync_admin_create_invite_code():
    """Token-admin: create invite code for a deck."""
    data = request.get_json(force=True, silent=True) or {}
    deck_id = str(data.get("deckId") or "").strip()
    if not deck_id:
        return jsonify({"error": "deckId required"}), 400

    access = _load_deck_access()
    code = None
    for _ in range(50):
        c = uuid.uuid4().hex[:8].upper()
        if c not in access.get("inviteCodes", {}):
            code = c
            break
    if not code:
        return jsonify({"error": "Could not generate code"}), 500

    access.setdefault("inviteCodes", {})[code] = {"deckId": deck_id, "createdAt": int(time.time()), "uses": 0}
    _save_deck_access(access)

    username = (request.android_user or {}).get("username", "")
    log_activity("info", f"Invite code {code} created for deck {deck_id} via Android token admin", username)
    return jsonify({"success": True, "code": code, "deckId": deck_id})


@app.delete("/api/sync/admin/deck-invite-code/<code>")
@android_admin_required
def api_sync_admin_delete_invite_code(code: str):
    """Token-admin: delete invite code."""
    code = str(code or "").strip()
    if not code:
        return jsonify({"error": "code required"}), 400
    access = _load_deck_access()
    if code in access.get("inviteCodes", {}):
        del access["inviteCodes"][code]
        _save_deck_access(access)
    username = (request.android_user or {}).get("username", "")
    log_activity("info", f"Invite code {code} deleted via Android token admin", username)
    return jsonify({"success": True})


@app.post("/api/sync/redeem-invite-code")
@android_auth_required
def api_sync_redeem_invite_code():
    """Token-user: redeem invite code to unlock deck."""
    uid = request.android_uid
    data = request.get_json(force=True, silent=True) or {}
    code = str(data.get("code", "")).strip()
    if not code:
        return jsonify({"error": "code required"}), 400

    access = _load_deck_access()
    if code not in access.get("inviteCodes", {}):
        return jsonify({"error": "Invalid invite code"}), 404

    code_data = access["inviteCodes"][code]
    deck_id = code_data.get("deckId")
    if not deck_id:
        return jsonify({"error": "Invalid invite code"}), 400

    user_unlocks = access.get("userUnlocks", {}).get(uid, [])
    if deck_id in user_unlocks:
        return jsonify({"error": "Deck already unlocked"}), 400

    access.setdefault("userUnlocks", {}).setdefault(uid, [])
    access["userUnlocks"][uid].append(deck_id)
    access["inviteCodes"][code]["uses"] = int(code_data.get("uses", 0)) + 1
    _save_deck_access(access)

    decks = _load_decks(include_all=True)
    deck_name = deck_id
    for d in decks:
        if d.get("id") == deck_id:
            deck_name = d.get("name", deck_id)
            break

    username = (request.android_user or {}).get("username", "")
    log_activity("info", f"User {username} redeemed code {code} for deck {deck_name} via Android token", username)

    return jsonify({"success": True, "deckId": deck_id, "deckName": deck_name})



@app.get("/api/admin/users")
def api_get_admin_users():
    """
    Get list of admin usernames (Source of Truth).
    Used by Android app to check admin status locally.
    
    Returns: {"admin_usernames": ["sidscri", ...]}
    """
    return jsonify({
        'admin_usernames': list(ADMIN_USERNAMES)
    })


# ============ END ANDROID SYNC API ============

# ============ WEB AI ACCESS ENDPOINTS (session-based) ============

@app.get("/api/web/admin/apikeys")
def web_admin_get_apikeys():
    """Get API keys for web admin page (session auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({'error': 'Login required'}), 401
    
    user = _get_user(uid)
    username = user.get('username', '') if user else ''
    if not _is_admin_user(username):
        return jsonify({'error': 'Admin access required'}), 403
    
    keys = _load_encrypted_api_keys()
    return jsonify({
        'chatGptKey': keys.get('chatGptKey', ''),
        'chatGptModel': keys.get('chatGptModel', 'gpt-4o'),
        'geminiKey': keys.get('geminiKey', ''),
        'geminiModel': keys.get('geminiModel', 'gemini-1.5-flash'),
        'hasKeys': bool(keys.get('chatGptKey') or keys.get('geminiKey'))
    })


@app.post("/api/web/admin/apikeys")
def web_admin_save_apikeys():
    """Save API keys from web admin page (session auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({'error': 'Login required'}), 401
    
    user = _get_user(uid)
    username = user.get('username', '') if user else ''
    if not _is_admin_user(username):
        return jsonify({'error': 'Admin access required'}), 403
    
    payload = request.get_json(force=True, silent=True) or {}
    chat_gpt_key = str(payload.get('chatGptKey') or '').strip()
    chat_gpt_model = str(payload.get('chatGptModel') or 'gpt-4o').strip()
    gemini_key = str(payload.get('geminiKey') or '').strip()
    gemini_model = str(payload.get('geminiModel') or 'gemini-1.5-flash').strip()
    
    keys = _load_encrypted_api_keys()
    if chat_gpt_key:
        keys['chatGptKey'] = chat_gpt_key
    elif 'chatGptKey' in payload and not chat_gpt_key:
        keys.pop('chatGptKey', None)
    
    keys['chatGptModel'] = chat_gpt_model
    
    if gemini_key:
        keys['geminiKey'] = gemini_key
    elif 'geminiKey' in payload and not gemini_key:
        keys.pop('geminiKey', None)
    
    keys['geminiModel'] = gemini_model
    
    if _save_encrypted_api_keys(keys):
        global OPENAI_API_KEY, GEMINI_API_KEY, OPENAI_MODEL, GEMINI_MODEL
        if chat_gpt_key:
            OPENAI_API_KEY = chat_gpt_key
        OPENAI_MODEL = chat_gpt_model
        if gemini_key:
            GEMINI_API_KEY = gemini_key
        GEMINI_MODEL = gemini_model
        
        print(f"[ADMIN-WEB] API keys updated by {username}")
        return jsonify({'success': True, 'message': 'API keys saved'})
    else:
        return jsonify({'error': 'Failed to save'}), 500


# ============================================================
# DECK MANAGEMENT & USER CARDS
# ============================================================

# Paths for deck and user cards storage
DECKS_PATH = os.path.join(DATA_DIR, "decks.json")
USER_CARDS_DIR = os.path.join(DATA_DIR, "user_cards")
DECK_CARDS_DIR = os.path.join(DATA_DIR, "deck_cards")
DECK_ACCESS_PATH = os.path.join(DATA_DIR, "deck_access.json")
DECK_CONFIG_PATH = os.path.join(DATA_DIR, "deck_config.json")


def _load_deck_config() -> Dict[str, Any]:
    """Load global deck configuration."""
    if not os.path.exists(DECK_CONFIG_PATH):
        return {
            "newUsersGetBuiltInDecks": True,  # New users get built-in decks by default
            "allowNonAdminDeckEdits": True,   # Non-admins can edit built-in/unlocked decks
            "builtInDecks": ["kenpo"]          # List of built-in deck IDs
        }
    try:
        with open(DECK_CONFIG_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"newUsersGetBuiltInDecks": True, "allowNonAdminDeckEdits": True, "builtInDecks": ["kenpo"]}


def _save_deck_config(config: Dict[str, Any]) -> None:
    """Save global deck configuration."""
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(DECK_CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(config, f, ensure_ascii=False, indent=2)


def _load_deck_access() -> Dict[str, Any]:
    """Load deck access data (invite codes, user unlocks, user overrides)."""
    if not os.path.exists(DECK_ACCESS_PATH):
        return {
            "inviteCodes": {},      # code -> {deckId, createdAt, uses}
            "userUnlocks": {},      # userId -> [deckIds...]
            "userOverrides": {},    # userId -> {deckId -> {cards added/removed, settings}}
            "userBuiltInDisabled": []  # userIds who have built-in decks disabled
        }
    try:
        with open(DECK_ACCESS_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        # Ensure all keys exist
        data.setdefault("inviteCodes", {})
        data.setdefault("userUnlocks", {})
        data.setdefault("userOverrides", {})
        data.setdefault("userBuiltInDisabled", [])
        return data
    except Exception:
        return {"inviteCodes": {}, "userUnlocks": {}, "userOverrides": {}, "userBuiltInDisabled": []}


def _save_deck_access(data: Dict[str, Any]) -> None:
    """Save deck access data."""
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(DECK_ACCESS_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _generate_invite_code() -> str:
    """Generate a simple invite code like 'KenpoStudy409'."""
    import random
    words = ["Study", "Learn", "Flash", "Card", "Deck", "Quiz", "Train", "Vocab", "Word", "Smart"]
    word = random.choice(words)
    num = random.randint(100, 999)
    return f"Kenpo{word}{num}"


def _get_user_accessible_decks(user_id: str) -> List[str]:
    """Get list of deck IDs accessible to a user (built-in + unlocked)."""
    config = _load_deck_config()
    access = _load_deck_access()
    
    accessible = []
    
    # Check if user has built-in decks disabled
    if user_id not in access.get("userBuiltInDisabled", []):
        # Add built-in decks
        accessible.extend(config.get("builtInDecks", ["kenpo"]))
    
    # Add unlocked decks
    user_unlocks = access.get("userUnlocks", {}).get(user_id, [])
    accessible.extend(user_unlocks)
    
    return list(set(accessible))  # Dedupe

def _load_decks(user_id: str = None, include_all: bool = False) -> List[Dict[str, Any]]:
    """Load deck definitions.
    
    Args:
        user_id: If provided, filter to decks accessible by this user
        include_all: If True (admin mode), return all decks regardless of access
    """
    config = _load_deck_config()
    access = _load_deck_access()
    built_in_deck_ids = config.get("builtInDecks", ["kenpo"])
    
    # Define built-in decks
    built_in_decks = {
        "kenpo": {
            "id": "kenpo",
            "name": "Kenpo Vocabulary",
            "description": "Korean martial arts terminology for Kenpo students",
            "isDefault": False,
            "isBuiltIn": True,
            "sourceFile": "kenpo_words.json",
            "cardCount": 88,
            "createdAt": 0,
            "updatedAt": 0,
            "logoPath": "/res/decklogos/kenpo_vocabulary.png"
        }
    }
    
    all_decks = []
    
    # Load saved decks from file
    if os.path.exists(DECKS_PATH):
        try:
            with open(DECKS_PATH, "r", encoding="utf-8") as f:
                saved_decks = json.load(f)
            if isinstance(saved_decks, list):
                all_decks = saved_decks
        except Exception:
            pass
    
    # Ensure built-in decks are in the list with correct properties
    deck_ids = [d.get("id") for d in all_decks]
    for bid in built_in_deck_ids:
        if bid in built_in_decks:
            if bid not in deck_ids:
                all_decks.insert(0, built_in_decks[bid])
            else:
                # Update existing to ensure built-in properties
                for i, d in enumerate(all_decks):
                    if d.get("id") == bid:
                        all_decks[i] = {**built_in_decks[bid], **{"isDefault": d.get("isDefault", False)}}
                        break
    
    # If include_all (admin), return everything
    if include_all:
        return all_decks
    
    # If no user, return just built-in decks
    if not user_id:
        return [d for d in all_decks if d.get("id") in built_in_deck_ids]
    
    # Get user's accessible decks
    accessible_ids = _get_user_accessible_decks(user_id)
    
    # Also include user-created decks (those not in built-in list and not locked)
    # User can see: their unlocked decks + decks they created
    result = []
    for d in all_decks:
        did = d.get("id")
        # Built-in deck that user has access to
        if did in accessible_ids:
            # Mark as unlocked if not in original built-in list for this user
            d_copy = dict(d)
            user_has_built_in = user_id not in access.get("userBuiltInDisabled", [])
            if did in built_in_deck_ids and user_has_built_in:
                d_copy["accessType"] = "built-in"
            else:
                d_copy["accessType"] = "unlocked"
            result.append(d_copy)
        # Non-built-in deck (user created or shared)
        elif not d.get("isBuiltIn"):
            # Non-built-in deck: only include if owned by user.
            owner_id = d.get("ownerId") or d.get("createdBy")
            if owner_id == user_id or (not owner_id and _user_has_legacy_cards_for_deck(user_id, d.get("id"))):
                d_copy = dict(d)
                d_copy["accessType"] = "owned"
                if not owner_id:
                    d_copy["accessType"] = "owned_legacy"
                result.append(d_copy)
    
    return result


def _save_decks(decks: List[Dict[str, Any]]) -> None:
    """Save deck definitions."""
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(DECKS_PATH, "w", encoding="utf-8") as f:
        json.dump(decks, f, ensure_ascii=False, indent=2)


def _user_cards_path(user_id: str) -> str:
    """Get path to user's custom cards file."""
    udir = os.path.join(USER_CARDS_DIR, user_id)
    os.makedirs(udir, exist_ok=True)
    return os.path.join(udir, "cards.json")


def _load_user_cards(user_id: str) -> List[Dict[str, Any]]:
    """Load user-created cards."""
    path = _user_cards_path(user_id)
    if not os.path.exists(path):
        return []
    try:
        with open(path, "r", encoding="utf-8") as f:
            cards = json.load(f)
        return cards if isinstance(cards, list) else []
    except Exception:
        return []


def _save_user_cards(user_id: str, cards: List[Dict[str, Any]]) -> None:
    """Save user-created cards."""
    path = _user_cards_path(user_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cards, f, ensure_ascii=False, indent=2)




# ----------------------------
# Deck-scoped card storage
# ----------------------------

def _deck_cards_path(deck_id: str) -> str:
    """Get path to a deck's card file."""
    ddir = os.path.join(DECK_CARDS_DIR, deck_id)
    os.makedirs(ddir, exist_ok=True)
    return os.path.join(ddir, "cards.json")


def _load_deck_cards(deck_id: str) -> List[Dict[str, Any]]:
    path = _deck_cards_path(deck_id)
    if not os.path.exists(path):
        return []
    try:
        with open(path, "r", encoding="utf-8") as f:
            cards = json.load(f)
        return cards if isinstance(cards, list) else []
    except Exception:
        return []


def _save_deck_cards(deck_id: str, cards: List[Dict[str, Any]]) -> None:
    path = _deck_cards_path(deck_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cards, f, ensure_ascii=False, indent=2)


def _get_deck_by_id(deck_id: str) -> Optional[Dict[str, Any]]:
    decks = _load_decks(include_all=True)
    for d in decks:
        if d.get("id") == deck_id:
            return d
    return None


def _deck_owner_id(deck: Optional[Dict[str, Any]]) -> str:
    if not deck:
        return ""
    return str(deck.get("ownerId") or deck.get("createdBy") or "").strip()


def _user_has_legacy_cards_for_deck(user_id: str, deck_id: str) -> bool:
    """Legacy support: before deck ownership existed, non-built-in deck cards were stored per-user.
    If a deck has no ownerId/createdBy, we treat it as 'legacy' and allow access/edit for users who have cards for it.
    """
    try:
        cards = _load_user_cards(user_id)
        for c in cards:
            if str(c.get("deckId", "")).strip() == deck_id:
                return True
        return False
    except Exception:
        return False



def _is_admin_uid(user_id: str) -> bool:
    user = _get_user(user_id)
    username = user.get("username", "") if user else ""
    return _is_admin_user(username)


def _user_can_access_deck(user_id: str, deck_id: str) -> bool:
    if not user_id or not deck_id:
        return False

    config = _load_deck_config()
    access = _load_deck_access()
    built_in = set(config.get("builtInDecks", ["kenpo"]))

    # Built-in decks
    if deck_id in built_in:
        # userBuiltInDisabled disables built-ins entirely for that user
        return user_id not in access.get("userBuiltInDisabled", [])

    deck = _get_deck_by_id(deck_id)
    if not deck or deck.get("isBuiltIn"):
        return False

    owner_id = _deck_owner_id(deck)
    if owner_id == user_id:
        return True

    # Legacy (no owner stored yet): allow access if this user has cards for it
    if not owner_id and _user_has_legacy_cards_for_deck(user_id, deck_id):
        return True

    # Shared / unlocked via admin grant or invite code
    unlocked = set(access.get("userUnlocks", {}).get(user_id, []) or [])
    return deck_id in unlocked


def _user_can_edit_deck(user_id: str, deck_id: str) -> bool:
    """Whether this user can edit a non-built-in deck (metadata + cards)."""
    deck = _get_deck_by_id(deck_id)
    if not deck or deck.get("isBuiltIn"):
        return False
    owner_id = _deck_owner_id(deck)
    if owner_id == user_id:
        return True

    # Legacy (no owner stored yet): allow edit if this user has cards for it
    if not owner_id and _user_has_legacy_cards_for_deck(user_id, deck_id):
        return True

    # Admin override (useful for support)
    return _is_admin_uid(user_id)


def _migrate_user_deck_cards(user_id: str) -> None:
    """One-way migration: move non-kenpo cards from per-user storage to per-deck storage for decks owned by user."""
    try:
        cards = _load_user_cards(user_id)
        if not cards:
            return

        keep = []
        moved_by_deck: Dict[str, List[Dict[str, Any]]] = {}
        for c in cards:
            did = str(c.get("deckId", "kenpo")).strip() or "kenpo"
            if did == "kenpo":
                keep.append(c)
                continue

            deck = _get_deck_by_id(did)
            if _deck_owner_id(deck) == user_id:
                moved_by_deck.setdefault(did, []).append(c)
            else:
                # If deck isn't owned by this user, keep it in per-user file to avoid data loss.
                keep.append(c)

        # Merge into deck files
        for did, moved in moved_by_deck.items():
            existing = _load_deck_cards(did)
            existing_ids = set(str(x.get("id")) for x in existing)
            for c in moved:
                if str(c.get("id")) not in existing_ids:
                    existing.append(c)
            _save_deck_cards(did, existing)

        if len(keep) != len(cards):
            _save_user_cards(user_id, keep)
    except Exception:
        # Don't block app operation on migration issues
        return


def _owned_decks_for_user(user_id: str) -> List[Dict[str, Any]]:
    decks = _load_decks(include_all=True)
    out = []
    for d in decks:
        if d.get("isBuiltIn"):
            continue
        if _deck_owner_id(d) == user_id:
            out.append(d)
    return out


def _find_editable_card(user_id: str, card_id: str) -> Tuple[Optional[str], Optional[str], List[Dict[str, Any]], int]:
    """Find a user-editable card. Returns (storage, deck_id, cards_list, index).
    storage: 'user' for kenpo personal cards, 'deck' for deck-scoped cards.
    """
    # 1) Search personal user_cards
    ucards = _load_user_cards(user_id)
    for i, c in enumerate(ucards):
        if c.get("id") == card_id:
            return "user", str(c.get("deckId", "kenpo")) or "kenpo", ucards, i

    # 2) Search owned deck cards
    for d in _owned_decks_for_user(user_id):
        did = d.get("id")
        dcards = _load_deck_cards(did)
        for i, c in enumerate(dcards):
            if c.get("id") == card_id:
                return "deck", did, dcards, i

    return None, None, [], -1
def _generate_card_id() -> str:
    """Generate a unique 16-character hex ID for a new card."""
    return uuid.uuid4().hex[:16]


@app.get("/api/decks")
def api_get_decks():
    """Get all available decks for the current user."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    _migrate_user_deck_cards(uid)

    decks = _load_decks(user_id=uid)

    # Update card counts for non-built-in decks (deck-scoped)
    for deck in decks:
        if not deck.get("isBuiltIn"):
            did = deck.get("id")
            dmeta = _get_deck_by_id(did)
            if not _deck_owner_id(dmeta):
                # Legacy: still stored per-user
                uc = _load_user_cards(uid)
                deck["cardCount"] = len([c for c in uc if c.get("deckId") == did])
            else:
                deck["cardCount"] = len(_load_deck_cards(did))
    
    return jsonify(decks)


@app.post("/api/decks")
def api_create_deck():
    """Create a new user deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    name = str(data.get("name", "")).strip()
    description = str(data.get("description", "")).strip()
    
    if not name:
        return jsonify({"error": "Deck name is required"}), 400
    
    decks = _load_decks(include_all=True)

    # Enforce per-user uniqueness for user-created decks (case-insensitive).
    # Also prevent collisions with built-in deck names.
    lower_name = name.lower()
    for d in decks:
        dname = str(d.get("name", "")).strip().lower()
        if not dname:
            continue
        if d.get("isBuiltIn") and dname == lower_name:
            return jsonify({"error": "This deck name is reserved"}), 400
        if (not d.get("isBuiltIn")) and (_deck_owner_id(d) == uid) and dname == lower_name:
            return jsonify({"error": "You already have a deck with this name"}), 400
    
    new_deck = {
        "id": f"deck_{uuid.uuid4().hex[:8]}",
        "name": name,
        "description": description,
        "isDefault": False,
        "isBuiltIn": False,
        "sourceFile": None,
        "cardCount": 0,
        "ownerId": uid,
        "createdBy": uid,  # Back-compat
        "createdAt": int(time.time()),
        "updatedAt": int(time.time()),
}
    
    decks.append(new_deck)
    _save_decks(decks)
    
    return jsonify(new_deck)


@app.post("/api/decks/<deck_id>")
def api_update_deck(deck_id: str):
    """Update a user-created deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    new_name = str(data.get("name", "")).strip()
    new_desc = str(data.get("description", "")).strip()
    
    if not new_name:
        return jsonify({"error": "Deck name is required"}), 400
    
    decks = _load_decks(include_all=True)
    
    # Find deck
    deck_to_update = None
    for d in decks:
        if d.get("id") == deck_id:
            deck_to_update = d
            break
    
    if not deck_to_update:
        return jsonify({"error": "Deck not found"}), 404
    
    if deck_to_update.get("isBuiltIn"):
        return jsonify({"error": "Cannot edit built-in deck"}), 400

    if not _user_can_edit_deck(uid, deck_id):
        return jsonify({"error": "not_owner"}), 403

    # If this is a legacy deck with no owner stored yet, claim it for this user on first edit.
    if not _deck_owner_id(deck_to_update):
        deck_to_update["ownerId"] = uid
        deck_to_update["createdBy"] = uid

    # Enforce per-owner uniqueness (case-insensitive), and avoid built-in collisions
    decks_all = _load_decks(include_all=True)
    lower_name = new_name.lower()
    for d in decks_all:
        if d.get("id") == deck_id:
            continue
        dname = str(d.get("name", "")).strip().lower()
        if not dname:
            continue
        if d.get("isBuiltIn") and dname == lower_name:
            return jsonify({"error": "This deck name is reserved"}), 400
        if (not d.get("isBuiltIn")) and (_deck_owner_id(d) == uid) and dname == lower_name:
            return jsonify({"error": "You already have a deck with this name"}), 400

    # Update fields
    deck_to_update["name"] = new_name
    deck_to_update["description"] = new_desc
    

    # Optional logoPath update (user decks only)
    if "logoPath" in data:
        deck_to_update["logoPath"] = data.get("logoPath")
    _save_decks(decks)
    
    return jsonify(deck_to_update)




@app.post("/api/decks/<deck_id>/upload_logo")
def api_upload_deck_logo(deck_id: str):
    """Upload / set a deck logo image for a user-created deck.

    Accepts multipart/form-data with field name: file
    Saves the image under static/res/decklogos/user/<user_id>/...
    """
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401

    decks = _load_decks(include_all=True)
    deck = next((d for d in decks if d.get("id") == deck_id), None)
    if not deck:
        return jsonify({"error": "Deck not found"}), 404

    # Built-in decks are not editable here (set built-in logos in decks.json)
    if deck.get("isBuiltIn"):
        return jsonify({"error": "Cannot upload logo for built-in deck"}), 400

    if not _user_can_edit_deck(uid, deck_id):
        return jsonify({"error": "not_owner"}), 403

    # Legacy deck: claim ownership on first edit action
    if not _deck_owner_id(deck):
        deck["ownerId"] = uid
        deck["createdBy"] = uid
        _save_decks(decks)

    if "file" not in request.files:
        return jsonify({"error": "No file uploaded"}), 400

    f = request.files["file"]
    if not f or not getattr(f, "filename", ""):
        return jsonify({"error": "Invalid file"}), 400

    filename = secure_filename(f.filename)
    ext = os.path.splitext(filename)[1].lower()
    if ext not in [".png", ".jpg", ".jpeg", ".webp"]:
        return jsonify({"error": "Unsupported file type. Use PNG/JPG/WEBP."}), 400

    # Save
    out_dir = os.path.join("static", "res", "decklogos", "user", uid)
    os.makedirs(out_dir, exist_ok=True)

    ts = int(time.time())
    out_name = f"{deck_id}_{ts}{ext}"
    out_path = os.path.join(out_dir, out_name)
    f.save(out_path)

    url_path = f"/res/decklogos/user/{uid}/{out_name}"
    deck["logoPath"] = url_path
    deck["updatedAt"] = int(time.time())
    _save_decks(decks)

    return jsonify({"success": True, "deckId": deck_id, "logoPath": url_path})


@app.delete("/api/decks/<deck_id>")
def api_delete_deck(deck_id: str):
    """Delete a user-created deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    decks = _load_decks(include_all=True)
    
    # Find and validate deck
    deck_to_delete = None
    for d in decks:
        if d.get("id") == deck_id:
            deck_to_delete = d
            break
    
    if not deck_to_delete:
        return jsonify({"error": "Deck not found"}), 404
    
    if deck_to_delete.get("isBuiltIn"):
        return jsonify({"error": "Cannot delete built-in deck"}), 400

    if not _user_can_edit_deck(uid, deck_id):
        return jsonify({"error": "not_owner"}), 403

    # Remove deck definition
    decks = [d for d in decks if d.get("id") != deck_id]
    _save_decks(decks)

    # Remove deck-scoped cards on disk
    try:
        ddir = os.path.join(DECK_CARDS_DIR, deck_id)
        if os.path.isdir(ddir):
            import shutil
            shutil.rmtree(ddir, ignore_errors=True)
    except Exception:
        pass

    # Clean up access grants & invite codes referencing this deck
    try:
        access = _load_deck_access()
        # Remove invite codes for this deck
        inv = access.get("inviteCodes", {}) or {}
        for code in list(inv.keys()):
            if inv.get(code, {}).get("deckId") == deck_id:
                inv.pop(code, None)
        access["inviteCodes"] = inv

        # Remove unlocks for this deck from all users
        unlocks = access.get("userUnlocks", {}) or {}
        for uid2 in list(unlocks.keys()):
            if isinstance(unlocks.get(uid2), list) and deck_id in unlocks[uid2]:
                unlocks[uid2] = [x for x in unlocks[uid2] if x != deck_id]
        access["userUnlocks"] = unlocks

        _save_deck_access(access)
    except Exception:
        pass

    # Remove any leftover per-user cards pointing at this deck (legacy)
    _migrate_user_deck_cards(uid)
    user_cards = _load_user_cards(uid)
    user_cards = [c for c in user_cards if c.get("deckId") != deck_id]
    _save_user_cards(uid, user_cards)

    return jsonify({"success": True, "deleted": deck_id})


@app.post("/api/decks/<deck_id>/set_default")
def api_set_default_deck(deck_id: str):
    """Set a deck as the default startup deck."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    decks = _load_decks(include_all=True)
    
    # Check deck exists
    deck_found = False
    for d in decks:
        if d.get("id") == deck_id:
            deck_found = True
            break
    
    if not deck_found:
        return jsonify({"error": "Deck not found"}), 404

    if not _user_can_access_deck(uid, deck_id):
        return jsonify({"error": "deck_not_accessible"}), 403

    # Clear all isDefault flags, then set the new one
    for d in decks:
        d["isDefault"] = (d.get("id") == deck_id)
    
    _save_decks(decks)
    
    # Also save as the user's active deck preference
    progress = load_progress(uid)
    settings = progress.get("__settings__", _default_settings())
    settings = _set_active_deck_id(settings, deck_id)
    progress["__settings__"] = settings
    save_progress(uid, progress)
    
    return jsonify({"success": True, "defaultDeckId": deck_id})


@app.get("/api/user_cards")
def api_get_user_cards():
    """Get user-editable cards.

    - deck_id=kenpo: returns this user's personal cards attached to built-in kenpo deck.
    - deck_id=<user deck>: returns deck-scoped cards (owner/admin only).
    - no deck_id: returns kenpo personal + all cards for decks owned by this user.
    """
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401

    deck_id = str(request.args.get("deck_id", "") or "").strip()
    _migrate_user_deck_cards(uid)

    if deck_id:
        if deck_id == "kenpo":
            cards = _load_user_cards(uid)
            cards = [c for c in cards if (c.get("deckId", "kenpo") == "kenpo")]
            return jsonify(cards)

        if not _user_can_edit_deck(uid, deck_id):
            return jsonify({"error": "not_owner"}), 403

        deck_meta = _get_deck_by_id(deck_id)
        if not _deck_owner_id(deck_meta):
            # Legacy: still per-user
            cards = _load_user_cards(uid)
            cards = [c for c in cards if c.get("deckId") == deck_id]
            return jsonify(cards)

        return jsonify(_load_deck_cards(deck_id))

    # No deck filter: return everything this user can edit (kenpo personal + owned decks)
    out = []
    u_cards = _load_user_cards(uid)
    out.extend([c for c in u_cards if (c.get("deckId", "kenpo") == "kenpo")])

    for d in _owned_decks_for_user(uid):
        did = d.get("id")
        out.extend(_load_deck_cards(did))

    return jsonify(out)


@app.post("/api/user_cards")
def api_add_user_card():
    """Add a new user-created card."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    term = str(data.get("term", "")).strip()
    meaning = str(data.get("meaning", "")).strip()
    pron = str(data.get("pron", "")).strip()
    group = str(data.get("group", "")).strip()
    deck_id = str(data.get("deckId", "kenpo")).strip()
    
    if not term:
        return jsonify({"error": "Term is required"}), 400
    if not meaning:
        return jsonify({"error": "Definition is required"}), 400
    
    new_card = {
        "id": _generate_card_id(),
        "term": term,
        "meaning": meaning,
        "pron": pron,
        "group": group,
        "subgroup": "",
        "deckId": deck_id,
        "isUserCreated": True,
        "createdAt": int(time.time()),
        "updatedAt": int(time.time()),
}

    _migrate_user_deck_cards(uid)

    if deck_id == "kenpo":
        cards = _load_user_cards(uid)
        cards.append(new_card)
        _save_user_cards(uid, cards)
        return jsonify(new_card)

    # Non-built-in deck: cards are stored per-deck once ownership is established.
    if not _user_can_edit_deck(uid, deck_id):
        return jsonify({"error": "not_owner"}), 403

    deck_meta = _get_deck_by_id(deck_id)
    if not _deck_owner_id(deck_meta):
        # Legacy: still stored per-user until a deck owner is set
        cards = _load_user_cards(uid)
        cards.append(new_card)
        _save_user_cards(uid, cards)
        return jsonify(new_card)

    cards = _load_deck_cards(deck_id)
    cards.append(new_card)
    _save_deck_cards(deck_id, cards)

    return jsonify(new_card)


@app.put("/api/user_cards/<card_id>")
def api_update_user_card(card_id: str):
    """Update a user-editable card (kenpo personal or owned deck cards)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401

    data = request.get_json() or {}
    _migrate_user_deck_cards(uid)

    storage, current_deck_id, cards, idx = _find_editable_card(uid, card_id)
    if storage is None or idx < 0:
        return jsonify({"error": "Card not found"}), 404

    card = dict(cards[idx])
    target_deck_id = str(data.get("deckId", current_deck_id) or current_deck_id).strip() or "kenpo"

    # Validate move target
    if target_deck_id != "kenpo" and not _user_can_edit_deck(uid, target_deck_id):
        return jsonify({"error": "not_owner"}), 403

    # Update fields
    if "term" in data:
        card["term"] = str(data["term"]).strip()
    if "meaning" in data:
        card["meaning"] = str(data["meaning"]).strip()
    if "pron" in data:
        card["pron"] = str(data["pron"]).strip()
    if "group" in data:
        card["group"] = str(data["group"]).strip()
    card["deckId"] = target_deck_id
    card["updatedAt"] = int(time.time())

    # If deck changed, move between stores
    if target_deck_id != current_deck_id:
        # Remove from old store
        del cards[idx]
        if storage == "user":
            _save_user_cards(uid, cards)
        else:
            _save_deck_cards(current_deck_id, cards)

        # Add to new store
        if target_deck_id == "kenpo":
            new_store = _load_user_cards(uid)
            new_store.append(card)
            _save_user_cards(uid, new_store)
        else:
            tmeta = _get_deck_by_id(target_deck_id)
            if not _deck_owner_id(tmeta):
                # Legacy target deck: keep per-user
                new_store = _load_user_cards(uid)
                new_store.append(card)
                _save_user_cards(uid, new_store)
            else:
                new_store = _load_deck_cards(target_deck_id)
                new_store.append(card)
                _save_deck_cards(target_deck_id, new_store)

        return jsonify(card)

    # Same deck: update in place
    cards[idx] = card
    if storage == "user":
        _save_user_cards(uid, cards)
    else:
        _save_deck_cards(current_deck_id, cards)

    return jsonify(card)


@app.delete("/api/user_cards/<card_id>")
def api_delete_user_card(card_id: str):
    """Delete a user-editable card (kenpo personal or owned deck cards)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401

    _migrate_user_deck_cards(uid)

    storage, deck_id, cards, idx = _find_editable_card(uid, card_id)
    if storage is None or idx < 0:
        return jsonify({"error": "Card not found"}), 404

    del cards[idx]
    if storage == "user":
        _save_user_cards(uid, cards)
    else:
        _save_deck_cards(deck_id, cards)

    return jsonify({"success": True, "deleted": card_id})


@app.post("/api/ai/generate_definition")
def api_ai_generate_definition():
    """Generate definition options using AI."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    term = str(data.get("term", "")).strip()
    deck_name = str(data.get("deckName", "")).strip() or "General vocabulary"
    deck_desc = str(data.get("deckDescription", "")).strip()
    
    if not term:
        return jsonify({"error": "Term is required"}), 400
    
    if not OPENAI_API_KEY and not GEMINI_API_KEY:
        return jsonify({"error": "No AI provider configured"}), 400
    
    # Build context from deck info
    context = f"{deck_name}"
    if deck_desc:
        context += f" ({deck_desc})"
    
    prompt = f"""For the vocabulary term "{term}" in the context of {context}:

Provide 3 translation/definition options. Keep them SHORT and LITERAL:
- For foreign language words: give the English translation (e.g., "Hola" = "Hello")
- For English words: give a brief definition (1-5 words)
- For technical terms: give the simple meaning

Return ONLY a JSON array with 3 short strings. Example: ["Hello", "Hi", "Greetings"]
Do NOT include explanations or full sentences."""
    
    try:
        if OPENAI_API_KEY:
            result = _call_openai_chat(prompt, OPENAI_MODEL, OPENAI_API_KEY)
        else:
            result = _call_gemini_chat(prompt, GEMINI_MODEL, GEMINI_API_KEY)
        
        # Parse JSON array from result
        import re
        match = re.search(r'\[.*\]', result, re.DOTALL)
        if match:
            definitions = json.loads(match.group())
            return jsonify({"definitions": definitions})
        else:
            return jsonify({"definitions": [result.strip()]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.post("/api/ai/generate_pronunciation")
def api_ai_generate_pronunciation():
    """Generate pronunciation using AI."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    term = str(data.get("term", "")).strip()
    
    if not term:
        return jsonify({"error": "Term is required"}), 400
    
    if not OPENAI_API_KEY and not GEMINI_API_KEY:
        return jsonify({"error": "No AI provider configured"}), 400
    
    prompt = f"""Provide the English phonetic pronunciation for the term "{term}".
Use simple syllables separated by hyphens that an English speaker can read.
Return ONLY the pronunciation guide, nothing else. Example: tay-kwon-doh"""
    
    try:
        if OPENAI_API_KEY:
            result = _call_openai_chat(prompt, OPENAI_MODEL, OPENAI_API_KEY)
        else:
            result = _call_gemini_chat(prompt, GEMINI_MODEL, GEMINI_API_KEY)
        
        return jsonify({"pronunciation": result.strip()})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.post("/api/ai/generate_group")
def api_ai_generate_group():
    """Generate group suggestions using AI."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    term = str(data.get("term", "")).strip()
    meaning = str(data.get("meaning", "")).strip()
    existing_groups = data.get("existingGroups", [])
    
    if not term:
        return jsonify({"error": "Term is required"}), 400
    
    if not OPENAI_API_KEY and not GEMINI_API_KEY:
        return jsonify({"error": "No AI provider configured"}), 400
    
    groups_str = ", ".join(existing_groups[:20]) if existing_groups else "None yet"
    meaning_context = f" (meaning: {meaning})" if meaning else ""
    
    prompt = f"""Suggest 3 category/group names for the vocabulary term "{term}"{meaning_context}.
Existing groups in the deck: {groups_str}
Prefer existing groups if they fit. Return ONLY a JSON array with 3 strings. Example: ["Category1", "Category2", "Category3"]"""
    
    try:
        if OPENAI_API_KEY:
            result = _call_openai_chat(prompt, OPENAI_MODEL, OPENAI_API_KEY)
        else:
            result = _call_gemini_chat(prompt, GEMINI_MODEL, GEMINI_API_KEY)
        
        import re
        match = re.search(r'\[.*\]', result, re.DOTALL)
        if match:
            groups = json.loads(match.group())
            return jsonify({"groups": groups})
        else:
            return jsonify({"groups": [result.strip()]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.post("/api/ai/generate_deck")
def api_ai_generate_deck():
    """Generate flashcard deck from keywords, photo, or document using AI."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    if not OPENAI_API_KEY and not GEMINI_API_KEY:
        return jsonify({"error": "No AI provider configured"}), 400
    
    data = request.get_json() or {}
    gen_type = data.get("type", "keywords")
    max_cards = min(int(data.get("maxCards", 20)), 200)
    
    try:
        if gen_type == "keywords":
            keywords = str(data.get("keywords", "")).strip()
            if not keywords:
                return jsonify({"error": "Keywords required"}), 400
            print(f"[AI GEN] Generating {max_cards} cards for keywords: {keywords[:100]}")
            cards = _ai_generate_from_keywords(keywords, max_cards)
            print(f"[AI GEN] Generated {len(cards)} cards")
        
        elif gen_type == "photo":
            image_data = data.get("imageData", "")
            if not image_data:
                return jsonify({"error": "Image data required"}), 400
            print(f"[AI GEN] Generating from photo, max {max_cards} cards")
            cards = _ai_generate_from_image(image_data, max_cards)
            print(f"[AI GEN] Generated {len(cards)} cards from photo")
        
        elif gen_type == "document":
            doc = data.get("document", {})
            if not doc.get("content"):
                return jsonify({"error": "Document content required"}), 400
            print(f"[AI GEN] Generating from document: {doc.get('name', 'unknown')}")
            cards = _ai_generate_from_document(doc, max_cards)
            print(f"[AI GEN] Generated {len(cards)} cards from document")
        
        else:
            return jsonify({"error": "Invalid generation type"}), 400
        
        return jsonify({"cards": cards})
    
    except Exception as e:
        print(f"[AI GEN ERROR] {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


def _ai_generate_from_keywords(keywords: str, max_cards: int) -> list:
    """Generate flashcards from search keywords."""
    prompt = f"""Generate exactly {max_cards} vocabulary flashcards about: {keywords}

IMPORTANT: For foreign language vocabulary, definitions should be SHORT LITERAL TRANSLATIONS.
Example for Spanish: "Hola" -> definition: "Hello" (NOT a long explanation)

For each card provide:
- term: the vocabulary word in the target language
- definition: SHORT English translation or meaning (1-5 words max)
- pronunciation: phonetic guide (e.g., "oh-lah"), or empty string if obvious
- group: category (e.g., "Greetings", "Numbers", "Colors")

Respond ONLY with valid JSON, no markdown or explanation:
{{"cards": [
    {{"term": "Hola", "definition": "Hello", "pronunciation": "oh-lah", "group": "Greetings"}},
    {{"term": "Adiós", "definition": "Goodbye", "pronunciation": "ah-dee-ohs", "group": "Greetings"}}
]}}"""
    
    return _parse_ai_cards_response(_call_ai_chat(prompt))


def _ai_generate_from_image(image_data: str, max_cards: int) -> list:
    """Generate flashcards from image using vision API."""
    # Extract base64 data if it's a data URL
    if "," in image_data:
        image_data = image_data.split(",", 1)[1]
    
    prompt = f"""Analyze this image and extract educational content to create up to {max_cards} flashcards.
Look for: vocabulary terms, definitions, concepts, diagrams with labels, or any study material.

For each item found, create a flashcard with:
- term: the word or concept
- definition: clear explanation
- pronunciation: phonetic guide if applicable, or empty string
- group: category for organization

Respond ONLY with valid JSON, no markdown:
{{"cards": [
    {{"term": "Term", "definition": "Definition", "pronunciation": "", "group": "Category"}}
]}}"""
    
    # Use vision-capable model
    if OPENAI_API_KEY:
        result = _call_openai_vision(prompt, image_data)
    elif GEMINI_API_KEY:
        result = _call_gemini_vision(prompt, image_data)
    else:
        return []
    
    return _parse_ai_cards_response(result)


def _ai_generate_from_document(doc: dict, max_cards: int) -> list:
    """Generate flashcards from document content."""
    content = doc.get("content", "")
    doc_type = doc.get("type", "text/plain")
    
    # For PDF base64, we need vision API
    if doc_type == "application/pdf" and content.startswith("data:"):
        if "," in content:
            content = content.split(",", 1)[1]
        
        prompt = f"""Analyze this PDF document and extract educational content to create up to {max_cards} flashcards.
Extract key terms, definitions, concepts, and important facts.

For each item, create a flashcard with:
- term: the word or concept  
- definition: clear explanation
- pronunciation: phonetic guide if applicable, or empty string
- group: category for organization

Respond ONLY with valid JSON:
{{"cards": [{{"term": "Term", "definition": "Definition", "pronunciation": "", "group": "Category"}}]}}"""
        
        if OPENAI_API_KEY:
            result = _call_openai_vision(prompt, content, "application/pdf")
        elif GEMINI_API_KEY:
            result = _call_gemini_vision(prompt, content, "application/pdf")
        else:
            return []
        
        return _parse_ai_cards_response(result)
    
    # For text content, use regular chat
    # Truncate if too long
    if len(content) > 15000:
        content = content[:15000] + "...[truncated]"
    
    prompt = f"""Analyze this document and create up to {max_cards} educational flashcards from its content:

---DOCUMENT START---
{content}
---DOCUMENT END---

For each key term/concept found, create a flashcard with:
- term: the word or concept
- definition: clear explanation based on the document
- pronunciation: phonetic guide if applicable, or empty string
- group: category for organization

Respond ONLY with valid JSON:
{{"cards": [{{"term": "Term", "definition": "Definition", "pronunciation": "", "group": "Category"}}]}}"""
    
    return _parse_ai_cards_response(_call_ai_chat(prompt))


def _call_ai_chat(prompt: str) -> str:
    """Call the configured AI chat API."""
    if OPENAI_API_KEY:
        return _call_openai_chat(prompt, OPENAI_MODEL, OPENAI_API_KEY)
    elif GEMINI_API_KEY:
        return _call_gemini_chat(prompt, GEMINI_MODEL, GEMINI_API_KEY)
    else:
        raise Exception("No AI provider configured")


def _call_openai_vision(prompt: str, image_data: str, media_type: str = "image/jpeg") -> str:
    """Call OpenAI with vision capability."""
    url = f"{OPENAI_API_BASE}/v1/chat/completions"
    
    # Determine the correct media type prefix
    if media_type == "application/pdf":
        data_url = f"data:application/pdf;base64,{image_data}"
    else:
        data_url = f"data:image/jpeg;base64,{image_data}"
    
    payload = {
        "model": "gpt-4o",  # Use vision-capable model
        "messages": [{
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": data_url}}
            ]
        }],
        "max_tokens": 4000,
        "temperature": 0.7
    }
    
    r = requests.post(
        url,
        headers={"Authorization": f"Bearer {OPENAI_API_KEY}", "Content-Type": "application/json"},
        json=payload,
        timeout=60
    )
    
    if r.status_code != 200:
        raise Exception(f"OpenAI error: {r.status_code}")
    
    data = r.json()
    return data.get("choices", [{}])[0].get("message", {}).get("content", "")


def _call_gemini_vision(prompt: str, image_data: str, media_type: str = "image/jpeg") -> str:
    """Call Gemini with vision capability."""
    # Use gemini-1.5-flash or gemini-1.5-pro for vision
    model = "gemini-1.5-flash"
    url = f"{GEMINI_API_BASE}/v1beta/models/{model}:generateContent"
    
    payload = {
        "contents": [{
            "parts": [
                {"text": prompt},
                {"inline_data": {"mime_type": media_type, "data": image_data}}
            ]
        }],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 4000
        }
    }
    
    r = requests.post(
        url,
        headers={"Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY},
        json=payload,
        timeout=60
    )
    
    if r.status_code != 200:
        raise Exception(f"Gemini error: {r.status_code}")
    
    data = r.json()
    try:
        return data["candidates"][0]["content"]["parts"][0]["text"]
    except:
        return ""


def _parse_ai_cards_response(response: str) -> list:
    """Parse AI response to extract cards array."""
    import re
    
    if not response:
        print("[AI PARSE] Empty response")
        return []
    
    print(f"[AI PARSE] Response length: {len(response)}, first 200 chars: {response[:200]}")
    
    # Try to find JSON object in response
    response = response.strip()
    
    # Remove markdown code blocks if present
    response = re.sub(r'^```json\s*', '', response)
    response = re.sub(r'^```\s*', '', response)
    response = re.sub(r'\s*```$', '', response)
    response = response.strip()
    
    cards = []
    
    try:
        # Try parsing as complete JSON
        data = json.loads(response)
        cards = data.get("cards", [])
        print(f"[AI PARSE] Parsed JSON directly, found {len(cards)} cards")
    except Exception as e:
        print(f"[AI PARSE] Direct JSON parse failed: {e}")
        # Try to find cards array in response
        match = re.search(r'"cards"\s*:\s*\[', response)
        if match:
            # Find matching closing bracket
            start = match.end() - 1
            bracket_count = 0
            end = start
            for i, c in enumerate(response[start:]):
                if c == '[':
                    bracket_count += 1
                elif c == ']':
                    bracket_count -= 1
                    if bracket_count == 0:
                        end = start + i + 1
                        break
            try:
                cards = json.loads(response[start:end])
                print(f"[AI PARSE] Extracted cards array, found {len(cards)} cards")
            except Exception as e2:
                print(f"[AI PARSE] Cards array parse failed: {e2}")
                cards = []
        else:
            print("[AI PARSE] No 'cards' key found in response")
            cards = []
    
    # Validate and normalize cards
    valid_cards = []
    for card in cards:
        if isinstance(card, dict) and card.get("term") and card.get("definition"):
            valid_cards.append({
                "term": str(card.get("term", "")).strip(),
                "definition": str(card.get("definition", "")).strip(),
                "pronunciation": str(card.get("pronunciation", "")).strip(),
                "group": str(card.get("group", "General")).strip() or "General"
            })
    
    return valid_cards


# --- Common public files (avoid 404 noise) ---
@app.get("/favicon.ico")
def favicon():
    return send_from_directory("static/res/webappicons", "favicon.ico")

@app.get("/robots.txt")
def robots():
    return send_from_directory("static", "robots.txt")

@app.get("/sitemap.xml")
def sitemap():
    return send_from_directory("static", "sitemap.xml")

# OPTION A (recommended for your project): put security.txt at static/.well-known/security.txt
@app.get("/.well-known/security.txt")
def security_txt():
    return send_from_directory("static/.well-known", "security.txt")

@app.get("/<path:filename>")
def static_files(filename):
    return send_from_directory("static", filename)


# ============ ANDROID SYNC API ============

@app.get("/api/vocabulary")
def api_get_vocabulary():
    """Get the kenpo vocabulary file (for Android app sync)."""
    # Return the canonical kenpo_words.json from data folder
    vocab_path = DATA_DIR / "kenpo_words.json"
    if vocab_path.exists():
        with open(vocab_path, "r", encoding="utf-8") as f:
            return jsonify(json.load(f))
    else:
        # Fallback: load from cards if vocab file doesn't exist
        cards, status = load_cards_cached()
        if status == "ok":
            # Convert to the format expected by Android
            vocab = []
            for c in cards:
                vocab.append({
                    "group": c.get("group", ""),
                    "subgroup": c.get("subgroup"),
                    "term": c.get("term", ""),
                    "pron": c.get("pron"),
                    "meaning": c.get("meaning", "")
                })
            return jsonify(vocab)
        return jsonify({"error": "Vocabulary not available"}), 500


@app.get("/api/sync/decks")
def api_sync_get_decks():
    """Get all decks for Android sync (requires auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    _migrate_user_deck_cards(uid)

    decks = _load_decks(user_id=uid)

    # Update card counts for non-built-in decks (deck-scoped)
    for deck in decks:
        if not deck.get("isBuiltIn"):
            did = deck.get("id")
            dmeta = _get_deck_by_id(did)
            if not _deck_owner_id(dmeta):
                # Legacy: still stored per-user
                uc = _load_user_cards(uid)
                deck["cardCount"] = len([c for c in uc if c.get("deckId") == did])
            else:
                deck["cardCount"] = len(_load_deck_cards(did))
    
    # Get user's active deck setting
    progress = load_progress(uid)
    settings = progress.get("__settings__", _default_settings())
    active_deck_id = _get_active_deck_id(settings, "kenpo")
    
    return jsonify({
        "decks": decks,
        "activeDeckId": active_deck_id
    })


@app.post("/api/sync/decks")
def api_sync_push_decks():
    """Push deck changes from Android (requires auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    incoming_decks = data.get("decks", [])
    active_deck_id = data.get("activeDeckId", "")
    
    if not incoming_decks:
        return jsonify({"error": "No decks provided"}), 400
    
    # Load existing decks
    existing_decks = _load_decks(include_all=True)
    existing_by_id = {d["id"]: d for d in existing_decks}
    
    # Merge incoming decks (Android wins for non-built-in decks)
    for incoming in incoming_decks:
        deck_id = incoming.get("id")
        if not deck_id:
            continue
        
        # Skip built-in decks
        if incoming.get("isBuiltIn"):
            continue
        
        if deck_id in existing_by_id:
            # Update existing
            existing = existing_by_id[deck_id]
            existing["name"] = incoming.get("name", existing["name"])
            existing["description"] = incoming.get("description", existing["description"])
            existing["isDefault"] = incoming.get("isDefault", existing.get("isDefault", False))
            existing["updatedAt"] = int(time.time())
        else:
            # Add new deck
            new_deck = {
                "id": deck_id,
                "name": incoming.get("name", "Untitled"),
                "description": incoming.get("description", ""),
                "isDefault": incoming.get("isDefault", False),
                "isBuiltIn": False,
                "sourceFile": None,
                "cardCount": incoming.get("cardCount", 0),
                "createdAt": incoming.get("createdAt", int(time.time())),
                "updatedAt": int(time.time()),
                "logoPath": incoming.get("logoPath", None),
            }
            existing_decks.append(new_deck)
    
    _save_decks(existing_decks)
    
    # Update active deck setting if provided
    if active_deck_id:
        progress = load_progress(uid)
        settings = progress.get("__settings__", _default_settings())
        settings = _set_active_deck_id(settings, active_deck_id)
        progress["__settings__"] = settings
        save_progress(uid, progress)
    
    return jsonify({"success": True, "deckCount": len(existing_decks)})


@app.get("/api/sync/user_cards")
def api_sync_get_user_cards():
    """Get all user-created cards for Android sync (requires auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    deck_id = request.args.get("deck_id", "")
    cards = _load_user_cards(uid)
    
    if deck_id:
        cards = [c for c in cards if c.get("deckId") == deck_id]
    
    return jsonify({"cards": cards})


@app.post("/api/sync/user_cards")
def api_sync_push_user_cards():
    """Push user-created cards from Android (requires auth)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    data = request.get_json() or {}
    incoming_cards = data.get("cards", [])
    deck_id = data.get("deckId", "")
    replace_all = data.get("replaceAll", False)  # If true, replace all cards for this deck
    
    if not incoming_cards:
        return jsonify({"error": "No cards provided"}), 400
    
    existing_cards = _load_user_cards(uid)
    
    if replace_all and deck_id:
        # Remove all existing cards for this deck
        existing_cards = [c for c in existing_cards if c.get("deckId") != deck_id]
    
    # Build lookup of existing cards by ID
    existing_by_id = {c["id"]: c for c in existing_cards}
    
    added = 0
    updated = 0
    
    for incoming in incoming_cards:
        card_id = incoming.get("id")
        if not card_id:
            # Generate new ID
            card_id = _generate_card_id()
            incoming["id"] = card_id
        
        if card_id in existing_by_id:
            # Update existing card
            existing = existing_by_id[card_id]
            existing["term"] = incoming.get("term", existing["term"])
            existing["meaning"] = incoming.get("meaning", existing["meaning"])
            existing["pron"] = incoming.get("pron", existing.get("pron", ""))
            existing["group"] = incoming.get("group", existing.get("group", ""))
            existing["deckId"] = incoming.get("deckId", existing.get("deckId", "kenpo"))
            existing["updatedAt"] = int(time.time())
            updated += 1
        else:
            # Add new card
            new_card = {
                "id": card_id,
                "term": incoming.get("term", ""),
                "meaning": incoming.get("meaning", ""),
                "pron": incoming.get("pron", ""),
                "group": incoming.get("group", ""),
                "subgroup": incoming.get("subgroup", ""),
                "deckId": incoming.get("deckId", deck_id or "kenpo"),
                "isUserCreated": True,
                "createdAt": incoming.get("createdAt", int(time.time())),
                "updatedAt": int(time.time()),
            }
            existing_cards.append(new_card)
            added += 1
    
    _save_user_cards(uid, existing_cards)
    
    return jsonify({
        "success": True,
        "added": added,
        "updated": updated,
        "totalCards": len(existing_cards)
    })


@app.delete("/api/sync/user_cards/<card_id>")
def api_sync_delete_user_card(card_id: str):
    """Delete a user-created card (for Android sync)."""
    uid = current_user_id()
    if not uid:
        return jsonify({"error": "Not logged in"}), 401
    
    cards = _load_user_cards(uid)
    original_count = len(cards)
    cards = [c for c in cards if c.get("id") != card_id]
    
    if len(cards) == original_count:
        return jsonify({"error": "Card not found"}), 404
    
    _save_user_cards(uid, cards)
    return jsonify({"success": True, "deleted": card_id})


def _load_api_keys_on_startup():
    """Load encrypted API keys from file and set global variables."""
    global OPENAI_API_KEY, OPENAI_MODEL, GEMINI_API_KEY, GEMINI_MODEL
    
    keys = _load_encrypted_api_keys()
    if keys:
        if keys.get('chatGptKey'):
            OPENAI_API_KEY = keys['chatGptKey']
        if keys.get('chatGptModel'):
            OPENAI_MODEL = keys['chatGptModel']
        if keys.get('geminiKey'):
            GEMINI_API_KEY = keys['geminiKey']
        if keys.get('geminiModel'):
            GEMINI_MODEL = keys['geminiModel']
        print(f"[STARTUP] Loaded encrypted API keys from {API_KEYS_PATH}")


if __name__ == "__main__":
    # Load encrypted API keys from file (overrides environment variables)
    _load_api_keys_on_startup()
    
    # Startup diagnostics (helps confirm your keys were picked up)
    try:
        openai_state = "SET" if bool(OPENAI_API_KEY) else "not set"
        gemini_state = "SET" if bool(GEMINI_API_KEY) else "not set"
        print(f"[AI] OpenAI key: {openai_state} • model: {OPENAI_MODEL if OPENAI_API_KEY else 'n/a'}")
        print(f"[AI] Gemini key: {gemini_state} • model: {GEMINI_MODEL if GEMINI_API_KEY else 'n/a'}")
    except Exception:
        pass

    app.run(host="0.0.0.0", port=PORT, debug=False)
