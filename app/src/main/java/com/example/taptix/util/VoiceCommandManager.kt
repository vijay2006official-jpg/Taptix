package com.example.taptix.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

enum class VoiceCommand {
    ACCEPT,
    DECLINE,
    START,
    STOP
}

/**
 * Speech Recognition Manager for hands-free driver voice command acceptance.
 */
class VoiceCommandManager(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningState = false

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(createListener())
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListeningState = true
            Log.d(TAG, "Speech recognizer started listening...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
        }
    }

    fun stopListening() {
        isListeningState = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    fun shutdown() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isListening(): Boolean = isListeningState

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for voice command...")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListeningState = false
        }

        override fun onError(error: Int) {
            isListeningState = false
            Log.w(TAG, "Speech recognition error code: $error")
        }

        override fun onResults(results: Bundle?) {
            isListeningState = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            Log.d(TAG, "Voice recognition results: $matches")
            parseVoiceMatches(matches)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Deliberately ignore partial results to avoid premature or accidental triggers
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private var lastVoiceCommandTime = 0L

    private fun parseVoiceMatches(matches: List<String>) {
        val now = System.currentTimeMillis()
        if (now - lastVoiceCommandTime < 1200L) return // Debounce duplicate recognitions

        val acceptKeywords = listOf("ACCEPT", "YES", "TAKE", "CONFIRM", "OK", "GET IT")
        val declineKeywords = listOf("DECLINE", "SKIP", "NO", "PASS", "REJECT")
        val startKeywords = listOf("START", "GO", "PLAY", "ENABLE")
        val stopKeywords = listOf("STOP", "PAUSE", "CANCEL", "DISABLE")

        for (text in matches) {
            val upper = text.uppercase(Locale.ROOT)
            val words = upper.split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }

            val isAccept = acceptKeywords.any { kw -> if (kw.contains(" ")) upper.contains(kw) else words.contains(kw) }
            val isDecline = declineKeywords.any { kw -> words.contains(kw) }
            val isStart = startKeywords.any { kw -> words.contains(kw) }
            val isStop = stopKeywords.any { kw -> words.contains(kw) }

            when {
                isAccept -> {
                    lastVoiceCommandTime = now
                    onCommandDetected(VoiceCommand.ACCEPT)
                    return
                }
                isDecline -> {
                    lastVoiceCommandTime = now
                    onCommandDetected(VoiceCommand.DECLINE)
                    return
                }
                isStart -> {
                    lastVoiceCommandTime = now
                    onCommandDetected(VoiceCommand.START)
                    return
                }
                isStop -> {
                    lastVoiceCommandTime = now
                    onCommandDetected(VoiceCommand.STOP)
                    return
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoiceCommandManager"
    }
}
