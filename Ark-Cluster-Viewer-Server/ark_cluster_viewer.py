#!/usr/bin/env python3
"""
ARK Cluster Character Viewer
Monitors a cluster folder and displays uploaded characters via web interface.
Access at http://192.168.0.129:8008
"""

import os
import re
import struct
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# Configuration - UPDATE THIS PATH to your cluster folder
CLUSTER_FOLDER = r"C:\ASA Servers\clusters\0d7d9e27-8c3d-4fa2-8064-b9478ecc548a"
# CLUSTER_FOLDER = "/path/to/cluster"  # Linux example

# Server save locations - UPDATE THESE PATHS
SERVER_PATHS = {
    'TheIsland': r"C:\ASA Servers\TheIsland\ShooterGame\Saved\SavedArks\TheIsland_WP",
    'TheCenter': r"C:\ASA Servers\TheCenter\ShooterGame\Saved\SavedArks\TheCenter_WP",
    'Valguero': r"C:\ASA Servers\Valguero\ShooterGame\Saved\SavedArks\Valguero_WP",
    'ScorchedEarth': r"C:\ASA Servers\ScorchedEarth\ShooterGame\Saved\SavedArks\ScorchedEarth_WP",
}

HOST = "0.0.0.0"
PORT = 8008
REFRESH_INTERVAL = 30

# Cache for tribe data (tribe_id -> tribe_name)
TRIBE_CACHE = {}
TRIBE_CACHE_TIME = 0
TRIBE_CACHE_DURATION = 300  # Refresh tribe cache every 5 minutes


def load_tribe_data():
    """Load tribe names from .arktribe files across all servers."""
    global TRIBE_CACHE, TRIBE_CACHE_TIME
    
    import time
    current_time = time.time()
    
    # Return cached data if still fresh
    if TRIBE_CACHE and (current_time - TRIBE_CACHE_TIME) < TRIBE_CACHE_DURATION:
        return TRIBE_CACHE
    
    tribes = {}
    
    for server_name, server_path in SERVER_PATHS.items():
        if not os.path.exists(server_path):
            continue
        
        for filename in os.listdir(server_path):
            if filename.endswith('.arktribe'):
                filepath = os.path.join(server_path, filename)
                try:
                    tribe_id, tribe_name = parse_tribe_file(filepath)
                    if tribe_id and tribe_name:
                        tribes[tribe_id] = tribe_name
                except:
                    continue
    
    TRIBE_CACHE = tribes
    TRIBE_CACHE_TIME = current_time
    return tribes


def parse_tribe_file(filepath):
    """Parse a .arktribe file to extract tribe ID and name."""
    try:
        with open(filepath, 'rb') as f:
            data = f.read(50000)  # Tribe files can be larger due to logs
        
        tribe_id = None
        tribe_name = None
        
        # Extract TribeID from inside the file (not filename)
        tribe_id_match = re.search(rb'TribeID\x00.{0,5}?IntProperty\x00.{8}(.{4})', data)
        if tribe_id_match:
            tribe_id = struct.unpack('<I', tribe_id_match.group(1))[0]
        
        # Extract TribeName
        # Structure: TribeName\x00 + 4 bytes + StrProperty\x00 + 4 bytes padding + 4 bytes total_size + 1 byte + 4 bytes strlen + string
        tribe_pattern = rb'TribeName\x00.{4}StrProperty\x00.{4}.{4}.(.{4})'
        match = re.search(tribe_pattern, data)
        if match:
            str_len = struct.unpack('<I', match.group(1))[0]
            if 0 < str_len < 200:
                start = match.end()
                tribe_name = data[start:start+str_len-1].decode('utf-8', errors='ignore')
        
        return tribe_id, tribe_name
    except Exception as e:
        return None, None


def get_tribe_name(tribe_id):
    """Get tribe name for a given tribe ID."""
    if not tribe_id:
        return None
    
    tribes = load_tribe_data()
    return tribes.get(tribe_id)


def extract_tribe_id(data):
    """Extract TribeID from binary data (arkprofile or cluster file)."""
    tribe_match = re.search(rb'TribeID\x00.{0,5}?IntProperty\x00.{8}(.{4})', data)
    if tribe_match:
        return struct.unpack('<I', tribe_match.group(1))[0]
    return None


def has_steamid(data: bytes) -> bool:
    """Return True if file data contains a plausible SteamID64 (17 digits) near a Steam marker."""
    # SteamID64 is 17 digits. Require a nearby 'Steam' marker to avoid false positives.
    for m in re.finditer(rb'\b\d{17}\b', data):
        start = max(0, m.start() - 64)
        end = min(len(data), m.end() + 64)
        window = data[start:end]
        if b"Steam" in window or b"STEAM" in window:
            return True
    return False


def detect_platform(filename, data):
    """Detect the platform based on file contents.
    
    Key findings from file analysis:
    - PS5/Console cluster files have: RedpointEOS, UniqueNetIdRepl, SavedNetworkAddress
    - PC Steam cluster files do NOT have these markers (connects directly)
    - Both have SteamUserItemID (from crossplay items), so that's not a reliable indicator
    """
    client_id = filename

    # Explicit console markers first (rare but definitive)
    if b'PSN' in data or b'PlayStation' in data or b'PS5' in data:
        return ('PS5', client_id)

    if b'Xbox' in data or b'XBL' in data or b'XboxLive' in data:
        return ('Xbox', client_id)

    # PC/Epic markers
    if b'EpicGames' in data or b'Epic Games' in data:
        return ('PC (Epic)', client_id)

    # Key insight: Console players connect via EOS crossplay bridge
    # PC Steam players connect directly without EOS bridge
    # So presence of UniqueNetIdRepl + RedpointEOS = Console (PS5/Xbox)
    # Absence of these = PC Steam
    
    has_eos_bridge = b'RedpointEOS' in data and b'UniqueNetIdRepl' in data
    
    if has_eos_bridge:
        # This is a console player using EOS crossplay
        # Default to PS5 (most common console for your server)
        return ('PS5', client_id)
    else:
        # No EOS bridge markers = PC Steam
        # Verify by checking it has actual game content
        if b'SteamUserItemID' in data or b'BlueprintGeneratedClass' in data:
            return ('PC (Steam)', client_id)

    return ('Unknown', client_id)



def extract_item_name(blueprint_path):
    """Extract a readable item name from a blueprint path."""
    match = re.search(r'PrimalItem[A-Za-z_]*_([A-Za-z0-9_]+)\.', blueprint_path)
    if match:
        name = match.group(1)
        name = re.sub(r'_C$', '', name)
        name = re.sub(r'([a-z])([A-Z])', r'\1 \2', name)
        name = name.replace('_', ' ')
        return name.strip()
    
    match = re.search(r'/([^/]+)\.([^.]+)_C', blueprint_path)
    if match:
        name = match.group(1)
        name = re.sub(r'([a-z])([A-Z])', r'\1 \2', name)
        name = name.replace('_', ' ')
        return name.strip()
    
    return blueprint_path.split('/')[-1] if '/' in blueprint_path else blueprint_path


