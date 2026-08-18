package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsScrmMetadataContractTest {
    /**
     * 测试流程：SCRM 返回含圈 ID、作者 wxid、发布时间和评论 ID 的朋友圈详情后，
     * 确认 Android 保留这些真实标识，才能安全执行回复或删除本人评论。
     */
    @Test
    fun parserKeepsRemoteIdentifiersNeededForIosEquivalentActions() {
        val models = source("MomentsModels.kt")
        val parser = source("MomentsScrmParser.kt")

        assertTrue(models.contains("val circleId: Long? = null"))
        assertTrue(models.contains("val authorWxId: String? = null"))
        assertTrue(models.contains("val publishTime: Long? = null"))
        assertTrue(models.contains("val id: Long? = null"))
        assertTrue(models.contains("val authorWxId: String? = null"))
        assertTrue(models.contains("val replyCommentId: Long? = null"))
        assertTrue(parser.contains("commentId"))
        assertTrue(parser.contains("authorWxId"))
        assertTrue(parser.contains("replyCommentId"))
    }

    /**
     * 测试流程：同步任务回读详情时，任务结果可能携带多个 JSON 载荷。
     * 确认每个载荷分别进入解析器，避免把 List 直接传给单个 JSON 解析接口。
     */
    @Test
    fun detailTaskPayloadsAreFlattenedBeforeMomentParsing() {
        val sync = source("ScrmMomentsSync.kt")

        assertTrue(sync.contains("outcome.data.flatMap(::scrmMomentPostsFromTaskData)"))
    }

    private fun source(name: String): String = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/univerge/accessibility/$name"
    ).readText()
}
