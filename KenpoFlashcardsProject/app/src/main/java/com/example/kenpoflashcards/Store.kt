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
}
