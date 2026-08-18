package com.paifa.univerge.accessibility

import android.content.Context
import android.os.Build
import android.util.Log
import com.paifa.univerge.core.model.EdgeSide
import java.io.IOException
import java.util.concurrent.TimeUnit

internal interface RootShell {
    fun run(command: String): RootShellResult
}

internal data class RootShellResult(
    val exitCode: Int,
    val output: String = "",
    val error: String? = null
)

internal class ProcessRootShell(
    private val timeoutMillis: Long = ROOT_SHELL_TIMEOUT_MILLIS
) : RootShell {
    override fun run(command: String): RootShellResult {
        val process = try {
            ProcessBuilder("su", "-c", command).start()
        } catch (error: IOException) {
            return RootShellResult(exitCode = -1, error = error.message ?: "Unable to start su")
        } catch (error: SecurityException) {
            return RootShellResult(exitCode = -1, error = error.message ?: "Unable to execute su")
        }

        return try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroy()
                process.destroyForcibly()
                RootShellResult(
                    exitCode = -1,
                    error = "Root shell command timed out after ${timeoutMillis}ms"
                )
            } else {
                RootShellResult(
                    exitCode = process.exitValue(),
                    output = process.inputStream.bufferedReader().use { it.readText().trim() },
                    error = process.errorStream.bufferedReader().use { it.readText().trim() }
                        .takeIf { it.isNotEmpty() }
                )
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            RootShellResult(exitCode = -1, error = "Root shell command was interrupted")
        } catch (error: IOException) {
            RootShellResult(exitCode = -1, error = error.message ?: "Unable to read root shell output")
        }
    }
}

