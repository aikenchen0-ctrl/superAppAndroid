package com.example.eyeblinkdetect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.min

/**
 * Custom view that draws face landmark points and eye contours over the camera preview.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: FaceLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = true
    private var rotationDegrees: Int = 0

    private val landmarkPaint = Paint().apply {
        color = Color.parseColor("#00FF88")
        strokeWidth = 3f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val eyeOpenPaint = Paint().apply {
        color = Color.parseColor("#00FF88")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val eyeClosedPaint = Paint().apply {
        color = Color.parseColor("#FF4444")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // MediaPipe FaceMesh eye landmark indices
    // Left eye (from front-camera perspective, user's left)
    private val LEFT_EYE_INDICES = intArrayOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
    // Right eye
    private val RIGHT_EYE_INDICES = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)

    fun setResults(
        faceLandmarkerResults: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        isFrontCamera: Boolean = true,
        rotationDegrees: Int = 0
    ) {
        this.results = faceLandmarkerResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        this.rotationDegrees = rotationDegrees
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = results ?: return
        if (result.faceLandmarks().isEmpty()) return

        // When rotation is 90 or 270, the image axes are swapped relative to the view.
        val needsSwap = rotationDegrees == 90 || rotationDegrees == 270
        val imgW = if (needsSwap) imageHeight else imageWidth
        val imgH = if (needsSwap) imageWidth else imageHeight

        val scaleX = width.toFloat() / imgW
        val scaleY = height.toFloat() / imgH
        val scale = min(scaleX, scaleY)
        val offsetX = (width - imgW * scale) / 2f
        val offsetY = (height - imgH * scale) / 2f

        for (face in result.faceLandmarks()) {
            // Draw a subset of face landmarks (sparse)
            for (i in face.indices step 4) {
                val lm = face[i]
                val cx = mapX(lm.x(), lm.y(), needsSwap, scale, offsetX, imgW)
                val cy = mapY(lm.x(), lm.y(), needsSwap, scale, offsetY, imgH)
                canvas.drawCircle(cx, cy, 2f, landmarkPaint)
            }

            // Draw eye contours
            drawEyeContour(canvas, face, LEFT_EYE_INDICES, needsSwap, scale, offsetX, offsetY, imgW, imgH)
            drawEyeContour(canvas, face, RIGHT_EYE_INDICES, needsSwap, scale, offsetX, offsetY, imgW, imgH)
        }
    }

    /**
     * Map normalized landmark X to canvas X, accounting for portrait/landscape and front camera mirror.
     * MediaPipe always gives normalized coords relative to the raw image (before rotation).
     * When needsSwap=true (rotation 90/270), the image's Y axis becomes the view's X axis.
     */
    private fun mapX(nx: Float, ny: Float, needsSwap: Boolean, scale: Float, offsetX: Float, imgW: Int): Float {
        val raw = if (needsSwap) ny * imgW else nx * imgW
        return if (isFrontCamera) width - (raw * scale + offsetX)
        else raw * scale + offsetX
    }

    private fun mapY(nx: Float, ny: Float, needsSwap: Boolean, scale: Float, offsetY: Float, imgH: Int): Float {
        val raw = if (needsSwap) (1f - nx) * imgH else ny * imgH
        return raw * scale + offsetY
    }

    private fun drawEyeContour(
        canvas: Canvas,
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        indices: IntArray,
        needsSwap: Boolean,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        imgW: Int,
        imgH: Int
    ) {
        val paint = eyeOpenPaint
        val pts = indices.map { idx ->
            if (idx < landmarks.size) {
                val lm = landmarks[idx]
                Pair(
                    mapX(lm.x(), lm.y(), needsSwap, scale, offsetX, imgW),
                    mapY(lm.x(), lm.y(), needsSwap, scale, offsetY, imgH)
                )
            } else null
        }.filterNotNull()

        if (pts.size < 2) return
        for (i in pts.indices) {
            val next = pts[(i + 1) % pts.size]
            canvas.drawLine(pts[i].first, pts[i].second, next.first, next.second, paint)
        }
    }
}
