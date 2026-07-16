package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import java.util.Locale

@Composable
internal fun FilePreviewContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = fileWechatCardMinHeightDp().dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.fileWechatCard)
            .border(1.dp, OverlayTokens.fileWechatCardBorder, RoundedCornerShape(7.dp))
            .padding(start = 7.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = message.fileName ?: message.text,
                size = fileWechatTitleTextSizeSp().sp,
                weight = FontWeight.Normal,
                color = OverlayTokens.fileWechatTitle,
                maxLines = 2,
                lineHeight = 13.sp
            )
            TextLabel(
                text = message.fileSizeLabel.orEmpty(),
                size = fileWechatSizeTextSizeSp().sp,
                weight = FontWeight.Normal,
                color = OverlayTokens.fileWechatSize,
                maxLines = 1,
                lineHeight = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        FileFormatIcon(
            format = message.fileFormat,
            fileName = message.fileName ?: message.text
        )
    }
}

@Composable
internal fun FileFormatIcon(
    format: FloatingChatFileFormat?,
    fileName: String? = null
) {
    val label = fileBadgeLabelFor(fileName, format)
    Box(
        modifier = Modifier
            .size(width = fileBadgeWidthDp().dp, height = fileBadgeHeightDp().dp)
            .clip(RoundedCornerShape(3.dp))
            .background(fileBadgeColorFor(fileName, format)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(7.dp)
                .background(Color(0x26000000))
        )
        TextLabel(
            text = label,
            size = if (label.length > 3) 5.sp else 6.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.fileIconText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

internal fun filePreviewUsesWechatDocumentCard(): Boolean = true

internal fun filePreviewOpensFullscreenViewer(): Boolean = true

internal fun filePreviewUsesInlineExpansion(): Boolean = false

internal fun filePreviewCardUsesCombinedClickForPreviewAndLongPress(): Boolean = true

internal fun documentPreviewRunsInsideFloatingOverlay(): Boolean = true

internal fun documentExternalOpenHidesFloatingOverlay(): Boolean = true

internal fun documentExternalOpenRestoresOverlayOnReturn(): Boolean = true

internal fun fileWechatCardMinHeightDp(): Int = 46

internal fun fileWechatTitleTextSizeSp(): Int = 10

internal fun fileWechatSizeTextSizeSp(): Int = 8

internal fun fileWechatCardUsesNormalTextWeight(): Boolean = true

internal fun fileWechatCardUsesTextShadow(): Boolean = false

internal fun fileBadgeWidthDp(): Int = 25

internal fun fileBadgeHeightDp(): Int = 29

internal fun fileBadgeLabelFor(fileName: String?, format: FloatingChatFileFormat?): String {
    return when (format) {
        FloatingChatFileFormat.Txt -> "TXT"
        FloatingChatFileFormat.Markdown -> "MD"
        FloatingChatFileFormat.Word -> "DOCX"
        FloatingChatFileFormat.Pdf -> "PDF"
        null -> fileExtensionLabel(fileName)
    }
}

internal fun fileBadgeColorArgbFor(fileName: String?, format: FloatingChatFileFormat?): Int {
    return fileBadgeColorFor(fileName, format).toArgb()
}

private fun fileExtensionLabel(fileName: String?): String {
    val extension = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.uppercase(Locale.US)
    return when (extension) {
        "DOC", "DOCX" -> "DOCX"
        "PDF" -> "PDF"
        "TXT" -> "TXT"
        "MD", "MARKDOWN" -> "MD"
        "ZIP" -> "ZIP"
        "RAR" -> "RAR"
        else -> extension?.take(4) ?: "FILE"
    }
}

private fun fileBadgeColorFor(fileName: String?, format: FloatingChatFileFormat?): Color {
    return when (fileBadgeLabelFor(fileName, format)) {
        "DOCX" -> Color(0xFF1E88E5)
        "PDF" -> Color(0xFFE44747)
        "TXT" -> Color(0xFF65727A)
        "MD" -> Color(0xFF476B82)
        "ZIP" -> Color(0xFF8A6F2A)
        "RAR" -> Color(0xFF7A5493)
        else -> Color(0xFF65727A)
    }
}
