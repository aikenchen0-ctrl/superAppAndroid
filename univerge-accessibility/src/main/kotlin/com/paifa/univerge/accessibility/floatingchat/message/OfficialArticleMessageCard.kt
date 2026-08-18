package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap
import com.paifa.univerge.core.model.FloatingChatArticleItem
import com.paifa.univerge.core.model.FloatingChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders one or more official-account articles without exposing raw JSON or URL text. */
@Composable
internal fun OfficialArticleMessageCard(message: FloatingChatMessage) {
    val items = message.articleItems.ifEmpty {
        listOf(
            FloatingChatArticleItem(
                title = message.text,
                description = message.detail.orEmpty(),
                detailUrl = message.resourceUrl,
                bannerImageUrl = message.thumbnailUrl
            )
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 300.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            OfficialArticleLeadItem(
                publisher = message.appName.orEmpty(),
                item = items.first()
            )
            items.drop(1).forEach { item ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OfficialArticleCompactItem(item)
            }
        }
    }
}

@Composable
private fun OfficialArticleLeadItem(
    publisher: String,
    item: FloatingChatArticleItem
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Article,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "公众号",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = publisher.ifBlank { "公众号" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OfficialArticleImage(
            imageUrl = item.bannerImageUrl ?: item.imageUrl,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop,
            preserveIntrinsicAspectRatio = true
        )
        Text(
            text = item.description.ifBlank { item.title },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        formatOfficialArticleTime(item.timestampSeconds)?.let { time ->
            Text(
                text = time,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun OfficialArticleCompactItem(item: FloatingChatArticleItem) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            formatOfficialArticleTime(item.timestampSeconds)?.let { time ->
                Text(
                    text = time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        OfficialArticleImage(
            imageUrl = item.bannerImageUrl ?: item.imageUrl,
            modifier = Modifier.size(56.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun OfficialArticleImage(
    imageUrl: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    preserveIntrinsicAspectRatio: Boolean = false
) {
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = LocalContext.current,
        uriText = imageUrl
    )
    val imageModifier = if (preserveIntrinsicAspectRatio) {
        val ratio = bitmap
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { it.width.toFloat() / it.height.toFloat() }
            ?: (16f / 9f)
        modifier.aspectRatio(ratio)
    } else {
        modifier
    }
    Box(
        modifier = imageModifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

internal fun formatOfficialArticleTime(timestampSeconds: Long?): String? {
    if (timestampSeconds == null || timestampSeconds <= 0L) return null
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(timestampSeconds * 1_000L))
}
