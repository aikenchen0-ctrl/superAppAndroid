package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

data class DoubaoRealtimeCredentials(
    val appId: String,
    val accessToken: String
) {
    fun requireValid() {
        require(appId.isNotBlank()) { "火山引擎 APP ID 不能为空" }
        require(accessToken.isNotBlank()) { "火山引擎 Access Token 不能为空" }
    }
}

data class DoubaoRealtimeFrame(
    val type: Int,
    val event: Int,
    val payload: ByteArray
)

object DoubaoRealtimeProtocol {
    fun startConnection(): ByteArray = encode(type = FullClient, event = StartConnection, payload = "{}".encodeToByteArray())

    fun startSession(sessionId: String, voiceProfileId: String? = null): ByteArray {
        val payload = buildJsonObject {
            put("asr", buildJsonObject {
                put("audio_info", buildJsonObject {
                    put("format", "pcm")
                    put("sample_rate", 16_000)
                    put("channel", 1)
                })
                put("extra", buildJsonObject { })
            })
            put("tts", buildJsonObject {
                put("audio_config", buildJsonObject {
                    put("format", "pcm_s16le")
                    put("sample_rate", 24_000)
                    put("channel", 1)
                })
                put("extra", buildJsonObject {
                    voiceProfileId?.takeIf(String::isNotBlank)?.let { put("speaker", it) }
                })
            })
            put("dialog", buildJsonObject {
                put("bot_name", "AI 助手")
                put("system_role", "你是一名简洁、友好的语音助手。")
                put("speaking_style", "自然、清晰地使用简体中文回答。")
                put("extra", buildJsonObject {
                    put("model", if (voiceProfileId.isNullOrBlank()) "1.2.1.1" else "2.2.0.0")
                    put("input_mod", "audio")
                })
            })
        }.toString().encodeToByteArray()
        return encode(FullClient, StartSession, payload, sessionId)
    }

    fun audio(sessionId: String, bytes: ByteArray): ByteArray =
        encode(AudioClient, TaskRequest, bytes, sessionId, serialization = Raw)

    fun finishSession(sessionId: String): ByteArray =
        encode(FullClient, FinishSession, "{}".encodeToByteArray(), sessionId)

    fun decode(bytes: ByteArray): DoubaoRealtimeFrame {
        require(bytes.size >= HeaderSize + IntSize * 2) { "实时语音响应帧长度不足" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.get()
        val typeAndFlags = buffer.get().toInt() and 0xff
        buffer.get()
        buffer.get()
        val type = typeAndFlags ushr 4
        val flags = typeAndFlags and 0x0f
        val event = if (flags and WithEvent != 0) buffer.int else 0
        if (event !in ConnectEvents && event != 0) skipSizedField(buffer, "session ID")
        if (event in ConnectEvents) skipSizedField(buffer, "connect ID")
        if (flags and SequenceMask != 0) buffer.int
        if (type == ErrorType) buffer.int
        require(buffer.remaining() >= IntSize) { "实时语音响应缺少 payload 长度" }
        val payloadSize = buffer.int
        require(payloadSize in 0..buffer.remaining()) { "实时语音响应 payload 长度非法: $payloadSize" }
        val payload = ByteArray(payloadSize).also(buffer::get)
        return DoubaoRealtimeFrame(type, event, payload)
    }

    private fun encode(
        type: Int,
        event: Int,
        payload: ByteArray,
        sessionId: String? = null,
        serialization: Int = Json
    ): ByteArray = ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { data ->
            data.writeByte(VersionAndHeader)
            data.writeByte((type shl 4) or WithEvent)
            data.writeByte(serialization)
            data.writeByte(0)
            data.writeInt(event)
            sessionId?.encodeToByteArray()?.let {
                data.writeInt(it.size)
                data.write(it)
            }
            data.writeInt(payload.size)
            data.write(payload)
        }
        output.toByteArray()
    }

    private fun skipSizedField(buffer: ByteBuffer, name: String) {
        require(buffer.remaining() >= IntSize) { "实时语音响应缺少 $name 长度" }
        val size = buffer.int
        require(size in 0..buffer.remaining()) { "实时语音响应 $name 长度非法: $size" }
        buffer.position(buffer.position() + size)
    }

