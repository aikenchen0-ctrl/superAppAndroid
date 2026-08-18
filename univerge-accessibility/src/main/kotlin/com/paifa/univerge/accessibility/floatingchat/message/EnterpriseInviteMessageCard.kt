package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel

/** Material 3 card for a WeCom enterprise invitation message. */
@Composable
internal fun EnterpriseInviteMessageCard(message: com.paifa.univerge.core.model.FloatingChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Business,
                contentDescription = "企业微信邀请",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                TextLabel(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    size = 14.sp,
                    maxLines = 2
                )
                message.detail?.takeIf { it.isNotBlank() }?.let { description ->
                    TextLabel(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 12.sp,
                        maxLines = 3,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
