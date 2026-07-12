package com.paifa.ubikitouch.app

import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountOption
import com.paifa.ubikitouch.accessibility.scrm.ScrmConnectionTestResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmCapabilityGroupOption
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsSummary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmSettingsPresentationTest {
    @Test
    fun configuredKeyHintShowsOnlyMask() {
        val summary = ScrmSettingsSummary(
            isConfigured = true,
            baseUrl = "https://api.example.com/openapi/v1",
            maskedApiKey = "****1234",
            selectedDeviceUuid = "device-sensitive",
            selectedWeChatId = "wxid_sensitive"
        )

        val hint = scrmApiKeySupportingText(summary)

        assertTrue(hint.contains("****1234"))
        assertFalse(hint.contains("scrm_"))
        assertFalse(summary.toString().contains("wxid_sensitive"))
    }

    @Test
    fun successStatusSummarizesCountsWithoutIdentifiers() {
        val result = ScrmConnectionTestResult.Success(
            userName = "Tester",
            deviceCount = 2,
            onlineDeviceCount = 1,
            accounts = listOf(
                ScrmAccountOption(
                    weChatId = "wxid_sensitive",
                    nickname = "测试账号",
                    deviceUuid = "device-sensitive",
                    isDeviceOnline = true,
                    accountStatus = 1
                )
            ),
            selectedDeviceUuid = "device-sensitive",
            selectedWeChatId = "wxid_sensitive",
            readyCapabilityCount = 8,
            blockedCapabilityCount = 2,
            unknownRuntimeCapabilityCount = 1,
            pausedCapabilityCount = 4,
            capabilityGroups = listOf(
                ScrmCapabilityGroupOption(
                    groupName = "消息",
                    totalCount = 15,
                    readyCount = 8,
                    blockedCount = 2,
                    pausedCount = 4,
                    unknownRuntimeCount = 1
                )
            ),
            warnings = emptyList()
        )

        val text = scrmConnectionStatusText(result)

        assertTrue(text.contains("2 台设备"))
        assertTrue(text.contains("1 台在线"))
        assertTrue(text.contains("1 个微信账号"))
        assertTrue(text.contains("8 项可测试"))
        assertTrue(text.contains("4 项暂停"))
        assertTrue(text.contains("1 项运行时未知"))
        assertFalse(text.contains("wxid_sensitive"))
        assertFalse(text.contains("device-sensitive"))
        assertFalse(result.toString().contains("wxid_sensitive"))
        assertFalse(result.toString().contains("device-sensitive"))
        assertFalse(result.accounts.single().toString().contains("wxid_sensitive"))
        assertFalse(result.accounts.single().toString().contains("device-sensitive"))
    }

    @Test
    fun failureStatusKeepsActionableMessage() {
        assertEquals(
            "API Key 无效或已停用",
            scrmConnectionStatusText(
                ScrmConnectionTestResult.Failure("API Key 无效或已停用")
            )
        )
    }

    @Test
    fun apiKeyInputIsNotWrittenToSavedInstanceState() {
        val source = projectFile(
            "app/src/main/java/com/paifa/ubikitouch/app/ScrmSettingsPanel.kt"
        ).readText()

        assertFalse(source.contains("apiKeyInput by rememberSaveable"))
        assertTrue(source.contains("apiKeyInput by remember"))
    }

    private fun projectFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"))
        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
