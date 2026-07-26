package com.yujif.thinklet.realtimecompanion.openai

import com.yujif.thinklet.realtimecompanion.core.ApiLogRedactor
import com.yujif.thinklet.realtimecompanion.session.SessionRecorder
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun realtimeSessionUpdateJson(
    model: String,
    voice: String,
    useCaseContext: String = "",
    useCase: RealtimeCompanionUseCase = RealtimeCompanionUseCase.GenericRealtimeConversation,
): String {
    val instructions = buildString {
        append(useCase.instructions)
        if (useCase.hasCapability(RealtimeCompanionCapability.CookingMemory) && useCaseContext.isNotBlank()) {
            append("\n\n")
            append(useCaseContext)
            append("\n\n")
            append("この調理メモリーを優先して、同じ内容をユーザーに聞き返さないでください。ユーザーが訂正した場合は訂正を優先してください。")
        }
    }
    val toolsJson = RealtimeCompanionToolCatalog.toolsJson(useCase)

    return """
        {
          "type":"session.update",
          "session":{
            "type":"realtime",
            "model":${jsonStringLiteral(model)},
            "instructions":${jsonStringLiteral(instructions)},
            "tools":$toolsJson,
            "tool_choice":"auto",
            "audio":{
              "input":{
                "format":{
                  "type":"audio/pcm",
                  "rate":24000
                },
                "transcription":{
                  "model":"gpt-4o-mini-transcribe",
                  "language":"ja"
                },
                "turn_detection":{
                  "type":"server_vad",
                  "create_response":true,
                  "interrupt_response":true
                }
              },
              "output":{"voice":${jsonStringLiteral(voice)}}
            }
          }
        }
    """.trimIndent()
}

internal fun functionCallOutputJson(callId: String, output: String): String {
    return """
        {
          "type":"conversation.item.create",
          "item":{
            "type":"function_call_output",
            "call_id":${jsonStringLiteral(callId)},
            "output":${jsonStringLiteral(output)}
          }
        }
    """.trimIndent()
}

internal fun clientEventLogEntry(json: String, persistedEventType: String? = null): String {
    return if (persistedEventType == null) {
        """{"direction":"client","payload":${ApiLogRedactor.redact(json)}}"""
    } else {
        """{"direction":"client","type":"$persistedEventType"}"""
    }
}

internal fun jsonStringLiteral(value: String): String {
    val escaped = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
    return "\"$escaped\""
}

