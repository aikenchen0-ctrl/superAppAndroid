package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

interface AiVoiceAudioEngine {
    fun start(onAudioChunk: (ByteArray) -> Unit)
    fun play(bytes: ByteArray)
    fun interruptPlayback()
    fun stop()
}

class AndroidAiVoiceAudioEngine(
    private val context: Context
) : AiVoiceAudioEngine {
    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private val playbackLock = Any()

    @SuppressLint("MissingPermission")
    override fun start(onAudioChunk: (ByteArray) -> Unit) {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is required for AI voice calls" }
        check(running.compareAndSet(false, true)) { "AI voice audio engine is already running" }

        val inputBufferSize = AudioRecord.getMinBufferSize(
            InputSampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MinimumBufferBytes)
        val outputBufferSize = AudioTrack.getMinBufferSize(
            OutputSampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(MinimumBufferBytes)

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(InputSampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(inputBufferSize)
            .build()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OutputSampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(outputBufferSize)
            .build()

        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Failed to initialize AI voice recorder" }
        check(audioTrack.state == AudioTrack.STATE_INITIALIZED) { "Failed to initialize AI voice player" }

        recorder = audioRecord
        player = audioTrack
        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
        } else {
            null
        }
        audioRecord.startRecording()
        audioTrack.play()
        playbackQueue.clear()
        playbackThread = Thread({ playbackLoop(audioTrack) }, "ai-voice-playback").apply { start() }
        captureThread = Thread({ captureLoop(audioRecord, inputBufferSize, onAudioChunk) }, "ai-voice-capture").apply {
            start()
        }
    }

    override fun play(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        check(player != null && running.get()) { "AI voice audio engine is not running" }
        playbackQueue.offer(bytes.copyOf())
    }

    override fun interruptPlayback() {
        val audioTrack = player ?: return
        playbackQueue.clear()
        synchronized(playbackLock) {
            if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) audioTrack.pause()
            audioTrack.flush()
            if (running.get()) audioTrack.play()
        }
    }

    override fun stop() {
        if (!running.getAndSet(false)) return
        val audioRecord = recorder
        val audioTrack = player
        recorder = null
        player = null
        runCatching { audioRecord?.stop() }
        captureThread?.join(CaptureThreadJoinMs)
        captureThread = null
        echoCanceler?.release()
        echoCanceler = null
        playbackQueue.clear()
        runCatching {
            synchronized(playbackLock) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
            }
        }
        playbackThread?.join(CaptureThreadJoinMs)
        playbackThread = null
        if (audioRecord != null) audioRecord.release()
        if (audioTrack != null) audioTrack.release()
    }

    private fun playbackLoop(audioTrack: AudioTrack) {
        while (running.get()) {
            val bytes = playbackQueue.poll(PlaybackQueuePollMs, TimeUnit.MILLISECONDS) ?: continue
            synchronized(playbackLock) {
                if (!running.get()) return
                val written = audioTrack.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0 && running.get()) {
                    running.set(false)
                    throw IllegalStateException("AI voice playback failed with code $written")
                }
            }
        }
    }

    private fun captureLoop(
        audioRecord: AudioRecord,
        bufferSize: Int,
        onAudioChunk: (ByteArray) -> Unit
    ) {
        val buffer = ByteArray(bufferSize)
        while (running.get()) {
            val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (count > 0) {
                onAudioChunk(buffer.copyOf(count))
            } else if (count < 0 && running.get()) {
                running.set(false)
                throw IllegalStateException("AI voice recording failed with code $count")
            }
        }
    }
}

private const val InputSampleRateHz = 16_000
private const val OutputSampleRateHz = 24_000
private const val MinimumBufferBytes = 2_048
private const val CaptureThreadJoinMs = 500L
private const val PlaybackQueuePollMs = 100L
