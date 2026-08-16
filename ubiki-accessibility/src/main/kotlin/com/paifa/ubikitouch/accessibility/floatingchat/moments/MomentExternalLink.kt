package com.paifa.ubikitouch.accessibility.floatingchat.moments

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.util.Locale

/**
 * 网页链接安全边界：只允许有主机名的 http/https 地址，避免把不可信 scheme 交给外部应用。
 * 测试流程：在朋友圈确认卡点击继续，浏览器可用时跳转；无浏览器或非法地址时保留错误状态，不创建新浮层窗口。
 */
internal fun normalizeMomentExternalLink(rawUrl: String?): String? {
    val value = rawUrl?.trim().orEmpty()
    if (value.isBlank()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return uri.toASCIIString()
}

/**
 * 在已有悬浮聊天宿主中启动系统浏览器，使用 application context + NEW_TASK，避免 BadTokenException。
 */
internal fun launchMomentExternalLink(context: Context, rawUrl: String): Boolean {
    val normalized = normalizeMomentExternalLink(rawUrl) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