class RealtimeSession(
    private val apiKeyProvider: () -> String,
    private val recorderProvider: () -> SessionRecorder?,
    private val listener: Listener,
    private val model: String = "gpt-realtime-2",
    private val voice: String = "marin",
    private val useCaseContextProvider: () -> String = { "" },
    private val useCase: RealtimeCompanionUseCase = RealtimeCompanionUseCase.GenericRealtimeConversation,
    private val client: OkHttpClient = defaultClient(),
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onAssistantAudio(bytes: ByteArray)
        fun onError(message: String)
        fun onUsageEvent(json: String)
        fun onToolRequest(request: RealtimeCompanionToolRequest) = Unit
    }

    private val connected = AtomicBoolean(false)
    private var webSocket: WebSocket? = null

    // onClosed (server/network closed the socket) and disconnect() (caller-initiated) can each
    // reach listener.onDisconnected for the same session. Guarantee it fires exactly once so a
    // caller that reacts to onDisconnected by tearing down the session (see MainActivity) can't
    // be re-entered for a session that's already torn down.
    private val disconnectNotified = AtomicBoolean(false)

    private fun notifyDisconnectedOnce(reason: String) {
        if (disconnectNotified.compareAndSet(false, true)) {
            listener.onDisconnected(reason)
        }
    }

    fun connect() {
        if (connected.get()) return
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            listener.onError("OpenAI API key is not configured")
            return
        }

        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=$model")
            .header("Authorization", "Bearer $apiKey")
            .build()
        recorderProvider()?.appendEvent(
            """{"direction":"client","type":"websocket.connect","url":"${request.url}","Authorization":"Bearer [REDACTED]"}""",
        )
        webSocket = client.newWebSocket(request, SocketListener())
    }

    fun disconnect(reason: String = "client_disconnect") {
        val socket = webSocket
        webSocket = null
        connected.set(false)
        socket?.close(1000, reason)
        recorderProvider()?.appendEvent("""{"direction":"client","type":"websocket.disconnect","reason":"$reason"}""")
        notifyDisconnectedOnce(reason)
    }

    fun sendAudioPcm16(bytes: ByteArray) {
        if (!connected.get()) return
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        sendJson(
            """{"type":"input_audio_buffer.append","audio":"$encoded"}""",
            persistedEventType = "input_audio_buffer.append.sent",
        )
    }

    fun sendImage(jpegBytes: ByteArray) {
        if (!connected.get()) return
        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        sendJson(
            """{"type":"conversation.item.create","item":{"type":"message","role":"user","content":[{"type":"input_image","image_url":"data:image/jpeg;base64,$encoded"}]}}""",
            persistedEventType = "image.sent",
        )
    }

    private fun sendSessionUpdate() {
        sendJson(
            realtimeSessionUpdateJson(
                model = model,
                voice = voice,
                useCaseContext = useCaseContextProvider(),
                useCase = useCase,
            ),
        )
    }

    fun refreshInstructions() {
        if (!connected.get()) return
        sendSessionUpdate()
    }

    fun sendFunctionCallOutput(callId: String, output: String) {
        sendJson(functionCallOutputJson(callId = callId, output = output))
    }

    private fun sendJson(json: String, persistedEventType: String? = null) {
        val socket = webSocket ?: return
        recorderProvider()?.appendEvent(clientEventLogEntry(json, persistedEventType))
        socket.send(json)
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            recorderProvider()?.appendEvent("""{"direction":"server","type":"websocket.open","code":${response.code}}""")
            sendSessionUpdate()
            connected.set(true)
            listener.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isAudioDeltaEvent(text)) {
                // The decoded audio bytes are already saved once via appendAssistantPcm below;
                // logging the full payload here would duplicate that same audio a second time,
                // base64-encoded, inside the event log.
                recorderProvider()?.appendEvent("""{"direction":"server","type":"audio.delta.received"}""")
            } else {
                recorderProvider()?.appendEvent("""{"direction":"server","payload":${ApiLogRedactor.redact(text)}}""")
            }
            if (text.contains("response.done")) {
                listener.onUsageEvent(text)
            }
            RealtimeCompanionToolCatalog.extractToolRequest(text)?.let { request ->
                listener.onToolRequest(request)
            }
            extractAssistantAudio(text)?.let { payload ->
                recorderProvider()?.appendAssistantPcm(payload)
                listener.onAssistantAudio(payload)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val payload = bytes.toByteArray()
            recorderProvider()?.appendAssistantPcm(payload)
            listener.onAssistantAudio(payload)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected.set(false)
            this@RealtimeSession.webSocket = null
            recorderProvider()?.appendEvent("""{"direction":"server","type":"websocket.closed","code":$code,"reason":"$reason"}""")
            notifyDisconnectedOnce(reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected.set(false)
            this@RealtimeSession.webSocket = null
            val message = t.message ?: t::class.java.simpleName
            Log.w(TAG, "Realtime WebSocket failed", t)
            recorderProvider()?.appendEvent("""{"direction":"server","type":"websocket.failure","message":"${message.replace("\"", "'")}"}""")
            listener.onError(message)
        }
    }

    companion object {
        const val TAG = "RealtimeSession"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        }

        fun extractAssistantAudio(text: String): ByteArray? {
            return runCatching {
                val json = JSONObject(text)
                val type = json.optString("type")
                if (type != "response.output_audio.delta" || !json.has("delta")) return null
                Base64.decode(json.getString("delta"), Base64.DEFAULT)
            }.getOrNull()
        }

        fun isAudioDeltaEvent(text: String): Boolean {
            return runCatching {
                val json = JSONObject(text)
                json.optString("type") == "response.output_audio.delta" && json.has("delta")
            }.getOrDefault(false)
        }
    }
}
