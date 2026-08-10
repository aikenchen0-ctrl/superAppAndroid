package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmApiConfigTest {
    @Test
    fun endpointNormalizesServerRootAndOpenApiBasePath() {
        val key = ScrmApiKey.from("scrm_test_secret_1234")

        assertEquals(
            "http://api.example.com/openapi/v1/me",
            ScrmApiConfig("http://api.example.com/", key).endpoint("/me")
        )
        assertEquals(
            "https://api.example.com/openapi/v1/devices",
            ScrmApiConfig("https://api.example.com/openapi/v1/", key).endpoint("devices")
        )
    }

    @Test
    fun endpointPercentEncodesQueryValuesAndSkipsNullValues() {
        val config = ScrmApiConfig(
            baseUrl = "https://api.example.com",
            apiKey = ScrmApiKey.from("scrm_test_secret_1234")
        )

        assertEquals(
            "https://api.example.com/openapi/v1/quick-start" +
                "?deviceUuid=device%20uuid&scope=p0%2Bmedia&includeBlocked=true",
            config.endpoint(
                path = "quick-start",
                query = linkedMapOf(
                    "deviceUuid" to "device uuid",
                    "weChatId" to null,
                    "scope" to "p0+media",
                    "includeBlocked" to "true"
                )
            )
        )
    }

    @Test
    fun configRejectsUnsupportedOrAmbiguousBaseUrls() {
        val key = ScrmApiKey.from("scrm_test_secret_1234")
        val invalidUrls = listOf(
            "ftp://api.example.com",
            "https://user:password@api.example.com",
            "https://api.example.com?token=secret",
            "https://api.example.com/#fragment"
        )

        invalidUrls.forEach { url ->
            val result = runCatching { ScrmApiConfig(url, key) }
            assertTrue("Expected invalid URL: $url", result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun apiKeyAndConfigStringRepresentationsNeverExposeTheSecret() {
        val rawKey = "scrm_test_secret_1234"
        val key = ScrmApiKey.from(rawKey)
        val config = ScrmApiConfig("https://api.example.com", key)

        assertEquals("****1234", key.masked)
        assertFalse(key.toString().contains(rawKey))
        assertFalse(config.toString().contains(rawKey))
        assertTrue(config.toString().contains("****1234"))
        assertEquals("****", ScrmApiKey.from("abc").masked)
    }

    @Test
    fun requestStringRepresentationRedactsAuthenticationHeader() {
        val request = ScrmHttpRequest(
            method = "GET",
            url = "https://api.example.com/openapi/v1/me",
            headers = mapOf(
                "Accept" to "application/json",
                "X-API-Key" to "scrm_test_secret_1234"
            )
        )

        assertFalse(request.toString().contains("scrm_test_secret_1234"))
        assertTrue(request.toString().contains("X-API-Key=****"))
    }

    @Test
    fun requestStringRepresentationDoesNotExposeIdentifiersFromUrl() {
        val request = ScrmHttpRequest(
            method = "GET",
            url = "https://api.example.com/openapi/v1/capabilities" +
                "?deviceUuid=device-sensitive&weChatId=wxid_sensitive",
            headers = emptyMap(),
            safeRoute = "/openapi/v1/capabilities"
        )

        assertFalse(request.toString().contains("device-sensitive"))
        assertFalse(request.toString().contains("wxid_sensitive"))
        assertTrue(request.toString().contains("route=/openapi/v1/capabilities"))
    }
}
