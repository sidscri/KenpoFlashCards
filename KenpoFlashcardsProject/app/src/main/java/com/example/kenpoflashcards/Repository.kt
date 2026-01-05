package com.example.kenpoflashcards

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ProgressState(
    val learnedIds: Set<String>,
    val deletedIds: Set<String>
)

class Repository(private val context: Context, private val store: Store) {

    private fun loadDefaultCards(): List<FlashCard> {
        val json = context.assets.open("kenpo_words.json").bufferedReader().use { it.readText() }
        return JsonUtil.readAssetCards(json)
    }

    fun allCardsFlow(): Flow<List<FlashCard>> {
        val defaults = loadDefaultCards()
        return store.customCardsFlow().combine(progressFlow()) { custom, _ ->
            (defaults + custom).distinctBy { it.id }
        }
    }

    fun progressFlow(): Flow<ProgressState> =
        combine(store.learnedIdsFlow(), store.deletedIdsFlow()) { learned, deleted ->
            ProgressState(learnedIds = learned, deletedIds = deleted)
        }

    fun settingsSingleFlow(): Flow<StudySettings> = store.settingsSingleFlow()
    fun settingsAllFlow(): Flow<StudySettings> = store.settingsAllFlow()
    suspend fun saveSettingsSingle(s: StudySettings) = store.saveSettingsSingle(s)
    suspend fun saveSettingsAll(s: StudySettings) = store.saveSettingsAll(s)

    suspend fun setLearned(id: String, learned: Boolean) = store.setLearned(id, learned)
    suspend fun setDeleted(id: String, deleted: Boolean) = store.setDeleted(id, deleted)

    suspend fun replaceCustomCards(cards: List<FlashCard>) = store.replaceCustomCards(cards)
    suspend fun clearAllProgress() = store.clearAllProgress()
}
