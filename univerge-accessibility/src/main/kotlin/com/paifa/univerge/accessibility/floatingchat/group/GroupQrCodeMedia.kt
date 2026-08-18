package com.paifa.univerge.accessibility.floatingchat.group

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.IOException

private const val GroupQrBitmapSizePx = 720

internal fun createGroupQrCodeBitmap(content: String): Bitmap {
    require(content.isNotBlank()) { "二维码内容不能为空" }
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        GroupQrBitmapSizePx,
        GroupQrBitmapSizePx
    )
    val pixels = IntArray(GroupQrBitmapSizePx * GroupQrBitmapSizePx)
    for (y in 0 until GroupQrBitmapSizePx) {
        for (x in 0 until GroupQrBitmapSizePx) {
            pixels[y * GroupQrBitmapSizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(
        pixels,
        GroupQrBitmapSizePx,
        GroupQrBitmapSizePx,
        Bitmap.Config.ARGB_8888
    )
}

internal fun decodeGroupQrDataImage(dataUrl: String?): Bitmap? {
    val source = dataUrl?.takeIf { it.startsWith("data:image/") } ?: return null
    val separator = source.indexOf(',')
    if (separator < 0 || separator == source.lastIndex) return null
    val bytes = runCatching {
        Base64.decode(source.substring(separator + 1), Base64.DEFAULT)
    }.getOrNull() ?: return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

internal fun saveGroupQrCodeBitmap(
    context: Context,
    bitmap: Bitmap,
    groupName: String
): String {
    val safeGroupName = groupName
        .trim()
        .replace(Regex("[^0-9A-Za-z\\u4e00-\\u9fa5_-]"), "_")
        .take(40)
        .ifBlank { "group" }
    val displayName = "${safeGroupName}_二维码_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UbikiTouch")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("无法创建二维码图片")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("二维码图片编码失败")
            }
        } ?: throw IOException("无法写入二维码图片")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        }
        return displayName
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}
