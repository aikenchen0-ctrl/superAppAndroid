package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.core.model.FloatingChatContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitBillMemberFilterTest {
    /** 测试流程：SCRM 成员与账号 ID 不同但微信号相同，发起人仍必须从默认全选列表排除。 */
    @Test
    fun currentAccountIsMatchedByWechatIdAcrossScopedIds() {
        val account = contact("account/device/wxid_owner", "online / status 1 / wxid_owner")
        val member = contact("thread/account/contact/wxid_owner", "WeChat group member / wxid_owner")

        assertTrue(splitBillMemberIsCurrentAccount(member, account))
    }

    @Test
    fun anotherGroupMemberIsRetained() {
        val account = contact("account/device/wxid_owner", "online / status 1 / wxid_owner")
        val member = contact("thread/account/contact/wxid_friend", "WeChat group member / wxid_friend")

        assertFalse(splitBillMemberIsCurrentAccount(member, account))
    }

    private fun contact(id: String, description: String) = FloatingChatContact(
        id = id,
        name = id.substringAfterLast('/'),
        initials = "WX",
        description = description,
        avatarColor = 0xFF607D8B
    )
}