    const val ConnectionStarted = 50
    const val SessionStarted = 150
    const val SessionFinished = 152
    const val AsrInfo = 450
    const val AsrEnded = 459
    const val TtsResponse = 352
    private const val StartConnection = 1
    private const val FinishSession = 102
    private const val StartSession = 100
    private const val TaskRequest = 200
    private const val FullClient = 1
    private const val AudioClient = 2
    private const val ErrorType = 15
    private const val WithEvent = 4
    private const val SequenceMask = 3
    private const val VersionAndHeader = 0x11
    private const val Json = 0x10
    private const val Raw = 0x00
    private const val HeaderSize = 4
    private const val IntSize = 4
    private val ConnectEvents = setOf(50, 51, 52)
}

fun buildDoubaoRealtimeRequest(credentials: DoubaoRealtimeCredentials): Request {
    credentials.requireValid()
    return Request.Builder()
        .url(DoubaoRealtimeUrl)
        .header("X-Api-App-ID", credentials.appId.trim())
        .header("X-Api-Access-Key", credentials.accessToken.trim())
        .header("X-Api-Resource-Id", DoubaoRealtimeResourceId)
        .header("X-Api-App-Key", DoubaoRealtimeAppKey)
        .header("X-Api-Connect-Id", UUID.randomUUID().toString())
        .build()
}

suspend fun probeDoubaoRealtime(
    credentials: DoubaoRealtimeCredentials,
    client: OkHttpClient = OkHttpClient()
): Unit = suspendCoroutine { continuation ->
    var completed = false
    client.newWebSocket(
        buildDoubaoRealtimeRequest(credentials),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(DoubaoRealtimeProtocol.startConnection().toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runCatching { DoubaoRealtimeProtocol.decode(bytes.toByteArray()) }
                    .onSuccess { frame ->
                        if (frame.event == DoubaoRealtimeProtocol.ConnectionStarted && !completed) {
                            completed = true
                            webSocket.close(1000, "capability probe complete")
                            continuation.resume(Unit)
                        } else if (frame.type == 15 && !completed) {
                            completed = true
                            continuation.resumeWithException(
                                IllegalStateException(frame.payload.decodeToString().ifBlank { "实时语音服务返回错误" })
                            )
                        }
                    }
                    .onFailure {
                        if (!completed) {
                            completed = true
                            continuation.resumeWithException(it)
                        }
                    }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (!completed) {
                    completed = true
                    val prefix = response?.code?.let { "HTTP $it: " }.orEmpty()
                    continuation.resumeWithException(IllegalStateException(prefix + (throwable.message ?: "实时语音连接失败")))
                }
            }
        }
    )
}

sealed interface AiVoiceRealtimeEvent {
    data object Connected : AiVoiceRealtimeEvent
    data class AudioReceived(val bytes: ByteArray) : AiVoiceRealtimeEvent
    data class StateChanged(val state: RealtimeCallState) : AiVoiceRealtimeEvent
    data class TranscriptReceived(val speaker: RealtimeSpeaker, val text: String) : AiVoiceRealtimeEvent
    data class Failed(val message: String) : AiVoiceRealtimeEvent
    data object Closed : AiVoiceRealtimeEvent
}

enum class RealtimeSpeaker { User, Assistant }

interface AiVoiceRealtimeSession {
    fun sendAudio(bytes: ByteArray): Boolean
    fun interrupt(): Boolean
    fun close()
}

interface AiVoiceRealtimeConnector {
    fun connect(feature: AiVoiceFeature, voiceProfileId: String?, onEvent: (AiVoiceRealtimeEvent) -> Unit): AiVoiceRealtimeSession
}

class OkHttpAiVoiceRealtimeClient(
    private val credentials: DoubaoRealtimeCredentials,
    private val client: OkHttpClient = OkHttpClient()
) : AiVoiceRealtimeConnector {
    override fun connect(feature: AiVoiceFeature, voiceProfileId: String?, onEvent: (AiVoiceRealtimeEvent) -> Unit): AiVoiceRealtimeSession {
        require(feature == AiVoiceFeature.RealtimeConversation || feature == AiVoiceFeature.ClonedVoiceCall) {
            "${feature.title} 不是实时通话能力"
        }
        val sessionId = UUID.randomUUID().toString()
        val listener = DoubaoRealtimeWebSocketListener(sessionId, voiceProfileId, onEvent)
        val socket = client.newWebSocket(buildDoubaoRealtimeRequest(credentials), listener)
        return DoubaoRealtimeSession(socket, sessionId)
    }
}

