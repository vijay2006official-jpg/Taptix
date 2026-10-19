package com.example.taptix.util

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TextToSpeech manager for driver audio feedback with language fallback and audio focus handling.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var currentLanguageTag: String = "en"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setLanguage(currentLanguageTag)
            isInitialized = true
        } else {
            Log.e(TAG, "Initialization of TextToSpeech failed with status: $status")
        }
    }

    fun setLanguage(languageTag: String) {
        currentLanguageTag = languageTag
        val targetLocale = when (languageTag.lowercase()) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "ta" -> Locale.forLanguageTag("ta-IN")
            else -> Locale.US
        }

        try {
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language $languageTag not fully supported, falling back to US English")
                tts?.setLanguage(Locale.US)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting TTS language: ${e.message}")
            tts?.setLanguage(Locale.US)
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "taptix_tts_id")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
