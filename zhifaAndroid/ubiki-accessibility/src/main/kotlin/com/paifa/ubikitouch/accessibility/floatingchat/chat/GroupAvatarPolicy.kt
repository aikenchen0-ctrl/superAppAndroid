package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.accessibility.floatingchat.media.normalizedRemoteImageUri
import com.paifa.ubikitouch.core.model.FloatingChatContact

internal fun groupChatAvatarGridMaxMembers(): Int = 9

internal fun groupChatAvatarGridImageUris(group: FloatingChatContact): List<String> {
    return group.groupMemberAvatarUrls
        .mapNotNull(::normalizedRemoteImageUri)
        .take(groupChatAvatarGridMaxMembers())
}

internal fun groupChatAvatarDisplayImageUris(group: FloatingChatContact): List<String> {
    return groupChatAvatarGridImageUris(group)
        .ifEmpty { listOfNotNull(normalizedRemoteImageUri(group.avatarUrl)) }
}

internal fun groupChatAvatarUsesNineGridMemberImages(): Boolean = true

internal fun groupChatAvatarSingleFallbackImageFillsTile(): Boolean = true

internal fun groupChatMemberAvatarScrollsWithMessageBubble(): Boolean = true

internal fun groupMemberAvatarBubbleCenterOffsetDp(): Int = GROUP_MEMBER_AVATAR_BUBBLE_CENTER_OFFSET_DP

internal fun groupChatConnectorUsesMessageScopedMemberAvatar(): Boolean = true

internal fun groupChatMemberToBubbleConnectorUsesDirectLine(): Boolean = true

internal fun groupChatMemberToBubbleConnectorSkipsTreeBend(): Boolean = true

internal fun groupChatMemberAvatarVisibilityCanBeToggledFromGroupEditPanel(): Boolean = true

internal fun groupChatHiddenMemberAvatarUsesGroupAvatarConnector(): Boolean = true

internal fun groupMemberAvatarSizeDp(): Int = GROUP_MEMBER_AVATAR_SIZE_DP

private const val GROUP_MEMBER_AVATAR_SIZE_DP = 28
private const val GROUP_MEMBER_AVATAR_BUBBLE_CENTER_OFFSET_DP = 4
