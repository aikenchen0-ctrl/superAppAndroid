package com.paifa.ubikitouch.accessibility.scrm

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal fun scrmReadableText(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return value
    if (!looksLikeScrmMojibake(text)) return text
    val bytes = runCatching { text.toByteArray(LegacyScrmMojibakeCharset) }.getOrNull() ?: return text
    val decoded = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
    return decoded?.takeIf { it.isNotBlank() && it != text } ?: text
}

private fun looksLikeScrmMojibake(value: String): Boolean {
    if (value.any { char -> char == '\uFFFD' || char.code in 0xE000..0xF8FF }) return true
    val markerCount = value.count { char -> ScrmMojibakeMarkers.indexOf(char) >= 0 }
    return markerCount >= 1
}

private val LegacyScrmMojibakeCharset: Charset = Charset.forName("GB18030")
private const val ScrmMojibakeMarkers =
    "\u934A\u9365\u93C0\u93C2\u9365\u9428\u93B4\u9365\u5267\u5896" +
        "\u6D63\u66E1\u5ACD\u7039\u5C7E\u579A\u675E\u5F42\u934F\u62BD" +
        "\u9352\u72BB\u6ACE\u5BB8\u67E5\u832C\u7CA1"
