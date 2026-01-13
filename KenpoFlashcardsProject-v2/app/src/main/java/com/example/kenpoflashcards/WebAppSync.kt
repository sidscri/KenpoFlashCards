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
 * Connects to: http://sidscri.tplinkdns.com:8009
 * 
 * Server Data Structure (c:/personal-servers/kenpoflashcardswebserver/data/):
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
    }
    
    /**
     * Push local progress to server
     * Server saves to users/{userId}/progress.json
     */
    suspend fun pushProgress(serverUrl: String, token: String, progress: ProgressState): SyncResult = withContext(Dispatchers.IO) {
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
            progress.statuses.forEach { (id, status) ->
                progressJson.put(id, status.name.lowercase())
            }
            
            val body = JSONObject().apply {
                put("progress", progressJson)
                put("timestamp", System.currentTimeMillis())
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            if (conn.responseCode == 200) {
                SyncResult(success = true, message = "Progress synced successfully")
            } else {
                SyncResult(success = false, error = "Sync failed: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message ?: "Sync failed")
        }
    }
    
    /**
     * Pull progress from server
     * Server reads from users/{userId}/progress.json
     */
    suspend fun pullProgress(serverUrl: String, token: String): Pair<SyncResult, ProgressState?> = withContext(Dispatchers.IO) {
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
                
                val statuses = mutableMapOf<String, CardStatus>()
                progressJson.keys().forEach { key ->
                    val statusStr = progressJson.optString(key, "active")
                    statuses[key] = when (statusStr.lowercase()) {
                        "learned" -> CardStatus.LEARNED
                        "unsure" -> CardStatus.UNSURE
                        "deleted" -> CardStatus.DELETED
                        else -> CardStatus.ACTIVE
                    }
                }
                
                Pair(SyncResult(success = true, message = "Progress loaded"), ProgressState(statuses))
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
}

/**
 * SERVER IMPLEMENTATION GUIDE
 * ===========================
 * 
 * Your Express.js server at http://sidscri.tplinkdns.com:8009 needs these endpoints:
 * 
 * Data directory: c:/personal-servers/kenpoflashcardswebserver/data/
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
