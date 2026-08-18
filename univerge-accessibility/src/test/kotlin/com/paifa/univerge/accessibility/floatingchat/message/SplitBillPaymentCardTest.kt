package com.paifa.univerge.accessibility.floatingchat.message

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitBillPaymentCardTest {
    /** 测试流程：打开 AA 消息卡，金额应从 iOS detail 中提取，进度未完成时显示参与提示。 */
    @Test
    fun splitBillCardParsesAmountAndPendingProgress() {
        val detail = "群收款总额：¥320.00\n已收 0/2 人"

        assertEquals("¥320.00", splitBillAmountTextFor(detail))
        assertEquals("请参与收款", splitBillStatusTextFor(detail))
    }

    /** 测试流程：已收人数等于总人数时，AA 卡片状态显示已收齐。 */
    @Test
    fun splitBillCardShowsCompletedState() {
        assertEquals("已收齐", splitBillStatusTextFor("已收 2/2 人"))
    }
}