def parse_cluster_file(filepath):
    """Parse an ARK cluster file and extract character information."""
    try:
        with open(filepath, 'rb') as f:
            data = f.read()
        
        filename = os.path.basename(filepath)
        
        # Detect platform
        platform, client_id = detect_platform(filename, data)
        
        # Find player username (Sidscri529 etc)
        # ASA saves can vary in padding around StrProperty, so we use wider windows and multiple key variants.
        player_username = None
        username_keys = [b'PlayerName', b'PlayerCharacterName', b'PlayerNamePrivate']

        for key in username_keys:
            username_pattern = key + rb'\x00.{0,80}?StrProperty\x00.{0,80}?([A-Za-z0-9_]{3,24})\x00'
            for match in re.finditer(username_pattern, data):
                potential_name = match.group(1).decode('utf-8', errors='ignore')
                # Skip if it contains "Lvl" (that's the display name) or looks like a server/map name
                if 'Lvl' in potential_name:
                    continue
                if 'Sidscri-TA' in potential_name:
                    continue
                if len(potential_name) >= 24:
                    continue
                player_username = potential_name
                break

            if player_username:
                break

        characters = []
        
        # Find all character display names (format: "Name - Lvl XX")
        name_pattern = rb'([A-Za-z0-9_\- ]+) - Lvl (\d+)'
        name_matches = list(re.finditer(name_pattern, data))
        
        for match in name_matches:
            name = match.group(1).decode('utf-8', errors='ignore').strip()
            level = int(match.group(2).decode('utf-8'))
            
            if len(name) < 2 or len(name) > 50:
                continue
                
            characters.append({
                'name': name,
                'level': level,
                'offset': match.start()
            })
        
        # Extract all items
        all_items = []
        item_pattern = rb'BlueprintGeneratedClass /Game/[^"]+\.([A-Za-z0-9_]+)_C'
        item_matches = re.finditer(item_pattern, data)
        seen_items = set()
        
        for match in item_matches:
            full_match = match.group(0).decode('utf-8', errors='ignore')
            item_name = extract_item_name(full_match)
            
            if 'Buff' in item_name or item_name in seen_items:
                continue
            if item_name in ['Starting Note', 'None']:
                continue
                
            seen_items.add(item_name)
            all_items.append(item_name)
        
        # Extract stats for each character
        # Stats appear AFTER the character display name
        for i, char in enumerate(characters):
            char_offset = char['offset']
            next_offset = characters[i + 1]['offset'] if (i + 1) < len(characters) else len(data)
            
            # Search AFTER the character name (stats follow the display name)
            search_start = char_offset
            search_end = min(len(data), char_offset + 20000, next_offset)
            stats_region = data[search_start:search_end]
            
            # Health
            health_match = re.search(rb'Health: ([\d.]+) / ([\d.]+)', stats_region)
            if health_match:
                try:
                    current = float(health_match.group(1))
                    maximum = float(health_match.group(2))
                    char['health'] = f"{current:.0f}/{maximum:.0f}"
                except:
                    pass
            
            # Stamina
            stamina_match = re.search(rb'Stamina: ([\d.]+) / ([\d.]+)', stats_region)
            if stamina_match:
                try:
                    current = float(stamina_match.group(1))
                    maximum = float(stamina_match.group(2))
                    char['stamina'] = f"{current:.0f}/{maximum:.0f}"
                except:
                    pass
            
            # Weight
            weight_match = re.search(rb'Weight: ([\d.]+) / ([\d.]+)', stats_region)
            if weight_match:
                try:
                    current = float(weight_match.group(1))
                    char['weight'] = f"{current:.0f}"
                except:
                    pass
            
            # Melee Damage
            melee_match = re.search(rb'Melee Damage: ([\d.]+)', stats_region)
            if melee_match:
                try:
                    melee = float(melee_match.group(1))
                    char['melee'] = f"{melee:.0f}%"
                except:
                    pass
            
            # Movement Speed
            speed_match = re.search(rb'Movement Speed: ([\d.]+)', stats_region)
            if speed_match:
                try:
                    speed = float(speed_match.group(1))
                    char['speed'] = f"{speed:.0f}%"
                except:
                    pass
            
            # Fortitude
            fort_match = re.search(rb'Fortitude: ([\d.]+)', stats_region)
            if fort_match:
                try:
                    fort = float(fort_match.group(1))
                    char['fortitude'] = f"{fort:.0f}"
                except:
                    pass
            
            # Server and Map
            # These fields exist per-character in many ASA cluster uploads, but the file may contain multiple characters.
            # We prefer matches in the per-character stats_region. If not found, we look for the *nearest* match in the file
            # around this character's offset (and avoid stealing the other character's map/server).
            map_regex = rb'UploadingServerMapName\x00.{0,200}?StrProperty\x00.{0,200}?([A-Za-z0-9_\-]+)\x00'
            server_regex = rb'UploadingServerName\x00.{0,200}?StrProperty\x00.{0,200}?([A-Za-z0-9_\-]+)\x00'

            def _nearest_match_value(rx: bytes, anchor: int, forward_window: int = 200000, backward_window: int = 200000):
                # 1) Prefer the first match AFTER the anchor within forward_window
                end = min(len(data), anchor + forward_window, next_offset)
                mm = re.search(rx, data[anchor:end])
                if mm:
                    return mm.group(1)

                # 2) Otherwise take the LAST match BEFORE the anchor within backward_window
                start = max(0, anchor - backward_window)
                matches = list(re.finditer(rx, data[start:anchor]))
                if matches:
                    return matches[-1].group(1)

                # 3) Finally, fall back to any match in the file (rare; better than nothing)
                mm = re.search(rx, data)
                if mm:
                    return mm.group(1)

                return None

            # Map
            map_match = re.search(map_regex, stats_region)
            if map_match:
                raw = map_match.group(1)
            else:
                raw = _nearest_match_value(map_regex, char.get('offset', 0))

            if raw:
                map_name = raw.decode('utf-8', errors='ignore')
                map_name = map_name.replace('_WP', '').replace('_P', '')
                char['map'] = map_name

            # Server
            server_match = re.search(server_regex, stats_region)
            if server_match:
                raw = server_match.group(1)
            else:
                raw = _nearest_match_value(server_regex, char.get('offset', 0))

            if raw:
                server_name = raw.decode('utf-8', errors='ignore')
                char['server'] = server_name

            char['items'] = all_items
            del char['offset']
        
        mtime = os.path.getmtime(filepath)
        upload_time = datetime.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M:%S')
        
        # Extract tribe info
        tribe_id = extract_tribe_id(data)
        tribe_name = get_tribe_name(tribe_id) if tribe_id else None
        
        return {
            'filename': filename,
            'client_id': client_id,
            'platform': platform,
            'player_username': player_username,
            'tribe_id': tribe_id,
            'tribe_name': tribe_name,
            'characters': characters,
            'upload_time': upload_time,
            'file_size': len(data)
        }
        
    except Exception as e:
        return {
            'filename': os.path.basename(filepath),
            'client_id': os.path.basename(filepath),
            'platform': 'Unknown',
            'player_username': None,
            'error': str(e),
            'characters': []
        }


def scan_cluster_folder():
    """Scan the cluster folder for all character upload files."""
    results = []
    
    if not os.path.exists(CLUSTER_FOLDER):
        return {'error': f'Cluster folder not found: {CLUSTER_FOLDER}', 'files': []}
    
    for filename in os.listdir(CLUSTER_FOLDER):
        filepath = os.path.join(CLUSTER_FOLDER, filename)
        
        if os.path.isdir(filepath):
            continue
        if os.path.getsize(filepath) < 1000:
            continue
        if filename.endswith('.ark') or filename.endswith('.tmp'):
            continue
        
        file_data = parse_cluster_file(filepath)
        if file_data['characters']:
            results.append(file_data)
    
    return {
        'folder': CLUSTER_FOLDER,
        'scan_time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        'files': results,
        'total_characters': sum(len(f['characters']) for f in results)
    }


