package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConnectorPathWriterTest {
    @Test
    fun directWriterMatchesLegacyGeometryForEveryConnectorShape() {
        val writer = ChatConnectorPathWriter()

        connectorScenarios().forEach { scenario ->
            val expected = legacyCommands(scenario)
            val actual = RecordingCommandSink()

            val wroteTree = writer.writeConnectorTree(
                avatarBounds = scenario.avatarBounds,
                bubbleBounds = scenario.bubbleBounds,
                layerBounds = scenario.layerBounds,
                visibleRootBounds = scenario.visibleRootBounds,
                target = scenario.target,
                hasMessagesAbove = scenario.hasMessagesAbove,
                hasMessagesBelow = scenario.hasMessagesBelow,
                avatarOffscreenEdge = scenario.avatarOffscreenEdge,
                sink = actual
            )

            assertEquals("${scenario.name}: tree presence", expected.treeCreated, wroteTree)
            assertEquals("${scenario.name}: path commands", expected.commands, actual.commands)
        }
    }

    @Test
    fun directGroupMemberBranchMatchesLegacyGeometry() {
        val layerBounds = Rect(left = 17f, top = 29f, right = 337f, bottom = 549f)
        val avatarBounds = Rect(left = 23f, top = 123f, right = 61f, bottom = 161f)
        val bubbleBounds = Rect(left = 149f, top = 105f, right = 263f, bottom = 183f)
        val branch = createGroupMemberMessageConnectorBranch(
            avatarBounds = avatarBounds,
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds
        )
        val expected = listOf(
            RecordedCommand.Move(branch.start.x, branch.start.y),
            RecordedCommand.Line(branch.end.x, branch.end.y)
        )
        val actual = RecordingCommandSink()

        ChatConnectorPathWriter().writeGroupMemberMessageConnector(
            avatarBounds = avatarBounds,
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            sink = actual
        )

        assertEquals(expected, actual.commands)
    }

    @Test
    fun productionLayerUsesRememberedWriterWithoutLegacyGeometryObjects() {
        val layerSource = sourceFile("ChatConnectorLayer.kt").readText()
        val writerSource = sourceFile("ChatConnectorPathWriter.kt").readText()

        listOf(
            "createChatConnectorTree(",
            "createChatConnectorBraceGeometry(",
            "roundedElbowGeometry()",
            "ChatConnectorBranch",
            "appendChatConnectorTree(",
            "appendBraceHookSegment("
        ).forEach { forbidden ->
            assertFalse("Legacy draw allocation remains: $forbidden", layerSource.contains(forbidden))
        }
        assertTrue(layerSource.contains("val connectorPathWriter = remember { ChatConnectorPathWriter() }"))
        assertTrue(
            layerSource.contains(
                "val connectorCommandSink = remember { PathConnectorCommandSink(connectorPath) }"
            )
        )
        assertTrue(
            layerSource.contains(
                "val connectorTreeCommandSink = remember { PathConnectorCommandSink(connectorTreePath) }"
            )
        )

        assertTrue(writerSource.contains("interface ChatConnectorCommandSink"))
        assertTrue(writerSource.contains("private var anchorCoordinates = FloatArray"))
        assertTrue(writerSource.contains("anchorCoordinates.copyOf"))
        listOf(
            ".map {",
            ".sortedBy",
            "listOf(",
            "Offset(",
            "ChatConnectorTree",
            "ChatConnectorBrace",
            "ChatConnectorBranch",
            "Pair<"
        ).forEach { forbidden ->
            assertFalse("Writer allocates legacy geometry: $forbidden", writerSource.contains(forbidden))
        }
    }

    private fun legacyCommands(scenario: ConnectorScenario): LegacyCommandResult {
        val tree = createChatConnectorTree(
            avatarBounds = scenario.avatarBounds,
            bubbleBounds = scenario.bubbleBounds,
            layerBounds = scenario.layerBounds,
            visibleRootBounds = scenario.visibleRootBounds,
            target = scenario.target,
            hasMessagesAbove = scenario.hasMessagesAbove,
            hasMessagesBelow = scenario.hasMessagesBelow,
            avatarOffscreenEdge = scenario.avatarOffscreenEdge
        ) ?: return LegacyCommandResult(treeCreated = false, commands = emptyList())
        val sink = RecordingCommandSink()
        val geometry = createChatConnectorBraceGeometry(tree)
        geometry.trunkSegments.forEach { segment ->
            sink.moveTo(segment.start.x, segment.start.y)
            sink.lineTo(segment.end.x, segment.end.y)
        }
        geometry.hooks.forEach { hook ->
            if (abs(hook.branchEnd.x - hook.center.x) <= 0.5f) return@forEach
            val rounded = hook.roundedElbowGeometry()
            sink.moveTo(rounded.curveStart.x, rounded.curveStart.y)
            sink.quadTo(
                rounded.curveControl.x,
                rounded.curveControl.y,
                rounded.horizontalStart.x,
                rounded.horizontalStart.y
            )
            sink.lineTo(rounded.branchEnd.x, rounded.branchEnd.y)
        }
        return LegacyCommandResult(treeCreated = true, commands = sink.commands)
    }

    private fun connectorScenarios(): List<ConnectorScenario> {
        val layerBounds = Rect(left = 10.25f, top = 20.75f, right = 330.5f, bottom = 540.25f)
        val visibleRootBounds = Rect(left = 70.5f, top = 80.125f, right = 280.75f, bottom = 470.625f)
        return listOf(
            ConnectorScenario(
                name = "user bubbles preserve stable y ordering while scratch grows",
                avatarBounds = Rect(left = 14f, top = 100f, right = 48f, bottom = 134f),
                bubbleBounds = listOf(
                    Rect(left = 144f, top = 326f, right = 254f, bottom = 366f),
                    Rect(left = 118f, top = 104f, right = 224f, bottom = 144f),
                    Rect(left = 136f, top = 256f, right = 246f, bottom = 296f),
                    Rect(left = 129f, top = 186f, right = 239f, bottom = 226f),
                    Rect(left = 152f, top = 396f, right = 262f, bottom = 436f),
                    Rect(left = 121f, top = 151f, right = 231f, bottom = 191f),
                    Rect(left = 147f, top = 291f, right = 257f, bottom = 331f),
                    Rect(left = 132f, top = 221f, right = 242f, bottom = 261f),
                    Rect(left = 155f, top = 431f, right = 265f, bottom = 471f)
                ),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User
            ),
            ConnectorScenario(
                name = "account bubbles preserve reverse-side trunk constraints",
                avatarBounds = Rect(left = 282f, top = 92f, right = 316f, bottom = 126f),
                bubbleBounds = listOf(
                    Rect(left = 82f, top = 310f, right = 218f, bottom = 354f),
                    Rect(left = 74f, top = 108f, right = 238f, bottom = 148f),
                    Rect(left = 96f, top = 210f, right = 226f, bottom = 250f)
                ),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.Account
            ),
            ConnectorScenario(
                name = "above and below message edges extend an otherwise empty trunk",
                avatarBounds = Rect(left = 14f, top = 230f, right = 48f, bottom = 264f),
                bubbleBounds = emptyList(),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User,
                hasMessagesAbove = true,
                hasMessagesBelow = true
            ),
            ConnectorScenario(
                name = "avatar above viewport omits avatar hook and pins trunk",
                avatarBounds = Rect(left = 14f, top = -44f, right = 48f, bottom = -10f),
                bubbleBounds = listOf(
                    Rect(left = 146f, top = 35f, right = 250f, bottom = 55f),
                    Rect(left = 129f, top = 220f, right = 239f, bottom = 260f)
                ),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User,
                avatarOffscreenEdge = ChatConnectorViewportEdge.Above
            ),
            ConnectorScenario(
                name = "avatar below viewport omits account hook and pins trunk",
                avatarBounds = Rect(left = 282f, top = 550f, right = 316f, bottom = 584f),
                bubbleBounds = listOf(
                    Rect(left = 74f, top = 190f, right = 230f, bottom = 234f)
                ),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.Account,
                avatarOffscreenEdge = ChatConnectorViewportEdge.Below
            ),
            ConnectorScenario(
                name = "offscreen avatar without visible bubbles contributes only its pinned edge",
                avatarBounds = Rect(left = 14f, top = -144f, right = 48f, bottom = -110f),
                bubbleBounds = emptyList(),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User,
                hasMessagesBelow = true,
                avatarOffscreenEdge = ChatConnectorViewportEdge.Above
            ),
            ConnectorScenario(
                name = "group tree preserves member bounds input order at pinned y",
                avatarBounds = Rect(left = 14f, top = 202f, right = 48f, bottom = 236f),
                bubbleBounds = listOf(
                    Rect(left = 92f, top = 42f, right = 126f, bottom = 76f),
                    Rect(left = 138f, top = 24f, right = 172f, bottom = 58f),
                    Rect(left = 116f, top = 278f, right = 150f, bottom = 312f)
                ),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User
            ),
            ConnectorScenario(
                name = "empty connector emits no tree",
                avatarBounds = Rect(left = 14f, top = 202f, right = 48f, bottom = 236f),
                bubbleBounds = emptyList(),
                layerBounds = layerBounds,
                visibleRootBounds = visibleRootBounds,
                target = FloatingChatConnectionTarget.User
            )
        )
    }

    private fun sourceFile(fileName: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/chat",
            fileName
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/chat",
            fileName
        )
    }
}

