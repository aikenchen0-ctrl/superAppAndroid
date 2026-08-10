package com.paifa.ubikitouch.accessibility.floatingchat.contacts

internal fun contactsToolOpensCenteredFloatingPanel(): Boolean = true

internal fun contactPanelMatchesMomentsFloatingSheet(): Boolean = true

internal fun leftRailAvatarsSupportLongPressEditPanel(): Boolean = true

internal fun contactEditPanelSupportsRemarkAndTags(): Boolean = true

internal fun contactEditPanelUsesWechatFriendProfileLayout(): Boolean = true

internal fun contactEditPanelHasDeleteFriendAction(): Boolean = true

internal fun wechatContactIntroFriendProfileReusesLongPressPanel(): Boolean = true

internal fun contactEditPanelWechatSectionTitles(): List<String> = listOf("备注", "朋友权限", "更多信息")

internal fun contactEditPanelWechatFieldLabels(): List<String> = listOf(
    "备注名",
    "电话",
    "标签",
    "备注",
    "照片",
    "朋友圈和状态",
    "仅聊天",
    "我和他的共同群聊",
    "来源",
    "添加时间"
)
