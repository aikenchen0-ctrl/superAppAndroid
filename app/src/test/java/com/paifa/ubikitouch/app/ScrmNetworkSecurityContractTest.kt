package com.paifa.ubikitouch.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmNetworkSecurityContractTest {
    @Test
    fun cleartextIsAllowedOnlyForConfiguredScrmAndWeChatStickerHosts() {
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
        assertEquals(2, domains.length)
        val allowedHosts = (0 until domains.length)
            .map { domains.item(it) as org.w3c.dom.Element }
        assertEquals(
            setOf("112.74.164.233", "vweixinf.tc.qq.com"),
            allowedHosts.map { it.textContent.trim() }.toSet()
        )
        allowedHosts.forEach { domain ->
            assertEquals("false", domain.getAttribute("includeSubdomains"))
            val domainConfig = domain.parentNode as org.w3c.dom.Element
            assertTrue(domainConfig.getAttribute("cleartextTrafficPermitted") == "true")
        }
    }

    /**
     * 测试流程：同步一条 messageType=47 的历史表情消息，确认其 Thumb 下载地址可以被
     * Android 图片加载器访问。该腾讯表情下载域名当前仅提供 HTTP，不能强制升级为 HTTPS。
     */
    @Test
    fun weChatStickerThumbnailHostAllowsRequiredCleartextTraffic() {
        val config = projectFile("app/src/main/res/xml/network_security_config.xml")
        val configDocument = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(config)

        val domains = configDocument.getElementsByTagName("domain")
        val stickerDomain = (0 until domains.length)
            .map { domains.item(it) as org.w3c.dom.Element }
            .firstOrNull { it.textContent.trim() == "vweixinf.tc.qq.com" }

        assertTrue("Missing cleartext policy for WeChat sticker Thumb host", stickerDomain != null)
        assertEquals("false", stickerDomain?.getAttribute("includeSubdomains"))
        assertEquals("true", (stickerDomain?.parentNode as? org.w3c.dom.Element)
            ?.getAttribute("cleartextTrafficPermitted"))
    }

    private fun projectFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"))
        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
