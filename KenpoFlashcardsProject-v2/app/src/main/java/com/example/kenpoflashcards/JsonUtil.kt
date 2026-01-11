package com.example.kenpoflashcards

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object JsonUtil {

    fun parseCardsArray(jsonArrayString: String): List<FlashCard> {
        val arr = JSONArray(jsonArrayString)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val group = o.optString("group", "Ungrouped")
                val subgroup = if (o.isNull("subgroup")) null else o.optString("subgroup").takeIf { it.isNotBlank() }
                val term = o.optString("term")
                val idFromJson = o.optString("id", "").trim()
                val pron = if (o.isNull("pron")) null else o.optString("pron").takeIf { it.isNotBlank() }
                val meaning = o.optString("meaning")
                add(
                    FlashCard(
                        id = if (idFromJson.isNotBlank()) idFromJson else stableId(group, subgroup, term, meaning, pron),
                        group = group,
                        subgroup = subgroup,
                        term = term,
                        pron = pron,
                        meaning = meaning
                    )
                )
            }
        }
    }

    fun readAssetCards(assetJson: String): List<FlashCard> = parseCardsArray(assetJson)

    fun cardsToJsonArray(cards: List<FlashCard>): String {
        val arr = JSONArray()
        cards.forEach { c ->
            val o = JSONObject()
            o.put("group", c.group)
            o.put("subgroup", c.subgroup)
            o.put("term", c.term)
            o.put("pron", c.pron)
            o.put("meaning", c.meaning)
            arr.put(o)
        }
        return arr.toString()
    }

    fun stableId(group: String, subgroup: String?, term: String, meaning: String, pron: String?): String {
    // MUST match the web server algorithm:
    // id = sha1(f"{group}||{subgroup}||{term}||{meaning}||{pron}")[:16]
    val base = "$group||${subgroup ?: ""}||$term||$meaning||${pron ?: ""}"
    val md = MessageDigest.getInstance("SHA-1").digest(base.toByteArray())
    return md.take(8).joinToString("") { "%02x".format(it) }
}
}
