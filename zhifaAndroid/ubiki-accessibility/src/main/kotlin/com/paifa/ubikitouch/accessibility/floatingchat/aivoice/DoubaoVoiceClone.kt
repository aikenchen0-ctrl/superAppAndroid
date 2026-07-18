package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class VoiceCloneRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        check(recorder == null) { "声音样本正在录制" }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "请先授予麦克风权限"
        }
        val directory = File(context.cacheDir, "voice-clone").apply { mkdirs() }
        val file = File(directory, "sample-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        return file
    }

    fun stop(): File {
        val active = recorder ?: error("尚未开始录制")
        val file = outputFile ?: error("录音输出文件不存在")
        recorder = null
        outputFile = null
        try {
            active.stop()
        } finally {
            active.release()
        }
        check(file.length() > 0L) { "录音文件为空，请重新录制" }
        check(file.length() <= MaxCloneAudioBytes) { "录音超过 10MB，请缩短后重新录制" }
        return file
    }

    fun cancel() {
        val active = recorder ?: return
        recorder = null
        outputFile = null
        runCatching { active.stop() }
        active.release()
    }
}

class DoubaoVoiceCloneApi(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun train(apiKey: String, speakerId: String, audioFile: File, referenceText: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "豆包语音 API Key 不能为空" }
            require(speakerId.isNotBlank()) { "请填写火山控制台分配的音色 ID" }
            require(audioFile.isFile && audioFile.length() > 0L) { "请先完成声音样本录制" }
            val body = buildJsonObject {
                put("speaker_id", speakerId.trim())
                put("audio", buildJsonObject {
                    put("data", Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP))
                    put("format", "m4a")
                    put("text", referenceText)
                })
                put("language", 0)
                put("extra_params", buildJsonObject { put("demo_text", "你好，这是我的专属声音。") })
            }.toString()
            val request = Request.Builder()
                .url(VoiceCloneUrl)
                .header("X-Api-Key", apiKey.trim())
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .post(body.toRequestBody(JsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                check(response.isSuccessful) { "HTTP ${response.code}: ${responseText.ifBlank { response.message }}" }
                Json.parseToJsonElement(responseText).jsonObject["speaker_id"]?.jsonPrimitive?.content
                    ?: error("声音复刻响应缺少 speaker_id")
            }
        }
}

const val VoiceCloneReferenceText = "清晨的阳光照进窗户，我带着轻松的心情开始新的一天。"
private const val VoiceCloneUrl = "https://openspeech.bytedance.com/api/v3/tts/voice_clone"
private const val MaxCloneAudioBytes = 10L * 1024L * 1024L
private val JsonMediaType = "application/json; charset=utf-8".toMediaType()
