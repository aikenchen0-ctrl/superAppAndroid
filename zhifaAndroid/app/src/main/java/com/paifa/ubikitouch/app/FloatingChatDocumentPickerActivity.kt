package com.paifa.ubikitouch.app

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.FloatingChatPickedDocument
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FloatingChatDocumentPickerActivity : Activity() {
    private val pickerLifecycle = FloatingChatMediaPickerLifecycle(
        notifyPickerClosed = { FloatingChatMediaPickerBridge.notifyPickerClosed() },
        finishPicker = { finish() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            startActivityForResult(documentPickIntent(), REQUEST_PICK_DOCUMENT)
        }
    }

    @Deprecated("Used for the platform document picker result bridge.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_DOCUMENT && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                grantReadAccess(uri, data.flags)
                pickerLifecycle.startPickedMediaProcessing()
                processPickedDocumentInBackground(uri)
                return
            }
        }
        pickerLifecycle.cancelPicker()
    }

    override fun onDestroy() {
        pickerLifecycle.onDestroy(isChangingConfigurations)
        super.onDestroy()
    }

    private fun documentPickIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, DocumentMimeTypes)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    private fun grantReadAccess(uri: Uri, resultFlags: Int) {
        val takeFlags = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (takeFlags == 0) return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }.onFailure {
            Log.w(TAG, "document uri is not persistable: $uri", it)
        }
    }

    private fun processPickedDocumentInBackground(uri: Uri) {
        documentProcessingExecutor.execute {
            val result = runCatching {
                val meta = queryOpenableDocument(uri)
                val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { mimeTypeFromName(meta.displayName) }
                FloatingChatPickedDocument(
                    uri = uri.toString(),
                    displayName = meta.displayName.ifBlank { fallbackNameFor(uri) },
                    fileFormat = fileFormatFor(meta.displayName, mimeType),
                    fileSizeLabel = meta.sizeBytes?.let(::fileSizeLabel),
                    previewLines = previewLinesFor(uri, meta.displayName, mimeType),
                    mimeType = mimeType
                )
            }.onFailure { error ->
                Log.w(TAG, "failed to process picked floating chat document", error)
            }.getOrElse {
                FloatingChatPickedDocument(
                    uri = uri.toString(),
                    displayName = fallbackNameFor(uri),
                    fileFormat = null,
                    fileSizeLabel = null,
                    previewLines = emptyList(),
                    mimeType = contentResolver.getType(uri)
                )
            }
            mainHandler.post {
                pickerLifecycle.completePickedMediaProcessing {
                    FloatingChatMediaPickerBridge.deliverPickedDocument(result)
                }
            }
        }
    }

    private fun queryOpenableDocument(uri: Uri): DocumentMeta {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                return DocumentMeta(
                    displayName = cursor.stringOrEmpty(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = cursor.longOrNull(OpenableColumns.SIZE)
                )
            }
        }
        return DocumentMeta(displayName = fallbackNameFor(uri), sizeBytes = null)
    }

    private fun Cursor.stringOrEmpty(columnName: String): String {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun fileFormatFor(displayName: String, mimeType: String?): FloatingChatFileFormat? {
        val extension = displayName.extensionLowercase()
        return when {
            extension == "txt" || mimeType == "text/plain" -> FloatingChatFileFormat.Txt
            extension == "md" || extension == "markdown" || mimeType == "text/markdown" -> FloatingChatFileFormat.Markdown
            extension == "doc" || extension == "docx" ||
                mimeType == "application/msword" ||
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FloatingChatFileFormat.Word
            extension == "pdf" || mimeType == "application/pdf" -> FloatingChatFileFormat.Pdf
            else -> null
        }
    }

    private fun mimeTypeFromName(displayName: String): String? {
        return when (displayName.extensionLowercase()) {
            "txt" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            else -> null
        }
    }

    private fun previewLinesFor(uri: Uri, displayName: String, mimeType: String?): List<String> {
        if (!isTextPreviewSupported(displayName, mimeType)) return emptyList()
        return runCatching {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@runCatching emptyList()
                val buffer = ByteArray(TEXT_PREVIEW_MAX_BYTES)
                val readCount = input.read(buffer).coerceAtLeast(0)
                buffer.decodeToString(endIndex = readCount)
                    .lineSequence()
                    .map { line -> line.trimEnd() }
                    .filter { line -> line.isNotBlank() }
                    .take(TEXT_PREVIEW_MAX_LINES)
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun isTextPreviewSupported(displayName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("text/") == true) return true
        return displayName.extensionLowercase() in TextPreviewExtensions
    }

    private fun fallbackNameFor(uri: Uri): String {
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: "未命名文档"
    }

    private fun String.extensionLowercase(): String {
        return substringBefore('?')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
    }

    private data class DocumentMeta(
        val displayName: String,
        val sizeBytes: Long?
    )

    companion object {
        private const val TAG = "FloatingChatDocumentPicker"
        private const val REQUEST_PICK_DOCUMENT = 9003
        private const val TEXT_PREVIEW_MAX_BYTES = 16 * 1024
        private const val TEXT_PREVIEW_MAX_LINES = 4
        private val documentProcessingExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val TextPreviewExtensions = setOf(
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
        private val DocumentMimeTypes = arrayOf(
            "text/*",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.rar",
            "application/x-rar-compressed",
            "application/json",
            "application/xml"
        )
    }
}

private fun fileSizeLabel(sizeBytes: Long): String {
    val safeBytes = sizeBytes.coerceAtLeast(0L)
    val kb = safeBytes / 1024f
    return if (kb < 1024f) {
        String.format(Locale.US, "%.1f KB", kb.coerceAtLeast(0.1f))
    } else {
        String.format(Locale.US, "%.1f MB", kb / 1024f)
    }
}
