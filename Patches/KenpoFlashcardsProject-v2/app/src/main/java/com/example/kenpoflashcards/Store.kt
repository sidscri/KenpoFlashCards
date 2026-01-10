package com.example.kenpoflashcards

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "kenpo_flashcards_store")

class Store(private val context: Context) {

    private val KEY_PROGRESS_JSON = stringPreferencesKey("progress_json")
    private val KEY_CUSTOM_CARDS_JSON = stringPreferencesKey("custom_cards_json")
    private val KEY_BREAKDOWNS_JSON = stringPreferencesKey("breakdowns_json")
    private val KEY_SETTINGS_SINGLE_JSON = stringPreferencesKey("settings_single_json")
    private val KEY_SETTINGS_ALL_JSON = stringPreferencesKey("settings_all_json")
    private val KEY_LEARNED_JSON = stringPreferencesKey("learned_json")
    private val KEY_DELETED_JSON = stringPreferencesKey("deleted_json")
    private val KEY_CUSTOM_SET_JSON = stringPreferencesKey("custom_set_json")
    private val KEY_ADMIN_JSON = stringPreferencesKey("admin_json")

    fun progressFlow(): Flow<ProgressState> =
        context.dataStore.data.map { prefs ->
            val progressRaw = prefs[KEY_PROGRESS_JSON]
            if (!progressRaw.isNullOrBlank()) {
                val obj = JSONObject(progressRaw)
                val statuses = mutableMapOf<String, CardStatus>()
                obj.keys().forEach { key ->
                    val statusStr = obj.optString(key, "active")
                    statuses[key] = when (statusStr.lowercase()) {
                        "learned" -> CardStatus.LEARNED
                        "unsure" -> CardStatus.UNSURE
                        "deleted" -> CardStatus.DELETED
                        else -> CardStatus.ACTIVE
                    }
                }
                return@map ProgressState(statuses)
            }
            val learnedRaw = prefs[KEY_LEARNED_JSON] ?: "{}"
            val deletedRaw = prefs[KEY_DELETED_JSON] ?: "{}"
            val learnedObj = JSONObject(learnedRaw)
            val deletedObj = JSONObject(deletedRaw)
            val statuses = mutableMapOf<String, CardStatus>()
            learnedObj.keys().asSequence().filter { learnedObj.optBoolean(it, false) }.forEach { statuses[it] = CardStatus.LEARNED }
            deletedObj.keys().asSequence().filter { deletedObj.optBoolean(it, false) }.forEach { statuses[it] = CardStatus.DELETED }
            ProgressState(statuses)
        }