def scan_server_players(server_name):
    """Scan a server's save folder for player profile files (.arkprofile)."""
    if server_name not in SERVER_PATHS:
        return {'error': f'Unknown server: {server_name}', 'players': []}
    
    server_path = SERVER_PATHS[server_name]
    
    if not os.path.exists(server_path):
        return {'error': f'Server path not found: {server_path}', 'players': []}
    
    players = []
    
    for filename in os.listdir(server_path):
        if filename.endswith('.arkprofile'):
            filepath = os.path.join(server_path, filename)
            
            # Extract client ID from filename (e.g., "0002e3000f8443b6a579db3224df0bd1.arkprofile")
            client_id = filename.replace('.arkprofile', '')
            
            try:
                file_size = os.path.getsize(filepath)
                mtime = os.path.getmtime(filepath)
                last_seen = datetime.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M:%S')
                
                # Quick parse to get player name using correct arkprofile format
                with open(filepath, 'rb') as f:
                    data = f.read(50000)  # Read first 50KB for quick scan
                
                # Find player username - arkprofile format
                player_name = None
                name_match = re.search(rb'PlayerName\x00.{0,5}?StrProperty\x00', data)
                if name_match:
                    start = name_match.end()
                    if start + 13 < len(data):
                        str_len = struct.unpack('<I', data[start+9:start+13])[0]
                        if 0 < str_len < 100:
                            player_name = data[start+13:start+13+str_len-1].decode('utf-8', errors='ignore')
                
                # Detect platform - use improved detection
                platform = detect_platform_from_profile(client_id, data, player_name or '')
                
                players.append({
                    'client_id': client_id,
                    'player_name': player_name or 'Unknown',
                    'platform': platform,
                    'file_size': file_size,
                    'last_seen': last_seen
                })
                
            except Exception as e:
                continue
    
    # Sort by last seen (most recent first)
    players.sort(key=lambda x: x['last_seen'], reverse=True)
    
    return {
        'server': server_name,
        'path': server_path,
        'players': players,
        'total_players': len(players)
    }


def parse_player_profile(server_name, client_id):
    """Parse a player's .arkprofile file to extract character details."""
    if server_name not in SERVER_PATHS:
        return {'error': f'Unknown server: {server_name}'}
    
    server_path = SERVER_PATHS[server_name]
    filepath = os.path.join(server_path, f"{client_id}.arkprofile")
    
    if not os.path.exists(filepath):
        return {'error': f'Profile not found: {filepath}'}
    
    try:
        with open(filepath, 'rb') as f:
            data = f.read()
        
        result = {
            'server': server_name,
            'client_id': client_id,
            'stats': {}
        }
        
        # ===== Get Player Name =====
        name_match = re.search(rb'PlayerName\x00.{0,5}?StrProperty\x00', data)
        if name_match:
            start = name_match.end()
            if start + 13 < len(data):
                str_len = struct.unpack('<I', data[start+9:start+13])[0]
                if 0 < str_len < 100:
                    result['player_name'] = data[start+13:start+13+str_len-1].decode('utf-8', errors='ignore')
        
        # ===== Get Character Name =====
        char_match = re.search(rb'PlayerCharacterName\x00.{0,5}?StrProperty\x00', data)
        if char_match:
            start = char_match.end()
            if start + 13 < len(data):
                str_len = struct.unpack('<I', data[start+9:start+13])[0]
                if 0 < str_len < 100:
                    result['character_name'] = data[start+13:start+13+str_len-1].decode('utf-8', errors='ignore')
        
        # ===== Get Character Level =====
        level_match = re.search(rb'CharacterStatusComponent_ExtraCharacterLevel\x00.{0,10}?UInt16Property\x00', data)
        if level_match:
            start = level_match.end()
            if start + 10 <= len(data):
                result['level'] = data[start + 9]
        
        # ===== Get Stat Points (points invested per stat) =====
        stat_names = {
            0: 'health', 1: 'stamina', 2: 'torpidity', 3: 'oxygen', 4: 'food', 5: 'water',
            6: 'temperature', 7: 'weight', 8: 'melee', 9: 'speed', 10: 'fortitude', 11: 'crafting'
        }
        
        # Parse all stat entries - find each NumberOfLevelUpPointsApplied entry
        stat_pattern = rb'CharacterStatusComponent_NumberOfLevelUpPointsApplied\x00'
        for match in re.finditer(stat_pattern, data):
            chunk = data[match.end():match.end()+50]
            bp_idx = chunk.find(b'ByteProperty\x00')
            if bp_idx < 0:
                continue
            after_bp = chunk[bp_idx + len(b'ByteProperty\x00'):]
            
            if len(after_bp) < 14:
                continue
            
            # Structure after ByteProperty\x00:
            # 4 bytes padding + 4 bytes size (=1) + flag/index byte + data
            flag_or_idx = after_bp[8]
            
            if flag_or_idx == 0:
                # Direct entry: index=0 (Health), value at byte 9
                stat_idx = 0
                stat_val = after_bp[9]
            else:
                # Indexed entry: flag=1 at byte 8, stat index at byte 9, 
                # 3 bytes padding, value at byte 13
                stat_idx = after_bp[9]
                stat_val = after_bp[13]
            
            if stat_idx in stat_names:
                result['stats'][stat_names[stat_idx]] = f"{stat_val} pts"
        
        # ===== Platform Detection =====
        # Server-side arkprofiles don't reliably identify platforms
        # All players appear through EOS crossplay layer
        result['platform'] = detect_platform_from_profile(client_id, data, result.get('player_name', ''))
        
        # ===== Get Tribe Info =====
        tribe_id = extract_tribe_id(data)
        if tribe_id:
            result['tribe_id'] = tribe_id
            tribe_name = get_tribe_name(tribe_id)
            if tribe_name:
                result['tribe_name'] = tribe_name
        
        # Get file info
        mtime = os.path.getmtime(filepath)
        result['last_seen'] = datetime.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M:%S')
        result['file_size'] = len(data)
        
        return result
        
    except Exception as e:
        return {'error': str(e)}


def detect_platform_from_profile(client_id, data, player_name):
    """
    Detect platform from arkprofile.
    
    Note: Server-side arkprofiles are nearly identical for all platforms because
    the server sees everyone through the EOS crossplay layer. The reliable
    platform indicators are only in cluster upload files (client-side generated).
    
    For arkprofiles, we use a combination of heuristics.
    """
    
    # Method 1: Check for explicit platform markers (very rare in arkprofiles)
    if b'PSN' in data or b'PlayStation' in data:
        return 'PS5'
    if b'Xbox' in data or b'XBL' in data:
        return 'Xbox'
    
    # Method 2: Check for Steam ID (17 digits starting with 7656)
    steam_pattern = rb'7656\d{13}'
    if re.search(steam_pattern, data):
        return 'PC (Steam)'
    
    # Method 3: Known player mappings (add your players here!)
    # This is the most reliable method for arkprofiles
    known_players = {
        # Format: 'client_id': 'platform'
        '0002fda69f5e47d8b37c4fe42cf5dc0e': 'PC (Steam)',  # sidscri
        '0002e3000f8443b6a579db3224df0bd1': 'PS5',          # Sidscri529
        # Add more known players as needed
    }
    
    if client_id in known_players:
        return known_players[client_id]
    
    # Method 4: Player name heuristics (less reliable but useful)
    if player_name:
        name_lower = player_name.lower()
        
        # Explicit platform indicators in name
        if any(x in name_lower for x in ['_ps', 'ps5', 'psn', '_ps5', '_playstation']):
            return 'PS5'
        if any(x in name_lower for x in ['_xbox', 'xbx', '_xb', '_xbox']):
            return 'Xbox'
        if any(x in name_lower for x in ['_pc', '_steam', 'pc_', 'steam_']):
            return 'PC (Steam)'
        
        # PSN names often have numeric suffixes (e.g., "Sidscri529", "henk99911")
        # But this is not reliable - some PC players also have numbers
        # Only use this as a weak signal
        import re as re_module
        if re_module.search(r'\d{3,}$', player_name):  # Ends with 3+ digits
            return 'PS5 (likely)'
    
    # Default: Can't determine from arkprofile alone
    return 'Unknown'


