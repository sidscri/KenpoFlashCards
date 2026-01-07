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
                val pron = if (o.isNull("pron")) null else o.optString("pron").takeIf { it.isNotBlank() }
                val meaning = o.optString("meaning")
                add(
                    FlashCard(
                        id = stableId(group, subgroup, term, pron, meaning),
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

    fun stableId(group: String, subgroup: String?, term: String, pron: String?, meaning: String): String {
        val base = "$group|${subgroup ?: ""}|$term|${pron ?: ""}|$meaning"
        val md = MessageDigest.getInstance("SHA-256").digest(base.toByteArray())
        return md.take(8).joinToString("") { "%02x".format(it) }
    }
}
