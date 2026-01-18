package com.example.kenpoflashcards

/**
 * Card status enum - now includes Unsure state from web app
 */
enum class CardStatus {
    ACTIVE,   // Not yet studied / needs review
    UNSURE,   // Studied but not confident
    LEARNED,  // Got it - confident
    DELETED   // Hidden from all views
}


// Progress entry with last-modified timestamp (unix seconds) used for Web App Sync merges
data class ProgressEntry(
    val status: CardStatus,
    val updatedAt: Long
)
/**
 * Study Deck - a collection of flashcards that can be studied
 * Default deck is "kenpo" which loads from kenpo_words.json
 */
data class StudyDeck(
    val id: String,              // Unique deck identifier
    val name: String,            // Display name (e.g., "Kenpo Vocabulary")
    val description: String,     // Brief description
    val isDefault: Boolean,      // If true, loads on app start
    val isBuiltIn: Boolean,      // If true, cannot be deleted (e.g., kenpo deck)
    val sourceFile: String?,     // For built-in decks: asset filename (e.g., "kenpo_words.json")
    val cardCount: Int,          // Number of cards in deck
    val createdAt: Long,         // Unix timestamp
    val updatedAt: Long          // Unix timestamp
) {
    companion object {
        // Default Kenpo deck - built-in and cannot be deleted
        val KENPO_DEFAULT = StudyDeck(
            id = "kenpo",
            name = "Kenpo Vocabulary",
            description = "Korean martial arts terminology for Kenpo students",
            isDefault = true,
            isBuiltIn = true,
            sourceFile = "kenpo_words.json",
            cardCount = 88,
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}

/**
 * Deck settings - which deck is active, etc.
 */
data class DeckSettings(
    val activeDeckId: String = "kenpo",    // Currently selected deck
    val availableDecks: List<String> = listOf("kenpo")  // List of deck IDs
)

/**
 * Flash card data model
 */
data class FlashCard(
    val id: String,
    val group: String,
    val subgroup: String?,
    val term: String,
    val pron: String?,
    val meaning: String,
    val deckId: String = "kenpo"  // Which deck this card belongs to
)

/**
 * A single part of a term breakdown (e.g., "Tae" = "Foot")
 */
data class BreakdownPart(
    val part: String,
    val meaning: String
)

/**
 * Term breakdown - breaks compound terms into parts with meanings
 * Ported from web app's breakdown feature
 */
data class TermBreakdown(
    val id: String,           // Same as card id
    val term: String,
    val parts: List<BreakdownPart>,
    val literal: String,      // e.g., "The Way of the Foot and Fist"
    val notes: String,        // Optional user notes
    val updatedAt: Long,      // Unix timestamp
    val updatedBy: String?    // Username if applicable
) {
    /**
     * Check if this breakdown has any meaningful content
     */
    fun hasContent(): Boolean {
        val anyParts = parts.any { it.part.isNotBlank() || it.meaning.isNotBlank() }
        return anyParts || literal.isNotBlank()
    }
    
    /**
     * Format parts for display: "Tae = Foot • Kwon = Hand • Do = Way"
     */
    fun formatParts(): String {
        return parts
            .filter { it.part.isNotBlank() || it.meaning.isNotBlank() }
            .joinToString(" • ") { p ->
                when {
                    p.part.isNotBlank() && p.meaning.isNotBlank() -> "${p.part} = ${p.meaning}"
                    p.part.isNotBlank() -> p.part
                    else -> p.meaning
                }
            }
    }
}

/**
 * Progress state - now tracks full status instead of just learned/deleted sets
 */
data class ProgressState(
    val statuses: Map<String, CardStatus>
) {
    val learnedIds: Set<String>
        get() = statuses.filterValues { it == CardStatus.LEARNED }.keys
    
    val unsureIds: Set<String>
        get() = statuses.filterValues { it == CardStatus.UNSURE }.keys
    
    val deletedIds: Set<String>
        get() = statuses.filterValues { it == CardStatus.DELETED }.keys
    
    val activeIds: Set<String>
        get() = statuses.filterValues { it == CardStatus.ACTIVE }.keys
    
    fun getStatus(id: String): CardStatus = statuses[id] ?: CardStatus.ACTIVE
    
    companion object {
        val EMPTY = ProgressState(emptyMap())
    }
}
