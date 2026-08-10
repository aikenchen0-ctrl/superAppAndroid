package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatImageActionPill
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.message.fixedThumbnailHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatMessage
@Composable
internal fun InlineImageThumbnailContent(message: FloatingChatMessage) {
    val context = LocalContext.current
    val mediaBitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = message.thumbnailUrl
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedThumbnailHeightDp(message.thumbnailOrientation).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
        ) {
            ImageThumbnailSurface(
                message = message,
                mediaBitmap = mediaBitmap,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FloatingChatImageActionPill("识图")
                FloatingChatImageActionPill("找物")
            }
            TextLabel(
                text = message.text,
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 2,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 44.dp, end = 8.dp, top = 8.dp)
            )
        }
    }
}
