package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayAvatarEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayAvatarProps
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatThemeValues

@Composable
internal fun OverlayAvatar(
    props: OverlayAvatarProps,
    onEvent: (OverlayAvatarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(FloatingChatThemeValues.dimensions.avatarSize)
            .clip(CircleShape)
            .background(Color(props.colorArgb))
            .clickable { onEvent(OverlayAvatarEvent.Clicked(props.id)) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = props.label.take(2),
            color = FloatingChatThemeValues.colors.primaryText,
            style = FloatingChatThemeValues.typography.avatar
        )
    }
}
