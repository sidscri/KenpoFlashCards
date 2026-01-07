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

    // Progress: now stores full status per card
    private val KEY_PROGRESS_JSON = stringPreferencesKey("progress_json")  // map of id -> status string
    private val KEY_CUSTOM_CARDS_JSON = stringPreferencesKey("custom_cards_json")
    
    // Breakdowns: shared term breakdowns
    private val KEY_BREAKDOWNS_JSON = stringPreferencesKey("breakdowns_json")

    // Per-study-mode settings
    private val KEY_SETTINGS_SINGLE_JSON = stringPreferencesKey("settings_single_json")
    private val KEY_SETTINGS_ALL_JSON = stringPreferencesKey("settings_all_json")

    // Legacy keys for migration
    private val KEY_LEARNED_JSON = stringPreferencesKey("learned_json")
    private val KEY_DELETED_JSON = stringPreferencesKey("deleted_json")

    /**
     * Get progress flow with full status tracking
     */
    fun progressFlow(): Flow<ProgressState> =
        context.dataStore.data.map { prefs ->
            // Try new format first
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
            
            // Fall back to legacy format and migrate
            val learnedRaw = prefs[KEY_LEARNED_JSON] ?: "{}"
            val deletedRaw = prefs[KEY_DELETED_JSON] ?: "{}"
            val learnedObj = JSONObject(learnedRaw)
            val deletedObj = JSONObject(deletedRaw)
            
            val statuses = mutableMapOf<String, CardStatus>()
            learnedObj.keys().asSequence()
                .filter { learnedObj.optBoolean(it, false) }
                .forEach { statuses[it] = CardStatus.LEARNED }
            deletedObj.keys().asSequence()
                .filter { deletedObj.optBoolean(it, false) }
                .forEach { statuses[it] = CardStatus.DELETED }
            
            ProgressState(statuses)
        }

    /**
     * Set card status (active, unsure, learned, deleted)
     */
    suspend fun setStatus(id: String, status: CardStatus) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_PROGRESS_JSON] ?: "{}"
            val obj = JSONObject(raw)
            
            if (status == CardStatus.ACTIVE) {
                obj.remove(id)  // Active is default, no need to store
            } else {
                obj.put(id, status.name.lowercase())
            }
            
            prefs[KEY_PROGRESS_JSON] = obj.toString()
        }
    }

    // Legacy compatibility methods
    suspend fun setLearned(id: String, learned: Boolean) {
        setStatus(id, if (learned) CardStatus.LEARNED else CardStatus.ACTIVE)
    }

    suspend fun setDeleted(id: String, deleted: Boolean) {
        setStatus(id, if (deleted) CardStatus.DELETED else CardStatus.ACTIVE)
    }

    suspend fun setUnsure(id: String, unsure: Boolean) {
        setStatus(id, if (unsure) CardStatus.UNSURE else CardStatus.ACTIVE)
    }

    fun customCardsFlow(): Flow<List<FlashCard>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_CUSTOM_CARDS_JSON] ?: "[]"
            JsonUtil.parseCardsArray(raw)
        }

    suspend fun replaceCustomCards(cards: List<FlashCard>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_CARDS_JSON] = JsonUtil.cardsToJsonArray(cards)
        }
    }

    suspend fun clearAllProgress() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PROGRESS_JSON)
            prefs.remove(KEY_LEARNED_JSON)
            prefs.remove(KEY_DELETED_JSON)
        }
    }

    // --------------------
    // Breakdowns
    // --------------------
    
    fun breakdownsFlow(): Flow<Map<String, TermBreakdown>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_BREAKDOWNS_JSON] ?: "{}"
            parseBreakdowns(raw)
        }
    
    suspend fun getBreakdown(cardId: String): TermBreakdown? {
        var result: TermBreakdown? = null
        context.dataStore.data.collect { prefs ->
            val raw = prefs[KEY_BREAKDOWNS_JSON] ?: "{}"
            val all = parseBreakdowns(raw)
            result = all[cardId]
        }
        return result
    }
    
    suspend fun saveBreakdown(breakdown: TermBreakdown) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_BREAKDOWNS_JSON] ?: "{}"
            val obj = JSONObject(raw)
            obj.put(breakdown.id, encodeBreakdown(breakdown))
            prefs[KEY_BREAKDOWNS_JSON] = obj.toString()
        }
    }
    
    suspend fun deleteBreakdown(cardId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_BREAKDOWNS_JSON] ?: "{}"
            val obj = JSONObject(raw)
            obj.remove(cardId)
            prefs[KEY_BREAKDOWNS_JSON] = obj.toString()
        }
    }
    
    private fun parseBreakdowns(raw: String): Map<String, TermBreakdown> {
        val result = mutableMapOf<String, TermBreakdown>()
        try {
            val obj = JSONObject(raw)
            obj.keys().forEach { key ->
                val bdObj = obj.optJSONObject(key) ?: return@forEach
                result[key] = decodeBreakdown(key, bdObj)
            }
        } catch (_: Exception) {}
        return result
    }
    
    private fun decodeBreakdown(id: String, obj: JSONObject): TermBreakdown {
        val partsArray = obj.optJSONArray("parts") ?: JSONArray()
        val parts = mutableListOf<BreakdownPart>()
        for (i in 0 until partsArray.length()) {
            val pObj = partsArray.optJSONObject(i) ?: continue
            parts.add(BreakdownPart(
                part = pObj.optString("part", ""),
                meaning = pObj.optString("meaning", "")
            ))
        }
        return TermBreakdown(
            id = id,
            term = obj.optString("term", ""),
            parts = parts,
            literal = obj.optString("literal", ""),
            notes = obj.optString("notes", ""),
            updatedAt = obj.optLong("updated_at", System.currentTimeMillis() / 1000),
            updatedBy = obj.optString("updated_by", null)
        )
    }
    
    private fun encodeBreakdown(bd: TermBreakdown): JSONObject {
        val obj = JSONObject()
        obj.put("term", bd.term)
        obj.put("literal", bd.literal)
        obj.put("notes", bd.notes)
        obj.put("updated_at", bd.updatedAt)
        bd.updatedBy?.let { obj.put("updated_by", it) }
        
        val partsArray = JSONArray()
        bd.parts.forEach { part ->
            val pObj = JSONObject()
            pObj.put("part", part.part)
            pObj.put("meaning", part.meaning)
            partsArray.put(pObj)
        }
        obj.put("parts", partsArray)
        
        return obj
    }

    // --------------------
    // Study settings (per mode)
    // --------------------

    fun settingsSingleFlow(): Flow<StudySettings> =
        context.dataStore.data.map { prefs -> decodeStudySettings(prefs[KEY_SETTINGS_SINGLE_JSON]) }

    fun settingsAllFlow(): Flow<StudySettings> =
        context.dataStore.data.map { prefs -> decodeStudySettings(prefs[KEY_SETTINGS_ALL_JSON]) }

    suspend fun saveSettingsSingle(s: StudySettings) {
        context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_SINGLE_JSON] = encodeStudySettings(s) }
    }

    suspend fun saveSettingsAll(s: StudySettings) {
        context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_ALL_JSON] = encodeStudySettings(s) }
    }

    private fun encodeStudySettings(s: StudySettings): String {
        val o = JSONObject()
        o.put("selectedGroup", s.selectedGroup)
        o.put("selectedSubgroup", s.selectedSubgroup)
        o.put("sortMode", s.sortMode.name)
        o.put("randomize", s.randomize)
        o.put("showGroup", s.showGroup)
        o.put("showSubgroup", s.showSubgroup)
        o.put("reverseCards", s.reverseCards)
        
        // New settings from web app
        o.put("randomizeUnlearned", s.randomizeUnlearned)
        o.put("randomizeUnsure", s.randomizeUnsure)
        o.put("randomizeLearnedStudy", s.randomizeLearnedStudy)
        o.put("linkRandomizeTabs", s.linkRandomizeTabs)
        o.put("showBreakdownOnDefinition", s.showBreakdownOnDefinition)
        o.put("showDefinitionsInAllList", s.showDefinitionsInAllList)
        o.put("showDefinitionsInLearnedList", s.showDefinitionsInLearnedList)
        o.put("showLearnedListGroupLabel", s.showLearnedListGroupLabel)
        o.put("showUnlearnedUnsureButtonsInAllList", s.showUnlearnedUnsureButtonsInAllList)
        o.put("showRelearnUnsureButtonsInLearnedList", s.showRelearnUnsureButtonsInLearnedList)
        o.put("learnedViewMode", s.learnedViewMode.name)
        o.put("speechVoice", s.speechVoice)
        o.put("speechRate", s.speechRate.toDouble())
        
        return o.toString()
    }

    private fun decodeStudySettings(raw: String?): StudySettings {
        if (raw.isNullOrBlank()) return StudySettings()
        return try {
            val o = JSONObject(raw)
            StudySettings(
                selectedGroup = o.optString("selectedGroup", "").takeIf { it.isNotBlank() },
                selectedSubgroup = o.optString("selectedSubgroup", "").takeIf { it.isNotBlank() },
                sortMode = runCatching {
                    SortMode.valueOf(o.optString("sortMode", SortMode.RANDOM.name))
                }.getOrDefault(SortMode.RANDOM),
                randomize = o.optBoolean("randomize", true),
                showGroup = o.optBoolean("showGroup", true),
                showSubgroup = o.optBoolean("showSubgroup", true),
                reverseCards = o.optBoolean("reverseCards", false),
                
                // New settings
                randomizeUnlearned = o.optBoolean("randomizeUnlearned", true),
                randomizeUnsure = o.optBoolean("randomizeUnsure", true),
                randomizeLearnedStudy = o.optBoolean("randomizeLearnedStudy", true),
                linkRandomizeTabs = o.optBoolean("linkRandomizeTabs", true),
                showBreakdownOnDefinition = o.optBoolean("showBreakdownOnDefinition", false),
                showDefinitionsInAllList = o.optBoolean("showDefinitionsInAllList", true),
                showDefinitionsInLearnedList = o.optBoolean("showDefinitionsInLearnedList", true),
                showLearnedListGroupLabel = o.optBoolean("showLearnedListGroupLabel", true),
                showUnlearnedUnsureButtonsInAllList = o.optBoolean("showUnlearnedUnsureButtonsInAllList", true),
                showRelearnUnsureButtonsInLearnedList = o.optBoolean("showRelearnUnsureButtonsInLearnedList", true),
                learnedViewMode = runCatching {
                    LearnedViewMode.valueOf(o.optString("learnedViewMode", LearnedViewMode.LIST.name))
                }.getOrDefault(LearnedViewMode.LIST),
                speechVoice = o.optString("speechVoice", null)?.takeIf { it.isNotBlank() },
                speechRate = o.optDouble("speechRate", 1.0).toFloat().coerceIn(0.5f, 2.0f)
            )
        } catch (_: Exception) {
            StudySettings()
        }
    }
}
