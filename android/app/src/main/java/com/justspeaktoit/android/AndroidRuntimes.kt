package com.justspeaktoit.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

interface LiveTranscriptionSession {
    fun stop()
}

class AndroidSpeechTranscriber(private val context: Context) {
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ): LiveTranscriptionSession? {
        if (!canStart()) return null

        val mainHandler = Handler(Looper.getMainLooper())
        var recognizer: SpeechRecognizer? = null
        val stopped = AtomicBoolean(false)

        mainHandler.post {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults?.bestRecognitionText()?.takeIf { it.isNotBlank() }?.let(onPartial)
                    }

                    override fun onResults(results: Bundle?) {
                        results?.bestRecognitionText()?.takeIf { it.isNotBlank() }?.let(onFinal)
                    }

                    override fun onError(error: Int) {
                        if (!stopped.get()) onError("Android Speech error $error")
                    }
                })
                startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                )
            }
        }

        return object : LiveTranscriptionSession {
            override fun stop() {
                stopped.set(true)
                mainHandler.post {
                    recognizer?.stopListening()
                    recognizer?.destroy()
                    recognizer = null
                }
            }
        }
    }

    private fun canStart(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun Bundle.bestRecognitionText(): String? {
        return getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }
}

class OpenRouterPostProcessor(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun process(text: String, prompt: String, model: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = prompt.ifBlank {
            "Clean up the transcript without adding facts. Preserve meaning, fix punctuation, and return only the final transcript."
        }
        val payload = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", text))
            )
            .put("stream", false)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("OpenRouter returned HTTP ${response.code}")
            response.body?.string()?.let(::extractContent).orEmpty()
        }
    }

    private fun extractContent(body: String): String {
        return JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

interface GatewaySession {
    fun close()
}

class OpenClawGatewayClient(private val client: OkHttpClient = OkHttpClient()) {
    fun send(
        gatewayUrl: String,
        token: String,
        conversation: Conversation,
        message: String,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ): GatewaySession {
        val payload = OpenClawProtocol.chatPayload(conversation.sessionKey, message)
        val request = Request.Builder()
            .url(OpenClawProtocol.normaliseGatewayUrl(gatewayUrl))
            .header("Authorization", "Bearer $token")
            .build()

        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(payload.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val content = OpenClawProtocol.extractAssistantContent(text)
                    if (content.isNotBlank()) onChunk(content)
                    if (OpenClawProtocol.isCompletionMessage(text)) {
                        onComplete()
                        webSocket.close(1000, "complete")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onError(t.message ?: "OpenClaw gateway failed")
                }
            }
        )
        return object : GatewaySession {
            override fun close() {
                socket.close(1000, "closed")
            }
        }
    }
}

class AndroidSpeechSpeaker(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
    }

    fun speak(text: String, speed: Float) {
        if (!ready || text.isBlank()) return
        tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "openclaw-${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }
}
