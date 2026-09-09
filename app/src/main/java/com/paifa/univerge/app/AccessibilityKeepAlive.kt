package com.paifa.univerge.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.adbcore.AdbCore
import com.paifa.univerge.accessibility.UniVergeAccessibilityService
import com.paifa.univerge.accessibility.UniVergePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class AccessibilityKeepAliveResult(
    val enabled: Boolean,
    val method: Method,
    val message: String
) {
    enum class Method { ROOT, ADB, NONE }
}

internal fun accessibilityServiceComponent(context: Context): String =
    ComponentName(context, UniVergeAccessibilityService::class.java).flattenToShortString()

internal fun mergeAccessibilityServices(current: String?, service: String): String {
    val entries = current.orEmpty()
        .split(':')
        .map(String::trim)
        .filter(String::isNotBlank)
        .toMutableList()
    if (service !in entries) entries += service
    return entries.joinToString(":") + ":"
}

internal fun buildAccessibilityEnableCommands(
    packageName: String,
    serviceClassName: String,
    currentServices: String?
): List<String> {
    val service = "$packageName/$serviceClassName"
    val merged = mergeAccessibilityServices(currentServices, service)
    return listOf(
        "settings put secure accessibility_enabled 1",
        "settings put secure enabled_accessibility_services $merged"
    )
}

internal interface RootShell {
    suspend fun execute(command: String): Result<String>
}

internal class RuntimeRootShell : RootShell {
    override suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (code != 0) error(error.trim().ifBlank { "su exited with code $code" })
            output.ifBlank { error }
        }
    }
}

internal class AccessibilityKeepAliveController(
    private val context: Context,
    private val preferences: UniVergePreferences,
    private val rootShell: RootShell = RuntimeRootShell()
) {
    suspend fun ensureEnabled(): AccessibilityKeepAliveResult = rootGate.withLock {
        ensureEnabledLocked()
    }

    private suspend fun ensureEnabledLocked(): AccessibilityKeepAliveResult = withContext(Dispatchers.IO) {
        val service = accessibilityServiceComponent(context)
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.trim()?.takeUnless { it.isBlank() || it == "null" }
        if (current.orEmpty().split(':').any { it.equals(service, ignoreCase = true) }) {
            return@withContext AccessibilityKeepAliveResult(true, AccessibilityKeepAliveResult.Method.NONE, "无障碍服务已启用")
        }

        // Once su has been verified in this process, do not execute `su -c id`
        // again. This prevents concurrent startup/toggle calls from re-opening
        // the superuser authorization flow.
        if (rootVerified) {
            val commands = buildAccessibilityEnableCommands(context.packageName, service.substringAfter('/'), current)
            val rootResult = commands.fold(Result.success("")) { _, command -> rootShell.execute(command) }
            if (rootResult.isSuccess) {
                return@withContext AccessibilityKeepAliveResult(true, AccessibilityKeepAliveResult.Method.ROOT, "ROOT 保活已执行")
            }
        }

        val rootIdentity = rootShell.execute("id").getOrNull().orEmpty()
        if (rootIdentity.contains("uid=0")) {
            rootVerified = true
            val rootCurrent = rootShell.execute("settings get secure enabled_accessibility_services")
                .getOrNull()?.trim()?.takeUnless { it == "null" }
            val commands = buildAccessibilityEnableCommands(context.packageName, service.substringAfter('/'), rootCurrent)
            val rootResult = commands.fold(Result.success("")) { _, command -> rootShell.execute(command) }
            if (rootResult.isSuccess) {
                return@withContext AccessibilityKeepAliveResult(true, AccessibilityKeepAliveResult.Method.ROOT, "ROOT 保活已执行")
            }
        }

        if (!AdbCore.isPaired()) {
            return@withContext AccessibilityKeepAliveResult(false, AccessibilityKeepAliveResult.Method.NONE, "未检测到 ROOT，也未完成 ADB 配对")
        }
        val adbCurrent = AdbCore.exec("settings get secure enabled_accessibility_services").getOrNull()?.trim()
        val adbCommands = buildAccessibilityEnableCommands(context.packageName, service.substringAfter('/'), adbCurrent)
        val adbResult = adbCommands.fold(Result.success("")) { _, command -> AdbCore.exec(command) }
        if (adbResult.isSuccess) {
            AccessibilityKeepAliveResult(true, AccessibilityKeepAliveResult.Method.ADB, "ADB 保活已执行")
        } else {
            AccessibilityKeepAliveResult(false, AccessibilityKeepAliveResult.Method.NONE, adbResult.exceptionOrNull()?.message ?: "ADB 命令执行失败")
        }
    }

    private companion object {
        val rootGate = Mutex()
        @Volatile var rootVerified: Boolean = false
    }
}
