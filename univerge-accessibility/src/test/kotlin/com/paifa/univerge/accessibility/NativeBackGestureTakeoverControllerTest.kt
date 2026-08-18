package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackGestureTakeoverControllerTest {
    @Test
    fun rootTakeoverChangesOnlyRequestedSideAndRestoresCapturedValue() {
        val shell = FakeRootShell(
            mutableMapOf(SETTING_BACK_GESTURE_INSET_SCALE_LEFT to "1.5")
        )
        val controller = controller(shell)

        assertTrue(
            controller.synchronize(
                requestedSides = setOf(EdgeSide.LEFT),
                sdkInt = 29,
                navBarHeightPx = null
            ).applied
        )
        assertEquals("0", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_LEFT])
        assertEquals(null, shell.values[SETTING_BACK_GESTURE_INSET_SCALE_RIGHT])

        controller.restore()

        assertEquals("1.5", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_LEFT])
        assertEquals(null, shell.values[SETTING_BACK_GESTURE_INSET_SCALE_RIGHT])
    }

    @Test
    fun restoreDeletesASettingThatDidNotExistBeforeTakeover() {
        val shell = FakeRootShell()
        val controller = controller(shell)

        assertTrue(
            controller.synchronize(
                requestedSides = setOf(EdgeSide.RIGHT),
                sdkInt = 29,
                navBarHeightPx = null
            ).applied
        )

        controller.restore()

        assertEquals(null, shell.values[SETTING_BACK_GESTURE_INSET_SCALE_RIGHT])
        assertTrue(
            shell.commands.contains(
                "settings delete secure $SETTING_BACK_GESTURE_INSET_SCALE_RIGHT"
            )
        )
    }

    @Test
    fun nonRootShellDoesNotChangeSettingsOrClaimTakeover() {
        val shell = FakeRootShell(
            values = mutableMapOf(SETTING_BACK_GESTURE_INSET_SCALE_LEFT to "1.5"),
            idOutput = "uid=2000(shell) gid=2000(shell)"
        )
        val controller = controller(shell)

        val result = controller.synchronize(
            requestedSides = setOf(EdgeSide.LEFT),
            sdkInt = 29,
            navBarHeightPx = null
        )

        assertFalse(result.applied)
        assertEquals("1.5", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_LEFT])
        assertEquals(listOf("id"), shell.commands)
    }

    @Test
    fun failedWriteRollsBackTheCurrentSynchronizationWithoutCapturingPseudoState() {
        val shell = FakeRootShell(
            values = mutableMapOf(
                SETTING_BACK_GESTURE_INSET_SCALE_LEFT to "1.5",
                SETTING_BACK_GESTURE_INSET_SCALE_RIGHT to "2.0"
            ),
            failedCommands = setOf("settings put secure $SETTING_BACK_GESTURE_INSET_SCALE_RIGHT 0")
        )
        val controller = controller(shell)

        val result = controller.synchronize(
            requestedSides = setOf(EdgeSide.LEFT, EdgeSide.RIGHT),
            sdkInt = 29,
            navBarHeightPx = null
        )

        assertFalse(result.applied)
        assertEquals("1.5", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_LEFT])
        assertEquals("2.0", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_RIGHT])

        controller.restore()

        assertEquals("1.5", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_LEFT])
        assertEquals("2.0", shell.values[SETTING_BACK_GESTURE_INSET_SCALE_RIGHT])
    }

    @Test
    fun apiTwentyEightUsesOnlyTheFixedBottomOverscanCommandAndRestoresItsPriorValue() {
        val shell = FakeRootShell(overscan = Overscan(0, 0, 0, 0))
        val controller = controller(shell)

        assertTrue(
            controller.synchronize(
                requestedSides = setOf(EdgeSide.LEFT),
                sdkInt = 28,
                navBarHeightPx = 96
            ).applied
        )
        assertEquals(Overscan(0, 0, 0, -96), shell.overscan)
        assertFalse(shell.commands.any { it.startsWith("settings put secure") })

        controller.restore()

        assertEquals(Overscan(0, 0, 0, 0), shell.overscan)
    }

    @Test
    fun apiTwentyEightDoesNotRunRootCommandsWithoutANavigationBarHeight() {
        val shell = FakeRootShell()
        val controller = controller(shell)

        val result = controller.synchronize(
            requestedSides = setOf(EdgeSide.LEFT),
            sdkInt = 28,
            navBarHeightPx = null
        )

        assertFalse(result.applied)
        assertEquals(emptyList<String>(), shell.commands)
    }

    @Test
    fun failedLegacyOverscanWriteDoesNotLeaveRestorationStateAfterRollback() {
        val shell = FakeRootShell(
            failedCommands = setOf("wm overscan 0,0,0,-96")
        )
        val controller = controller(shell)

        assertFalse(
            controller.synchronize(
                requestedSides = setOf(EdgeSide.LEFT),
                sdkInt = 28,
                navBarHeightPx = 96
            ).applied
        )
        val commandsBeforeRestore = shell.commands.toList()

        controller.restore()

        assertEquals(commandsBeforeRestore, shell.commands)
    }

    @Test
    fun apiTwentyNineDoesNotUseOverscanForSideBackGestureTakeover() {
        val shell = FakeRootShell(
            values = mutableMapOf(SETTING_BACK_GESTURE_INSET_SCALE_LEFT to "1.5")
        )
        val controller = controller(shell)

        assertTrue(
            controller.synchronize(
                requestedSides = setOf(EdgeSide.LEFT),
                sdkInt = 29,
                navBarHeightPx = 96
            ).applied
        )

        assertFalse(shell.commands.any { it.startsWith("wm overscan") })
    }

    @Test
    fun restoreSecureSettingDeletesNullAndLiteralNullValues() {
        assertEquals(
            "settings delete secure $SETTING_BACK_GESTURE_INSET_SCALE_LEFT",
            restoreSecureSettingCommand(SETTING_BACK_GESTURE_INSET_SCALE_LEFT, null)
        )
        assertEquals(
            "settings delete secure $SETTING_BACK_GESTURE_INSET_SCALE_LEFT",
            restoreSecureSettingCommand(SETTING_BACK_GESTURE_INSET_SCALE_LEFT, "null")
        )
    }

    private fun controller(shell: FakeRootShell): NativeBackGestureTakeoverController {
        return NativeBackGestureTakeoverController(
            rootShell = shell,
            warningLogger = {}
        )
    }

    private class FakeRootShell(
        val values: MutableMap<String, String> = mutableMapOf(),
        private val idOutput: String = "uid=0(root) gid=0(root)",
        private val failedCommands: Set<String> = emptySet(),
        var overscan: Overscan = Overscan(0, 0, 0, 0)
    ) : RootShell {
        val commands = mutableListOf<String>()

        override fun run(command: String): RootShellResult {
            commands += command
            if (command in failedCommands) {
                return RootShellResult(exitCode = 1, error = "forced failure")
            }
            return when {
                command == "id" -> RootShellResult(exitCode = 0, output = idOutput)
                command.startsWith("settings get secure ") -> {
                    val key = command.removePrefix("settings get secure ")
                    RootShellResult(exitCode = 0, output = values[key] ?: "null")
                }
                command.startsWith("settings put secure ") -> {
                    val parts = command.split(" ", limit = 5)
                    values[parts[3]] = parts[4].removeSurrounding("'")
                    RootShellResult(exitCode = 0)
                }
                command.startsWith("settings delete secure ") -> {
                    values.remove(command.removePrefix("settings delete secure "))
                    RootShellResult(exitCode = 0)
                }
                command == "wm overscan" -> {
                    RootShellResult(exitCode = 0, output = "overscan: ${overscan.asCommandArguments()}")
                }
                command.startsWith("wm overscan ") -> {
                    overscan = Overscan.parse(command.removePrefix("wm overscan "))
                    RootShellResult(exitCode = 0)
                }
                else -> error("Unexpected root command: $command")
            }
        }
    }
}
