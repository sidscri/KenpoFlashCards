package com.example.kenpoflashcards

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Web App Sync Service
 * 
 * Connects to: (admin-managed)
 * 
 * Server Data Structure (C:/Program Files/Kenpo Flashcards/_internal/data/):
 * - users/           -> User directories (one per user)
 * - breakdown.json   -> Shared breakdown data
 * - profiles.json    -> User profiles and auth
 * 
 * API Endpoints (to be implemented on server):
 * - POST /api/login        -> { username, password } -> { token, userId }
 * - GET  /api/profile      -> (auth) -> user profile data
 * - POST /api/sync/push    -> (auth) push local progress to server
 * - GET  /api/sync/pull    -> (auth) pull server progress to local
 * - GET  /api/breakdowns   -> get shared breakdowns
 * - POST /api/breakdowns   -> (auth) save breakdown
 */
object WebAppSync {
    
    const val DEFAULT_SERVER_URL = "http://sidscri.tplinkdns.com:8009"
    
    data class LoginResult(
        val success: Boolean,
        val token: String = "",
        val userId: String = "",
        val username: String = "",
        val error: String = "",
        val debugInfo: String = ""
    )
    
    data class SyncResult(
        val success: Boolean,
        val message: String = "",
        val error: String = ""
    )
    
