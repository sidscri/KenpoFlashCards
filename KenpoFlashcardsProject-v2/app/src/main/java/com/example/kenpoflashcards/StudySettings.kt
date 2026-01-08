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

/**
 * Study settings - v4.0 with group filters and admin features
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
    val showGroup: Boolean = true,
    val showSubgroup: Boolean = true,
    val reverseCards: Boolean = false,
    
    // Per-tab randomization
    val randomizeUnlearned: Boolean = true,
    val randomizeUnsure: Boolean = true,
    val randomizeLearnedStudy: Boolean = true,
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
    val speakPronunciationOnly: Boolean = true,  // If pron exists, speak only pron
    
    // Group filter for All screen
    val filterGroup: String? = null,
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
    
    // ChatGPT API for breakdown autofill
    val chatGptApiKey: String = "",
    val chatGptEnabled: Boolean = false,
)
