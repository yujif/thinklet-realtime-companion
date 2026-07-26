package com.yujif.thinklet.realtimecompanion.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class JapaneseTtsManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TextToSpeech initialization failed: $status")
                ready = false
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val result = engine.setLanguage(Locale.JAPANESE)
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)
            Log.i(TAG, "TextToSpeech ready=$ready languageResult=$result")
        }
    }

    fun speak(text: String, utteranceId: String = "realtime-companion-status") {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "JapaneseTtsManager"
    }
}
