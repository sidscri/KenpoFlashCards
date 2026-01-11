package com.example.kenpoflashcards

import java.io.BufferedReader

/**
 * Supports CSV columns (header optional):
 *  - group,term,pron,meaning
 *  - group,subgroup,term,pron,meaning
 */
object CsvImport {

    fun parseCsv(reader: BufferedReader): List<FlashCard> {
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val startIndex = if (looksLikeHeader(lines.first())) 1 else 0

        return lines.drop(startIndex).mapNotNull { line ->
            val cols = splitCsvLine(line)
            if (cols.size < 4) return@mapNotNull null

            val hasSubgroup = cols.size >= 5

            val group = cols.getOrNull(0)?.ifBlank { "Ungrouped" } ?: "Ungrouped"
            val subgroup = if (hasSubgroup) cols.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() } else null

            val term = if (hasSubgroup) cols.getOrNull(2)?.trim().orEmpty() else cols.getOrNull(1)?.trim().orEmpty()
            val pron = if (hasSubgroup) cols.getOrNull(3)?.trim().takeIf { !it.isNullOrBlank() } else cols.getOrNull(2)?.trim().takeIf { !it.isNullOrBlank() }
            val meaning = if (hasSubgroup) cols.getOrNull(4)?.trim().orEmpty() else cols.getOrNull(3)?.trim().orEmpty()

            if (term.isBlank() || meaning.isBlank()) return@mapNotNull null

            FlashCard(
                id = JsonUtil.stableId(group, subgroup, term, meaning, pron),
                group = group,
                subgroup = subgroup,
                term = term,
                pron = pron,
                meaning = meaning
            )
        }
    }

    private fun looksLikeHeader(first: String): Boolean {
        val f = first.lowercase()
        return f.contains("group") && f.contains("term") && f.contains("meaning")
    }

    // Simple CSV splitter with quotes support
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) sb.append(c) else {
                        out.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out.map { it.trim() }
    }
}
