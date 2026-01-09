package com.example.kenpoflashcards

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class Repository(private val context: Context, private val store: Store) {

    private var cachedDefaults: List<FlashCard>? = null
    
    private fun loadDefaultCards(): List<FlashCard> {
        cachedDefaults?.let { return it }
        val json = context.assets.open("kenpo_words.json").bufferedReader().use { it.readText() }
        val cards = JsonUtil.readAssetCards(json)
        cachedDefaults = cards
        return cards
    }

    fun allCardsFlow(): Flow<List<FlashCard>> {
        val defaults = loadDefaultCards()
        return store.customCardsFlow().combine(progressFlow()) { custom, _ ->
            (defaults + custom).distinctBy { it.id }
        }
    }
    
    fun getGroups(): List<String> = loadDefaultCards().map { it.group }.distinct().sorted()

    fun progressFlow(): Flow<ProgressState> = store.progressFlow()
    fun settingsSingleFlow(): Flow<StudySettings> = store.settingsSingleFlow()
    fun settingsAllFlow(): Flow<StudySettings> = store.settingsAllFlow()
    suspend fun saveSettingsSingle(s: StudySettings) = store.saveSettingsSingle(s)
    suspend fun saveSettingsAll(s: StudySettings) = store.saveSettingsAll(s)

    // Admin settings
    fun adminSettingsFlow(): Flow<AdminSettings> = store.adminSettingsFlow()
    suspend fun saveAdminSettings(s: AdminSettings) = store.saveAdminSettings(s)

    // Status management
    suspend fun setStatus(id: String, status: CardStatus) = store.setStatus(id, status)
    suspend fun setLearned(id: String, learned: Boolean) = store.setLearned(id, learned)
    suspend fun setDeleted(id: String, deleted: Boolean) = store.setDeleted(id, deleted)
    suspend fun setUnsure(id: String, unsure: Boolean) = store.setUnsure(id, unsure)

    suspend fun replaceCustomCards(cards: List<FlashCard>) = store.replaceCustomCards(cards)
    suspend fun clearAllProgress() = store.clearAllProgress()
    
    // Custom Study Set
    fun customSetFlow(): Flow<Set<String>> = store.customSetFlow()
    suspend fun addToCustomSet(id: String) = store.addToCustomSet(id)
    suspend fun removeFromCustomSet(id: String) = store.removeFromCustomSet(id)
    suspend fun clearCustomSet() = store.clearCustomSet()
    
    // Breakdowns
    fun breakdownsFlow(): Flow<Map<String, TermBreakdown>> = store.breakdownsFlow()
    suspend fun getBreakdown(cardId: String): TermBreakdown? = store.breakdownsFlow().first()[cardId]
    suspend fun saveBreakdown(breakdown: TermBreakdown) = store.saveBreakdown(breakdown)
    suspend fun deleteBreakdown(cardId: String) = store.deleteBreakdown(cardId)
    
    // Sync with web app
    suspend fun syncLogin(username: String, password: String): WebAppSync.LoginResult {
        val admin = adminSettingsFlow().first()
        val serverUrl = admin.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }
        return WebAppSync.login(serverUrl, username, password)
    }
    
    suspend fun syncPushProgress(): WebAppSync.SyncResult {
        // Re-read admin settings to ensure we have latest token
        val admin = store.adminSettingsFlow().first()
        if (!admin.isLoggedIn) {
            return WebAppSync.SyncResult(false, error = "Not logged in")
        }
        if (admin.authToken.isBlank()) {
            return WebAppSync.SyncResult(false, error = "No auth token - please login again")
        }
        val serverUrl = admin.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }
        val progress = progressFlow().first()
        return WebAppSync.pushProgress(serverUrl, admin.authToken, progress)
    }
    
    suspend fun syncPullProgress(): WebAppSync.SyncResult {
        val admin = store.adminSettingsFlow().first()
        if (!admin.isLoggedIn) {
            return WebAppSync.SyncResult(false, error = "Not logged in")
        }
        if (admin.authToken.isBlank()) {
            return WebAppSync.SyncResult(false, error = "No auth token - please login again")
        }
        val serverUrl = admin.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }
        val (result, progress) = WebAppSync.pullProgress(serverUrl, admin.authToken)
        if (result.success && progress != null) {
            // Apply pulled progress
            progress.statuses.forEach { (id, status) ->
                store.setStatus(id, status)
            }
        }
        return result
    }
    
    suspend fun syncBreakdowns(): WebAppSync.SyncResult {
        val admin = adminSettingsFlow().first()
        val serverUrl = admin.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }
        val (result, breakdowns) = WebAppSync.getBreakdowns(serverUrl)
        if (result.success && breakdowns != null) {
            // Merge with local breakdowns (server wins for conflicts)
            breakdowns.forEach { (_, breakdown) ->
                store.saveBreakdown(breakdown)
            }
        }
        return result
    }
    
    // Auto-fill breakdown
    suspend fun autoFillBreakdown(cardId: String, term: String, useAI: Boolean = false): TermBreakdown {
        val admin = adminSettingsFlow().first()
        return if (useAI && admin.chatGptEnabled && admin.chatGptApiKey.isNotBlank()) {
            ChatGptHelper.createAIBreakdown(admin.chatGptApiKey, cardId, term)
        } else {
            ChatGptHelper.createBasicBreakdown(cardId, term)
        }
    }
    
    suspend fun getCounts(): StatusCounts {
        val progress = progressFlow().first()
        val allCards = allCardsFlow().first()
        var active = 0; var unsure = 0; var learned = 0; var deleted = 0
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

data class StatusCounts(val active: Int, val unsure: Int, val learned: Int, val deleted: Int) {
    val total: Int get() = active + unsure + learned + deleted
}
