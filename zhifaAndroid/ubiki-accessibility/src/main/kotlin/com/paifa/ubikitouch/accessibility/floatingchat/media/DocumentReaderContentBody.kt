package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.FloatingChatDocumentReaderContent

@Composable
internal fun DocumentReaderContentBody(content: FloatingChatDocumentReaderContent, modifier: Modifier = Modifier) {
    when (content) {
        FloatingChatDocumentReaderContent.Loading -> DocumentReaderStatus("正在读取文件...", modifier)
        is FloatingChatDocumentReaderContent.Error -> DocumentReaderStatus(content.message, modifier)
        is FloatingChatDocumentReaderContent.Unsupported -> DocumentReaderStatus(content.message, modifier)
        is FloatingChatDocumentReaderContent.TextLines -> LazyColumn(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) { itemsIndexed(content.lines) { _, line -> TextLabel(line, 12.sp, color = OverlayTokens.mediaSheetText, lineHeight = 17.sp) } }
        is FloatingChatDocumentReaderContent.ZipEntries -> LazyColumn(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) { itemsIndexed(content.entries) { index, entry -> TextLabel("${index + 1}. $entry", 12.sp, color = OverlayTokens.mediaSheetText, lineHeight = 17.sp, maxLines = 2) } }
        is FloatingChatDocumentReaderContent.SpreadsheetRows -> LazyColumn(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) { itemsIndexed(content.rows) { _, row -> TextLabel(row.joinToString("    "), 11.sp, color = OverlayTokens.mediaSheetText, lineHeight = 16.sp, maxLines = 3) } }
        is FloatingChatDocumentReaderContent.PdfPages -> LazyColumn(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            itemsIndexed(content.pages) { index, bitmap ->
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    TextLabel("第${index + 1} / ${content.totalPageCount} 页", 10.sp, color = OverlayTokens.mediaSheetMutedText, maxLines = 1)
                    Image(bitmap.asImageBitmap(), "PDF 第${index + 1} 页", Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(Color.White).border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(5.dp)), contentScale = ContentScale.FillWidth)
                }
            }
            if (content.renderedPageCount < content.totalPageCount) item { TextLabel("已预览前 ${content.renderedPageCount} 页，更多页面可用其他应用打开。", 11.sp, color = OverlayTokens.mediaSheetMutedText, lineHeight = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}
