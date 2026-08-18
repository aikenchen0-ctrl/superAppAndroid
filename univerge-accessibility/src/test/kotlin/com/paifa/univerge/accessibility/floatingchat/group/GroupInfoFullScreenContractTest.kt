package com.paifa.univerge.accessibility.floatingchat.group

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupInfoFullScreenContractTest {
    @Test
    fun memberPreviewUsesThreeRowsOfFiveWithMoreAndAddAsTheLastCells() {
        val cells = groupInfoMemberPreviewCells(memberCount = 20)

        assertEquals(15, cells.size)
        assertEquals(GroupInfoMemberPreviewCell.More, cells[13])
        assertEquals(GroupInfoMemberPreviewCell.Add, cells[14])
        assertEquals((0 until 13).map(GroupInfoMemberPreviewCell::Member), cells.take(13))
    }

    @Test
    fun memberPreviewKeepsActionsAfterShortMemberLists() {
        val cells = groupInfoMemberPreviewCells(memberCount = 2)

        assertEquals(15, cells.size)
        assertEquals(GroupInfoMemberPreviewCell.Member(0), cells[0])
        assertEquals(GroupInfoMemberPreviewCell.Member(1), cells[1])
        assertEquals(List(11) { GroupInfoMemberPreviewCell.Empty }, cells.subList(2, 13))
        assertEquals(GroupInfoMemberPreviewCell.More, cells[13])
        assertEquals(GroupInfoMemberPreviewCell.Add, cells[14])
    }

    @Test
    fun qrPayloadReadsNestedImageAndContentWithoutInventingFallbackData() {
        val payload = parseGroupQrCodePayload(
            Json.parseToJsonElement(
                """{"data":{"payload":{"qrCodeUrl":"https://example.test/group.png","qrContent":"weixin://group/42"}}}"""
            )
        )

        assertEquals("https://example.test/group.png", payload?.imageUrl)
        assertEquals("weixin://group/42", payload?.content)
        assertNull(parseGroupQrCodePayload(Json.parseToJsonElement("""{"status":"completed"}""")))
    }
}
