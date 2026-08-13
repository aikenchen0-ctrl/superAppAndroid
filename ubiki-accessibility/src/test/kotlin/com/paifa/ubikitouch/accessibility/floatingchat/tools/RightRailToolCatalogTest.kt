package com.paifa.ubikitouch.accessibility.floatingchat.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class RightRailToolCatalogTest {
    @Test
    fun catalogPreservesTheRequestedToolOrder() {
        assertEquals(
            listOf(
                "群信息", "眨眼测试", "语音助手", "遥感套索", "智能抠图", "UI组件", "OpenAI", "侧边特效",
                "账号设备", "好友管理", "同步群聊", "侧边特效", "3D气泡", "AI自动回复", "朋友圈", "素材库",
                "微信小程序", "视屏号发布", "客户档案", "通讯录", "申请审核", "隐藏用户", "左侧全部", "携带名字",
                "快捷语", "拍摄照片", "图片", "视频", "视频号视频", "视频号直播", "位置信息", "实时位置",
                "推名片", "群邀请卡", "网页链接", "公众号文章", "小程序卡片", "音乐分享", "收藏分享", "多选",
                "接龙消息", "文件/文档", "语音消息", "语音通话", "视频通话", "红包", "转账", "AA收款", "微信卡券"
            ),
            rightRailToolCatalog.map { item -> item.label }
        )
    }

    @Test
    fun toolButtonsMatchTheAccountAvatarSize() {
        assertEquals(rightRailAvatarSizeDp(), rightRailToolButtonWidthDp())
        assertEquals(44, rightRailToolButtonHeightDp())
    }

    @Test
    fun backgroundRemovalToolUsesItsDedicatedExternalActivityAction() {
        val tool = rightRailToolCatalog.first { item -> item.label == "智能抠图" }

        assertEquals(true, tool.opensBackgroundRemoval)
    }

    @Test
    fun friendManagementToolUsesItsDedicatedExternalActivityAction() {
        val tool = rightRailToolCatalog.first { item -> item.label == "好友管理" }

        assertEquals(true, tool.opensFriendManagement)
    }
}
