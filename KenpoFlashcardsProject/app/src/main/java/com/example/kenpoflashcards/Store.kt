package com.example.kenpoflashcards

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "kenpo_flashcards_store")

class Store(private val context: Context) {

    private val KEY_LEARNED_JSON = stringPreferencesKey("learned_json")   // map of id -> true
    private val KEY_DELETED_JSON = stringPreferencesKey("deleted_json")   // map of id -> true (hidden everywhere)
    private val KEY_CUSTOM_CARDS_JSON = stringPreferencesKey("custom_cards_json") // array

    // Per-study-mode settings
    private val KEY_SETTINGS_SINGLE_JSON = stringPreferencesKey("settings_single_json")
    private val KEY_SETTINGS_ALL_JSON = stringPreferencesKey("settings_all_json")

    fun learnedIdsFlow(): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_LEARNED_JSON] ?: "{}"
            val obj = JSONObject(raw)
            obj.keys().asSequence().filter { obj.optBoolean(it, false) }.toSet()
        }

    fun deletedIdsFlow(): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_DELETED_JSON] ?: "{}"
            val obj = JSONObject(raw)
            obj.keys().asSequence().filter { obj.optBoolean(it, false) }.toSet()
        }

    suspend fun setLearned(id: String, learned: Boolean) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_LEARNED_JSON] ?: "{}"
            val obj = JSONObject(raw)
            if (learned) obj.put(id, true) else obj.remove(id)
            prefs[KEY_LEARNED_JSON] = obj.toString()
        }
    }

    suspend fun setDeleted(id: String, deleted: Boolean) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_DELETED_JSON] ?: "{}"
            val obj = JSONObject(raw)
            if (deleted) obj.put(id, true) else obj.remove(id)
            prefs[KEY_DELETED_JSON] = obj.toString()
        }
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
            prefs.remove(KEY_LEARNED_JSON)
            prefs.remove(KEY_DELETED_JSON)
        }
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
                reverseCards = o.optBoolean("reverseCards", false)
            )
        } catch (_: Exception) {
            StudySettings()
        }
    }
}