    /**
     * Login to web app
     * Server checks profiles.json with werkzeug password hash
     */
    suspend fun login(serverUrl: String, username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val token = json.optString("token", "")
                val userId = json.optString("userId", "")
                val uname = json.optString("username", json.optString("displayName", username))
                // Debug: include raw response info
                LoginResult(
                    success = true,
                    token = token,
                    userId = userId,
                    username = uname,
                    debugInfo = "raw token length: ${token.length}, response: ${response.take(100)}"
                )
            } else {
                val error = try { 
                    val errJson = JSONObject(conn.errorStream?.bufferedReader()?.readText() ?: "{}")
                    errJson.optString("error", "Login failed")
                } catch (_: Exception) { "Login failed" }
                LoginResult(success = false, error = error)
            }
        } catch (e: Exception) {
            LoginResult(success = false, error = e.message ?: "Connection failed")
        }
    }    /**
     * Push local progress to server (legacy wrapper)
     */
    suspend fun pushProgress(serverUrl: String, token: String, progress: ProgressState): SyncResult {
        val nowSec = System.currentTimeMillis() / 1000
        val entries = progress.statuses.mapValues { ProgressEntry(it.value, nowSec) }
        return pushProgressEntries(serverUrl, token, entries)
    }

    /**
     * Push progress entries to server.
     * Sends: progress[id] = {status, updated_at}
     */
    suspend fun pushProgressEntries(serverUrl: String, token: String, entries: Map<String, ProgressEntry>): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/push")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val progressJson = JSONObject()
            entries.forEach { (id, entry) ->
                val obj = JSONObject()
                obj.put("status", entry.status.name.lowercase())
                obj.put("updated_at", entry.updatedAt)
                progressJson.put(id, obj)
            }

            val body = JSONObject().apply {
                put("progress", progressJson)
                put("timestamp", System.currentTimeMillis())
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "Progress synced successfully")
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                SyncResult(success = false, error = "Sync failed: ${conn.responseCode} ${err.take(120)}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Sync failed")
        }
    }

    /**
     * Pull progress from server
     * Server reads from users/{userId}/progress.json
     */
    suspend fun pullProgress(serverUrl: String, token: String): Pair<SyncResult, ProgressState?> {
        val (res, entries) = pullProgressEntries(serverUrl, token)
        if (!res.success || entries == null) return Pair(res, null)
        val statuses = entries.mapValues { it.value.status }
        return Pair(res, ProgressState(statuses))
    }

    /**
     * Pull progress entries from server.
     * Accepts legacy server payloads where progress[id] is a string.
     */
    suspend fun pullProgressEntries(serverUrl: String, token: String): Pair<SyncResult, Map<String, ProgressEntry>?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/pull")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val progressJson = json.optJSONObject("progress") ?: JSONObject()

                val entries = mutableMapOf<String, ProgressEntry>()
                progressJson.keys().forEach { key ->
                    val v = progressJson.opt(key)
                    when (v) {
                        is String -> {
                            val st = when (v.lowercase()) {
                                "learned" -> CardStatus.LEARNED
                                "unsure" -> CardStatus.UNSURE
                                "deleted" -> CardStatus.DELETED
                                else -> CardStatus.ACTIVE
                            }
                            entries[key] = ProgressEntry(st, 0)
                        }
                        is JSONObject -> {
                            val statusStr = v.optString("status", "active")
                            val st = when (statusStr.lowercase()) {
                                "learned" -> CardStatus.LEARNED
                                "unsure" -> CardStatus.UNSURE
                                "deleted" -> CardStatus.DELETED
                                else -> CardStatus.ACTIVE
                            }
                            val ua = v.optLong("updated_at", 0)
                            entries[key] = ProgressEntry(st, ua)
                        }
                        else -> {}
                    }
                }

                Pair(SyncResult(success = true, message = "Progress loaded"), entries)
            } else {
                Pair(SyncResult(success = false, error = "Pull failed: ${conn.responseCode}"), null)
            }
        } catch (e: Exception) {
            Pair(SyncResult(success = false, error = e.message ?: "Pull failed"), null)
        }
    }

    /**
     * Get shared breakdowns from server
     * Server reads from data/breakdowns.json
     */
    suspend fun getBreakdowns(serverUrl: String): Pair<SyncResult, Map<String, TermBreakdown>?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/breakdowns")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val breakdownsJson = json.optJSONObject("breakdowns") ?: JSONObject()
                
                val breakdowns = mutableMapOf<String, TermBreakdown>()
                breakdownsJson.keys().forEach { key ->
                    val bdObj = breakdownsJson.optJSONObject(key) ?: return@forEach
                    val partsArray = bdObj.optJSONArray("parts") ?: JSONArray()
                    val parts = mutableListOf<BreakdownPart>()
                    for (i in 0 until partsArray.length()) {
                        val pObj = partsArray.optJSONObject(i) ?: continue
                        parts.add(BreakdownPart(pObj.optString("part", ""), pObj.optString("meaning", "")))
                    }
                    breakdowns[key] = TermBreakdown(
                        id = key,
                        term = bdObj.optString("term", ""),
                        parts = parts,
                        literal = bdObj.optString("literal", ""),
                        notes = bdObj.optString("notes", ""),
                        updatedAt = bdObj.optLong("updated_at", 0),
                        updatedBy = bdObj.optString("updated_by", null)
                    )
                }
                
                Pair(SyncResult(success = true, message = "Breakdowns loaded"), breakdowns)
            } else {
                Pair(SyncResult(success = false, error = "Failed: ${conn.responseCode}"), null)
            }
        } catch (e: Exception) {
            Pair(SyncResult(success = false, error = e.message ?: "Failed"), null)
        }
    }
    
    /**
     * Save breakdown to server (requires auth)
     * Server saves to data/breakdown.json
     */
    suspend fun saveBreakdown(serverUrl: String, token: String, breakdown: TermBreakdown): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/breakdowns")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val partsArray = JSONArray()
            breakdown.parts.forEach { part ->
                val pObj = JSONObject()
                pObj.put("part", part.part)
                pObj.put("meaning", part.meaning)
                partsArray.put(pObj)
            }
            
            val bdObj = JSONObject().apply {
                put("id", breakdown.id)
                put("term", breakdown.term)
                put("parts", partsArray)
                put("literal", breakdown.literal)
                put("notes", breakdown.notes)
                put("updated_at", System.currentTimeMillis() / 1000)
            }
            
            conn.outputStream.use { it.write(bdObj.toString().toByteArray()) }
            
            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "Breakdown saved")
            } else {
                SyncResult(success = false, error = "Save failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Save failed")
        }
    }
    
    /**
     * Push custom set to server
     */
    suspend fun pushCustomSet(serverUrl: String, token: String, customSet: Set<String>): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/customset")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            
            val arr = JSONArray()
            customSet.forEach { arr.put(it) }
            val body = JSONObject().apply { put("customSet", arr) }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            if (conn.responseCode == 200) SyncResult(success = true, message = "Custom set synced")
            else SyncResult(success = false, error = "Sync failed: ${conn.responseCode}")
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Sync failed")
        }
    }
    
    /**
     * API Keys sync result
     */
    data class ApiKeysResult(
        val success: Boolean,
        val chatGptKey: String = "",
        val chatGptModel: String = "gpt-4o",
        val geminiKey: String = "",
        val geminiModel: String = "gemini-1.5-flash",
        val error: String = ""
    )
    

    /**
     * App/server config result (e.g., managed sync server URL)
     */
    data class ServerConfigResult(
        val success: Boolean,
        val managedServerUrl: String = "",
        val configVersion: Long = 0,
        val error: String = ""
    )

    /**
     * Pull app config from server (available to any logged-in user).
     * Endpoint: GET /api/app/config  -> { "server_url": "...", "config_version": 1 }
     * If the endpoint is not implemented yet, this will fail gracefully.
     */
    suspend fun pullServerConfig(serverUrl: String, token: String): ServerConfigResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/app/config")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val su = json.optString("server_url", json.optString("managed_server_url", ""))
                val ver = json.optLong("config_version", 0)
                ServerConfigResult(success = true, managedServerUrl = su, configVersion = ver)
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                ServerConfigResult(success = false, error = "Config pull failed: $code ${err.take(120)}")
            }
        } catch (e: Exception) {
            ServerConfigResult(success = false, error = e.message ?: "Config pull failed")
        }
    }

    /**
     * Push managed server URL to the server (admin only).
     * Endpoint: POST /api/admin/app/config -> { "server_url": "..." }
     */
    suspend fun pushManagedServerUrl(serverUrl: String, token: String, newServerUrl: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/admin/app/config")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = JSONObject().apply { put("server_url", newServerUrl) }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "Server URL updated")
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                SyncResult(success = false, error = "Config push failed: ${conn.responseCode} ${err.take(120)}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Config push failed")
        }
    }

    /**
     * Push API keys to server (admin only)
     * Server encrypts and stores in secure file
     */
    suspend fun pushApiKeys(serverUrl: String, token: String, chatGptKey: String, chatGptModel: String, geminiKey: String, geminiModel: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/admin/apikeys")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val body = JSONObject().apply {
                put("chatGptKey", chatGptKey)
                put("chatGptModel", chatGptModel)
                put("geminiKey", geminiKey)
                put("geminiModel", geminiModel)
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "API keys saved to server")
            } else {
                SyncResult(success = false, error = "Save failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Save failed")
        }
    }
    
    /**
     * Pull API keys from server (for new installs or logins)
     * Server decrypts and returns keys
     */
    suspend fun pullApiKeys(serverUrl: String, token: String): ApiKeysResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/admin/apikeys")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                ApiKeysResult(
                    success = true,
                    chatGptKey = json.optString("chatGptKey", ""),
                    chatGptModel = json.optString("chatGptModel", "gpt-4o"),
                    geminiKey = json.optString("geminiKey", ""),
                    geminiModel = json.optString("geminiModel", "gemini-1.5-flash")
                )
            } else {
                ApiKeysResult(success = false, error = "Pull failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            ApiKeysResult(success = false, error = e.message ?: "Pull failed")
        }
    }
    
    /**
     * Pull API keys for any authenticated user (not just admin)
     * Uses /api/sync/apikeys endpoint which is available to all authenticated users
     */
    suspend fun pullApiKeysForUser(serverUrl: String, token: String): ApiKeysResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/apikeys")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                ApiKeysResult(
                    success = true,
                    chatGptKey = json.optString("chatGptKey", ""),
                    chatGptModel = json.optString("chatGptModel", "gpt-4o"),
                    geminiKey = json.optString("geminiKey", ""),
                    geminiModel = json.optString("geminiModel", "gemini-1.5-flash")
                )
            } else {
                ApiKeysResult(success = false, error = "Pull failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            ApiKeysResult(success = false, error = e.message ?: "Pull failed")
        }
    }
    
    /**
     * Fetch admin usernames from server (Source of Truth)
     */
    suspend fun fetchAdminUsers(serverUrl: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/admin/users")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val admins = json.optJSONArray("admin_usernames") ?: return@withContext emptySet()
                val set = mutableSetOf<String>()
                for (i in 0 until admins.length()) {
                    set.add(admins.optString(i, "").lowercase())
                }
                set
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    // ============ DECK SYNC ============
    
    data class DeckSyncResult(
        val success: Boolean,
        val decks: List<StudyDeck> = emptyList(),
        val activeDeckId: String = "kenpo",
        val error: String = ""
    )
    
    /**
     * Pull decks from server
     */
    suspend fun pullDecks(serverUrl: String, token: String): DeckSyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/decks")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val decksArray = json.optJSONArray("decks") ?: JSONArray()
                val activeDeckId = json.optString("activeDeckId", "kenpo")
                
                val decks = mutableListOf<StudyDeck>()
                for (i in 0 until decksArray.length()) {
                    val dObj = decksArray.optJSONObject(i) ?: continue
                    decks.add(StudyDeck(
                        id = dObj.optString("id", ""),
                        name = dObj.optString("name", ""),
                        description = dObj.optString("description", ""),
                        isDefault = dObj.optBoolean("isDefault", false),
                        isBuiltIn = dObj.optBoolean("isBuiltIn", false),
                        sourceFile = dObj.optString("sourceFile", null),
                        cardCount = dObj.optInt("cardCount", 0),
                        createdAt = dObj.optLong("createdAt", 0),
                        updatedAt = dObj.optLong("updatedAt", 0)
                    ))
                }
                
                DeckSyncResult(success = true, decks = decks, activeDeckId = activeDeckId)
            } else {
                DeckSyncResult(success = false, error = "Pull failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            DeckSyncResult(success = false, error = e.message ?: "Pull failed")
        }
    }
    
    /**
     * Push decks to server
     */
    suspend fun pushDecks(serverUrl: String, token: String, decks: List<StudyDeck>, activeDeckId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/decks")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val decksArray = JSONArray()
            decks.forEach { deck ->
                val dObj = JSONObject().apply {
                    put("id", deck.id)
                    put("name", deck.name)
                    put("description", deck.description)
                    put("isDefault", deck.isDefault)
                    put("isBuiltIn", deck.isBuiltIn)
                    put("sourceFile", deck.sourceFile)
                    put("cardCount", deck.cardCount)
                    put("createdAt", deck.createdAt)
                    put("updatedAt", deck.updatedAt)
                }
                decksArray.put(dObj)
            }
            
            val body = JSONObject().apply {
                put("decks", decksArray)
                put("activeDeckId", activeDeckId)
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "Decks synced")
            } else {
                SyncResult(success = false, error = "Push failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Push failed")
        }
    }
    
    // ============ USER CARDS SYNC ============
    
    data class UserCardsSyncResult(
        val success: Boolean,
        val cards: List<FlashCard> = emptyList(),
        val error: String = ""
    )
    
    /**
     * Pull user cards from server
     */
    suspend fun pullUserCards(serverUrl: String, token: String, deckId: String = ""): UserCardsSyncResult = withContext(Dispatchers.IO) {
        try {
            val deckParam = if (deckId.isNotBlank()) "?deck_id=$deckId" else ""
            val url = URL("$serverUrl/api/sync/user_cards$deckParam")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val cardsArray = json.optJSONArray("cards") ?: JSONArray()
                
                val cards = mutableListOf<FlashCard>()
                for (i in 0 until cardsArray.length()) {
                    val cObj = cardsArray.optJSONObject(i) ?: continue
                    cards.add(FlashCard(
                        id = cObj.optString("id", ""),
                        group = cObj.optString("group", ""),
                        subgroup = cObj.optString("subgroup", null),
                        term = cObj.optString("term", ""),
                        pron = cObj.optString("pron", null),
                        meaning = cObj.optString("meaning", ""),
                        deckId = cObj.optString("deckId", "kenpo")
                    ))
                }
                
                UserCardsSyncResult(success = true, cards = cards)
            } else {
                UserCardsSyncResult(success = false, error = "Pull failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            UserCardsSyncResult(success = false, error = e.message ?: "Pull failed")
        }
    }
    
    /**
     * Push user cards to server
     */
    suspend fun pushUserCards(serverUrl: String, token: String, cards: List<FlashCard>, deckId: String = "", replaceAll: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/sync/user_cards")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val cardsArray = JSONArray()
            cards.forEach { card ->
                val cObj = JSONObject().apply {
                    put("id", card.id)
                    put("group", card.group)
                    put("subgroup", card.subgroup)
                    put("term", card.term)
                    put("pron", card.pron)
                    put("meaning", card.meaning)
                    put("deckId", card.deckId)
                }
                cardsArray.put(cObj)
            }
            
            val body = JSONObject().apply {
                put("cards", cardsArray)
                put("deckId", deckId)
                put("replaceAll", replaceAll)
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val added = json.optInt("added", 0)
                val updated = json.optInt("updated", 0)
                SyncResult(success = true, message = "Cards synced: $added added, $updated updated")
            } else {
                SyncResult(success = false, error = "Push failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Push failed")
        }
    }
    
    // ============ VOCABULARY SYNC ============
    
    data class VocabularySyncResult(
        val success: Boolean,
        val cards: List<FlashCard> = emptyList(),
        val error: String = ""
    )
    
    /**
     * Pull vocabulary (kenpo_words.json) from server
     * This is the canonical source of the built-in vocabulary
     */
    suspend fun pullVocabulary(serverUrl: String): VocabularySyncResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/vocabulary")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val cardsArray = JSONArray(response)
                
                val cards = mutableListOf<FlashCard>()
                for (i in 0 until cardsArray.length()) {
                    val cObj = cardsArray.optJSONObject(i) ?: continue
                    // Generate ID from term if not present
                    val term = cObj.optString("term", "")
                    val id = term.lowercase().replace(" ", "_").take(16) + "_" + i.toString().padStart(3, '0')
                    
                    cards.add(FlashCard(
                        id = id,
                        group = cObj.optString("group", ""),
                        subgroup = cObj.optString("subgroup", null),
                        term = term,
                        pron = cObj.optString("pron", null),
                        meaning = cObj.optString("meaning", ""),
                        deckId = "kenpo"
                    ))
                }
                
                VocabularySyncResult(success = true, cards = cards)
            } else {
                VocabularySyncResult(success = false, error = "Pull failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            VocabularySyncResult(success = false, error = e.message ?: "Pull failed")
        }
    }
}

