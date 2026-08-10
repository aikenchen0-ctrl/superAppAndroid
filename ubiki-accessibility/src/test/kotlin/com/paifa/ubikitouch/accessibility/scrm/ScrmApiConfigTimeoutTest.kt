package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrmApiConfigTimeoutTest {
    @Test
    fun openApiReadTimeoutAllowsSlowMomentMaterialEndpoint() {
        val config = ScrmApiConfig(
            baseUrl = "http://127.0.0.1:42718",
            apiKey = null
        )

        assertEquals(60_000, config.readTimeoutMillis)
    }
}
