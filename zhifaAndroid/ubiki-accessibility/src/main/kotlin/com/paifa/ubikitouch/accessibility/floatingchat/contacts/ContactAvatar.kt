package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactSummary
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap

@Composable
internal fun ContactAvatar(contact: ContactSummary) {
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = LocalContext.current,
        uriText = contact.avatarUrl?.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(contact.avatarColor?.let(::Color) ?: fallbackAvatarColor(contact.id)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = contact.displayName.take(2),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

private fun fallbackAvatarColor(contactId: String): Color {
    val palette = listOf(
        Color(0xFF5B8EB7),
        Color(0xFF58A36D),
        Color(0xFFB97A56),
        Color(0xFF7B73B7),
        Color(0xFFB75B76)
    )
    return palette[(contactId.hashCode() and Int.MAX_VALUE) % palette.size]
}
