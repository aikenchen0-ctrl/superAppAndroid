package com.paifa.ubikitouch.accessibility.floatingchat.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.account.railSelectedAvatarHighlightStrokeDp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rootBoundsFromPosition
import com.paifa.ubikitouch.accessibility.floatingchat.media.avatarImageDecodeMaxSizePx
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlin.math.sqrt

private const val DefaultAvatarSizeDp = 42

@Composable
internal fun DraftBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OverlayTokens.aiBadge)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        TextLabel(
            text = "鑽夌",
            size = 8.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.aiText,
            maxLines = 1
        )
    }
}

@Composable
internal fun SquareAvatarChip(
    text: String,
    background: Color,
    sizeDp: Int = 34,
    imageUri: String? = null
) {
    val avatarBitmap = rememberAsyncAvatarBitmap(imageUri)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = text,
                size = if (sizeDp > 30) 10.sp else 8.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

internal enum class AvatarRole {
    Session,
    GroupMember,
    Account
}
internal fun resolvedAvatarImageUri(
    localImageUri: String?,
    remoteAvatarUrl: String?
): String? {
    return localImageUri?.takeIf { uri -> uri.isNotBlank() } ?: remoteAvatarUrl
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CompactAvatar(
    contact: FloatingChatContact,
    role: AvatarRole,
    sizeDp: Int = DefaultAvatarSizeDp,
    imageUri: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit,
    onRemoved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val avatarColor = Color(contact.avatarColor)
    val border = if (contact.selected) OverlayTokens.accent else OverlayTokens.hairline
    val borderWidth = if (contact.selected) railSelectedAvatarHighlightStrokeDp().dp else 1.dp
    val avatarBitmap = rememberAsyncAvatarBitmap(
        resolvedAvatarImageUri(
            localImageUri = imageUri,
            remoteAvatarUrl = contact.avatarUrl
        )
    )
    DisposableEffect(Unit) {
        onDispose { onRemoved() }
    }
    MaterialSurface(
        modifier = Modifier
            .then(modifier)
            .size(sizeDp.dp)
            .then(avatarPressModifier(onClick = onClick, onLongClick = onLongClick))
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    rootBoundsFromPosition(
                        positionInRoot = coordinates.positionInRoot(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                )
        },
        shape = shape,
        color = avatarColor,
        shadowElevation = if (contact.selected) 7.dp else 3.dp,
        border = BorderStroke(borderWidth, border)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val faceColor = Color(0xFFF7D6C1)
            val shoulderColor = Color(0xCCF8FCFF)
            drawCircle(
                color = faceColor,
                radius = size.minDimension * 0.17f,
                center = Offset(size.width * 0.50f, size.height * 0.35f)
            )
            drawRoundRect(
                color = shoulderColor,
                topLeft = Offset(size.width * 0.23f, size.height * 0.57f),
                size = Size(size.width * 0.54f, size.height * 0.26f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawCircle(
                color = Color(0x55000000),
                radius = size.minDimension * 0.19f,
                center = Offset(size.width * 0.50f, size.height * 0.34f),
                style = Stroke(width = 1.2f)
            )
        }
        if (avatarTextTagsVisible()) {
            val nameTagShape = RoundedCornerShape(4.dp)
            Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .clip(nameTagShape)
                .background(OverlayTokens.avatarNameTag)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            TextLabel(
                text = contact.initials,
                size = 7.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            }
        }
        }
        if (avatarTextTagsVisible()) {
            TextLabel(
            text = if (role == AvatarRole.Account) "我" else "",
            size = 8.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.avatarBadgeText,
            textAlign = TextAlign.Center
            )
        }
        if (contact.online) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(OverlayTokens.accent)
            )
        }
    }
    }
}

internal fun avatarTextTagsVisible(): Boolean = false

internal enum class AvatarPressResult {
    None,
    Click,
    LongClick
}

internal fun classifyAvatarPress(
    durationMillis: Long,
    travelDistancePx: Float,
    touchSlopPx: Float,
    longPressTimeoutMillis: Long,
    cancelled: Boolean
): AvatarPressResult {
    if (cancelled || durationMillis < 0 || travelDistancePx > touchSlopPx) {
        return AvatarPressResult.None
    }
    return if (durationMillis >= longPressTimeoutMillis) {
        AvatarPressResult.LongClick
    } else {
        AvatarPressResult.Click
    }
}

@Composable
internal fun avatarPressModifier(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val viewConfiguration = LocalViewConfiguration.current
    return Modifier.pointerInput(
        viewConfiguration.touchSlop,
        viewConfiguration.longPressTimeoutMillis
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var travelDistancePx = 0f
            var upTimeMillis: Long? = null
            var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
            var cancelled = false

            while (upTimeMillis == null && !cancelled) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    cancelled = true
                    continue
                }
                travelDistancePx = maxOf(
                    travelDistancePx,
                    (change.position - down.position).getDistance()
                )
                if (!change.pressed) {
                    upTimeMillis = change.uptimeMillis
                    upChange = change
                }
            }

            when (
                classifyAvatarPress(
                    durationMillis = (upTimeMillis ?: down.uptimeMillis) - down.uptimeMillis,
                    travelDistancePx = travelDistancePx,
                    touchSlopPx = viewConfiguration.touchSlop,
                    longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                    cancelled = cancelled
                )
            ) {
                AvatarPressResult.None -> Unit
                AvatarPressResult.Click -> {
                    upChange?.consume()
                    currentOnClick()
                }
                AvatarPressResult.LongClick -> {
                    upChange?.consume()
                    currentOnLongClick()
                }
            }
        }
    }
}

private fun Offset.getDistance(): Float {
    return sqrt((x * x) + (y * y))
}

private const val AvatarImageCacheNamespace = "avatar"

@Composable
internal fun rememberAsyncAvatarBitmap(imageUri: String?): Bitmap? {
    val context = LocalContext.current
    return rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = imageUri?.takeIf { it.isNotBlank() },
        maxSizePx = avatarImageDecodeMaxSizePx(),
        cacheNamespace = AvatarImageCacheNamespace
    )
}
