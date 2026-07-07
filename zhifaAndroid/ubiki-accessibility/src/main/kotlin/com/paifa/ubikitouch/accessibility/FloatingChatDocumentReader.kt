package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.min

internal enum class FloatingChatDocumentReaderKind {
    Pdf,
    PlainText,
    DocxText,
    Spreadsheet,
    ZipArchive,
    Unsupported
}

internal sealed class FloatingChatDocumentReaderContent {
    data object Loading : FloatingChatDocumentReaderContent()
    data class PdfPages(
        val pages: List<Bitmap>,
        val renderedPageCount: Int,
        val totalPageCount: Int
    ) : FloatingChatDocumentReaderContent()
    data class TextLines(
        val lines: List<String>
    ) : FloatingChatDocumentReaderContent()
    data class SpreadsheetRows(
        val rows: List<List<String>>
    ) : FloatingChatDocumentReaderContent()
    data class ZipEntries(
        val entries: List<String>
    ) : FloatingChatDocumentReaderContent()
    data class Unsupported(
        val message: String
    ) : FloatingChatDocumentReaderContent()
    data class Error(
        val message: String
    ) : FloatingChatDocumentReaderContent()
}

internal fun documentReaderKindFor(
    fileName: String?,
    mimeType: String?,
    format: FloatingChatFileFormat?
): FloatingChatDocumentReaderKind {
    val extension = fileName
        ?.substringBefore('?')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        .orEmpty()
    val normalizedMime = mimeType?.lowercase(Locale.US).orEmpty()
    return when {
        format == FloatingChatFileFormat.Pdf ||
            extension == "pdf" ||
            normalizedMime == "application/pdf" -> FloatingChatDocumentReaderKind.Pdf
        format == FloatingChatFileFormat.Txt ||
            format == FloatingChatFileFormat.Markdown ||
            normalizedMime.startsWith("text/") ||
            extension in PlainTextExtensions -> FloatingChatDocumentReaderKind.PlainText
        extension == "docx" ||
            normalizedMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
            FloatingChatDocumentReaderKind.DocxText
        extension in SpreadsheetExtensions ||
            normalizedMime in SpreadsheetMimeTypes -> FloatingChatDocumentReaderKind.Spreadsheet
        extension == "zip" ||
            normalizedMime in ZipMimeTypes -> FloatingChatDocumentReaderKind.ZipArchive
        else -> FloatingChatDocumentReaderKind.Unsupported
    }
}

internal fun documentReaderDefaultsToExternalApp(): Boolean = false

internal fun documentReaderKeepsExternalOpenAsFallback(): Boolean = true

internal fun documentReaderPdfUsesAndroidPdfRenderer(): Boolean = true

internal fun documentReaderDocxExtractsTextInApp(): Boolean = true

internal fun documentReaderZipShowsEntryList(): Boolean = true

internal fun loadBuiltInDocumentReaderContent(
    context: Context,
    message: FloatingChatMessage
): FloatingChatDocumentReaderContent {
    val uriText = message.resourceUrl?.takeIf { it.isNotBlank() }
        ?: return FloatingChatDocumentReaderContent.Unsupported("没有可读取的文件链接")
    val uri = Uri.parse(uriText)
    return when (documentReaderKindFor(message.fileName ?: message.text, message.mediaMimeType, message.fileFormat)) {
        FloatingChatDocumentReaderKind.Pdf -> renderPdfPages(context, uri)
        FloatingChatDocumentReaderKind.PlainText -> readPlainText(context, uri)
        FloatingChatDocumentReaderKind.DocxText -> readDocxText(context, uri)
        FloatingChatDocumentReaderKind.Spreadsheet -> readSpreadsheetRows(context, uri)
        FloatingChatDocumentReaderKind.ZipArchive -> readZipEntries(context, uri)
        FloatingChatDocumentReaderKind.Unsupported -> {
            FloatingChatDocumentReaderContent.Unsupported("当前格式暂不支持内置阅读，可用其他应用打开")
        }
    }
}

