package com.paifa.ubikitouch.accessibility.floatingchat.moments

import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute

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
