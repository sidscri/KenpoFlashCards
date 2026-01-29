package com.example.kenpoflashcards

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ChatGPT API Helper for auto-filling breakdown data
 * 
 * Uses OpenAI API to:
 * 1. Break compound terms into parts (TaeKwonDo -> Tae, Kwon, Do)
 * 2. Translate each part's meaning
 * 3. Generate literal translation
 */
object ChatGptHelper {
    
    private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    
    data class BreakdownResult(
        val success: Boolean,
        val parts: List<BreakdownPart> = emptyList(),
        val literal: String = "",
        val error: String = ""
    )
    
    /**
     * Auto-split a term into parts based on capital letters or common patterns
     * This is the LOCAL version that doesn't need API
     */
    fun autoSplitTerm(term: String): List<String> {
        // Split on capital letters (TaeKwonDo -> Tae, Kwon, Do)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        
        for (i in term.indices) {
            val c = term[i]
            if (i > 0 && c.isUpperCase() && current.isNotEmpty()) {
                parts.add(current.toString())
                current.clear()
            }
            current.append(c)
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        
        // If no splits found, try splitting on common patterns
        if (parts.size <= 1) {
            // Try splitting on hyphens
            if (term.contains("-")) {
                return term.split("-").filter { it.isNotBlank() }
            }
            // Try splitting on spaces
            if (term.contains(" ")) {
                return term.split(" ").filter { it.isNotBlank() }
            }
        }
        
        return parts.ifEmpty { listOf(term) }
    }
    
    /**
     * Use ChatGPT to get meanings for term parts
     * Requires valid API key
     */
    suspend fun getBreakdownWithAI(apiKey: String, term: String, model: String = "gpt-4o", language: String = "Korean"): BreakdownResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext BreakdownResult(success = false, error = "API key not set")
        }
        
        try {
            val url = URL(OPENAI_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            
            val prompt = """
                Break down the $language martial arts term "$term" into its component parts.
                For each part, provide:
                1. The part/syllable
                2. Its meaning in English
                
                Also provide a literal English translation of the full term.
                
                Respond in JSON format only:
                {
                    "parts": [
                        {"part": "Tae", "meaning": "Foot/Kick"},
                        {"part": "Kwon", "meaning": "Fist/Hand"},
                        {"part": "Do", "meaning": "Way/Path"}
                    ],
                    "literal": "The Way of Foot and Fist"
                }
                
                Only respond with the JSON, no other text.
            """.trimIndent()
            
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a martial arts terminology expert. You break down terms into their component parts and provide accurate translations. Always respond with valid JSON only.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            
            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 500)
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val choices = json.optJSONArray("choices")
                val content = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?: return@withContext BreakdownResult(success = false, error = "Invalid response")
                
                // Parse the JSON response
                val resultJson = JSONObject(content.trim())
                val partsArray = resultJson.optJSONArray("parts") ?: JSONArray()
                val parts = mutableListOf<BreakdownPart>()
                for (i in 0 until partsArray.length()) {
                    val partObj = partsArray.optJSONObject(i) ?: continue
                    parts.add(BreakdownPart(
                        part = partObj.optString("part", ""),
                        meaning = partObj.optString("meaning", "")
                    ))
                }
                val literal = resultJson.optString("literal", "")
                
                BreakdownResult(success = true, parts = parts, literal = literal)
            } else {
                val error = try { 
                    conn.errorStream?.bufferedReader()?.readText() ?: "API error: $responseCode" 
                } catch (_: Exception) { 
                    "API error: $responseCode" 
                }
                BreakdownResult(success = false, error = error)
            }
        } catch (e: Exception) {
            BreakdownResult(success = false, error = e.message ?: "API call failed")
        }
    }
    
    /**
     * Create a basic breakdown from auto-split (no API needed)
     * Parts will have empty meanings that user can fill in
     */
    fun createBasicBreakdown(cardId: String, term: String): TermBreakdown {
        val splitParts = autoSplitTerm(term)
        val parts = splitParts.map { BreakdownPart(part = it, meaning = "") }
        return TermBreakdown(
            id = cardId,
            term = term,
            parts = parts,
            literal = "",
            notes = "",
            updatedAt = System.currentTimeMillis() / 1000,
            updatedBy = null
        )
    }
    
    /**
     * Create a full breakdown using ChatGPT API
     */
    suspend fun createAIBreakdown(apiKey: String, cardId: String, term: String, model: String = "gpt-4o"): TermBreakdown {
        val result = getBreakdownWithAI(apiKey, term, model)
        return if (result.success) {
            TermBreakdown(
                id = cardId,
                term = term,
                parts = result.parts,
                literal = result.literal,
                notes = "Auto-generated by AI",
                updatedAt = System.currentTimeMillis() / 1000,
                updatedBy = "ChatGPT"
            )
        } else {
            // Fall back to basic breakdown if API fails
            createBasicBreakdown(cardId, term)
        }
    }
}
