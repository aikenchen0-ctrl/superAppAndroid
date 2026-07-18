package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceCallRuntimeTest {
    @Test
    fun synchronousConnectionFailureIsReportedWithoutEscapingMainThread() {
        val errors = mutableListOf<String>()
        val runtime = AiVoiceCallRuntime(
            connector = ThrowingConnector(),
            audioEngine = FakeAudioEngine(),
            onStateChanged = {},
            onError = errors::add
        )

        val thrown = runCatching {
            runtime.start(AiVoiceFeature.RealtimeConversation, null)
        }.exceptionOrNull()

        assertEquals(null, thrown)
        assertEquals(listOf("AI voice gateway base URL is required"), errors)
    }

    @Test
    fun connectedSessionBridgesMicrophoneAndPlayback() {
        val connector = FakeConnector()
        val audio = FakeAudioEngine()
        val states = mutableListOf<RealtimeCallState>()
        val runtime = AiVoiceCallRuntime(connector, audio, states::add, { throw AssertionError(it) })

        runtime.start(AiVoiceFeature.RealtimeConversation, null)
        connector.emit(AiVoiceRealtimeEvent.Connected)
        audio.emit(byteArrayOf(1, 2))
        connector.emit(AiVoiceRealtimeEvent.AudioReceived(byteArrayOf(3, 4)))

        assertEquals(listOf(RealtimeCallState.Connecting, RealtimeCallState.Listening), states)
        assertEquals(byteArrayOf(1, 2).toList(), connector.session.sentAudio.single().toList())
        assertEquals(byteArrayOf(3, 4).toList(), audio.played.single().toList())
        assertTrue(audio.started)
    }

    @Test
    fun userSpeechImmediatelyFlushesAssistantPlayback() {
        val connector = FakeConnector()
        val audio = FakeAudioEngine()
        val runtime = AiVoiceCallRuntime(connector, audio, {}, { throw AssertionError(it) })

        runtime.start(AiVoiceFeature.RealtimeConversation, null)
        connector.emit(AiVoiceRealtimeEvent.Connected)
        connector.emit(AiVoiceRealtimeEvent.StateChanged(RealtimeCallState.UserSpeaking))

        assertEquals(1, audio.interruptCount)
    }

    @Test
    fun eventsArrivingAfterStopCannotResumePlayback() {
        val connector = FakeConnector()
        val audio = FakeAudioEngine()
        val runtime = AiVoiceCallRuntime(connector, audio, {}, { throw AssertionError(it) })

        runtime.start(AiVoiceFeature.RealtimeConversation, null)
        connector.emit(AiVoiceRealtimeEvent.Connected)
        runtime.stop()
        connector.emit(AiVoiceRealtimeEvent.AudioReceived(byteArrayOf(9)))

        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun microphoneVoiceActivityInterruptsBeforeServerAsrEventArrives() {
        val connector = FakeConnector()
        val audio = FakeAudioEngine()
        val states = mutableListOf<RealtimeCallState>()
        val runtime = AiVoiceCallRuntime(connector, audio, states::add, { throw AssertionError(it) })

        runtime.start(AiVoiceFeature.RealtimeConversation, null)
        connector.emit(AiVoiceRealtimeEvent.Connected)
        connector.emit(AiVoiceRealtimeEvent.StateChanged(RealtimeCallState.AiResponding))
        val loudPcm = ByteArray(2_048).also { bytes ->
            for (index in bytes.indices step 2) {
                bytes[index] = 0x00
                bytes[index + 1] = 0x20
            }
        }
        audio.emit(loudPcm)
        audio.emit(loudPcm)

        assertEquals(1, audio.interruptCount)
        assertEquals(RealtimeCallState.UserSpeaking, states.last())
    }

    private class FakeConnector : AiVoiceRealtimeConnector {
        lateinit var callback: (AiVoiceRealtimeEvent) -> Unit
        val session = FakeSession()

        override fun connect(
            feature: AiVoiceFeature,
            voiceProfileId: String?,
            onEvent: (AiVoiceRealtimeEvent) -> Unit
        ): AiVoiceRealtimeSession {
            callback = onEvent
            return session
        }

        fun emit(event: AiVoiceRealtimeEvent) = callback(event)
    }

    private class ThrowingConnector : AiVoiceRealtimeConnector {
        override fun connect(
            feature: AiVoiceFeature,
            voiceProfileId: String?,
            onEvent: (AiVoiceRealtimeEvent) -> Unit
        ): AiVoiceRealtimeSession {
            throw MissingAiVoiceApiConfigException("AI voice gateway base URL is required")
        }
    }

    private class FakeSession : AiVoiceRealtimeSession {
        val sentAudio = mutableListOf<ByteArray>()
        override fun sendAudio(bytes: ByteArray): Boolean = sentAudio.add(bytes)
        override fun interrupt(): Boolean = true
        override fun close() = Unit
    }

    private class FakeAudioEngine : AiVoiceAudioEngine {
        lateinit var callback: (ByteArray) -> Unit
        var started = false
        val played = mutableListOf<ByteArray>()
        var interruptCount = 0
        override fun start(onAudioChunk: (ByteArray) -> Unit) { started = true; callback = onAudioChunk }
        override fun play(bytes: ByteArray) { played += bytes }
        override fun interruptPlayback() { interruptCount++ }
        override fun stop() = Unit
        fun emit(bytes: ByteArray) = callback(bytes)
    }
}
