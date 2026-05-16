package com.justspeaktoit.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
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

class RemoteStreamingTranscriber(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun start(
        model: String,
        apiKey: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ): LiveTranscriptionSession? {
        if (apiKey.isBlank() || !canRecord()) return null

        val provider = RemoteProvider.fromModel(model) ?: return null
        val stopped = AtomicBoolean(false)
        var streamer: PcmAudioStreamer? = null
        var socket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                provider.onOpen(webSocket)
                streamer = PcmAudioStreamer(context) { audio ->
                    if (!stopped.get()) provider.sendAudio(webSocket, audio)
                }.also { it.start() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                provider.parseTranscript(text)?.let { result ->
                    if (result.isFinal) onFinal(result.text) else onPartial(result.text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!stopped.get()) onError(t.message ?: "${provider.displayName} transcription failed")
            }
        }

        val request = provider.request(apiKey)
        socket = client.newWebSocket(request, listener)

        return object : LiveTranscriptionSession {
            override fun stop() {
                stopped.set(true)
                streamer?.stop()
                provider.onStop(socket)
                socket?.close(1000, "recording stopped")
                socket = null
            }
        }
    }

    private fun canRecord(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}

private data class TranscriptEvent(val text: String, val isFinal: Boolean)

private enum class RemoteProvider(val id: String, val displayName: String) {
    Deepgram("deepgram", "Deepgram"),
    ElevenLabs("elevenlabs", "ElevenLabs"),
    OpenAI("openai", "OpenAI");

    fun request(apiKey: String): Request {
        return when (this) {
            Deepgram -> Request.Builder()
                .url("wss://api.deepgram.com/v1/listen?model=nova-3&punctuate=true&smart_format=true&numerals=true&interim_results=true&encoding=linear16&sample_rate=16000&channels=1&endpointing=300&vad_events=true")
                .header("Authorization", "Token $apiKey")
                .build()
            ElevenLabs -> Request.Builder()
                .url("wss://api.elevenlabs.io/v1/speech-to-text/stream?model_id=scribe_v1")
                .header("xi-api-key", apiKey)
                .build()
            OpenAI -> Request.Builder()
                .url("wss://api.openai.com/v1/realtime?intent=transcription")
                .header("Authorization", "Bearer $apiKey")
                .build()
        }
    }

    fun onOpen(webSocket: WebSocket) {
        if (this != OpenAI) return
        webSocket.send(
            JSONObject()
                .put("type", "session.update")
                .put(
                    "session",
                    JSONObject()
                        .put("type", "transcription")
                        .put(
                            "audio",
                            JSONObject()
                                .put(
                                    "input",
                                    JSONObject()
                                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", 16000))
                                        .put("transcription", JSONObject().put("model", "gpt-realtime-whisper"))
                                        .put("noise_reduction", JSONObject().put("type", "near_field"))
                                        .put("turn_detection", JSONObject.NULL)
                                )
                        )
                )
                .toString()
        )
    }

    fun sendAudio(webSocket: WebSocket, audio: ByteArray) {
        when (this) {
            Deepgram, ElevenLabs -> webSocket.send(okio.ByteString.of(*audio))
            OpenAI -> webSocket.send(
                JSONObject()
                    .put("type", "input_audio_buffer.append")
                    .put("audio", Base64.encodeToString(audio, Base64.NO_WRAP))
                    .toString()
            )
        }
    }

    fun onStop(webSocket: WebSocket?) {
        if (this == OpenAI) {
            webSocket?.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
        }
    }

    fun parseTranscript(message: String): TranscriptEvent? {
        val obj = runCatching { JSONObject(message) }.getOrNull() ?: return null
        return when (this) {
            Deepgram -> {
                val transcript = obj.optJSONObject("channel")
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optString("transcript")
                    .orEmpty()
                transcript.takeIf { it.isNotBlank() }?.let { TranscriptEvent(it, obj.optBoolean("is_final")) }
            }
            ElevenLabs -> {
                val transcript = obj.optString("transcript")
                transcript.takeIf { it.isNotBlank() }?.let {
                    TranscriptEvent(it, obj.optString("speech_event_type") == "FINAL_TRANSCRIPT")
                }
            }
            OpenAI -> {
                when (obj.optString("type")) {
                    "conversation.item.input_audio_transcription.delta" -> obj.optString("delta")
                        .takeIf { it.isNotBlank() }
                        ?.let { TranscriptEvent(it, false) }
                    "conversation.item.input_audio_transcription.completed" -> obj.optString("transcript")
                        .takeIf { it.isNotBlank() }
                        ?.let { TranscriptEvent(it, true) }
                    else -> null
                }
            }
        }
    }

    companion object {
        fun fromModel(model: String): RemoteProvider? = values().firstOrNull { model.startsWith(it.id) }
    }
}

private class PcmAudioStreamer(
    private val context: Context,
    private val onAudio: (ByteArray) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running.getAndSet(true)) return
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING).coerceAtLeast(3200)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            minBuffer * 2
        )
        audioRecord?.startRecording()
        thread = Thread({
            val buffer = ByteArray(minBuffer)
            while (running.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) onAudio(buffer.copyOf(read))
            }
        }, "AndroidRemoteTranscriptionAudio").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(500)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        thread = null
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
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
