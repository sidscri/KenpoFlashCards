package com.example.kenpoflashcards

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Enhanced TTS helper with voice selection and rate control
 * Ported from web app's voice customization features
 */
class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var availableVoices: List<Voice> = emptyList()
    
    private var currentRate: Float = 1.0f
    private var currentVoiceName: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            if (ready) {
                tts?.language = Locale.US
                loadAvailableVoices()
            }
        }
    }
    
    private fun loadAvailableVoices() {
        availableVoices = tts?.voices
            ?.filter { voice ->
                // Filter to English voices that aren't network-only
                voice.locale.language == "en" && 
                !voice.isNetworkConnectionRequired
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Get list of available voices for selection UI
     */
    fun getAvailableVoices(): List<VoiceInfo> {
        return availableVoices.map { voice ->
            VoiceInfo(
                name = voice.name,
                displayName = formatVoiceName(voice),
                locale = voice.locale.displayName,
                quality = voice.quality
            )
        }
    }
    
    private fun formatVoiceName(voice: Voice): String {
        // Create a user-friendly display name
        val locale = voice.locale.displayName
        val name = voice.name
            .replace("en-", "")
            .replace("-", " ")
            .replaceFirstChar { it.uppercase() }
        
        return "$name ($locale)"
    }

    /**
     * Set the voice by name
     */
    fun setVoice(voiceName: String?) {
        currentVoiceName = voiceName
        if (voiceName == null) {
            // Reset to default
            tts?.language = Locale.US
            return
        }
        
        val voice = availableVoices.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    /**
     * Set speech rate (0.5 to 2.0)
     */
    fun setRate(rate: Float) {
        currentRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentRate)
    }

    /**
     * Speak text with current settings
     */
    fun speak(text: String) {
        if (!ready) return
        tts?.setSpeechRate(currentRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kenpo_tts")
    }
    
    /**
     * Speak a test phrase
     */
    fun speakTest() {
        speak("This is a test of the voice settings.")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/**
 * Voice information for UI display
 */
data class VoiceInfo(
    val name: String,        // Internal name for selection
    val displayName: String, // User-friendly display name
    val locale: String,      // Locale display name
    val quality: Int         // Voice quality level
)
