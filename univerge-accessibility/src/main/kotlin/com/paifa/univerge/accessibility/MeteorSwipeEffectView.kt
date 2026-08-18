package com.paifa.univerge.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 流星滑动效果的窗口控制器。
 *
 * 控制器负责把全屏的 [MeteorSwipeEffectView] 挂载到无障碍悬浮窗口中，并在手势进行时
 * 复用同一个视图更新绘制状态。手势结束时可以选择短暂淡出，或者在屏幕关闭等生命周期
 * 场景下立即移除窗口。
 */
internal class MeteorSwipeEffectController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    /** 当前挂载到 [WindowManager] 的效果视图；为空表示视图尚未创建或已经回收。 */
    private var effectView: MeteorSwipeEffectView? = null

    /**
     * 更新滑动轨迹的起点、终点和归一化进度。
     *
     * 第一次更新时会创建并挂载全屏效果视图，后续更新直接复用该视图，避免每个手势帧
     * 重复创建窗口。
     */
    fun update(startX: Float, startY: Float, endX: Float, endY: Float, progress: Float) {
        val view = effectView ?: createView() ?: return
        view.update(startX, startY, endX, endY, progress)
    }

    /** 请求效果视图执行短暂的淡出动画，动画结束后自行从窗口管理器移除。 */
    fun dismissAnimated() {
        effectView?.finish()
    }

    /**
     * 立即停止绘制并移除效果窗口。
     *
     * 该方法用于屏幕关闭、服务销毁或手势被取消等不需要等待淡出的场景；移除操作使用
     * [runCatching]，以容忍窗口已经被系统回收的情况。
     */
    fun dismissImmediately() {
        val view = effectView ?: return
        effectView = null
        view.stop()
        runCatching { windowManager.removeViewImmediate(view) }
    }

    /**
     * 创建并挂载一个全屏、不可触摸的无障碍悬浮视图。
     *
     * 窗口添加失败时返回 `null`，同时停止已创建的视图，避免留下一个无法管理的绘制
     * 对象。视图完成淡出后通过回调清空控制器中的引用并移除自身。
     */
    private fun createView(): MeteorSwipeEffectView? {
        val display = context.resources.displayMetrics
        val view = MeteorSwipeEffectView(context) {
            if (effectView === it) effectView = null
            runCatching { windowManager.removeViewImmediate(it) }
        }
        return runCatching {
            windowManager.addView(view, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            })
            effectView = view
            view
        }.onFailure {
            view.stop()
        }.getOrNull()
    }
}

/**
 * 绘制滑动手势流星拖尾的全屏视图。
 *
 * 视图不接收触摸事件，只负责把当前手势的线段、发光圆点和火花绘制到屏幕上。绘制
 * 坐标直接使用屏幕坐标，因此该视图需要以全屏窗口方式挂载。
 */