/**
 * SERVER IMPLEMENTATION GUIDE
 * ===========================
 * 
 * Your Express.js server at http://sidscri.tplinkdns.com:8009 needs these endpoints:
 * 
 * Data directory: C:/Program Files/Kenpo Flashcards/_internal/data/
 * 
 * 1. POST /api/login
 *    - Read profiles.json to find user
 *    - Verify password (consider bcrypt)
 *    - Return { token, userId, username }
 *    - Token can be JWT or simple UUID stored in session
 * 
 * 2. GET /api/sync/pull (Authorization: Bearer {token})
 *    - Decode token to get userId
 *    - Read users/{userId}/progress.json
 *    - Return { progress: { cardId: "status", ... } }
 * 
 * 3. POST /api/sync/push (Authorization: Bearer {token})
 *    - Decode token to get userId
 *    - Write to users/{userId}/progress.json
 *    - Body: { progress: { cardId: "status", ... } }
 * 
 * 4. GET /api/breakdowns
 *    - Read breakdown.json
 *    - Return { breakdowns: { termId: { term, parts, literal, ... }, ... } }
 * 
 * 5. POST /api/breakdowns (Authorization: Bearer {token})
 *    - Update breakdown.json with new/updated breakdown
 *    - Body: { id, term, parts, literal, notes }
 * 
 * profiles.json structure:
 * {
 *   "users": [
 *     { "id": "user1", "username": "sidscri", "passwordHash": "...", "email": "..." },
 *     ...
 *   ]
 * }
 * 
 * users/{userId}/progress.json structure:
 * {
 *   "cardId1": "learned",
 *   "cardId2": "unsure",
 *   ...
 * }
 */


    // =========================
    // GEN8 Token Admin Endpoints
    // =========================
    suspend fun syncFetchAdminStatus(baseUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/admin/status")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncRedeemInviteCode(baseUrl: String, token: String, inviteCode: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/redeem-invite-code")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("code", inviteCode)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminGetDeckConfig(baseUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/deck-config")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminSetDeckConfig(baseUrl: String, token: String, config: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/deck-config")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.outputStream.use { it.write(config.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminCreateInviteCode(baseUrl: String, token: String, deckId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/deck-invite-code")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("deckId", deckId)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminDeleteInviteCode(baseUrl: String, token: String, codeToDelete: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/deck-invite-code/${codeToDelete}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    // Option2 admin endpoints
    suspend fun syncAdminGetUsers(baseUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/users")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminGetStats(baseUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/stats")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminGetLogs(baseUrl: String, token: String, type: String = "all", limit: Int = 200): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/logs?type=${type}&limit=${limit}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminClearLogs(baseUrl: String, token: String, type: String = "all"): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/logs/clear")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("type", type)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminGetUserDeckAccess(baseUrl: String, token: String, userId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/user/${userId}/deck-access")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminSetUserDeckAccess(baseUrl: String, token: String, userId: String, unlockedDecks: List<String>, builtInDisabled: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/user/${userId}/deck-access")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("unlockedDecks", JSONArray(unlockedDecks)).put("builtInDisabled", builtInDisabled)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminUpdateUser(baseUrl: String, token: String, userId: String, isAdmin: Boolean): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/user/update")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("userId", userId).put("isAdmin", isAdmin)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }

    suspend fun syncAdminResetPassword(baseUrl: String, token: String, userId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/api/sync/admin/user/reset_password")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val payload = JSONObject().put("userId", userId)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        JSONObject(text)
    }