private data class ConnectorScenario(
    val name: String,
    val avatarBounds: Rect,
    val bubbleBounds: List<Rect>,
    val layerBounds: Rect,
    val visibleRootBounds: Rect,
    val target: FloatingChatConnectionTarget,
    val hasMessagesAbove: Boolean = false,
    val hasMessagesBelow: Boolean = false,
    val avatarOffscreenEdge: ChatConnectorViewportEdge? = null
)

private data class LegacyCommandResult(
    val treeCreated: Boolean,
    val commands: List<RecordedCommand>
)

private sealed interface RecordedCommand {
    data class Move(val x: Float, val y: Float) : RecordedCommand
    data class Line(val x: Float, val y: Float) : RecordedCommand
    data class Quad(
        val controlX: Float,
        val controlY: Float,
        val endX: Float,
        val endY: Float
    ) : RecordedCommand
}

private class RecordingCommandSink : ChatConnectorCommandSink {
    val commands = mutableListOf<RecordedCommand>()

    override fun moveTo(x: Float, y: Float) {
        commands += RecordedCommand.Move(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        commands += RecordedCommand.Line(x, y)
    }

    override fun quadTo(controlX: Float, controlY: Float, endX: Float, endY: Float) {
        commands += RecordedCommand.Quad(controlX, controlY, endX, endY)
    }
}
