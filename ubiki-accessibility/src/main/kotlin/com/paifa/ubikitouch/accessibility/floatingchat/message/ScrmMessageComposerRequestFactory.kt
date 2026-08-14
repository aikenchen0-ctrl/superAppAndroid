package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.scrm.ScrmBatchSendMessageByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmSendEmojiRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSendWeAppCardRequest

/** UI adapter only. Callers must not submit the returned write requests automatically. */
internal fun buildScrmEmojiSendRequest(
    route: ScrmFloatingAccountRoute,
    conversationId: String,
    md5: String
): ScrmSendEmojiRequest = ScrmSendEmojiRequest(
    deviceUuid = route.deviceUuid,
    weChatId = route.weChatId,
    conversationId = conversationId,
    md5 = md5
)

/** UI adapter only. Human acceptance is required before calling sendWeAppCard. */
internal fun buildScrmWeAppCardRequest(
    route: ScrmFloatingAccountRoute,
    conversationId: String,
    appId: String,
    title: String,
    pagePath: String,
    url: String,
    thumb: String
): ScrmSendWeAppCardRequest = ScrmSendWeAppCardRequest(
    deviceUuid = route.deviceUuid,
    weChatId = route.weChatId,
    conversationId = conversationId,
    appId = appId,
    title = title,
    pagePath = pagePath,
    url = url,
    thumb = thumb
)

/** The conservative batch-send UI default is one target and filter-based selection. */
internal fun buildScrmBatchTextPreview(
    route: ScrmFloatingAccountRoute,
    content: String,
    labelNames: List<String>
): ScrmBatchSendMessageByFilterRequest = ScrmBatchSendMessageByFilterRequest(
    deviceUuid = route.deviceUuid,
    weChatId = route.weChatId,
    messageType = "text",
    content = content,
    filterLabelNames = labelNames.filter(String::isNotBlank),
    maxCount = 1
)