internal class NativeBackGestureTakeoverController(
    private val rootShell: RootShell,
    private val warningLogger: (String) -> Unit = { message -> Log.w(TAG, message) }
) {
    private val originalSecureSettings = linkedMapOf<String, String?>()
    private var originalOverscan: Overscan? = null

    internal constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this(ProcessRootShell())

    /**
     * Compatibility for the previous service call. It deliberately has no requested side, so it
     * cannot take over both system back edges before the caller has computed the real coverage.
     */
    @Deprecated("Use synchronize(requestedSides, sdkInt, navBarHeightPx)")
    fun applyTakeover(): NativeBackGestureTakeoverResult {
        return synchronize(emptySet(), Build.VERSION.SDK_INT, navBarHeightPx = null)
    }

    fun synchronize(
        requestedSides: Set<EdgeSide>,
        sdkInt: Int,
        navBarHeightPx: Int?
    ): NativeBackGestureTakeoverResult {
        return when {
            sdkInt >= ANDROID_Q_API -> synchronizeSecureSettings(requestedSides)
            sdkInt >= ANDROID_O_API -> synchronizeOverscan(requestedSides, navBarHeightPx)
            else -> failed("Root back gesture takeover requires Android API $ANDROID_O_API or newer")
        }
    }

    fun restore(): NativeBackGestureTakeoverResult {
        if (originalSecureSettings.isEmpty() && originalOverscan == null) {
            return inactiveResult()
        }

        val rootError = requireRoot()
        if (rootError != null) return failed("Cannot restore root takeover: $rootError")
        val before = linkedMapOf<String, String?>()
        val changedKeys = mutableListOf<String>()
        originalSecureSettings.toMap().forEach { (key, originalValue) ->
            before[key] = originalValue
            val result = runCommand(restoreSecureSettingCommand(key, originalValue))
            if (!result.isSuccess()) {
                return failed(
                    message = "Unable to restore $key: ${commandFailure(result)}",
                    changedKeys = changedKeys,
                    before = before,
                    after = before
                )
            }
            originalSecureSettings.remove(key)
            changedKeys += key
        }

        originalOverscan?.let { overscan ->
            before[OVERSCAN_STATE_KEY] = overscan.asCommandArguments()
            val result = runCommand("wm overscan ${overscan.asCommandArguments()}")
            if (!result.isSuccess()) {
                return failed(
                    message = "Unable to restore overscan: ${commandFailure(result)}",
                    changedKeys = changedKeys,
                    before = before,
                    after = before
                )
            }
            originalOverscan = null
            changedKeys += OVERSCAN_STATE_KEY
        }

        return NativeBackGestureTakeoverResult(
            applied = false,
            changedKeys = changedKeys,
            before = before,
            after = before
        )
    }

    private fun synchronizeSecureSettings(
        requestedSides: Set<EdgeSide>
    ): NativeBackGestureTakeoverResult {
        if (requestedSides.isEmpty()) return restore()

        val rootError = requireRoot()
        if (rootError != null) return failed("Cannot synchronize root takeover: $rootError")
        val requestedKeys = EdgeSide.entries
            .filter { it in requestedSides }
            .map(::backGestureInsetSettingKey)
        val before = linkedMapOf<String, String?>()
        val currentValues = linkedMapOf<String, String?>()
        requestedKeys.forEach { key ->
            val readResult = runCommand("settings get secure $key")
            if (!readResult.isSuccess()) {
                return failed(
                    message = "Unable to read $key: ${commandFailure(readResult)}",
                    before = before,
                    after = before
                )
            }
            val currentValue = normalizeSecureSettingValue(readResult.output)
            currentValues[key] = currentValue
            before[key] = currentValue
        }

        val changedThisSynchronization = linkedMapOf<String, String?>()
        requestedKeys.forEach { key ->
            if (currentValues[key] == SECURE_SETTING_DISABLED) return@forEach

            val writeResult = runCommand("settings put secure $key $SECURE_SETTING_DISABLED")
            if (!writeResult.isSuccess()) {
                val rollbackFailures = rollbackSettings(changedThisSynchronization)
                rollbackFailures.forEach { failedKey ->
                    if (failedKey !in originalSecureSettings) {
                        originalSecureSettings[failedKey] = changedThisSynchronization.getValue(failedKey)
                    }
                }
                return failed(
                    message = "Unable to disable $key: ${commandFailure(writeResult)}",
                    before = before,
                    after = before
                )
            }
            changedThisSynchronization[key] = currentValues.getValue(key)
        }

        changedThisSynchronization.forEach { (key, originalValue) ->
            if (key !in originalSecureSettings) {
                originalSecureSettings[key] = originalValue
            }
        }

        val changedKeys = changedThisSynchronization.keys.toMutableList()
        originalSecureSettings.keys
            .filterNot { it in requestedKeys }
            .toList()
            .forEach { key ->
                val originalValue = originalSecureSettings.getValue(key)
                before.putIfAbsent(key, originalValue)
                val restoreResult = runCommand(restoreSecureSettingCommand(key, originalValue))
                if (!restoreResult.isSuccess()) {
                    return failed(
                        message = "Unable to stop takeover for $key: ${commandFailure(restoreResult)}",
                        changedKeys = changedKeys,
                        before = before,
                        after = before
                    )
                }
                originalSecureSettings.remove(key)
                changedKeys += key
            }

        val after = before.toMutableMap().apply {
            requestedKeys.forEach { key -> this[key] = SECURE_SETTING_DISABLED }
            originalSecureSettings.keys
                .filterNot { it in requestedKeys }
                .forEach { key -> this[key] = originalSecureSettings.getValue(key) }
        }
        return NativeBackGestureTakeoverResult(
            applied = true,
            changedKeys = changedKeys,
            before = before,
            after = after
        )
    }

    private fun synchronizeOverscan(
        requestedSides: Set<EdgeSide>,
        navBarHeightPx: Int?
    ): NativeBackGestureTakeoverResult {
        if (requestedSides.isEmpty()) return restore()
        if (navBarHeightPx == null || navBarHeightPx <= 0) {
            return failed("Cannot synchronize legacy root takeover without a navigation bar height")
        }

        val rootError = requireRoot()
        if (rootError != null) return failed("Cannot synchronize root takeover: $rootError")
        val target = Overscan(left = 0, top = 0, right = 0, bottom = -navBarHeightPx)
        val capturedOverscan = originalOverscan
        val previousOverscan = capturedOverscan ?: run {
            val readResult = runCommand("wm overscan")
            if (!readResult.isSuccess()) {
                return failed("Unable to read overscan: ${commandFailure(readResult)}")
            }
            val current = Overscan.fromShellOutput(readResult.output)
                ?: return failed("Unable to parse current overscan: ${readResult.output}")
            if (current == target) {
                return NativeBackGestureTakeoverResult(applied = true)
            }
            current
        }

        val writeResult = runCommand("wm overscan ${target.asCommandArguments()}")
        if (!writeResult.isSuccess()) {
            if (capturedOverscan == null) {
                val rollbackResult = runCommand("wm overscan ${previousOverscan.asCommandArguments()}")
                if (!rollbackResult.isSuccess()) {
                    originalOverscan = previousOverscan
                    return failed(
                        "Unable to set overscan: ${commandFailure(writeResult)}; " +
                            "rollback failed: ${commandFailure(rollbackResult)}"
                    )
                }
            }
            return failed("Unable to set overscan: ${commandFailure(writeResult)}")
        }
        if (capturedOverscan == null) {
            originalOverscan = previousOverscan
        }
        return NativeBackGestureTakeoverResult(
            applied = true,
            changedKeys = listOf(OVERSCAN_STATE_KEY),
            before = mapOf(OVERSCAN_STATE_KEY to previousOverscan.asCommandArguments()),
            after = mapOf(OVERSCAN_STATE_KEY to target.asCommandArguments())
        )
    }

    private fun rollbackSettings(changedSettings: Map<String, String?>): Set<String> {
        return changedSettings.entries
            .toList()
            .asReversed()
            .mapNotNull { (key, originalValue) ->
                runCommand(restoreSecureSettingCommand(key, originalValue))
                    .takeUnless { it.isSuccess() }
                    ?.let { key }
            }
            .toSet()
    }

    private fun requireRoot(): String? {
        val result = runCommand("id")
        return when {
            !result.isSuccess() -> "id failed: ${commandFailure(result)}"
            !ROOT_UID_PATTERN.containsMatchIn(result.output) -> "id did not report $ROOT_UID_MARKER"
            else -> null
        }
    }

    private fun runCommand(command: String): RootShellResult {
        return try {
            rootShell.run(command)
        } catch (error: Exception) {
            RootShellResult(exitCode = -1, error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun failed(
        message: String,
        changedKeys: List<String> = emptyList(),
        before: Map<String, String?> = emptyMap(),
        after: Map<String, String?> = before
    ): NativeBackGestureTakeoverResult {
        warningLogger("Native back gesture takeover: $message")
        return NativeBackGestureTakeoverResult(
            applied = false,
            changedKeys = changedKeys,
            before = before,
            after = after,
            errorMessage = message
        )
    }

    private fun inactiveResult(): NativeBackGestureTakeoverResult {
        return NativeBackGestureTakeoverResult(
            applied = false,
            changedKeys = emptyList(),
            before = emptyMap(),
            after = emptyMap()
        )
    }

    private fun RootShellResult.isSuccess(): Boolean = exitCode == 0

    private fun commandFailure(result: RootShellResult): String {
        return result.error?.takeIf { it.isNotBlank() }
            ?: result.output.takeIf { it.isNotBlank() }
            ?: "exitCode=${result.exitCode}"
    }

    private companion object {
        const val TAG = "UbikiTouch"
    }
}

internal data class NativeBackGestureTakeoverResult(
    val applied: Boolean,
    val changedKeys: List<String> = emptyList(),
    val before: Map<String, String?> = emptyMap(),
    val after: Map<String, String?> = emptyMap(),
    val errorMessage: String? = null
)

internal data class SecureSettingOverride(
    val key: String,
    val value: String
)

internal data class Overscan(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun asCommandArguments(): String = "$left,$top,$right,$bottom"

    companion object {
        fun parse(value: String): Overscan {
            val values = value.split(',').map { it.trim().toInt() }
            require(values.size == 4) { "Expected four overscan values" }
            return Overscan(values[0], values[1], values[2], values[3])
        }

        fun fromShellOutput(output: String): Overscan? {
            val match = OVERSCAN_OUTPUT_PATTERN.find(output) ?: return null
            return runCatching { parse(match.value) }.getOrNull()
        }
    }
}

internal fun nativeBackGestureTakeoverSettings(): List<SecureSettingOverride> {
    return EdgeSide.entries.map { side ->
        SecureSettingOverride(backGestureInsetSettingKey(side), SECURE_SETTING_DISABLED)
    }
}

internal fun restoreSecureSettingCommand(key: String, originalValue: String?): String {
    require(key in BACK_GESTURE_INSET_SETTING_KEYS) { "Unsupported secure setting: $key" }
    return if (originalValue.isNullOrBlank() || originalValue == "null") {
        "settings delete secure $key"
    } else {
        "settings put secure $key ${shellQuote(originalValue)}"
    }
}

internal fun backGestureInsetSettingKey(side: EdgeSide): String {
    return when (side) {
        EdgeSide.LEFT -> SETTING_BACK_GESTURE_INSET_SCALE_LEFT
        EdgeSide.RIGHT -> SETTING_BACK_GESTURE_INSET_SCALE_RIGHT
    }
}

private fun normalizeSecureSettingValue(output: String): String? {
    return output.trim().takeUnless { it.isEmpty() || it == "null" }
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

private const val ROOT_SHELL_TIMEOUT_MILLIS = 5_000L
private const val ROOT_UID_MARKER = "uid=0"
private const val ANDROID_O_API = 26
private const val ANDROID_Q_API = 29
private const val OVERSCAN_STATE_KEY = "wm_overscan"
private val OVERSCAN_OUTPUT_PATTERN = Regex("-?\\d+\\s*,-?\\d+\\s*,-?\\d+\\s*,-?\\d+")
private val ROOT_UID_PATTERN = Regex("\\buid=0(?:\\b|\\()")

internal const val SETTING_BACK_GESTURE_INSET_SCALE_LEFT = "back_gesture_inset_scale_left"
internal const val SETTING_BACK_GESTURE_INSET_SCALE_RIGHT = "back_gesture_inset_scale_right"
internal const val SECURE_SETTING_DISABLED = "0"
private val BACK_GESTURE_INSET_SETTING_KEYS = setOf(
    SETTING_BACK_GESTURE_INSET_SCALE_LEFT,
    SETTING_BACK_GESTURE_INSET_SCALE_RIGHT
)
