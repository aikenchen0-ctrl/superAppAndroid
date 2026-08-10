package com.paifa.ubikitouch.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmNetworkSecurityContractTest {
    @Test
    fun cleartextIsAllowedOnlyForConfiguredScrmHost() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml")
        val manifestDocument = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(manifest)
        val application = manifestDocument.getElementsByTagName("application").item(0)
            as org.w3c.dom.Element

        assertEquals(
            "@xml/network_security_config",
            application.getAttribute("android:networkSecurityConfig")
        )

        val config = projectFile("app/src/main/res/xml/network_security_config.xml")
        val configDocument = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(config)
        val baseConfig = configDocument.getElementsByTagName("base-config").item(0)
            as org.w3c.dom.Element
        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))

        val domains = configDocument.getElementsByTagName("domain")
        assertEquals(1, domains.length)
        val allowedDomain = domains.item(0) as org.w3c.dom.Element
        assertEquals("112.74.164.233", allowedDomain.textContent.trim())
        assertEquals("false", allowedDomain.getAttribute("includeSubdomains"))
        val domainConfig = allowedDomain.parentNode as org.w3c.dom.Element
        assertTrue(domainConfig.getAttribute("cleartextTrafficPermitted") == "true")
    }

    private fun projectFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"))
        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
