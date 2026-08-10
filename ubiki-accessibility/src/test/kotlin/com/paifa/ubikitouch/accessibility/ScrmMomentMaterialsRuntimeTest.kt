package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrmMomentMaterialsRuntimeTest {
    @Test
    fun materialsStartAtFirstPageAndRequestEightyItems() {
        val query = scrmMomentMaterialQuery(
            ScrmFloatingAccountRoute(deviceUuid = "device-a", weChatId = "wxid-a")
        )

        assertEquals(null, query.tenantId)
        assertEquals(0, query.skip)
        assertEquals(80, query.take)
    }

    @Test
    fun createRequestRoutesWechatAccountWithoutPretendingItIsTenantId() {
        val request = scrmMomentMaterialCreateRequest(
            route = ScrmFloatingAccountRoute(deviceUuid = "device-a", weChatId = "wxid-a"),
            content = "素材内容",
            name = "活动素材",
            category = "活动",
            clientRequestId = "request-a"
        )

        assertEquals(null, request.tenantId)
        assertEquals("wxid-a", request.payload?.weChatId)
        assertEquals("request-a", request.clientRequestId)
    }
}
