package com.example.kenpoflashcards

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

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

    fun progressFlow(): Flow<ProgressState> = store.progressFlow()

    fun settingsSingleFlow(): Flow<StudySettings> = store.settingsSingleFlow()
    fun settingsAllFlow(): Flow<StudySettings> = store.settingsAllFlow()
    suspend fun saveSettingsSingle(s: StudySettings) = store.saveSettingsSingle(s)
    suspend fun saveSettingsAll(s: StudySettings) = store.saveSettingsAll(s)

    // Status management
    suspend fun setStatus(id: String, status: CardStatus) = store.setStatus(id, status)
    suspend fun setLearned(id: String, learned: Boolean) = store.setLearned(id, learned)
    suspend fun setDeleted(id: String, deleted: Boolean) = store.setDeleted(id, deleted)
    suspend fun setUnsure(id: String, unsure: Boolean) = store.setUnsure(id, unsure)

    suspend fun replaceCustomCards(cards: List<FlashCard>) = store.replaceCustomCards(cards)
    suspend fun clearAllProgress() = store.clearAllProgress()
    
    // --------------------
    // Breakdowns
    // --------------------
    
    fun breakdownsFlow(): Flow<Map<String, TermBreakdown>> = store.breakdownsFlow()
    
    suspend fun getBreakdown(cardId: String): TermBreakdown? {
        return store.breakdownsFlow().first()[cardId]
    }
    
    suspend fun saveBreakdown(breakdown: TermBreakdown) = store.saveBreakdown(breakdown)
    
    suspend fun deleteBreakdown(cardId: String) = store.deleteBreakdown(cardId)
    
    /**
     * Get counts for each status
     */
    suspend fun getCounts(): StatusCounts {
        val progress = progressFlow().first()
        val allCards = allCardsFlow().first()
        
        var active = 0
        var unsure = 0
        var learned = 0
        var deleted = 0
        
        allCards.forEach { card ->
            when (progress.getStatus(card.id)) {
                CardStatus.ACTIVE -> active++
                CardStatus.UNSURE -> unsure++
                CardStatus.LEARNED -> learned++
                CardStatus.DELETED -> deleted++
            }
        }
        
        return StatusCounts(active, unsure, learned, deleted)
    }
}

data class StatusCounts(
    val active: Int,
    val unsure: Int,
    val learned: Int,
    val deleted: Int
) {
    val total: Int get() = active + unsure + learned + deleted
}
