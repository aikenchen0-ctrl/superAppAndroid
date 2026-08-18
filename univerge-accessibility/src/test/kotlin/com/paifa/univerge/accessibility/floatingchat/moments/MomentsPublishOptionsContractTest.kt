package com.paifa.univerge.accessibility.floatingchat.moments

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 朋友圈发布契约回归：可见范围、受众、提醒和位置必须穿过 Android 的真实请求链路。
 * 测试流程：打开朋友圈 -> 发表 -> 选择可见范围/好友/提醒并填写位置 -> 提交，检查请求模型和 UI 传参。
 */
class MomentsPublishOptionsContractTest {
    @Test
    fun requestModelCarriesIosPublishOptions() {
        val source = source("scrm/ScrmModels.kt")

        assertTrue(source.contains("val visibleType: String? = null"))
        assertTrue(source.contains("val friendWxids: List<String>? = null"))
        assertTrue(source.contains("val invisibleFriendWxids: List<String>? = null"))
        assertTrue(source.contains("val notiUsers: List<String>? = null"))
        assertTrue(source.contains("data class ScrmMomentPoi"))
    }

    @Test
    fun publishWorkspacePassesOptionsToRealScrmRequest() {
        val syncSource = source("ScrmMomentsSync.kt")

        assertTrue(syncSource.contains("options: ScrmMomentPublishOptions"))
        assertTrue(syncSource.contains("visibleType = options.visibility.apiValue"))
        assertTrue(syncSource.contains("friendWxids = if (options.visibility"))
        assertTrue(syncSource.contains("notiUsers = remindWxids"))
    }

    @Test
    fun publishWorkspaceMatchesIosSlowDispatchFlags() {
        val syncSource = source("ScrmMomentsSync.kt")

        assertTrue(syncSource.contains("attachments = uploadedMedia?.url?.let(::listOf),\n                sendSlow = true,"))
        assertTrue(syncSource.contains("attachment = uploadedMedia?.let { ScrmMomentPostAttachment(it.attachmentTypeCode, listOf(it.url)) },\n                    sendSlow = true,"))
    }

    private fun source(relativePath: String): String = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
    ).canonicalFile.readText()
}
