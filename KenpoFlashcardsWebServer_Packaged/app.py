import os
import json
import time
import hashlib
import re
import uuid
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

import requests

from flask import Flask, jsonify, request, send_from_directory, session
from werkzeug.security import generate_password_hash, check_password_hash

# =========================================================
# Kenpo Flashcards Web (Multi-user with Username/Password Auth)
# - Users create a profile with username/password
# - Progress + settings are stored per user on the server
# - Login from any device with the same credentials
# =========================================================

DEFAULT_KENPO_ROOT = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps"
# Fallback (only used if auto-discovery fails)
DEFAULT_KENPO_JSON_FALLBACK = r"C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsProject-v2\app\src\main\assets\kenpo_words.json"

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
            "breakdown_ai_provider": "openai"
        },
        "groups": {}
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
        return jsonify({"error": "invalid_credentials"}), 401

    _ensure_user_progress(user_id)
    session["user_id"] = user_id
    return jsonify({"ok": True, "user": _get_user(user_id)})


@app.post("/api/logout")
def api_logout():
    session.pop("user_id", None)
    return jsonify({"ok": True})


@app.get("/api/health")
def health():
    cards, status = load_cards_cached()
    v = get_version()
    return jsonify({"status": status, "cards_loaded": len(cards), "server_time": _now(), "version": v.get("version"), "build": v.get("build")})


@app.get("/api/version")
def api_version():
    return jsonify(get_version())
@app.get("/api/groups")
def api_groups():
    cards, status = load_cards_cached()
    if status != "ok":
        return jsonify({"error": status}), 500
    return jsonify(sorted({c["group"] for c in cards}))

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
    except Exception:
        return jsonify({"error": "reportlab not installed on server. Run: pip install reportlab"}), 500

    v = get_version()
    title = f"Kenpo Flashcards (Web) — User Guide  (v{v.get('version','')}, build {v.get('build','')})"
    lines = [
        "Created by Sidney Shelton (Sidscri@yahoo.com)",
        "",
        "Overview:",
        "• Study Kenpo vocabulary and track progress across devices.",
        "• Tabs: Unlearned / Unsure / Learned / All.",
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
        "Troubleshooting:",
        "• If sync seems stuck: logout/login and pull again.",
        "• Ensure server is running and reachable from your device.",
        "• Visit /admin for diagnostics.",
    ]

    from io import BytesIO
    buf = BytesIO()
    c = canvas.Canvas(buf, pagesize=letter)
    width, height = letter

    x = 0.8 * inch
    y = height - 1.0 * inch
    c.setTitle("Kenpo Flashcards User Guide")
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

@app.get("/api/whoami")
def whoami():
    ip = (request.headers.get("X-Forwarded-For") or request.remote_addr or "").split(",")[0].strip()
    return jsonify({
        "ip": ip,
        "ua": request.headers.get("User-Agent", "-"),
        "time": datetime.datetime.utcnow().isoformat() + "Z",
    })

@app.get("/api/settings")
def api_settings_get():
    uid, _ = require_user()
    if not uid:
        return jsonify({"error": "login_required"}), 401
    progress = load_progress(uid)
    scope = request.args.get("scope", "all")
    settings = _migrate_settings(progress.get("__settings__", _default_settings()))
    if scope == "all":
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
    cards, status = load_cards_cached()
    if status != "ok":
        return jsonify({"error": status}), 500

    progress = load_progress(uid)
    counts = {"active": 0, "unsure": 0, "learned": 0, "deleted": 0, "total": 0}
    for c in cards:
        if group and c["group"] != group:
            continue
        s = card_status(progress, c["id"])
        counts["total"] += 1
        counts[s] += 1
    return jsonify(counts)


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

    cards, status = load_cards_cached()
    if status != "ok":
        return jsonify({"error": status}), 500

    progress = load_progress(uid)
    out: List[Dict[str, Any]] = []
    for c in cards:
        if group and c["group"] != group:
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
    items.sort(key=lambda x: int(x.get("updated_at") or 0), reverse=True)
    return jsonify({"ok": True, "items": items})




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
            updated_at = int(value.get('updated_at') or 0)
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
                ua = int(v.get('updated_at') or 0)
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
                cur_updated = int(cur.get('updated_at') or 0)
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


# --- Common public files (avoid 404 noise) ---
@app.get("/favicon.ico")
def favicon():
    return send_from_directory("static", "favicon.ico")

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
