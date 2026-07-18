package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.FloatingChatMediaTarget
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.moments.toMomentMedia
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedDocumentEvent
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedMediaEvent
import com.paifa.ubikitouch.core.model.FloatingChatContact

@Composable
internal fun PickedMediaEffects(
    pickedMediaEvent: FloatingChatPickedMediaEvent?,
    pickedDocumentEvent: FloatingChatPickedDocumentEvent?,
    pendingAvatarAccountId: String?,
    accountContacts: List<FloatingChatContact>,
    fallbackAccountContacts: List<FloatingChatContact>,
    accountProfile: (FloatingChatContact) -> FloatingChatAccountProfile,
    pickedMediaMessageActions: PickedMediaMessageActions,
    onAccountProfileChanged: (String, FloatingChatAccountProfile) -> Unit,
    onPendingAvatarAccountIdChanged: (String?) -> Unit,
    onPendingMomentMediaChanged: (AppMomentMedia?) -> Unit,
    onBottomPanelModeChanged: (BottomPanelMode) -> Unit,
    onClearPickedMediaEvent: (Long) -> Unit,
    onClearPickedDocumentEvent: (Long) -> Unit
) {
    LaunchedEffect(pickedMediaEvent) {
        val event = pickedMediaEvent ?: return@LaunchedEffect
        if (event.target == FloatingChatMediaTarget.AccountAvatar) {
            val accountId = pendingAvatarAccountId
            if (accountId != null) {
                val account = accountContacts.firstOrNull { it.id == accountId }
                    ?: fallbackAccountContacts.firstOrNull { it.id == accountId }
                if (account != null) {
                    val currentProfile = accountProfile(account)
                    onAccountProfileChanged(
                        accountId,
                        currentProfile.copy(avatarImageUri = event.previewUri.ifBlank { event.mediaUri })
                    )
                }
            }
            onPendingAvatarAccountIdChanged(null)
            onClearPickedMediaEvent(event.token)
            return@LaunchedEffect
        }
        if (event.target == FloatingChatMediaTarget.Moment) {
            onPendingMomentMediaChanged(event.toMomentMedia())
            onBottomPanelModeChanged(BottomPanelMode.Moments)
            onClearPickedMediaEvent(event.token)
            return@LaunchedEffect
        }
        pickedMediaMessageActions.addPickedMediaMessage(event)
        onClearPickedMediaEvent(event.token)
    }
    LaunchedEffect(pickedDocumentEvent) {
        val event = pickedDocumentEvent ?: return@LaunchedEffect
        pickedMediaMessageActions.addPickedDocumentMessage(event)
        onClearPickedDocumentEvent(event.token)
    }
}
