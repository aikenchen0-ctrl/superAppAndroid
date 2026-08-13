package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MessageRendererRegistryTest {
    @Test
    fun modelKeepsMessageIdentityAndType() {
        val message = FloatingChatMessage(
            id = "message-42",
            type = FloatingChatMessageType.Text,
            text = "hello",
            fromMe = false,
            senderName = "Alex",
            time = "10:00"
        )

        val model = MessageRenderModel.from(message = message, index = 4)

        assertEquals("message-42", model.id)
        assertEquals(FloatingChatMessageType.Text, model.type)
        assertSame(message, model.message)
        assertEquals(4, model.index)
    }

    @Test
    fun registryRoutesEveryActualMessageTypeToOneOfTheSixRendererFamilies() {
        val registry = MessageRendererRegistry.default()
        val allowedRenderers = setOf(
            SystemMessageRenderer::class,
            TextMessageRenderer::class,
            MediaMessageRenderer::class,
            VoiceCallMessageRenderer::class,
            CardMessageRenderer::class,
            PaymentMessageRenderer::class
        )

        FloatingChatMessageType.entries.forEach { type ->
            assertTrue("$type should use a migrated renderer", registry.resolve(type)::class in allowedRenderers)
        }
    }

    @Test
    fun registryKeepsTheIosAlignedTypeFamilies() {
        val registry = MessageRendererRegistry.default()

        assertTrue(registry.resolve(FloatingChatMessageType.GroupNotice) is SystemMessageRenderer)
        assertTrue(registry.resolve(FloatingChatMessageType.Quote) is TextMessageRenderer)
        assertTrue(registry.resolve(FloatingChatMessageType.ChannelsVideo) is MediaMessageRenderer)
        assertTrue(registry.resolve(FloatingChatMessageType.VideoCall) is VoiceCallMessageRenderer)
        assertTrue(registry.resolve(FloatingChatMessageType.FilePreview) is CardMessageRenderer)
        assertTrue(registry.resolve(FloatingChatMessageType.Transfer) is PaymentMessageRenderer)
    }

    @Test
    fun unregisteredTypeUsesTheRealLegacyRenderer() {
        val registry = MessageRendererRegistry(emptyList(), LegacyMessageRenderer)

        assertSame(LegacyMessageRenderer, registry.resolve(FloatingChatMessageType.Text))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateRegistrationsAreRejected() {
        MessageRendererRegistry(
            renderers = listOf(
                TextMessageRenderer,
                TextMessageRenderer
            ),
            legacyRenderer = LegacyMessageRenderer
        )
    }

    @Test
    fun messageContentDelegatesNormalMessagesAndKeepsUnavailableAndDraftHandling() {
        val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "ubiki-accessibility").isDirectory }
        val source = File(
            projectRoot,
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/message/MessageContent.kt"
        ).readText()

        assertTrue(source.contains("MessageRendererRegistry.default()"))
        assertTrue(source.contains("registry.resolve(model.type).render"))
        assertTrue(source.contains("messageUnavailableStateFor(message)"))
        assertTrue(source.contains("FloatingChatMessageKind.AiDraft"))
        assertTrue(source.contains("MessageRenderState(selected = selected)"))
    }
}
