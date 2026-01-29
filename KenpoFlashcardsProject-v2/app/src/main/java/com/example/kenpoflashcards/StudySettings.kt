package com.example.kenpoflashcards

enum class SortMode(val label: String) {
    JSON_ORDER("Original order"),
    ALPHABETICAL("Alphabetical (A-Z)"),
    GROUP_ALPHA("Groups (alphabetical)"),
    GROUP_RANDOM("Groups (random order)"),
    RANDOM("Random")
}

enum class LearnedViewMode(val label: String) {
    LIST("List"),
    STUDY("Study")
}

// Custom Set has its own status tracking
enum class CustomCardStatus {
    ACTIVE,   // In custom set, not yet studied
    UNSURE,   // Marked unsure within custom
    LEARNED   // Marked learned within custom
}

/**
 * Custom Set specific settings
 */
data class CustomSetSettings(
    val randomOrder: Boolean = false,
    val reverseCards: Boolean = false,
    val showGroupLabel: Boolean = false,
    val showBreakdown: Boolean = true,
    val sortMode: SortMode = SortMode.JSON_ORDER,
    val showDefinitions: Boolean = true,
    val showActionButtons: Boolean = true,
    val reflectInMainDecks: Boolean = false,  // If true, status changes affect main decks too
)

/**
 * Study settings - v4.0.3 with Custom Set isolation
 */
data class StudySettings(
    // Group selection for study screens
    val selectedGroup: String? = null,
    val selectedSubgroup: String? = null,
    
    // Study group filter (for To Study, Unsure, Learned Study)
    val studyFilterGroup: String? = null,  // null = All Cards
    
    // Sort mode (used when not randomized)
    val sortMode: SortMode = SortMode.JSON_ORDER,
    val randomize: Boolean = true,
    
    // Display options
    val showGroup: Boolean = false,
    val showSubgroup: Boolean = false,
    val reverseCards: Boolean = false,
    
    // Per-tab randomization
    val randomizeUnlearned: Boolean = false,
    val randomizeUnsure: Boolean = false,
    val randomizeLearnedStudy: Boolean = false,
    val linkRandomizeTabs: Boolean = true,
    
    // Breakdown display
    val showBreakdownOnDefinition: Boolean = true,
    
    // List view options
    val showDefinitionsInAllList: Boolean = true,
    val showDefinitionsInLearnedList: Boolean = true,
    val showLearnedListGroupLabel: Boolean = true,
    val showUnlearnedUnsureButtonsInAllList: Boolean = true,
    val showRelearnUnsureButtonsInLearnedList: Boolean = true,
    
    // Learned screen view mode
    val learnedViewMode: LearnedViewMode = LearnedViewMode.LIST,
    
    // Voice settings
    val speechVoice: String? = null,
    val speechRate: Float = 1.0f,
    val speakPronunciationOnly: Boolean = false,  // Default OFF per screenshot
    val autoSpeakOnCardChange: Boolean = false,   // Auto-speak term when switching cards
    val speakDefinitionOnFlip: Boolean = false,   // Speak definition when card flips to definition side
    
    // Group filter for All screen
    val filterGroup: String? = null,
    
    // Custom Set settings (isolated)
    val customSetSettings: CustomSetSettings = CustomSetSettings(),
    
    // Show Custom Set button (star icon) in study screens
    val showCustomSetButton: Boolean = true,
)

/**
 * Admin settings - stored separately for security
 */
data class AdminSettings(
    // Web app sync
    val webAppUrl: String = "",
    val authToken: String = "",
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val lastSyncTime: Long = 0,
    val isAdmin: Boolean = false,
    
    // ChatGPT API for breakdown autofill
    val chatGptApiKey: String = "",
    val chatGptEnabled: Boolean = false,
    val chatGptModel: String = "gpt-4o",  // Default model
    
    // Gemini API for breakdown autofill
    val geminiApiKey: String = "",
    val geminiEnabled: Boolean = false,
    val geminiModel: String = "gemini-1.5-flash",  // Default model
    
    // Auto-sync settings
    val autoPullOnLogin: Boolean = true,
    val autoPushOnChange: Boolean = false,
    val pendingSync: Boolean = false,  // True if changes made while offline
    
    // Breakdown AI selection
    val breakdownAiChoice: BreakdownAiChoice = BreakdownAiChoice.AUTO_SELECT
)

/**
 * Available ChatGPT models
 */
object ChatGptModels {
    val models = listOf(
        "gpt-4o" to "GPT-4o (Default)",
        "gpt-4o-mini" to "GPT-4o Mini (Faster)",
        "gpt-4-turbo" to "GPT-4 Turbo",
        "gpt-3.5-turbo" to "GPT-3.5 Turbo (Cheapest)"
    )
}

/**
 * Available Gemini models
 */
object GeminiModels {
    val models = listOf(
        "gemini-1.5-flash" to "Gemini 1.5 Flash (Default)",
        "gemini-1.5-pro" to "Gemini 1.5 Pro",
        "gemini-1.0-pro" to "Gemini 1.0 Pro"
    )
}

/**
 * Breakdown AI service selection
 */
enum class BreakdownAiChoice(val label: String) {
    AUTO_SELECT("Auto Select (Best Result)"),
    CHATGPT("ChatGPT"),
    GEMINI("Gemini")
}

/**
 * Admin users list - loaded from server with fallback
 */
object AdminUsers {
    // Default fallback if server is unreachable
    private val defaultAdmins = setOf("sidscri")
    
    // Cached admin list from server
    private var cachedAdmins: Set<String>? = null
    
    fun isAdmin(username: String): Boolean {
        if (username.isBlank()) return false
        val admins = cachedAdmins ?: defaultAdmins
        return username.trim().lowercase() in admins
    }
    
    fun updateAdminList(admins: Set<String>) {
        if (admins.isNotEmpty()) {
            cachedAdmins = admins
        }
    }
    
    fun getAdminList(): Set<String> = cachedAdmins ?: defaultAdmins
}