private class DoubaoRealtimeSession(private val socket: WebSocket, private val sessionId: String) : AiVoiceRealtimeSession {
    override fun sendAudio(bytes: ByteArray): Boolean = socket.send(DoubaoRealtimeProtocol.audio(sessionId, bytes).toByteString())
    override fun interrupt(): Boolean = true // Server VAD uses subsequent microphone audio to interrupt playback.
    override fun close() {
        socket.send(DoubaoRealtimeProtocol.finishSession(sessionId).toByteString())
        socket.close(1000, "client closed")
    }
}

private class DoubaoRealtimeWebSocketListener(
    private val sessionId: String,
    private val voiceProfileId: String?,
    private val onEvent: (AiVoiceRealtimeEvent) -> Unit
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send(DoubaoRealtimeProtocol.startConnection().toByteString())
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        runCatching { DoubaoRealtimeProtocol.decode(bytes.toByteArray()) }
            .onSuccess { frame ->
                when (frame.event) {
                    DoubaoRealtimeProtocol.ConnectionStarted -> webSocket.send(
                        DoubaoRealtimeProtocol.startSession(sessionId, voiceProfileId).toByteString()
                    )
                    DoubaoRealtimeProtocol.SessionStarted -> onEvent(AiVoiceRealtimeEvent.Connected)
                    DoubaoRealtimeProtocol.AsrInfo -> onEvent(AiVoiceRealtimeEvent.StateChanged(RealtimeCallState.UserSpeaking))
                    451 -> parseUserTranscript(frame.payload)?.let {
                        onEvent(AiVoiceRealtimeEvent.TranscriptReceived(RealtimeSpeaker.User, it))
                    }
                    DoubaoRealtimeProtocol.AsrEnded -> onEvent(AiVoiceRealtimeEvent.StateChanged(RealtimeCallState.AiResponding))
                    550 -> parseAssistantTranscript(frame.payload)?.let {
                        onEvent(AiVoiceRealtimeEvent.TranscriptReceived(RealtimeSpeaker.Assistant, it))
                    }
                    DoubaoRealtimeProtocol.TtsResponse -> onEvent(AiVoiceRealtimeEvent.AudioReceived(frame.payload))
                    DoubaoRealtimeProtocol.SessionFinished -> onEvent(AiVoiceRealtimeEvent.Closed)
                }
                if (frame.type == 15) {
                    onEvent(AiVoiceRealtimeEvent.Failed(frame.payload.decodeToString().ifBlank { "实时语音服务返回错误" }))
                }
            }
            .onFailure { onEvent(AiVoiceRealtimeEvent.Failed(it.message ?: "实时语音协议解析失败")) }
    }

    override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
        val prefix = response?.code?.let { "HTTP $it: " }.orEmpty()
        onEvent(AiVoiceRealtimeEvent.Failed(prefix + (throwable.message ?: throwable::class.java.simpleName)))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onEvent(AiVoiceRealtimeEvent.Closed)
}

internal fun parseUserTranscript(payload: ByteArray): String? = runCatching {
    Json.parseToJsonElement(payload.decodeToString()).jsonObject["results"]
        ?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
        ?.trim()?.takeIf(String::isNotEmpty)
}.getOrNull()

internal fun parseAssistantTranscript(payload: ByteArray): String? = runCatching {
    Json.parseToJsonElement(payload.decodeToString()).jsonObject["content"]
        ?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotEmpty)
}.getOrNull()

private fun ByteArray.toByteString(): ByteString = ByteString.of(*this)

private const val DoubaoRealtimeUrl = "https://openspeech.bytedance.com/api/v3/realtime/dialogue"
private const val DoubaoRealtimeResourceId = "volc.speech.dialog"
private const val DoubaoRealtimeAppKey = "PlgvMymc7f3tQnJ6"
