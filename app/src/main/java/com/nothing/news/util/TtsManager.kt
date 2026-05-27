package com.nothing.news.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupListener()
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isPlaying.value = false
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isPlaying.value = false
            }
        })
    }

    fun setLanguage(languagePref: String) {
        if (!isInitialized) return
        val locale = when (languagePref) {
            "Italiano" -> Locale.ITALIAN
            "Inglese" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        tts?.language = locale
        
        try {
            val voices = tts?.voices
            if (voices != null) {
                // Try to find a high-quality network voice or a high-quality local voice
                val bestVoice = voices.firstOrNull { 
                    it.locale.language == locale.language && it.isNetworkConnectionRequired 
                } ?: voices.firstOrNull { 
                    it.locale.language == locale.language && it.quality > android.speech.tts.Voice.QUALITY_NORMAL 
                }
                
                if (bestVoice != null) {
                    tts?.voice = bestVoice
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Slightly slower reading pace often sounds more natural and less robotic
        tts?.setSpeechRate(0.95f)
    }

    fun speak(text: String) {
        if (!isInitialized) return
        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
