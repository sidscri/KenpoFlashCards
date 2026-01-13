package com.example.kenpoflashcards

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API Helper for auto-filling breakdown data
 * 
 * Uses Google's Gemini API to:
 * 1. Break compound terms into parts (TaeKwonDo -> Tae, Kwon, Do)
 * 2. Translate each part's meaning
 * 3. Generate literal translation
 */
object GeminiHelper {
    
    private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    
    data class BreakdownResult(
        val success: Boolean,
        val parts: List<BreakdownPart> = emptyList(),
        val literal: String = "",
        val error: String = ""
    )
    
    /**
     * Use Gemini to get meanings for term parts
     * Requires valid API key
     */
    suspend fun getBreakdownWithAI(apiKey: String, term: String, model: String = "gemini-1.5-flash", language: String = "Korean"): BreakdownResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext BreakdownResult(success = false, error = "API key not set")
        }
        
        try {
            val urlString = "$GEMINI_API_BASE/$model:generateContent?key=$apiKey"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
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
                
                Only respond with the JSON, no other text or markdown.
            """.trimIndent()
            
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 500)
                })
            }
            
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val candidates = json.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    ?: return@withContext BreakdownResult(success = false, error = "Invalid response")
                
                // Clean up response - remove markdown code blocks if present
                val cleanContent = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                
                // Parse the JSON response
                val resultJson = JSONObject(cleanContent)
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
     * Create a full breakdown using Gemini API
     */
    suspend fun createAIBreakdown(apiKey: String, cardId: String, term: String, model: String = "gemini-1.5-flash"): TermBreakdown {
        val result = getBreakdownWithAI(apiKey, term, model)
        return if (result.success) {
            TermBreakdown(
                id = cardId,
                term = term,
                parts = result.parts,
                literal = result.literal,
                notes = "Auto-generated by AI",
                updatedAt = System.currentTimeMillis() / 1000,
                updatedBy = "Gemini"
            )
        } else {
            // Fall back to basic breakdown if API fails
            ChatGptHelper.createBasicBreakdown(cardId, term)
        }
    }
}
