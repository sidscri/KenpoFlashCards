package com.example.kenpoflashcards

enum class SortMode(val label: String) {
    RANDOM("Random"),
    GROUP_RANDOM("Main group (random order)"),
    GROUP_SUBGROUP("Main group → Subgroup → Term")
}

/**
 * View mode for Learned screen - list view or study mode
 */
enum class LearnedViewMode(val label: String) {
    LIST("List"),
    STUDY("Study")
}

/**
 * Study settings - enhanced with web app features
 */
data class StudySettings(
    // Group selection (for single-group mode)
    val selectedGroup: String? = null,
    val selectedSubgroup: String? = null,
    
    // Sort and randomize
    val sortMode: SortMode = SortMode.RANDOM,
    val randomize: Boolean = true,
    
    // Display options
    val showGroup: Boolean = true,
    val showSubgroup: Boolean = true,
    val reverseCards: Boolean = false,
    
    // === NEW: Ported from web app ===
    
    // Per-tab randomization (when linked=false, each tab has its own setting)
    val randomizeUnlearned: Boolean = true,
    val randomizeUnsure: Boolean = true,
    val randomizeLearnedStudy: Boolean = true,
    val linkRandomizeTabs: Boolean = true,  // When true, changing one changes all
    
    // Breakdown display
    val showBreakdownOnDefinition: Boolean = false,
    
    // List view options
    val showDefinitionsInAllList: Boolean = true,
    val showDefinitionsInLearnedList: Boolean = true,
    val showLearnedListGroupLabel: Boolean = true,
    val showUnlearnedUnsureButtonsInAllList: Boolean = true,
    val showRelearnUnsureButtonsInLearnedList: Boolean = true,
    
    // Learned screen view mode
    val learnedViewMode: LearnedViewMode = LearnedViewMode.LIST,
    
    // Voice settings
    val speechVoice: String? = null,  // null = system default
    val speechRate: Float = 1.0f,     // 0.5 to 2.0
)
