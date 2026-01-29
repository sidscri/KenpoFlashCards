package com.example.kenpoflashcards

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI Generation Helper for flashcard creation
 * Supports both ChatGPT and Gemini APIs
 */
object AiGenerationHelper {
    
    private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    
    /**
     * Generate multiple definition options for a term
     */
    suspend fun generateDefinitions(apiKey: String, term: String, model: String, useChatGpt: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        
        val prompt = """
            Provide 3 different definitions for the term "$term".
            Each definition should be clear, concise, and educational.
            
            Respond in JSON format only:
            {"definitions": ["definition 1", "definition 2", "definition 3"]}
            
            Only respond with the JSON, no other text.
        """.trimIndent()
        
        try {
            val response = if (useChatGpt) {
                callChatGpt(apiKey, prompt, model)
            } else {
                callGemini(apiKey, prompt, model)
            }
            
            val json = JSONObject(response.trim())
            val defsArray = json.optJSONArray("definitions") ?: return@withContext emptyList()
            val defs = mutableListOf<String>()
            for (i in 0 until defsArray.length()) {
                defs.add(defsArray.optString(i, ""))
            }
            defs.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Generate pronunciation for a term
     */
    suspend fun generatePronunciation(apiKey: String, term: String, model: String, useChatGpt: Boolean): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ""
        
        val prompt = """
            Provide a phonetic pronunciation guide for the term "$term".
            Use simple syllables separated by hyphens that an English speaker can read.
            Example: "Taekwondo" = "tay-kwon-doh"
            
            Respond in JSON format only:
            {"pronunciation": "the-pronunciation"}
            
            Only respond with the JSON, no other text.
        """.trimIndent()
        
        try {
            val response = if (useChatGpt) {
                callChatGpt(apiKey, prompt, model)
            } else {
                callGemini(apiKey, prompt, model)
            }
            
            val json = JSONObject(response.trim())
            json.optString("pronunciation", "")
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Generate group suggestions for a term, considering existing groups
     */
    suspend fun generateGroups(apiKey: String, term: String, existingGroups: List<String>, maxGroups: Int, model: String, useChatGpt: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        
        val existingGroupsText = if (existingGroups.isNotEmpty()) {
            "Existing groups in the deck: ${existingGroups.joinToString(", ")}\n" +
            "Prefer assigning to an existing group if appropriate."
        } else {
            "No existing groups yet."
        }
        
        val prompt = """
            Suggest up to 3 category/group names for the term "$term".
            $existingGroupsText
            
            The first suggestion should be the best fit.
            Maximum $maxGroups total groups are allowed in this deck.
            
            Respond in JSON format only:
            {"groups": ["best group", "alternative 1", "alternative 2"]}
            
            Only respond with the JSON, no other text.
        """.trimIndent()
        
        try {
            val response = if (useChatGpt) {
                callChatGpt(apiKey, prompt, model)
            } else {
                callGemini(apiKey, prompt, model)
            }
            
            val json = JSONObject(response.trim())
            val groupsArray = json.optJSONArray("groups") ?: return@withContext emptyList()
            val groups = mutableListOf<String>()
            for (i in 0 until groupsArray.length()) {
                groups.add(groupsArray.optString(i, ""))
            }
            groups.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Search and generate flashcard terms for a subject
     */
    suspend fun searchAndGenerateTerms(apiKey: String, keywords: String, maxCards: Int, model: String, useChatGpt: Boolean): List<AiGeneratedTerm> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        
        val prompt = """
            Generate up to $maxCards flashcard terms about: $keywords
            
            For each term, provide:
            - term: the vocabulary word or concept
            - definition: clear, educational definition
            - pronunciation: phonetic guide (if applicable)
            - group: category for organization
            
            Respond in JSON format only:
            {
                "cards": [
                    {"term": "Term 1", "definition": "Definition", "pronunciation": "pro-nun-ci-a-tion", "group": "Category"},
                    {"term": "Term 2", "definition": "Definition", "pronunciation": "", "group": "Category"}
                ]
            }
            
            Only respond with the JSON, no other text.
        """.trimIndent()
        
        try {
            val response = if (useChatGpt) {
                callChatGpt(apiKey, prompt, model)
            } else {
                callGemini(apiKey, prompt, model)
            }
            
            val json = JSONObject(response.trim())
            val cardsArray = json.optJSONArray("cards") ?: return@withContext emptyList()
            val cards = mutableListOf<AiGeneratedTerm>()
            for (i in 0 until cardsArray.length()) {
                val card = cardsArray.optJSONObject(i) ?: continue
                cards.add(AiGeneratedTerm(
                    term = card.optString("term", ""),
                    definition = card.optString("definition", ""),
                    pronunciation = card.optString("pronunciation", ""),
                    group = card.optString("group", "General")
                ))
            }
            cards.filter { it.term.isNotBlank() && it.definition.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun callChatGpt(apiKey: String, prompt: String, model: String): String = withContext(Dispatchers.IO) {
        val url = URL(OPENAI_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a helpful assistant that creates educational flashcard content. Always respond with valid JSON only, no markdown or other formatting.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }
        
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 2000)
        }
        
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        
        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "") ?: ""
        } else {
            throw Exception("API error: ${conn.responseCode}")
        }
    }
    
    private suspend fun callGemini(apiKey: String, prompt: String, model: String): String = withContext(Dispatchers.IO) {
        val url = URL("$GEMINI_API_URL/$model:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are a helpful assistant that creates educational flashcard content. Always respond with valid JSON only, no markdown or other formatting.\n\n$prompt")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2000)
            })
        }
        
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        
        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "") ?: ""
        } else {
            throw Exception("API error: ${conn.responseCode}")
        }
    }
}
