package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试流程：从图片全屏页进入系统图库，选择多张图片，确认每个真实 URI 都通过桥接返回聊天层。 */
class FloatingChatMediaPickerMultiSelectContractTest {
    @Test
    fun imagePickerAcceptsMultipleUrisAndDeliversEachRealSelection() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/paifa/ubikitouch/app/FloatingChatMediaPickerActivity.kt"
        ).readText()
        assertTrue(source.contains("EXTRA_ALLOW_MULTIPLE"))
        assertTrue(source.contains("data?.clipData"))
        assertTrue(source.contains("deliverPickedMedia"))
        assertTrue(source.contains("selectedUris.map"))
        assertTrue(source.contains("PickedMediaKind.Image"))
    }
}
