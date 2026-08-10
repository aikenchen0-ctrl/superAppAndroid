package com.paifa.ubikitouch.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.paifa.ubikitouch.accessibility.UbikiAccessibilityService

class FloatingChatDocumentViewerActivity : Activity() {
    private var externalViewerLaunched = false
    private var overlayRestored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalViewerLaunched = savedInstanceState?.getBoolean(KEY_EXTERNAL_VIEWER_LAUNCHED) == true
    }

    override fun onResume() {
        super.onResume()
        if (!externalViewerLaunched) {
            externalViewerLaunched = true
            openExternalViewer()
        } else {
            restoreOverlayAndFinish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_EXTERNAL_VIEWER_LAUNCHED, externalViewerLaunched)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !overlayRestored) {
            restoreFloatingOverlay()
        }
        super.onDestroy()
    }

    private fun openExternalViewer() {
        val uriText = intent.getStringExtra(EXTRA_EXTERNAL_DOCUMENT_URI).orEmpty()
        if (uriText.isBlank()) {
            Toast.makeText(this, "没有可打开的文件", Toast.LENGTH_SHORT).show()
            restoreOverlayAndFinish()
            return
        }
        val uri = Uri.parse(uriText)
        val mimeType = intent.getStringExtra(EXTRA_EXTERNAL_DOCUMENT_MIME_TYPE)
            ?.takeIf { it.isNotBlank() }
            ?: "*/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uri.scheme == "content" || uri.scheme == "file") {
                setDataAndType(uri, mimeType)
                clipData = ClipData.newUri(contentResolver, "floating-chat-document", uri)
            } else {
                data = uri
            }
        }
        try {
            startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
            restoreOverlayAndFinish()
        } catch (_: SecurityException) {
            Toast.makeText(this, "文件访问权限失效，请重新发送文件", Toast.LENGTH_SHORT).show()
            restoreOverlayAndFinish()
        }
    }

    private fun restoreOverlayAndFinish() {
        restoreFloatingOverlay()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun restoreFloatingOverlay() {
        if (overlayRestored) return
        overlayRestored = true
        UbikiAccessibilityService.instance?.onFloatingChatExternalDocumentClosed()
    }

    companion object {
        private const val KEY_EXTERNAL_VIEWER_LAUNCHED = "external_viewer_launched"
        private const val EXTRA_EXTERNAL_DOCUMENT_URI = "floating_chat_external_document_uri"
        private const val EXTRA_EXTERNAL_DOCUMENT_MIME_TYPE = "floating_chat_external_document_mime_type"
    }
}