@SuppressLint("ViewConstructor")
private class MeteorSwipeEffectView(
    context: Context,
    private val onFinished: (MeteorSwipeEffectView) -> Unit
) : View(context) {
    /** 当前设备的密度，用于把视觉尺寸从 dp 换算为像素。 */
    private val density = resources.displayMetrics.density.coerceAtLeast(0.75f)

    /** 绘制流星主拖尾的描边画笔。 */
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** 绘制拖尾末端外层光晕和核心亮点的填充画笔。 */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** 绘制拖尾沿线火花的填充画笔。 */
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** 复用的拖尾路径，避免每一帧创建新的 Path 对象。 */
    private val path = Path()

    /** 当前滑动线段的起点和终点，单位为屏幕像素。 */
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f

    /** 当前滑动相对于触发阈值的进度，范围固定为 0 到 1。 */
    private var progress = 0f

    /** 是否正在执行手势结束后的淡出阶段。 */
    private var finishing = false

    /** 淡出开始时的系统运行时间，用于按帧计算剩余透明度。 */
    private var finishStartedAt = 0L

    init {
        // 该视图只提供视觉反馈，不应出现在无障碍节点树中，也不参与触摸分发。
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * 写入一帧手势状态并请求下一帧重绘。
     *
     * 新的手势进度会取消之前尚未完成的淡出，确保视图可以在同一个窗口中连续复用。
     */
    fun update(nextStartX: Float, nextStartY: Float, nextEndX: Float, nextEndY: Float, nextProgress: Float) {
        startX = nextStartX
        startY = nextStartY
        endX = nextEndX
        endY = nextEndY
        progress = nextProgress.coerceIn(0f, 1f)
        finishing = false
        finishStartedAt = 0L
        postInvalidateOnAnimation()
    }

    /** 标记手势已经结束，并从下一帧开始执行 [FADE_MILLIS] 毫秒的淡出动画。 */
    fun finish() {
        if (finishing) return
        finishing = true
        finishStartedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** 立即停止后续绘制；实际窗口移除由控制器负责。 */
    fun stop() {
        finishing = true
    }

    /**
     * 按当前手势状态绘制流星拖尾。
     *
     * 拖尾长度和亮点大小会随 [progress] 增长，手势结束后通过透明度衰减实现平滑消失。
     * 淡出完成时回调控制器移除窗口；淡出期间持续请求下一帧以维持动画。
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 未结束时保持完全不透明；结束后根据已经经过的时间线性降低透明度。
        val fade = if (!finishing) {
            1f
        } else {
            (1f - (SystemClock.uptimeMillis() - finishStartedAt).toFloat() / FADE_MILLIS).coerceIn(0f, 1f)
        }
        if (fade <= 0f) {
            onFinished(this)
            return
        }

        // 将起点到终点的位移归一化，后续用它计算拖尾反方向的位置。
        val dx = endX - startX
        val dy = endY - startY
        val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val normalizedX = dx / length
        val normalizedY = dy / length
        // 先绘制细而亮的主线，再叠加较粗、较透明的外层光晕，形成发光拖尾。
        trailPaint.color = Color.argb((210f * fade).toInt().coerceIn(0, 255), 196, 240, 255)
        trailPaint.strokeWidth = dp(3f + 3f * progress)
        // Draw only the direct segment between the gesture start and current end.
        path.reset()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        canvas.drawPath(path, trailPaint)

        trailPaint.color = Color.argb((104f * fade).toInt().coerceIn(0, 255), 107, 194, 255)
        trailPaint.strokeWidth = dp(12f + 10f * progress)
        canvas.drawPath(path, trailPaint)

        // 拖尾末端由大范围柔光和小范围白色核心组成，突出当前滑动位置。
        glowPaint.color = Color.argb((170f * fade).toInt().coerceIn(0, 255), 239, 252, 255)
        canvas.drawCircle(endX, endY, dp(9f + 8f * progress), glowPaint)
        glowPaint.color = Color.argb((240f * fade).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawCircle(endX, endY, dp(4f + 4f * progress), glowPaint)

        // 火花使用固定种子计算，保证同一条轨迹在每一帧的位置稳定，不会闪烁跳动。
        drawSparks(canvas, startX, startY, normalizedX, normalizedY, fade)
        if (finishing) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * 在拖尾路径上绘制固定数量的火花点。
     *
     * 通过轨迹方向的垂直向量给每个火花施加横向偏移，并根据其序号递减透明度，形成
     * 有层次的粒子效果。
     */
    private fun drawSparks(canvas: Canvas, lineStartX: Float, lineStartY: Float, directionX: Float, directionY: Float, fade: Float) {
        val perpendicularX = -directionY
        val perpendicularY = directionX
        val seed = ((startX + startY + endX + endY).toInt() and 0x7FFFFFFF)
        for (index in 0 until SPARK_COUNT) {
            val ratio = (index + 1f) / (SPARK_COUNT + 1f)
            val jitter = (((seed + index * 37) % 23) - 11) / 11f
            val x = lineStartX + (endX - lineStartX) * ratio + perpendicularX * jitter * dp(13f)
            val y = lineStartY + (endY - lineStartY) * ratio + perpendicularY * jitter * dp(13f)
            val alpha = ((150f - index * 13f) * fade).toInt().coerceIn(0, 255)
            sparkPaint.color = Color.argb(alpha, 215, 247, 255)
            canvas.drawCircle(x, y, dp(1.5f + (SPARK_COUNT - index) * 0.14f), sparkPaint)
        }
    }

    /** 将设计稿中的 dp 尺寸转换为当前设备上的像素值。 */
    private fun dp(value: Float): Float = value * density

    private companion object {
        /** 手势结束后流星效果的淡出时长。 */
        const val FADE_MILLIS = 190L

        /** 每条拖尾绘制的火花数量。 */
        const val SPARK_COUNT = 9
    }
}
