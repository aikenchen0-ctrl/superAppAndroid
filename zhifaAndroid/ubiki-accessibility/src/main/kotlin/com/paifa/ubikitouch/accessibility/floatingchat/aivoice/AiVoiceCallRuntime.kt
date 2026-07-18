package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

class AiVoiceCallRuntime(
    private val connector: AiVoiceRealtimeConnector,
    private val audioEngine: AiVoiceAudioEngine,
    private val onStateChanged: (RealtimeCallState) -> Unit,
    private val onError: (String) -> Unit,
    private val onTranscript: (RealtimeSpeaker, String) -> Unit = { _, _ -> }
) {
    private var session: AiVoiceRealtimeSession? = null
    @Volatile private var active = false
    @Volatile private var currentState = RealtimeCallState.Connecting
    private var voiceActivityFrames = 0
    private var locallyInterrupted = false

    fun start(feature: AiVoiceFeature, voiceProfileId: String?) {
        check(session == null) { "AI voice call is already running" }
        active = true
        currentState = RealtimeCallState.Connecting
        voiceActivityFrames = 0
        locallyInterrupted = false
        onStateChanged(RealtimeCallState.Connecting)
        runCatching {
            connector.connect(feature, voiceProfileId, ::handleEvent)
        }.onSuccess { connectedSession ->
            session = connectedSession
        }.onFailure { error ->
            active = false
            session = null
            onError(error.message ?: "Failed to connect AI voice call")
        }
    }

    fun interrupt() {
        val activeSession = session ?: throw IllegalStateException("AI voice call is not running")
        check(activeSession.interrupt()) { "Failed to send AI voice interrupt event" }
        audioEngine.interruptPlayback()
        onStateChanged(RealtimeCallState.Interrupted)
    }

    fun stop() {
        active = false
        val activeSession = session
        session = null
        audioEngine.stop()
        activeSession?.close()
    }

    private fun handleEvent(event: AiVoiceRealtimeEvent) {
        if (!active) return
        when (event) {
            AiVoiceRealtimeEvent.Connected -> {
                val activeSession = session ?: return
                runCatching {
                    audioEngine.start { chunk ->
                        if (!activeSession.sendAudio(chunk)) {
                            onError("Failed to send microphone audio to AI voice session")
                        }
                        handleLocalVoiceActivity(chunk)
                    }
                }.onSuccess {
                    onStateChanged(RealtimeCallState.Listening)
                }.onFailure { error ->
                    stop()
                    onError(error.message ?: "Failed to start AI voice audio")
                }
            }
            is AiVoiceRealtimeEvent.AudioReceived -> runCatching {
                audioEngine.play(event.bytes)
            }.onFailure { error ->
                stop()
                onError(error.message ?: "Failed to play AI voice audio")
            }
            is AiVoiceRealtimeEvent.StateChanged -> {
                if (event.state == RealtimeCallState.UserSpeaking) audioEngine.interruptPlayback()
                currentState = event.state
                if (event.state != RealtimeCallState.AiResponding) locallyInterrupted = false
                onStateChanged(event.state)
            }
            is AiVoiceRealtimeEvent.TranscriptReceived -> onTranscript(event.speaker, event.text)
            is AiVoiceRealtimeEvent.Failed -> {
                stop()
                onError(event.message)
            }
            AiVoiceRealtimeEvent.Closed -> stop()
        }
    }

    private fun handleLocalVoiceActivity(chunk: ByteArray) {
        if (currentState != RealtimeCallState.AiResponding || locallyInterrupted) return
        voiceActivityFrames = if (pcmRms(chunk) >= VoiceActivityRmsThreshold) voiceActivityFrames + 1 else 0
        if (voiceActivityFrames < VoiceActivityFramesRequired) return
        locallyInterrupted = true
        voiceActivityFrames = 0
        audioEngine.interruptPlayback()
        currentState = RealtimeCallState.UserSpeaking
        onStateChanged(RealtimeCallState.UserSpeaking)
    }
}

internal fun pcmRms(bytes: ByteArray): Double {
    if (bytes.size < 2) return 0.0
    var sum = 0.0
    var samples = 0
    var index = 0
    while (index + 1 < bytes.size) {
        val sample = ((bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)).toShort().toInt()
        sum += sample.toDouble() * sample.toDouble()
        samples++
        index += 2
    }
    return kotlin.math.sqrt(sum / samples)
}

private const val VoiceActivityRmsThreshold = 1_200.0
private const val VoiceActivityFramesRequired = 2
