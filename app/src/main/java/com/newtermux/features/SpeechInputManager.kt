package com.newtermux.features

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Manages speech-to-text input for NewTermux terminal sessions.
 * Integrates with Android's SpeechRecognizer API for offline/online recognition.
 */
class SpeechInputManager(private val mContext: Context) {

    interface SpeechCallback {
        fun onResult(text: String)
        fun onError(error: String)
        fun onListeningStarted()
        fun onListeningStopped()
    }

    private var mSpeechRecognizer: SpeechRecognizer? = null
    private var mCallback: SpeechCallback? = null
    var isListening: Boolean = false
        private set

    fun setCallback(callback: SpeechCallback) {
        mCallback = callback
    }

    fun startListening() {
        if (isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
            mCallback?.onError("Speech recognition not available on this device.")
            return
        }

        mSpeechRecognizer?.destroy()
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext)
        mSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                mCallback?.onListeningStarted()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                mCallback?.onListeningStopped()
            }
            override fun onError(error: Int) {
                isListening = false
                val msg = speechErrorToString(error)
                Log.e(TAG, "Speech error: $msg")
                mCallback?.onError(msg)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.d(TAG, "Speech result: $text")
                    mCallback?.onResult(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...")
        }

        mSpeechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        if (mSpeechRecognizer != null && isListening) {
            mSpeechRecognizer?.stopListening()
            isListening = false
        }
    }

    fun destroy() {
        mSpeechRecognizer?.destroy()
        mSpeechRecognizer = null
        isListening = false
    }

    private fun speechErrorToString(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error ($error)"
        }
    }

    companion object {
        private const val TAG = "SpeechInputManager"

        @JvmStatic
        fun isAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }
    }
}
