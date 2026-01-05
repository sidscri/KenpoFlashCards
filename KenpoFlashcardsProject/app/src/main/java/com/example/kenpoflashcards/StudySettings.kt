package com.example.kenpoflashcards

enum class SortMode(val label: String) {
    RANDOM("Random"),
    GROUP_RANDOM("Main group (random order)"),
    GROUP_SUBGROUP("Main group → Subgroup → Term")
}

data class StudySettings(
    val selectedGroup: String? = null,     // used by SINGLE_GROUP mode
    val selectedSubgroup: String? = null,  // optional extra filter when single group selected
    val sortMode: SortMode = SortMode.RANDOM,
    val randomize: Boolean = true,         // shuffle final list
    val showGroup: Boolean = true,         // show group chip/label on card
    val showSubgroup: Boolean = true,      // show subgroup line on card
    val reverseCards: Boolean = false      // front=meaning, back=term
)
