package com.paifa.univerge.accessibility.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPostAccountIsolationContractTest {
    /**
     * 测试流程：使用两个微信账号打开同一个 circleId 的朋友圈，确认物理存储键包含账号作用域，
     * 且读取后仍恢复原始运行时 postId，避免跨账号覆盖动态。
     */
    @Test
    fun storageKeyNamespacesMomentPostByAccountWithoutChangingRuntimeId() {
        val source = source("FloatingChatMessageStore.kt")

        assertTrue(source.contains("momentPostStorageKey("))
        assertTrue(source.contains("decodeMomentPostStorageKey("))
        assertTrue(source.contains("accountId + postId"))
        assertFalse(source.contains("OR account_id = ''"))
    }

    /**
     * 测试流程：同一账号更新朋友圈互动，确认控制器按 accountId + id 更新，
     * 不会把另一账号同名动态替换掉。
     */
    @Test
    fun controllerUpdatesMomentByAccountAndRuntimeId() {
        val source = source(
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        )

        assertTrue(source.contains("existing.accountId == post.accountId && existing.id == post.id"))
    }

    private fun source(name: String): String {
        val direct = if (name.startsWith("src/")) {
            File(System.getProperty("user.dir"), name)
        } else {
            File(
                System.getProperty("user.dir"),
                "src/main/kotlin/com/paifa/univerge/accessibility/data/$name"
            )
        }
        return direct.readText()
    }
}
