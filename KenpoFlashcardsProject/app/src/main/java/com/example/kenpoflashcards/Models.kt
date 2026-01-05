package com.example.kenpoflashcards

data class FlashCard(
    val id: String,
    val group: String,
    val subgroup: String?,
    val term: String,
    val pron: String?,
    val meaning: String,
)