private fun renderPdfPages(
    context: Context,
    uri: Uri
): FloatingChatDocumentReaderContent {
    return runCatching {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return FloatingChatDocumentReaderContent.Error("PDF 文件无法读取")
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val totalPages = renderer.pageCount
                val pageCount = min(totalPages, PDF_MAX_RENDERED_PAGES)
                val pages = (0 until pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = (PDF_TARGET_WIDTH_PX.toFloat() / page.width.coerceAtLeast(1))
                            .coerceAtLeast(1f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
                FloatingChatDocumentReaderContent.PdfPages(
                    pages = pages,
                    renderedPageCount = pageCount,
                    totalPageCount = totalPages
                )
            }
        }
    }.getOrElse {
        FloatingChatDocumentReaderContent.Error("PDF 预览失败")
    }
}

private fun readPlainText(
    context: Context,
    uri: Uri
): FloatingChatDocumentReaderContent {
    return runCatching {
        val text = context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return@use ""
            val bytes = input.readBytes().take(TEXT_MAX_BYTES).toByteArray()
            bytes.decodeToString()
        }
        FloatingChatDocumentReaderContent.TextLines(text.toReaderLines())
    }.getOrElse {
        FloatingChatDocumentReaderContent.Error("文本读取失败")
    }
}

private fun readDocxText(
    context: Context,
    uri: Uri
): FloatingChatDocumentReaderContent {
    return runCatching {
        val xml = zipEntryBytes(context, uri, "word/document.xml")
            ?.decodeToString()
            .orEmpty()
        val lines = docxTextLinesFromDocumentXml(xml)
        if (lines.isEmpty()) {
            FloatingChatDocumentReaderContent.Unsupported("DOCX 暂无可抽取文本")
        } else {
            FloatingChatDocumentReaderContent.TextLines(lines)
        }
    }.getOrElse {
        FloatingChatDocumentReaderContent.Error("DOCX 读取失败")
    }
}

private fun readSpreadsheetRows(
    context: Context,
    uri: Uri
): FloatingChatDocumentReaderContent {
    return runCatching {
        val sharedStrings = zipEntryBytes(context, uri, "xl/sharedStrings.xml")
            ?.decodeToString()
            ?.let(::xlsxSharedStrings)
            .orEmpty()
        val sheetXml = zipEntryBytes(context, uri, "xl/worksheets/sheet1.xml")
            ?.decodeToString()
            .orEmpty()
        val rows = xlsxRowsFromSheetXml(sheetXml, sharedStrings)
        if (rows.isEmpty()) {
            FloatingChatDocumentReaderContent.Unsupported("表格暂无可抽取内容")
        } else {
            FloatingChatDocumentReaderContent.SpreadsheetRows(rows)
        }
    }.getOrElse {
        FloatingChatDocumentReaderContent.Error("表格读取失败")
    }
}

private fun readZipEntries(
    context: Context,
    uri: Uri
): FloatingChatDocumentReaderContent {
    return runCatching {
        val entries = context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return@use emptyList<String>()
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .map { entry -> if (entry.isDirectory) "${entry.name}/" else entry.name }
                    .filter { name -> name.isNotBlank() }
                    .take(ZIP_ENTRY_MAX_COUNT)
                    .toList()
            }
        }
        FloatingChatDocumentReaderContent.ZipEntries(entries)
    }.getOrElse {
        FloatingChatDocumentReaderContent.Error("压缩包读取失败")
    }
}

internal fun docxTextLinesFromDocumentXml(xml: String): List<String> {
    if (xml.isBlank()) return emptyList()
    return XmlParagraphRegex.findAll(xml)
        .map { paragraphMatch ->
            val paragraphXml = paragraphMatch.firstNonBlankGroup
            XmlTextRunRegex.findAll(paragraphXml)
                .joinToString("") { textMatch -> textMatch.firstNonBlankGroup.decodeXmlEntities() }
                .trim()
        }
        .filter { line -> line.isNotBlank() }
        .take(TEXT_MAX_LINES)
        .toList()
}

private fun xlsxSharedStrings(xml: String): List<String> {
    if (xml.isBlank()) return emptyList()
    return XmlSharedStringRegex.findAll(xml)
        .map { match ->
            XmlTextRunRegex.findAll(match.firstNonBlankGroup)
                .joinToString("") { textMatch -> textMatch.firstNonBlankGroup.decodeXmlEntities() }
        }
        .toList()
}