# HTML Template
HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ARK Cluster Viewer</title>
    <style>
        * {{
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }}
        
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            color: #eee;
            padding: 20px;
        }}
        
        .container {{
            max-width: 1200px;
            margin: 0 auto;
        }}
        
        header {{
            text-align: center;
            margin-bottom: 30px;
            padding: 20px;
            background: rgba(0,0,0,0.3);
            border-radius: 15px;
            border: 1px solid #e94560;
        }}
        
        h1 {{
            color: #e94560;
            font-size: 2.5em;
            text-shadow: 0 0 20px rgba(233,69,96,0.5);
            margin-bottom: 10px;
        }}
        
        .subtitle {{
            color: #888;
            font-size: 0.9em;
        }}
        
        .stats-bar {{
            display: flex;
            justify-content: center;
            gap: 30px;
            margin-top: 15px;
            flex-wrap: wrap;
        }}
        
        .stat {{
            background: rgba(233,69,96,0.2);
            padding: 10px 20px;
            border-radius: 8px;
            border: 1px solid #e94560;
        }}
        
        .stat-value {{
            font-size: 1.5em;
            font-weight: bold;
            color: #e94560;
        }}
        
        .stat-label {{
            font-size: 0.8em;
            color: #888;
        }}
        
        .error-box {{
            background: rgba(255,0,0,0.2);
            border: 1px solid #ff4444;
            padding: 20px;
            border-radius: 10px;
            margin: 20px 0;
            text-align: center;
        }}
        
        .no-chars {{
            text-align: center;
            padding: 50px;
            color: #666;
            font-size: 1.2em;
        }}
        
        .file-section {{
            background: rgba(0,0,0,0.3);
            border-radius: 15px;
            margin-bottom: 20px;
            overflow: hidden;
            border: 1px solid #333;
        }}
        
        .file-header {{
            background: rgba(233,69,96,0.1);
            padding: 15px 20px;
            border-bottom: 1px solid #333;
        }}
        
        .file-header-top {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
        }}
        
        .platform-badge {{
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 0.85em;
            margin-right: 10px;
        }}
        
        .platform-ps5 {{ background: #006FCD; color: white; }}
        .platform-xbox {{ background: #107C10; color: white; }}
        .platform-pc {{ background: #FF6B00; color: white; }}
        .platform-steam {{ background: #1b2838; color: #66c0f4; }}
        .platform-epic {{ background: #313131; color: white; }}
        .platform-unknown {{ background: #555; color: #ccc; }}
        
        .player-username {{
            color: #4ecca3;
            font-weight: bold;
            margin-left: 10px;
        }}
        
        .tribe-name {{
            color: #f0a500;
            font-size: 0.9em;
            margin-left: 10px;
            padding: 2px 8px;
            background: rgba(240, 165, 0, 0.15);
            border-radius: 4px;
        }}
        
        .tribe-id-small {{
            color: #555;
            font-size: 0.7em;
            margin-left: 3px;
        }}
        
        .client-id {{
            font-family: monospace;
            color: #666;
            font-size: 0.75em;
            margin-top: 5px;
            word-break: break-all;
        }}
        
        .file-meta {{
            color: #666;
            font-size: 0.85em;
        }}
        
        .characters-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
            gap: 15px;
            padding: 20px;
        }}
        
        .character-card {{
            background: linear-gradient(145deg, rgba(40,40,60,0.8), rgba(30,30,50,0.8));
            border-radius: 12px;
            padding: 20px;
            border: 1px solid #444;
            transition: transform 0.2s, box-shadow 0.2s;
        }}
        
        .character-card:hover {{
            transform: translateY(-3px);
            box-shadow: 0 10px 30px rgba(233,69,96,0.2);
            border-color: #e94560;
        }}
        
        .char-name {{
            font-size: 1.4em;
            font-weight: bold;
            color: #fff;
            margin-bottom: 5px;
        }}
        
        .char-level {{
            display: inline-block;
            background: #e94560;
            color: #fff;
            padding: 3px 12px;
            border-radius: 15px;
            font-size: 0.85em;
            font-weight: bold;
            margin-bottom: 15px;
        }}
        
        .main-stats {{
            display: flex;
            gap: 10px;
            margin-bottom: 15px;
        }}
        
        .main-stat {{
            flex: 1;
            display: flex;
            justify-content: space-between;
            padding: 8px 12px;
            background: rgba(0,0,0,0.3);
            border-radius: 6px;
            border: 1px solid #444;
        }}
        
        .main-stat-label {{
            color: #888;
            font-size: 0.9em;
        }}
        
        .main-stat-value {{
            color: #4ecca3;
            font-weight: bold;
            font-size: 1em;
        }}
        
        .server-info {{
            padding: 10px 0;
            font-size: 0.85em;
            color: #888;
            border-bottom: 1px solid #333;
            margin-bottom: 10px;
        }}
        
        .server-name {{
            color: #4ecca3;
        }}
        
        .expand-section {{
            margin-top: 10px;
        }}
        
        .expand-toggle {{
            background: rgba(78, 204, 163, 0.2);
            border: 1px solid #4ecca3;
            color: #4ecca3;
            padding: 10px 15px;
            border-radius: 5px;
            cursor: pointer;
            font-size: 0.85em;
            width: 100%;
            text-align: left;
            display: flex;
            justify-content: space-between;
            align-items: center;
            transition: background 0.2s;
        }}
        
        .expand-toggle:hover {{
            background: rgba(78, 204, 163, 0.3);
        }}
        
        .expand-toggle-arrow {{
            transition: transform 0.3s;
        }}
        
        .expand-toggle.open .expand-toggle-arrow {{
            transform: rotate(180deg);
        }}
        
        .expand-content {{
            display: none;
            margin-top: 10px;
            background: rgba(0,0,0,0.2);
            border-radius: 5px;
            padding: 15px;
            max-height: 400px;
            overflow-y: auto;
        }}
        
        .expand-content.open {{
            display: block;
        }}
        
        .secondary-stats {{
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 8px;
            margin-bottom: 15px;
            padding-bottom: 15px;
            border-bottom: 1px solid #333;
        }}
        
        .secondary-stat {{
            display: flex;
            justify-content: space-between;
            padding: 6px 10px;
            background: rgba(255,255,255,0.05);
            border-radius: 4px;
        }}
        
        .secondary-stat-label {{
            color: #888;
            font-size: 0.85em;
        }}
        
        .secondary-stat-value {{
            color: #4ecca3;
            font-weight: bold;
            font-size: 0.85em;
        }}
        
        .items-header {{
            color: #888;
            font-size: 0.8em;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}
        
        .item-entry {{
            padding: 5px 10px;
            margin: 2px 0;
            background: rgba(255,255,255,0.05);
            border-radius: 3px;
            font-size: 0.8em;
            color: #ccc;
        }}
        
        .item-entry:hover {{
            background: rgba(255,255,255,0.1);
        }}
        
        .badge {{
            background: #4ecca3;
            color: #1a1a2e;
            padding: 2px 8px;
            border-radius: 10px;
            font-size: 0.8em;
            font-weight: bold;
            margin-left: 5px;
        }}
        
        footer {{
            text-align: center;
            padding: 20px;
            color: #444;
            font-size: 0.85em;
        }}
        
        .refresh-btn {{
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: #e94560;
            color: #fff;
            border: none;
            padding: 15px 25px;
            border-radius: 30px;
            cursor: pointer;
            font-size: 1em;
            font-weight: bold;
            box-shadow: 0 5px 20px rgba(233,69,96,0.4);
            transition: transform 0.2s, background 0.2s;
        }}
        
        .refresh-btn:hover {{
            transform: scale(1.05);
            background: #ff6b6b;
        }}
        
        /* Settings dropdown */
        .settings-container {{
            position: fixed;
            bottom: 20px;
            left: 20px;
        }}
        
        .settings-btn {{
            background: #4ecca3;
            color: #1a1a2e;
            border: none;
            padding: 15px 25px;
            border-radius: 30px;
            cursor: pointer;
            font-size: 1em;
            font-weight: bold;
            box-shadow: 0 5px 20px rgba(78, 204, 163, 0.4);
            transition: transform 0.2s, background 0.2s;
        }}
        
        .settings-btn:hover {{
            transform: scale(1.05);
            background: #6fe0b8;
        }}
        
        .settings-dropdown {{
            display: none;
            position: absolute;
            bottom: 60px;
            left: 0;
            background: rgba(30, 30, 50, 0.98);
            border: 1px solid #4ecca3;
            border-radius: 10px;
            padding: 15px;
            min-width: 200px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        }}
        
        .settings-dropdown.open {{
            display: block;
        }}
        
        .settings-title {{
            color: #4ecca3;
            font-size: 0.85em;
            font-weight: bold;
            margin-bottom: 10px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}
        
        .settings-option {{
            display: block;
            width: 100%;
            padding: 10px 15px;
            margin: 5px 0;
            background: rgba(255,255,255,0.05);
            border: 1px solid transparent;
            border-radius: 5px;
            color: #ccc;
            cursor: pointer;
            text-align: left;
            font-size: 0.9em;
            transition: all 0.2s;
        }}
        
        .settings-option:hover {{
            background: rgba(78, 204, 163, 0.2);
            border-color: #4ecca3;
        }}
        
        .settings-option.active {{
            background: rgba(78, 204, 163, 0.3);
            border-color: #4ecca3;
            color: #4ecca3;
        }}
        
        .refresh-status {{
            margin-top: 10px;
            padding-top: 10px;
            border-top: 1px solid #333;
            font-size: 0.8em;
            color: #888;
        }}
        
        .refresh-status span {{
            color: #4ecca3;
        }}
        
        /* Find Character button */
        .find-char-btn {{
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            border: none;
            padding: 15px 30px;
            border-radius: 30px;
            cursor: pointer;
            font-size: 1em;
            font-weight: bold;
            box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
            transition: transform 0.2s, box-shadow 0.2s;
            text-decoration: none;
            display: inline-block;
        }}
        
        .find-char-btn:hover {{
            transform: translateX(-50%) scale(1.05);
            box-shadow: 0 8px 30px rgba(102, 126, 234, 0.6);
        }}
        
        @keyframes pulse {{
            0%, 100% {{ opacity: 1; }}
            50% {{ opacity: 0.5; }}
        }}
        
        .live-indicator {{
            display: inline-block;
            width: 10px;
            height: 10px;
            background: #4ecca3;
            border-radius: 50%;
            margin-right: 8px;
            animation: pulse 2s infinite;
        }}
        
        .expand-content::-webkit-scrollbar {{
            width: 8px;
        }}
        
        .expand-content::-webkit-scrollbar-track {{
            background: rgba(0,0,0,0.2);
            border-radius: 4px;
        }}
        
        .expand-content::-webkit-scrollbar-thumb {{
            background: #4ecca3;
            border-radius: 4px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>ARK Cluster Viewer</h1>
            <p class="subtitle">
                <span class="live-indicator"></span>
                Monitoring: <code>{cluster_folder}</code>
            </p>
            <div class="stats-bar">
                <div class="stat">
                    <div class="stat-value">{total_characters}</div>
                    <div class="stat-label">Characters Uploaded</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{total_files}</div>
                    <div class="stat-label">Player Files</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{scan_time}</div>
                    <div class="stat-label">Last Scan</div>
                </div>
            </div>
        </header>
        
        {content}
        
        <footer>
            Auto-refresh every {refresh_interval} seconds | ARK Cluster Character Viewer
        </footer>
    </div>
    
    <button class="refresh-btn" onclick="location.reload()">Refresh Now</button>
    
    <a href="/find" class="find-char-btn">Find Character</a>
    
    <div class="settings-container">
        <button class="settings-btn" onclick="toggleSettings()">Settings</button>
        <div class="settings-dropdown" id="settingsDropdown">
            <div class="settings-title">Auto-Refresh Interval</div>
            <button class="settings-option" data-interval="0">Off</button>
            <button class="settings-option" data-interval="60">1 Minute</button>
            <button class="settings-option" data-interval="300">5 Minutes</button>
            <button class="settings-option" data-interval="600">10 Minutes</button>
            <button class="settings-option active" data-interval="1800">30 Minutes</button>
            <button class="settings-option" data-interval="3600">1 Hour</button>
            <button class="settings-option" data-interval="21600">6 Hours</button>
            <button class="settings-option" data-interval="43200">12 Hours</button>
            <div class="refresh-status">
                Next refresh: <span id="nextRefresh">--</span>
            </div>
        </div>
    </div>
    
    <script>
        // Expand/collapse for character cards
        document.addEventListener('click', function(e) {{
            if (e.target.closest('.expand-toggle')) {{
                const toggle = e.target.closest('.expand-toggle');
                const content = toggle.nextElementSibling;
                toggle.classList.toggle('open');
                content.classList.toggle('open');
            }}
        }});
        
        // Settings dropdown and auto-refresh functionality
        let refreshInterval = parseInt(localStorage.getItem('arkRefreshInterval') || '1800');
        let refreshTimer = null;
        let countdownTimer = null;
        let nextRefreshTime = null;
        
        function toggleSettings() {{
            const dropdown = document.getElementById('settingsDropdown');
            dropdown.classList.toggle('open');
        }}
        
        // Close dropdown when clicking outside
        document.addEventListener('click', function(e) {{
            if (!e.target.closest('.settings-container')) {{
                document.getElementById('settingsDropdown').classList.remove('open');
            }}
        }});
        
        // Handle interval selection
        document.querySelectorAll('.settings-option').forEach(btn => {{
            btn.addEventListener('click', function() {{
                const interval = parseInt(this.dataset.interval);
                setRefreshInterval(interval);
                
                // Update active state
                document.querySelectorAll('.settings-option').forEach(b => b.classList.remove('active'));
                this.classList.add('active');
            }});
        }});
        
        function setRefreshInterval(seconds) {{
            refreshInterval = seconds;
            localStorage.setItem('arkRefreshInterval', seconds.toString());
            
            // Clear existing timers
            if (refreshTimer) clearTimeout(refreshTimer);
            if (countdownTimer) clearInterval(countdownTimer);
            
            if (seconds > 0) {{
                // Set next refresh time
                nextRefreshTime = Date.now() + (seconds * 1000);
                
                // Set refresh timer
                refreshTimer = setTimeout(() => {{
                    location.reload();
                }}, seconds * 1000);
                
                // Start countdown display
                updateCountdown();
                countdownTimer = setInterval(updateCountdown, 1000);
            }} else {{
                nextRefreshTime = null;
                document.getElementById('nextRefresh').textContent = 'Disabled';
            }}
        }}
        
        function updateCountdown() {{
            if (!nextRefreshTime) return;
            
            const remaining = Math.max(0, Math.floor((nextRefreshTime - Date.now()) / 1000));
            
            if (remaining === 0) {{
                document.getElementById('nextRefresh').textContent = 'Refreshing...';
                return;
            }}
            
            let display;
            if (remaining >= 3600) {{
                const hours = Math.floor(remaining / 3600);
                const mins = Math.floor((remaining % 3600) / 60);
                display = `${{hours}}h ${{mins}}m`;
            }} else if (remaining >= 60) {{
                const mins = Math.floor(remaining / 60);
                const secs = remaining % 60;
                display = `${{mins}}m ${{secs}}s`;
            }} else {{
                display = `${{remaining}}s`;
            }}
            
            document.getElementById('nextRefresh').textContent = display;
        }}
        
        // Initialize on page load
        document.addEventListener('DOMContentLoaded', function() {{
            // Set active button based on saved preference
            const savedInterval = parseInt(localStorage.getItem('arkRefreshInterval') || '1800');
            document.querySelectorAll('.settings-option').forEach(btn => {{
                btn.classList.remove('active');
                if (parseInt(btn.dataset.interval) === savedInterval) {{
                    btn.classList.add('active');
                }}
            }});
            
            // Start auto-refresh
            setRefreshInterval(savedInterval);
        }});
    </script>
</body>
</html>
"""

CHARACTER_CARD_TEMPLATE = """
<div class="character-card">
    <div class="char-name">{name}</div>
    <span class="char-level">Level {level}</span>
    
    <div class="main-stats">
        <div class="main-stat">
            <span class="main-stat-label">Health</span>
            <span class="main-stat-value">{health}</span>
        </div>
        <div class="main-stat">
            <span class="main-stat-label">Melee</span>
            <span class="main-stat-value">{melee}</span>
        </div>
    </div>
    
    {server_info}
    
    <div class="expand-section">
        <button class="expand-toggle">
            <span>Stats & Items <span class="badge">{item_count}</span></span>
            <span class="expand-toggle-arrow">▼</span>
        </button>
        <div class="expand-content">
            <div class="secondary-stats">
                {secondary_stats}
            </div>
            <div class="items-header">Uploaded Items</div>
            {items_html}
        </div>
    </div>
</div>
"""

FILE_SECTION_TEMPLATE = """
<div class="file-section">
    <div class="file-header">
        <div class="file-header-top">
            <div>
                <span class="platform-badge {platform_class}">{platform}</span>
                {username_display}
                {tribe_display}
                <span class="file-meta">Uploaded: {upload_time} | Size: {file_size}</span>
            </div>
        </div>
        <div class="client-id">Client ID: {client_id}</div>
    </div>
    <div class="characters-grid">
        {characters}
    </div>
</div>
"""

# Find Character Page Template
FIND_CHARACTER_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Find Character - ARK Cluster Viewer</title>
    <style>
        * {{
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }}
        
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            color: #eee;
            padding: 20px;
        }}
        
        .container {{
            max-width: 1200px;
            margin: 0 auto;
        }}
        
        header {{
            text-align: center;
            margin-bottom: 30px;
            padding: 20px;
            background: rgba(0,0,0,0.3);
            border-radius: 15px;
            border: 1px solid #667eea;
        }}
        
        h1 {{
            color: #667eea;
            font-size: 2.5em;
            text-shadow: 0 0 20px rgba(102, 126, 234, 0.5);
            margin-bottom: 10px;
        }}
        
        .back-btn {{
            display: inline-block;
            background: rgba(233, 69, 96, 0.2);
            border: 1px solid #e94560;
            color: #e94560;
            padding: 10px 20px;
            border-radius: 20px;
            text-decoration: none;
            margin-bottom: 20px;
            transition: all 0.2s;
        }}
        
        .back-btn:hover {{
            background: rgba(233, 69, 96, 0.3);
        }}
        
        .server-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }}
        
        .server-card {{
            background: linear-gradient(145deg, rgba(40,40,60,0.8), rgba(30,30,50,0.8));
            border-radius: 12px;
            padding: 25px;
            border: 2px solid #444;
            cursor: pointer;
            transition: all 0.3s;
            text-align: center;
        }}
        
        .server-card:hover {{
            border-color: #667eea;
            transform: translateY(-5px);
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
        }}
        
        .server-card.selected {{
            border-color: #4ecca3;
            background: linear-gradient(145deg, rgba(78, 204, 163, 0.2), rgba(40, 40, 60, 0.8));
        }}
        
        .server-name {{
            font-size: 1.3em;
            font-weight: bold;
            color: #fff;
            margin-bottom: 10px;
        }}
        
        .server-status {{
            font-size: 0.85em;
            color: #888;
        }}
        
        .server-status.online {{
            color: #4ecca3;
        }}
        
        .server-status.offline {{
            color: #e94560;
        }}
        
        .section-title {{
            color: #667eea;
            font-size: 1.5em;
            margin: 30px 0 20px 0;
            padding-bottom: 10px;
            border-bottom: 1px solid #333;
        }}
        
        .player-list {{
            background: rgba(0,0,0,0.3);
            border-radius: 15px;
            overflow: hidden;
            border: 1px solid #333;
        }}
        
        .player-item {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 15px 20px;
            border-bottom: 1px solid #333;
            cursor: pointer;
            transition: background 0.2s;
        }}
        
        .player-item:hover {{
            background: rgba(102, 126, 234, 0.2);
        }}
        
        .player-item:last-child {{
            border-bottom: none;
        }}
        
        .player-info {{
            display: flex;
            align-items: center;
            gap: 15px;
        }}
        
        .platform-badge {{
            display: inline-block;
            padding: 4px 10px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 0.75em;
        }}
        
        .platform-ps5 {{ background: #006FCD; color: white; }}
        .platform-xbox {{ background: #107C10; color: white; }}
        .platform-pc {{ background: #FF6B00; color: white; }}
        .platform-steam {{ background: #1b2838; color: #66c0f4; }}
        .platform-epic {{ background: #313131; color: white; }}
        .platform-unknown {{ background: #555; color: #ccc; }}
        
        .player-name {{
            font-weight: bold;
            color: #fff;
        }}
        
        .player-meta {{
            color: #888;
            font-size: 0.85em;
        }}
        
        .player-last-seen {{
            color: #666;
            font-size: 0.85em;
        }}
        
        .search-box {{
            width: 100%;
            padding: 15px 20px;
            background: rgba(0,0,0,0.3);
            border: 1px solid #444;
            border-radius: 10px;
            color: #fff;
            font-size: 1em;
            margin-bottom: 20px;
        }}
        
        .search-box:focus {{
            outline: none;
            border-color: #667eea;
        }}
        
        .search-box::placeholder {{
            color: #666;
        }}
        
        /* Character Detail View */
        .char-detail-card {{
            background: linear-gradient(145deg, rgba(40,40,60,0.9), rgba(30,30,50,0.9));
            border-radius: 15px;
            padding: 30px;
            border: 1px solid #667eea;
            margin-top: 20px;
        }}
        
        .char-header {{
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 25px;
            flex-wrap: wrap;
            gap: 15px;
        }}
        
        .char-title {{
            font-size: 2em;
            font-weight: bold;
            color: #fff;
        }}
        
        .char-level-big {{
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            padding: 8px 20px;
            border-radius: 25px;
            font-size: 1.2em;
            font-weight: bold;
        }}
        
        .stats-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }}
        
        .stat-card {{
            background: rgba(0,0,0,0.3);
            border-radius: 10px;
            padding: 15px;
            border: 1px solid #444;
        }}
        
        .stat-label {{
            color: #888;
            font-size: 0.85em;
            margin-bottom: 5px;
        }}
        
        .stat-value {{
            color: #4ecca3;
            font-size: 1.3em;
            font-weight: bold;
        }}
        
        .info-row {{
            display: flex;
            gap: 30px;
            margin-top: 20px;
            flex-wrap: wrap;
        }}
        
        .info-item {{
            color: #888;
        }}
        
        .info-item span {{
            color: #4ecca3;
        }}
        
        .tribe-info span {{
            color: #f0a500;
        }}
        
        .tribe-id {{
            color: #666;
            font-size: 0.75em;
            margin-left: 5px;
        }}
        
        .no-results {{
            text-align: center;
            padding: 50px;
            color: #666;
        }}
        
        .loading {{
            text-align: center;
            padding: 50px;
            color: #667eea;
        }}
        
        /* Back to top button */
        .back-to-top {{
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: #667eea;
            color: white;
            border: none;
            padding: 15px 20px;
            border-radius: 30px;
            cursor: pointer;
            font-size: 0.9em;
            font-weight: bold;
            box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
            transition: all 0.3s;
            opacity: 0;
            visibility: hidden;
            transform: translateY(20px);
        }}
        
        .back-to-top.visible {{
            opacity: 1;
            visibility: visible;
            transform: translateY(0);
        }}
        
        .back-to-top:hover {{
            background: #764ba2;
            transform: scale(1.05);
        }}
    </style>
</head>
<body>
    <div class="container">
        <a href="/" class="back-btn">← Back to Cluster Viewer</a>
        
        <header>
            <h1>Find Character</h1>
            <p style="color: #888;">Browse server saves to find player characters</p>
        </header>
        
        <h2 class="section-title">Select Server</h2>
        <div class="server-grid" id="serverGrid">
            {server_cards}
        </div>
        
        <div id="playerSection" style="display: none;">
            <h2 class="section-title">Players on <span id="selectedServerName"></span></h2>
            <input type="text" class="search-box" id="searchBox" placeholder="Search by player name or client ID...">
            <div class="player-list" id="playerList">
                <div class="loading">Select a server to view players...</div>
            </div>
        </div>
        
        <div id="characterSection" style="display: none;">
            <h2 class="section-title">Character Details</h2>
            <div id="characterDetail"></div>
        </div>
    </div>
    
    <script>
        let currentServer = null;
        let allPlayers = [];
        
        // Server card click handler
        document.querySelectorAll('.server-card').forEach(card => {{
            card.addEventListener('click', function() {{
                const server = this.dataset.server;
                selectServer(server);
                
                // Update selected state
                document.querySelectorAll('.server-card').forEach(c => c.classList.remove('selected'));
                this.classList.add('selected');
            }});
        }});
        
        function selectServer(server) {{
            currentServer = server;
            document.getElementById('selectedServerName').textContent = server;
            document.getElementById('playerSection').style.display = 'block';
            document.getElementById('characterSection').style.display = 'none';
            document.getElementById('playerList').innerHTML = '<div class="loading">Loading players...</div>';
            
            // Fetch players for this server
            fetch('/api/server/' + server + '/players')
                .then(r => r.json())
                .then(data => {{
                    if (data.error) {{
                        document.getElementById('playerList').innerHTML = '<div class="no-results">' + data.error + '</div>';
                        return;
                    }}
                    
                    allPlayers = data.players;
                    renderPlayers(allPlayers);
                }});
        }}
        
        function renderPlayers(players) {{
            if (players.length === 0) {{
                document.getElementById('playerList').innerHTML = '<div class="no-results">No players found</div>';
                return;
            }}
            
            let html = '';
            players.forEach(p => {{
                const platformClass = getPlatformClass(p.platform);
                html += `
                    <div class="player-item" onclick="viewCharacter('${{currentServer}}', '${{p.client_id}}')">
                        <div class="player-info">
                            <span class="platform-badge ${{platformClass}}">${{p.platform}}</span>
                            <div>
                                <div class="player-name">${{p.player_name}}</div>
                                <div class="player-meta">${{p.client_id}}</div>
                            </div>
                        </div>
                        <div class="player-last-seen">Last seen: ${{p.last_seen}}</div>
                    </div>
                `;
            }});
            
            document.getElementById('playerList').innerHTML = html;
        }}
        
        function getPlatformClass(platform) {{
            const p = platform.toLowerCase();
            if (p.includes('ps5') || p.includes('playstation')) return 'platform-ps5';
            if (p.includes('xbox')) return 'platform-xbox';
            if (p.includes('steam')) return 'platform-steam';
            if (p.includes('epic')) return 'platform-epic';
            if (p.includes('pc')) return 'platform-pc';
            return 'platform-unknown';
        }}
        
        // Search functionality
        document.getElementById('searchBox').addEventListener('input', function() {{
            const query = this.value.toLowerCase();
            const filtered = allPlayers.filter(p => 
                p.player_name.toLowerCase().includes(query) || 
                p.client_id.toLowerCase().includes(query)
            );
            renderPlayers(filtered);
        }});
        
        function viewCharacter(server, clientId) {{
            document.getElementById('characterSection').style.display = 'block';
            document.getElementById('characterDetail').innerHTML = '<div class="loading">Loading character...</div>';
            
            fetch('/api/server/' + server + '/player/' + clientId)
                .then(r => r.json())
                .then(data => {{
                    if (data.error) {{
                        document.getElementById('characterDetail').innerHTML = '<div class="no-results">' + data.error + '</div>';
                        return;
                    }}
                    
                    const platformClass = getPlatformClass(data.platform);
                    let statsHtml = '';
                    
                    const statLabels = {{
                        'health': 'Health',
                        'melee': 'Melee Damage',
                        'fortitude': 'Fortitude'
                    }};
                    
                    for (const [key, label] of Object.entries(statLabels)) {{
                        const value = data.stats[key] || 'N/A';
                        statsHtml += `
                            <div class="stat-card">
                                <div class="stat-label">${{label}}</div>
                                <div class="stat-value">${{value}}</div>
                            </div>
                        `;
                    }}
                    
                    document.getElementById('characterDetail').innerHTML = `
                        <div class="char-detail-card">
                            <div class="char-header">
                                <div>
                                    <div class="char-title">${{data.character_name || 'Unknown Character'}}</div>
                                    <div class="info-row">
                                        <div class="info-item"><span class="platform-badge ${{platformClass}}">${{data.platform}}</span></div>
                                        <div class="info-item">Player: <span>${{data.player_name}}</span></div>
                                        <div class="info-item">Server: <span>${{data.server}}</span></div>
                                        ${{data.tribe_name ? `<div class="info-item tribe-info">Tribe: <span>${{data.tribe_name}}</span> <small class="tribe-id">${{data.tribe_id}}</small></div>` : ''}}
                                    </div>
                                </div>
                                <div class="char-level-big">Level ${{data.level || '?'}}</div>
                            </div>
                            
                            <div class="info-row">
                                <div class="info-item">Client ID: <span>${{data.client_id}}</span></div>
                                <div class="info-item">Last Seen: <span>${{data.last_seen}}</span></div>
                                <div class="info-item">File Size: <span>${{(data.file_size / 1024).toFixed(1)}} KB</span></div>
                            </div>
                            
                            <div class="stats-grid">
                                ${{statsHtml}}
                            </div>
                        </div>
                    `;
                    
                    // Scroll to character detail
                    document.getElementById('characterSection').scrollIntoView({{ behavior: 'smooth' }});
                }});
        }}
        
        // Back to top button
        const backToTopBtn = document.getElementById('backToTop');
        window.addEventListener('scroll', function() {{
            if (window.scrollY > 300) {{
                backToTopBtn.classList.add('visible');
            }} else {{
                backToTopBtn.classList.remove('visible');
            }}
        }});
        
        function scrollToTop() {{
            window.scrollTo({{ top: 0, behavior: 'smooth' }});
        }}
    </script>
    
    <button class="back-to-top" id="backToTop" onclick="scrollToTop()">↑ Top</button>
</body>
</html>
"""


def get_platform_class(platform):
    platform_lower = platform.lower()
    if 'ps5' in platform_lower or 'playstation' in platform_lower:
        return 'platform-ps5'
    elif 'xbox' in platform_lower:
        return 'platform-xbox'
    elif 'steam' in platform_lower:
        return 'platform-steam'
    elif 'epic' in platform_lower:
        return 'platform-epic'
    elif 'pc' in platform_lower:
        return 'platform-pc'
    else:
        return 'platform-unknown'


class ClusterViewerHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        # API: Cluster data
        if self.path == '/api/data':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            data = scan_cluster_folder()
            self.wfile.write(json.dumps(data, indent=2).encode())
        
        # API: List servers
        elif self.path == '/api/servers':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            servers = []
            for name, path in SERVER_PATHS.items():
                exists = os.path.exists(path)
                servers.append({
                    'name': name,
                    'path': path,
                    'online': exists
                })
            
            self.wfile.write(json.dumps({'servers': servers}).encode())
        
        # API: List players on a server
        elif self.path.startswith('/api/server/') and self.path.endswith('/players'):
            server_name = self.path.replace('/api/server/', '').replace('/players', '')
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            data = scan_server_players(server_name)
            self.wfile.write(json.dumps(data).encode())
        
        # API: Get player character details
        elif '/api/server/' in self.path and '/player/' in self.path:
            parts = self.path.replace('/api/server/', '').split('/player/')
            if len(parts) == 2:
                server_name = parts[0]
                client_id = parts[1]
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                
                data = parse_player_profile(server_name, client_id)
                self.wfile.write(json.dumps(data).encode())
            else:
                self.send_error(400, 'Invalid request')
        
        # Find Character page
        elif self.path == '/find':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            
            # Generate server cards
            server_cards = ''
            for name, path in SERVER_PATHS.items():
                exists = os.path.exists(path)
                status_class = 'online' if exists else 'offline'
                status_text = 'Available' if exists else 'Path not found'
                
                server_cards += f'''
                    <div class="server-card" data-server="{name}">
                        <div class="server-name">{name}</div>
                        <div class="server-status {status_class}">{status_text}</div>
                    </div>
                '''
            
            html = FIND_CHARACTER_TEMPLATE.format(server_cards=server_cards)
            self.wfile.write(html.encode())
        
        # Main page
        else:
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            
            data = scan_cluster_folder()
            
            if 'error' in data and data['error']:
                content = f'<div class="error-box">{data["error"]}</div>'
            elif not data['files']:
                content = '<div class="no-chars">No characters currently uploaded to cluster</div>'
            else:
                file_sections = []
                for file_data in data['files']:
                    char_cards = []
                    for char in file_data['characters']:
                        health = char.get('health', 'N/A')
                        melee = char.get('melee', 'N/A')
                        
                        secondary_stats = []
                        secondary_fields = [
                            ('stamina', 'Stamina'),
                            ('weight', 'Weight'),
                            ('speed', 'Speed'),
                            ('fortitude', 'Fortitude')
                        ]
                        
                        for field, label in secondary_fields:
                            value = char.get(field, 'N/A')
                            secondary_stats.append(f'<div class="secondary-stat"><span class="secondary-stat-label">{label}</span><span class="secondary-stat-value">{value}</span></div>')
                        
                        server_info = ''
                        if 'server' in char or 'map' in char:
                            server_parts = []
                            if 'map' in char:
                                server_parts.append(f'Map: <span class="server-name">{char["map"]}</span>')
                            if 'server' in char:
                                server_parts.append(f'Server: <span class="server-name">{char["server"]}</span>')
                            server_info = f'<div class="server-info">{" | ".join(server_parts)}</div>'
                        
                        items_html = ''
                        item_count = 0
                        if 'items' in char and char['items']:
                            item_count = len(char['items'])
                            items_html = ''.join([f'<div class="item-entry">{item}</div>' for item in sorted(char['items'])])
                        else:
                            items_html = '<div class="item-entry">No items uploaded</div>'
                        
                        char_cards.append(CHARACTER_CARD_TEMPLATE.format(
                            name=char['name'],
                            level=char['level'],
                            health=health,
                            melee=melee,
                            server_info=server_info,
                            secondary_stats=''.join(secondary_stats),
                            items_html=items_html,
                            item_count=item_count
                        ))
                    
                    file_size = f"{file_data['file_size'] / 1024 / 1024:.1f} MB" if file_data['file_size'] > 1024*1024 else f"{file_data['file_size'] / 1024:.1f} KB"
                    
                    platform = file_data.get('platform', 'Unknown')
                    platform_class = get_platform_class(platform)
                    
                    # Username display
                    username = file_data.get('player_username')
                    username_display = f'<span class="player-username">{username}</span>' if username else ''
                    
                    # Tribe display with ID
                    tribe_name = file_data.get('tribe_name')
                    tribe_id = file_data.get('tribe_id')
                    if tribe_name:
                        tribe_display = f'<span class="tribe-name">Tribe: {tribe_name}</span> <small class="tribe-id-small">{tribe_id}</small>'
                    else:
                        tribe_display = ''
                    
                    file_sections.append(FILE_SECTION_TEMPLATE.format(
                        platform=platform,
                        platform_class=platform_class,
                        username_display=username_display,
                        tribe_display=tribe_display,
                        client_id=file_data.get('client_id', file_data['filename']),
                        upload_time=file_data['upload_time'],
                        file_size=file_size,
                        characters=''.join(char_cards)
                    ))
                
                content = ''.join(file_sections)
            
            html = HTML_TEMPLATE.format(
                cluster_folder=data.get('folder', CLUSTER_FOLDER),
                total_characters=data.get('total_characters', 0),
                total_files=len(data.get('files', [])),
                scan_time=data.get('scan_time', 'N/A'),
                content=content,
                refresh_interval=REFRESH_INTERVAL
            )
            
            self.wfile.write(html.encode())
    
    def log_message(self, format, *args):
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]}")


def main():
    print("=" * 60)
    print("ARK Cluster Character Viewer")
    print("=" * 60)
    print(f"\nCluster Folder: {CLUSTER_FOLDER}")
    print(f"Web Interface:  http://192.168.0.129:{PORT}")
    print(f"                http://localhost:{PORT}")
    print(f"\nAuto-refresh:   Configurable in Settings (default: 30 min)")
    print("\nPress Ctrl+C to stop\n")
    print("=" * 60)
    
    if not os.path.exists(CLUSTER_FOLDER):
        print(f"\n  WARNING: Cluster folder not found!")
        print(f"   Please edit CLUSTER_FOLDER in this script")
        print(f"   Current setting: {CLUSTER_FOLDER}\n")
    
    server = HTTPServer((HOST, PORT), ClusterViewerHandler)
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n\nShutting down server...")
        server.shutdown()


if __name__ == "__main__":
    main()
