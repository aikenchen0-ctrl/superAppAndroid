package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.accessibility.scrm.ScrmVoiceTranscriptionState
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageTranscriptionUiContractTest {
    @Test
    fun messageActionUsesScrmMessageIdAndRefreshesAfterSuccessfulTranscription() {
        val source = sourceFile("FloatingChatOverlayUi.kt").readText()
        val callbackStart = "onTranscribeMessage = transcribe@{ message ->"
        assertTrue(source.contains(callbackStart))
        val callback = source.substringAfter(callbackStart)
            .substringBefore("onScrmOperationRequested =")

        assertTrue(source.contains("onRefreshConversation: (String) -> Unit"))
        assertTrue(callback.contains("messageRemoteIdForVoiceTranscription("))
        assertTrue(callback.contains("voiceTranscriptionAccountId(message, selectedAccount.id)"))
        assertTrue(callback.contains("scrmFloatingAccountRouteForContactId(transcriptionAccountId)"))
        assertTrue(callback.contains("withContext(Dispatchers.IO)"))
        assertTrue(callback.contains("ScrmVoiceTranscriptionTaskRunner("))
        assertTrue(callback.contains("messageApi = session.messageOperationApi"))
        ScrmVoiceTranscriptionState.entries.forEach { state ->
            assertTrue(callback.contains("ScrmVoiceTranscriptionState.${state.name}"))
        }
        assertTrue(callback.contains("transcribingRemoteMessageIds.add(remoteMessageId)"))
        assertTrue(callback.contains("transcribingRemoteMessageIds.remove(remoteMessageId)"))
        assertTrue(callback.contains("runtimeState.voiceTranscriptionTaskId("))
        assertTrue(callback.contains("transcriptionAccountId"))
        assertTrue(callback.contains("remoteMessageId"))
        assertTrue(callback.contains("runner.awaitExistingTask(existingTaskId)"))
        assertTrue(callback.contains("onTaskAccepted = { taskId ->"))
        assertTrue(callback.contains("runtimeState.rememberVoiceTranscriptionTask("))
        assertTrue(callback.contains("runtimeState.clearVoiceTranscriptionTask("))
        assertTrue(callback.contains("onRefreshConversation(transcriptionAccountId)"))
        val unknownResultBranch = callback
            .substringAfterLast("ScrmVoiceTranscriptionState.RESULT_UNKNOWN ->")
        assertTrue(unknownResultBranch.contains("runtimeState.rememberVoiceTranscriptionTask("))
        assertFalse(callback.contains("remoteMessageServerId"))
    }

    @Test
    fun controllerInjectsItsExistingScrmConversationRefreshEntryPoint() {
        val source = sourceFile("FloatingChatOverlayController.kt").readText()

        assertTrue(source.contains("onRefreshConversation = ::refreshScrmConversationFromApi"))
        assertTrue(
            source.contains(
                "private fun refreshScrmConversationFromApi(" +
                    "requestedAccountId: String = selectedAccountId"
            )
        )
        assertTrue(source.contains("queueScrmConversationRefresh(requestedAccountId)"))
        assertTrue(source.contains("runNextPendingScrmConversationRefresh()"))
    }

    @Test
    fun transcribeCallbackIsRequiredByMessageActions() {
        val source = sourceFile("floatingchat/message/MessageLongPressActions.kt").readText()
        val constructor = source.substringAfter("internal class MessageLongPressActions(")
            .substringBefore(") {")

        assertTrue(
            constructor.contains(
                "private val onTranscribeMessage: (FloatingChatMessage) -> Unit,"
            )
        )
        assertFalse(constructor.contains("未配置语音消息转写处理器"))
    }

    private fun sourceFile(name: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/univerge/accessibility",
            name
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility",
            name
        )
    }
}