private fun xlsxRowsFromSheetXml(
    xml: String,
    sharedStrings: List<String>
): List<List<String>> {
    if (xml.isBlank()) return emptyList()
    return XmlRowRegex.findAll(xml)
        .map { rowMatch ->
            XmlCellRegex.findAll(rowMatch.firstNonBlankGroup)
                .map { cellMatch ->
                    val attrs = cellMatch.groupValues.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?: cellMatch.groupValues.getOrNull(3).orEmpty()
                    val cellXml = cellMatch.groupValues.getOrNull(2)
                        ?.takeIf { it.isNotBlank() }
                        ?: cellMatch.groupValues.getOrNull(4).orEmpty()
                    val value = XmlValueRegex.find(cellXml)
                        ?.firstNonBlankGroup
                        ?.decodeXmlEntities()
                        .orEmpty()
                    if (attrs.contains("t=\"s\"") || attrs.contains("t='s'")) {
                        sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                    } else {
                        value
                    }
                }
                .toList()
        }
        .filter { row -> row.any { cell -> cell.isNotBlank() } }
        .take(SPREADSHEET_MAX_ROWS)
        .map { cells -> cells.take(SPREADSHEET_MAX_COLUMNS) }
        .toList()
}

private fun String.decodeXmlEntities(): String {
    return replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

private val XmlRegexOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
private val XmlParagraphRegex = Regex(
    pattern = """<[\w.-]+:p\b[^>]*>(.*?)</[\w.-]+:p>|<p\b[^>]*>(.*?)</p>""",
    options = XmlRegexOptions
)
private val XmlTextRunRegex = Regex(
    pattern = """<[\w.-]+:t\b[^>]*>(.*?)</[\w.-]+:t>|<t\b[^>]*>(.*?)</t>""",
    options = XmlRegexOptions
)
private val XmlSharedStringRegex = Regex(
    pattern = """<[\w.-]+:si\b[^>]*>(.*?)</[\w.-]+:si>|<si\b[^>]*>(.*?)</si>""",
    options = XmlRegexOptions
)
private val XmlRowRegex = Regex(
    pattern = """<[\w.-]+:row\b[^>]*>(.*?)</[\w.-]+:row>|<row\b[^>]*>(.*?)</row>""",
    options = XmlRegexOptions
)
private val XmlCellRegex = Regex(
    pattern = """<[\w.-]+:c\b([^>]*)>(.*?)</[\w.-]+:c>|<c\b([^>]*)>(.*?)</c>""",
    options = XmlRegexOptions
)
private val XmlValueRegex = Regex(
    pattern = """<[\w.-]+:(?:v|t)\b[^>]*>(.*?)</[\w.-]+:(?:v|t)>|<(?:v|t)\b[^>]*>(.*?)</(?:v|t)>""",
    options = XmlRegexOptions
)

private val MatchResult.firstNonBlankGroup: String
    get() {
        return groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
    }

private fun zipEntryBytes(
    context: Context,
    uri: Uri,
    entryName: String
): ByteArray? {
    return context.contentResolver.openInputStream(uri).use { input ->
        if (input == null) return@use null
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { entry -> entry.name == entryName }
                ?.let { zip.readBytes().take(TEXT_MAX_BYTES).toByteArray() }
        }
    }
}

private fun String.toReaderLines(): List<String> {
    return lineSequence()
        .map { line -> line.trimEnd() }
        .take(TEXT_MAX_LINES)
        .toList()
        .ifEmpty { listOf("(空文件)") }
}

private val PlainTextExtensions = setOf(
    "txt",
    "md",
    "markdown",
    "csv",
    "json",
    "xml",
    "html",
    "htm",
    "log"
)
private val SpreadsheetExtensions = setOf("xls", "xlsx", "csv")
private val SpreadsheetMimeTypes = setOf(
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
)
private val ZipMimeTypes = setOf(
    "application/zip",
    "application/x-zip-compressed"
)

private const val PDF_TARGET_WIDTH_PX = 1080
private const val PDF_MAX_RENDERED_PAGES = 16
private const val TEXT_MAX_BYTES = 512 * 1024
private const val TEXT_MAX_LINES = 1200
private const val ZIP_ENTRY_MAX_COUNT = 200
private const val SPREADSHEET_MAX_ROWS = 120
private const val SPREADSHEET_MAX_COLUMNS = 20
