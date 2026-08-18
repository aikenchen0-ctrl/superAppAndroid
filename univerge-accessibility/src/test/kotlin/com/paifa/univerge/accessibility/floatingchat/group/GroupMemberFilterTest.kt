package com.paifa.univerge.accessibility.floatingchat.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupMemberFilterTest {
    @Test
    fun parsesTrimmedPositiveLabelIdsAndNames() {
        assertEquals(
            GroupMemberFilter(labelIds = listOf(7, 12), labelNames = listOf("新客户", "已跟进")),
            parseGroupMemberFilter(" 7, 12 ", " 新客户, 已跟进 ")
        )
    }

    @Test
    fun rejectsNonPositiveOrNonNumericLabelIds() {
        assertNull(parseGroupMemberFilter("7,0", "新客户"))
        assertNull(parseGroupMemberFilter("7,abc", "新客户"))
    }
}