    suspend fun setStatus(id: String, status: CardStatus) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_PROGRESS_JSON] ?: "{}"
            val obj = JSONObject(raw)
            if (status == CardStatus.ACTIVE) obj.remove(id) else obj.put(id, status.name.lowercase())
            prefs[KEY_PROGRESS_JSON] = obj.toString()
        }
    }

    suspend fun setLearned(id: String, learned: Boolean) = setStatus(id, if (learned) CardStatus.LEARNED else CardStatus.ACTIVE)
    suspend fun setDeleted(id: String, deleted: Boolean) = setStatus(id, if (deleted) CardStatus.DELETED else CardStatus.ACTIVE)
    suspend fun setUnsure(id: String, unsure: Boolean) = setStatus(id, if (unsure) CardStatus.UNSURE else CardStatus.ACTIVE)

    fun customCardsFlow(): Flow<List<FlashCard>> = context.dataStore.data.map { prefs -> JsonUtil.parseCardsArray(prefs[KEY_CUSTOM_CARDS_JSON] ?: "[]") }
    suspend fun replaceCustomCards(cards: List<FlashCard>) { context.dataStore.edit { prefs -> prefs[KEY_CUSTOM_CARDS_JSON] = JsonUtil.cardsToJsonArray(cards) } }
    suspend fun clearAllProgress() { context.dataStore.edit { prefs -> prefs.remove(KEY_PROGRESS_JSON); prefs.remove(KEY_LEARNED_JSON); prefs.remove(KEY_DELETED_JSON) } }

    // Custom Study Set
    private val KEY_CUSTOM_SET_STATUS_JSON = stringPreferencesKey("custom_set_status_json")
    
    fun customSetFlow(): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_SET_JSON] ?: "[]"
        try { val arr = JSONArray(raw); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); set } catch (_: Exception) { emptySet() }
    }
    suspend fun addToCustomSet(id: String) { context.dataStore.edit { prefs ->
        val arr = try { JSONArray(prefs[KEY_CUSTOM_SET_JSON] ?: "[]") } catch (_: Exception) { JSONArray() }
        var found = false; for (i in 0 until arr.length()) if (arr.getString(i) == id) { found = true; break }
        if (!found) { arr.put(id); prefs[KEY_CUSTOM_SET_JSON] = arr.toString() }
    } }
    suspend fun removeFromCustomSet(id: String) { context.dataStore.edit { prefs ->
        val arr = try { JSONArray(prefs[KEY_CUSTOM_SET_JSON] ?: "[]") } catch (_: Exception) { JSONArray() }
        val newArr = JSONArray(); for (i in 0 until arr.length()) { val item = arr.getString(i); if (item != id) newArr.put(item) }
        prefs[KEY_CUSTOM_SET_JSON] = newArr.toString()
        // Also remove from custom status
        val statusObj = try { JSONObject(prefs[KEY_CUSTOM_SET_STATUS_JSON] ?: "{}") } catch (_: Exception) { JSONObject() }
        statusObj.remove(id)
        prefs[KEY_CUSTOM_SET_STATUS_JSON] = statusObj.toString()
    } }
    suspend fun clearCustomSet() { context.dataStore.edit { prefs -> prefs[KEY_CUSTOM_SET_JSON] = "[]"; prefs[KEY_CUSTOM_SET_STATUS_JSON] = "{}" } }
    
    // Custom Set isolated status (separate from main deck status)
    fun customSetStatusFlow(): Flow<Map<String, CustomCardStatus>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_SET_STATUS_JSON] ?: "{}"
        try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, CustomCardStatus>()
            obj.keys().forEach { key ->
                map[key] = when (obj.optString(key, "active").lowercase()) {
                    "unsure" -> CustomCardStatus.UNSURE
                    "learned" -> CustomCardStatus.LEARNED
                    else -> CustomCardStatus.ACTIVE
                }
            }
            map
        } catch (_: Exception) { emptyMap() }
    }
    suspend fun setCustomSetStatus(id: String, status: CustomCardStatus) { context.dataStore.edit { prefs ->
        val obj = try { JSONObject(prefs[KEY_CUSTOM_SET_STATUS_JSON] ?: "{}") } catch (_: Exception) { JSONObject() }
        if (status == CustomCardStatus.ACTIVE) obj.remove(id) else obj.put(id, status.name.lowercase())
        prefs[KEY_CUSTOM_SET_STATUS_JSON] = obj.toString()
    } }

    // Breakdowns
    fun breakdownsFlow(): Flow<Map<String, TermBreakdown>> = context.dataStore.data.map { prefs -> parseBreakdowns(prefs[KEY_BREAKDOWNS_JSON] ?: "{}") }
    suspend fun saveBreakdown(breakdown: TermBreakdown) { context.dataStore.edit { prefs ->
        val obj = JSONObject(prefs[KEY_BREAKDOWNS_JSON] ?: "{}"); obj.put(breakdown.id, encodeBreakdown(breakdown)); prefs[KEY_BREAKDOWNS_JSON] = obj.toString()
    } }
    suspend fun deleteBreakdown(cardId: String) { context.dataStore.edit { prefs ->
        val obj = JSONObject(prefs[KEY_BREAKDOWNS_JSON] ?: "{}"); obj.remove(cardId); prefs[KEY_BREAKDOWNS_JSON] = obj.toString()
    } }
    private fun parseBreakdowns(raw: String): Map<String, TermBreakdown> {
        val result = mutableMapOf<String, TermBreakdown>()
        try { val obj = JSONObject(raw); obj.keys().forEach { key -> obj.optJSONObject(key)?.let { result[key] = decodeBreakdown(key, it) } } } catch (_: Exception) {}
        return result
    }
    private fun decodeBreakdown(id: String, obj: JSONObject): TermBreakdown {
        val partsArray = obj.optJSONArray("parts") ?: JSONArray()
        val parts = mutableListOf<BreakdownPart>()
        for (i in 0 until partsArray.length()) { partsArray.optJSONObject(i)?.let { parts.add(BreakdownPart(it.optString("part", ""), it.optString("meaning", ""))) } }
        return TermBreakdown(id, obj.optString("term", ""), parts, obj.optString("literal", ""), obj.optString("notes", ""), obj.optLong("updated_at", System.currentTimeMillis() / 1000), obj.optString("updated_by", null))
    }
    private fun encodeBreakdown(bd: TermBreakdown): JSONObject {
        val obj = JSONObject(); obj.put("term", bd.term); obj.put("literal", bd.literal); obj.put("notes", bd.notes); obj.put("updated_at", bd.updatedAt); bd.updatedBy?.let { obj.put("updated_by", it) }
        val partsArray = JSONArray(); bd.parts.forEach { val p = JSONObject(); p.put("part", it.part); p.put("meaning", it.meaning); partsArray.put(p) }; obj.put("parts", partsArray)
        return obj
    }

    // Study settings
    fun settingsSingleFlow(): Flow<StudySettings> = context.dataStore.data.map { prefs -> decodeStudySettings(prefs[KEY_SETTINGS_SINGLE_JSON]) }
    fun settingsAllFlow(): Flow<StudySettings> = context.dataStore.data.map { prefs -> decodeStudySettings(prefs[KEY_SETTINGS_ALL_JSON]) }
    suspend fun saveSettingsSingle(s: StudySettings) { context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_SINGLE_JSON] = encodeStudySettings(s) } }
    suspend fun saveSettingsAll(s: StudySettings) { context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_ALL_JSON] = encodeStudySettings(s) } }

    private fun encodeStudySettings(s: StudySettings): String {
        val o = JSONObject()
        o.put("selectedGroup", s.selectedGroup); o.put("selectedSubgroup", s.selectedSubgroup); o.put("studyFilterGroup", s.studyFilterGroup)
        o.put("sortMode", s.sortMode.name); o.put("randomize", s.randomize); o.put("showGroup", s.showGroup); o.put("showSubgroup", s.showSubgroup); o.put("reverseCards", s.reverseCards)
        o.put("randomizeUnlearned", s.randomizeUnlearned); o.put("randomizeUnsure", s.randomizeUnsure); o.put("randomizeLearnedStudy", s.randomizeLearnedStudy); o.put("linkRandomizeTabs", s.linkRandomizeTabs)
        o.put("showBreakdownOnDefinition", s.showBreakdownOnDefinition); o.put("showDefinitionsInAllList", s.showDefinitionsInAllList); o.put("showDefinitionsInLearnedList", s.showDefinitionsInLearnedList)
        o.put("showLearnedListGroupLabel", s.showLearnedListGroupLabel); o.put("showUnlearnedUnsureButtonsInAllList", s.showUnlearnedUnsureButtonsInAllList); o.put("showRelearnUnsureButtonsInLearnedList", s.showRelearnUnsureButtonsInLearnedList)
        o.put("learnedViewMode", s.learnedViewMode.name); o.put("speechVoice", s.speechVoice); o.put("speechRate", s.speechRate.toDouble()); o.put("speakPronunciationOnly", s.speakPronunciationOnly); o.put("filterGroup", s.filterGroup)
        o.put("showCustomSetButton", s.showCustomSetButton)
        // CustomSetSettings
        val cs = s.customSetSettings
        val cso = JSONObject()
        cso.put("randomOrder", cs.randomOrder); cso.put("reverseCards", cs.reverseCards); cso.put("showGroupLabel", cs.showGroupLabel)
        cso.put("showBreakdown", cs.showBreakdown); cso.put("sortMode", cs.sortMode.name); cso.put("showDefinitions", cs.showDefinitions)
        cso.put("showActionButtons", cs.showActionButtons); cso.put("reflectInMainDecks", cs.reflectInMainDecks)
        o.put("customSetSettings", cso)
        return o.toString()
    }

    private fun decodeStudySettings(raw: String?): StudySettings {
        if (raw.isNullOrBlank()) return StudySettings()
        return try {
            val o = JSONObject(raw)
            val cso = o.optJSONObject("customSetSettings")
            val customSetSettings = if (cso != null) {
                CustomSetSettings(
                    randomOrder = cso.optBoolean("randomOrder", false),
                    reverseCards = cso.optBoolean("reverseCards", false),
                    showGroupLabel = cso.optBoolean("showGroupLabel", false),
                    showBreakdown = cso.optBoolean("showBreakdown", true),
                    sortMode = runCatching { SortMode.valueOf(cso.optString("sortMode", SortMode.JSON_ORDER.name)) }.getOrDefault(SortMode.JSON_ORDER),
                    showDefinitions = cso.optBoolean("showDefinitions", true),
                    showActionButtons = cso.optBoolean("showActionButtons", true),
                    reflectInMainDecks = cso.optBoolean("reflectInMainDecks", false)
                )
            } else CustomSetSettings()
            StudySettings(
                selectedGroup = o.optString("selectedGroup", "").takeIf { it.isNotBlank() },
                selectedSubgroup = o.optString("selectedSubgroup", "").takeIf { it.isNotBlank() },
                studyFilterGroup = o.optString("studyFilterGroup", "").takeIf { it.isNotBlank() },
                sortMode = runCatching { SortMode.valueOf(o.optString("sortMode", SortMode.JSON_ORDER.name)) }.getOrDefault(SortMode.JSON_ORDER),
                randomize = o.optBoolean("randomize", true), showGroup = o.optBoolean("showGroup", false), showSubgroup = o.optBoolean("showSubgroup", false), reverseCards = o.optBoolean("reverseCards", false),
                randomizeUnlearned = o.optBoolean("randomizeUnlearned", false), randomizeUnsure = o.optBoolean("randomizeUnsure", false), randomizeLearnedStudy = o.optBoolean("randomizeLearnedStudy", false), linkRandomizeTabs = o.optBoolean("linkRandomizeTabs", true),
                showBreakdownOnDefinition = o.optBoolean("showBreakdownOnDefinition", true), showDefinitionsInAllList = o.optBoolean("showDefinitionsInAllList", true), showDefinitionsInLearnedList = o.optBoolean("showDefinitionsInLearnedList", true),
                showLearnedListGroupLabel = o.optBoolean("showLearnedListGroupLabel", true), showUnlearnedUnsureButtonsInAllList = o.optBoolean("showUnlearnedUnsureButtonsInAllList", true), showRelearnUnsureButtonsInLearnedList = o.optBoolean("showRelearnUnsureButtonsInLearnedList", true),
                learnedViewMode = runCatching { LearnedViewMode.valueOf(o.optString("learnedViewMode", LearnedViewMode.LIST.name)) }.getOrDefault(LearnedViewMode.LIST),
                speechVoice = o.optString("speechVoice", null)?.takeIf { it.isNotBlank() }, speechRate = o.optDouble("speechRate", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                speakPronunciationOnly = o.optBoolean("speakPronunciationOnly", false), filterGroup = o.optString("filterGroup", null)?.takeIf { it.isNotBlank() },
                showCustomSetButton = o.optBoolean("showCustomSetButton", true),
                customSetSettings = customSetSettings
            )
        } catch (_: Exception) { StudySettings() }
    }

    // Admin settings
    fun adminSettingsFlow(): Flow<AdminSettings> = context.dataStore.data.map { prefs -> decodeAdminSettings(prefs[KEY_ADMIN_JSON]) }
    suspend fun saveAdminSettings(s: AdminSettings) { context.dataStore.edit { prefs -> prefs[KEY_ADMIN_JSON] = encodeAdminSettings(s) } }

    private fun encodeAdminSettings(s: AdminSettings): String {
        val o = JSONObject()
        o.put("webAppUrl", s.webAppUrl); o.put("authToken", s.authToken); o.put("username", s.username); o.put("isLoggedIn", s.isLoggedIn); o.put("lastSyncTime", s.lastSyncTime)
        o.put("chatGptApiKey", s.chatGptApiKey); o.put("chatGptEnabled", s.chatGptEnabled)
        return o.toString()
    }

    private fun decodeAdminSettings(raw: String?): AdminSettings {
        if (raw.isNullOrBlank()) return AdminSettings()
        return try {
            val o = JSONObject(raw)
            AdminSettings(
                webAppUrl = o.optString("webAppUrl", ""), authToken = o.optString("authToken", ""), username = o.optString("username", ""),
                isLoggedIn = o.optBoolean("isLoggedIn", false), lastSyncTime = o.optLong("lastSyncTime", 0),
                chatGptApiKey = o.optString("chatGptApiKey", ""), chatGptEnabled = o.optBoolean("chatGptEnabled", false)
            )
        } catch (_: Exception) { AdminSettings() }
    }
}
