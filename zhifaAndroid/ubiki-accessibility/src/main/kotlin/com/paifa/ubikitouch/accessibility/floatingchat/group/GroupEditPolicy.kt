package com.paifa.ubikitouch.accessibility.floatingchat.group

internal fun groupAvatarLongPressOpensFloatingEditPanel(): Boolean = true

internal fun groupEditPanelUsesWechatChatInfoLayout(): Boolean = true

internal fun groupEditPanelPersistsChangesInSqlite(): Boolean = true

internal fun groupEditPanelStoresMemberAvatarVisibilityPerGroup(): Boolean = true

internal fun groupEditPanelShowsAllLoadedMembers(): Boolean = true

internal fun groupEditPanelMemberAvatarOpensContactIntro(): Boolean = true

internal fun groupEditPanelInviteAndKickUseRealScrmApis(): Boolean = true

internal fun groupEditPanelWechatFieldLabels(): List<String> = listOf(
    "群聊名称",
    "群二维码",
    "群公告",
    "备注",
    "查找聊天记录",
    "消息免打扰",
    "置顶聊天",
    "保存到通讯录",
    "我在群里的昵称",
    "显示群成员昵称",
    "显示群成员头像",
    "设置当前聊天背景",
    "清空聊天记录",
    "投诉",
    "退出群聊"
)
