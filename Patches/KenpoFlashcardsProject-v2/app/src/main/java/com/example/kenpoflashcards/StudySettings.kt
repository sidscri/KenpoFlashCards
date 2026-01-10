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
    
    // ChatGPT API for breakdown autofill
    val chatGptApiKey: String = "",
    val chatGptEnabled: Boolean = false,
)
