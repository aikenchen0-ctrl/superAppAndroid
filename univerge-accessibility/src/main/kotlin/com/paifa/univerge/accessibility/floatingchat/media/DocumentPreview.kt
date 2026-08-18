package com.paifa.univerge.accessibility.floatingchat.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel
import com.paifa.univerge.core.model.FloatingChatFileFormat
import com.paifa.univerge.core.model.FloatingChatMessage
import java.util.Locale

@Composable
internal fun FilePreviewContent(message: FloatingChatMessage) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = fileWechatCardMinHeightDp().dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileFormatIcon(
                format = message.fileFormat,
                fileName = fileDisplayName(message),
                thumbnailUrl = message.thumbnailUrl ?: message.resourceUrl
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                TextLabel(
                    text = fileDisplayName(message),
                    size = fileWechatTitleTextSizeSp().sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.fileWechatTitle,
                    maxLines = 2,
                    lineHeight = 13.sp
                )
                message.fileSizeLabel?.takeIf { it.isNotBlank() }?.let { sizeLabel ->
                    TextLabel(
                        text = sizeLabel,
                        size = fileWechatSizeTextSizeSp().sp,
                        weight = FontWeight.Normal,
                        color = OverlayTokens.fileWechatSize,
                        maxLines = 1,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}

internal fun fileDisplayName(message: FloatingChatMessage): String {
    return listOf(message.fileName, message.text, message.detail)
        .filterNotNull()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "文件"
}

@Composable
internal fun FileFormatIcon(
    format: FloatingChatFileFormat?,
    fileName: String? = null,
    thumbnailUrl: String? = null
) {
    val label = fileBadgeLabelFor(fileName, format)
    val iconKind = filePreviewIconKindFor(fileName, format)
    val thumbnail = if (iconKind == FilePreviewIconKind.Image) {
        rememberAsyncImageThumbnailBitmap(
            context = androidx.compose.ui.platform.LocalContext.current,
            uriText = thumbnailUrl
        )
    } else {
        null
    }
    Box(
        modifier = Modifier
            .size(width = fileBadgeWidthDp().dp, height = fileBadgeHeightDp().dp)
            .clip(RoundedCornerShape(3.dp))
            .background(fileBadgeColorFor(fileName, format)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "图片文件缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = filePreviewIconFor(iconKind),
                contentDescription = label,
                tint = OverlayTokens.fileIconText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal enum class FilePreviewIconKind {
    Android,
    Spreadsheet,
    Document,
    Archive,
    Audio,
    Video,
    Image,
    Presentation,
    Pdf,
    Generic
}

internal fun filePreviewIconKindFor(
    fileName: String?,
    format: FloatingChatFileFormat?
): FilePreviewIconKind {
    val extension = fileName
        ?.substringBefore('?')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        .orEmpty()
    return when {
        extension in setOf("apk", "apk1", "aar") -> FilePreviewIconKind.Android
        extension in setOf("xls", "xlsx") -> FilePreviewIconKind.Spreadsheet
        extension in setOf("doc", "docx", "docs", "txt", "md", "markdown") -> FilePreviewIconKind.Document
        extension in setOf("zip", "7z", "rar", "arr") || format == FloatingChatFileFormat.Zip -> FilePreviewIconKind.Archive
        extension in setOf("mp3", "wav", "aac", "flac") -> FilePreviewIconKind.Audio
        extension in setOf("mp4", "mkv", "avi", "mov") -> FilePreviewIconKind.Video
        extension in setOf("jpg", "jpeg", "jepg", "png", "gif", "webp", "bmp") -> FilePreviewIconKind.Image
        extension in setOf("ppt", "pptx") -> FilePreviewIconKind.Presentation
        extension == "pdf" || format == FloatingChatFileFormat.Pdf -> FilePreviewIconKind.Pdf
        format == FloatingChatFileFormat.Word || format == FloatingChatFileFormat.Markdown || format == FloatingChatFileFormat.Txt -> FilePreviewIconKind.Document
        else -> FilePreviewIconKind.Generic
    }
}

private fun filePreviewIconFor(kind: FilePreviewIconKind) = when (kind) {
    FilePreviewIconKind.Android -> Icons.Filled.PhoneAndroid
    FilePreviewIconKind.Spreadsheet -> Icons.Filled.GridOn
    FilePreviewIconKind.Document -> Icons.Filled.Description
    FilePreviewIconKind.Archive -> Icons.Filled.Archive
    FilePreviewIconKind.Audio -> Icons.Filled.Audiotrack
    FilePreviewIconKind.Video -> Icons.Filled.VideoLibrary
    FilePreviewIconKind.Image -> Icons.Filled.ImageIcon
    FilePreviewIconKind.Presentation -> Icons.Filled.Slideshow
    FilePreviewIconKind.Pdf -> Icons.Filled.PictureAsPdf
    FilePreviewIconKind.Generic -> Icons.Filled.InsertDriveFile
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
        FloatingChatFileFormat.Zip -> "ZIP"
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
