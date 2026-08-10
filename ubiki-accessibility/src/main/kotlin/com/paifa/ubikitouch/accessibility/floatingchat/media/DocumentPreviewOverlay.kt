package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatDocumentReaderContent
import com.paifa.ubikitouch.accessibility.loadBuiltInDocumentReaderContent
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DocumentPreviewOverlay(
    message: FloatingChatMessage,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val readerContent by produceState<FloatingChatDocumentReaderContent>(
        initialValue = FloatingChatDocumentReaderContent.Loading,
        message.id,
        message.resourceUrl,
        message.mediaMimeType,
        message.fileFormat
    ) {
        value = withContext(Dispatchers.IO) {
            loadBuiltInDocumentReaderContent(context, message)
        }
    }
    Column(
        modifier = modifier
            .background(OverlayTokens.blankScrim)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FileFormatIcon(
                format = message.fileFormat,
                fileName = message.fileName ?: message.text
            )
            Column(modifier = Modifier.weight(1f)) {
                TextLabel(
                    text = message.fileName ?: message.text,
                    size = 14.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.mediaActionIcon,
                    maxLines = 1,
                    lineHeight = 18.sp
                )
                TextLabel(
                    text = listOfNotNull(
                        message.fileSizeLabel?.takeIf { it.isNotBlank() },
                        message.mediaMimeType?.takeIf { it.isNotBlank() }
                    ).joinToString("  "),
                    size = 10.sp,
                    color = OverlayTokens.tertiaryText,
                    maxLines = 1,
                    lineHeight = 13.sp
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = OverlayTokens.mediaActionIcon
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭"
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(OverlayTokens.mediaSheet)
                .border(1.dp, OverlayTokens.mediaSheetBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            DocumentReaderContentBody(
                content = readerContent,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenExternal,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.mediaSheetCancel,
                    contentColor = OverlayTokens.mediaSheetText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextLabel(
                    text = "用其他应用打开",
                    size = 12.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.mediaSheetText,
                    maxLines = 1
                )
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.accent,
                    contentColor = OverlayTokens.mediaActionIconSelected
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                TextLabel(
                    text = "关闭",
                    size = 12.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.mediaActionIconSelected,
                    maxLines = 1
                )
            }
        }
    }
}
