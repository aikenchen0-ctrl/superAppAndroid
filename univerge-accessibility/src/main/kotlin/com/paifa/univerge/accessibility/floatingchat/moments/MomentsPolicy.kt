package com.paifa.univerge.accessibility.floatingchat.moments

import com.paifa.univerge.accessibility.AppMomentComment
import com.paifa.univerge.accessibility.AppMomentPost
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute

internal fun momentsToolOpensInAppTimeline(): Boolean = true

internal fun momentsTimelineBelongsToFloatingChatApp(): Boolean = true

internal fun momentsTimelineSupportsComposePost(): Boolean = true

internal fun momentsTimelineMatchesWechatFeedLayout(): Boolean = true

internal fun momentsTimelineShowsAvatarNameMediaTimeAndMore(): Boolean = true

internal fun momentsTimelineSupportsLikeAndComment(): Boolean = true

internal fun momentsMoreButtonShowsWechatLikeCommentMenu(): Boolean = true

internal fun momentsInlineLikeCommentButtonsAreHiddenUntilMoreMenu(): Boolean = true

internal fun momentsComposerSupportsImageAndVideo(): Boolean = true

internal fun momentsMediaPickDoesNotSendChatMessage(): Boolean = true

internal fun momentsPanelUsesLargerFloatingSheetWithCompactContent(): Boolean = true

internal fun momentsComposedPostsPersistInSqlite(): Boolean = true

internal fun momentsTimelineRestoresPersistedPostsOnOverlayRecreate(): Boolean = true

internal fun momentsTimelineRowsOpenDetail(): Boolean = false

internal fun momentsTimelineMediaOpensFullscreenPreview(): Boolean = true

internal fun momentsTimelineKeepsCurrentPageForTextAndActions(): Boolean = true

internal fun momentMaterialTenantIdForRoute(route: ScrmFloatingAccountRoute?): String? {
    return route?.weChatId?.takeIf { it.isNotBlank() }
}

internal fun momentMaterialsPanelUsesAccountScopedTenant(): Boolean = true

internal fun momentMaterialsPanelUsesQuickPhraseStyleList(): Boolean = true

internal fun momentMaterialsPanelOpensIndependentDetailPage(): Boolean = true

internal fun momentsTimelineReusesChatMediaPreview(): Boolean = true

/**
 * 接口前置策略：与 iOS cannotComment 一致，禁止对当前账号自己的动态或评论继续评论。
 * 测试流程：分别点击自己的动态、自己的评论和他人的评论，确认只有前两项被阻止。
 */
internal fun canCurrentAccountCommentOnMoment(
    post: AppMomentPost,
    currentWeChatId: String?,
    replyTarget: AppMomentComment? = null
): Boolean {
    val ownerId = currentWeChatId?.trim().orEmpty()
    fun isCurrentAccount(wxId: String?): Boolean {
        return !ownerId.isNullOrBlank() && wxId?.trim() == ownerId
    }

    return if (replyTarget == null) {
        !isCurrentAccount(post.authorWxId) && post.author != "我"
    } else {
        !isCurrentAccount(replyTarget.authorWxId) && replyTarget.author != "我"
    }
}
