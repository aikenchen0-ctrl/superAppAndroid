package com.paifa.univerge.app

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhifa.univerge.eyes.touch.AispectTouchEventType
import com.zhifa.univerge.eyes.touch.AispectTouchClassifier
import com.zhifa.univerge.eyes.touch.AispectTouchError
import com.zhifa.univerge.eyes.touch.AispectTouchModelInfo
import com.zhifa.univerge.eyes.touch.AispectTouchListener
import com.zhifa.univerge.eyes.touch.AispectTouchResult

class TouchTestActivity : ComponentActivity() {
    private lateinit var classifier: AispectTouchClassifier
    private var latestResult by mutableStateOf<AispectTouchResult?>(null)
    private var latestError by mutableStateOf<AispectTouchError?>(null)
    private var isClassifierRunning by mutableStateOf(false)
    private var touchPhase by mutableStateOf("等待触摸")
    private var touchPosition by mutableStateOf("-")
    private var gesturePreview by mutableStateOf("等待触摸")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        classifier = AispectTouchClassifier(applicationContext).also { instance ->
            instance.setListener(object : AispectTouchListener {
                override fun onTouchResult(result: AispectTouchResult) {
                    latestResult = result
                    latestError = null
                }

                override fun onTouchError(error: AispectTouchError) {
                    latestError = error
                }

            })
            instance.start()
            isClassifierRunning = instance.isRunning()
        }
        setContent {
            MaterialTheme {
                TouchTestScreen(
                    classifier = classifier,
                    selectedModel = classifier.selectedModelInfo(),
                    latestResult = latestResult,
                    latestError = latestError,
                    isClassifierRunning = isClassifierRunning,
                    touchPhase = touchPhase,
                    touchPosition = touchPosition,
                    gesturePreview = gesturePreview,
                    onTouchStateChanged = { phase, position ->
                        touchPhase = phase
                        touchPosition = position
                    },
                    onGesturePreviewChanged = { gesturePreview = it },
                    onBack = ::finish
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::classifier.isInitialized && !classifier.isRunning()) {
            classifier.start()
            isClassifierRunning = classifier.isRunning()
        }
    }

    override fun onStop() {
        if (::classifier.isInitialized && classifier.isRunning()) {
            classifier.stop()
            isClassifierRunning = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::classifier.isInitialized) {
            classifier.stop()
            classifier.close()
        }
        super.onDestroy()
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
private fun TouchTestScreen(
    classifier: AispectTouchClassifier,
    selectedModel: AispectTouchModelInfo?,
    latestResult: AispectTouchResult?,
    latestError: AispectTouchError?,
    isClassifierRunning: Boolean,
    touchPhase: String,
    touchPosition: String,
    gesturePreview: String,
    onTouchStateChanged: (String, String) -> Unit,
    onGesturePreviewChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    var touchAreaSize by remember { mutableStateOf(IntSize.Zero) }
    var maximumTouchArea by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("触感测试") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "在上方区域内自由触摸，用于检测当前触摸模型。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { touchAreaSize = it }
                    .background(
                        MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)
                    )
                    .pointerInteropFilter { event: MotionEvent ->
                        val phase = when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> "正在触摸"
                            MotionEvent.ACTION_MOVE -> "触摸移动中"
                            MotionEvent.ACTION_UP -> "触摸结束，等待分类"
                            MotionEvent.ACTION_CANCEL -> "触摸已取消"
                            else -> "触摸处理中"
                        }
                        onTouchStateChanged(
                            phase,
                            "x=${event.x.toInt()}, y=${event.y.toInt()}, size=${"%.3f".format(event.size)}, " + "touchMajor=${
                                "%.1f".format(event.getTouchMajor())
                            }, " + "touchMinor=${"%.1f".format(event.getTouchMinor())}, " + "area=${
                                "%.1f".format(
                                    event.getTouchMajor() * event.getTouchMinor()
                                )
                            }"
                        )
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                maximumTouchArea = event.getTouchMajor() * event.getTouchMinor()
                                onGesturePreviewChanged("触摸中")
                            }

                            MotionEvent.ACTION_MOVE -> {
                                maximumTouchArea = maxOf(
                                    maximumTouchArea, event.getTouchMajor() * event.getTouchMinor()
                                )
                            }

                            MotionEvent.ACTION_UP -> {
                                maximumTouchArea = maxOf(
                                    maximumTouchArea, event.getTouchMajor() * event.getTouchMinor()
                                )
                                onGesturePreviewChanged(
                                    if (maximumTouchArea > 40f || isHeavyTouch(event)) "重触" else "轻触"
                                )
                            }

                            MotionEvent.ACTION_CANCEL -> onGesturePreviewChanged("已取消")
                        }
                        classifier.handleMotionEvent(
                            event, touchAreaSize.width, touchAreaSize.height
                        )
                        true
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("触摸这里开始测试", style = MaterialTheme.typography.titleLarge)
            }
            TouchStatusPanel(
                selectedModel,
                latestResult,
                latestError,
                isClassifierRunning,
                touchPhase,
                touchPosition,
                gesturePreview
            )
        }
    }
}

private fun isHeavyTouch(event: MotionEvent): Boolean {
    val major = event.getTouchMajor()
    val minor = event.getTouchMinor()
    val area = major * minor
    if (area > 40f || event.size >= 0.026f) {
        return true
    }
    // Some devices report a constant normalized pressure of 1.0. Only use
    // pressure when contact geometry is unavailable and the value exceeds it.
    return major <= 0f && minor <= 0f && event.pressure >= 1.2f
}

@Composable
private fun TouchStatusPanel(
    selectedModel: AispectTouchModelInfo?,
    latestResult: AispectTouchResult?,
    latestError: AispectTouchError?,
    isClassifierRunning: Boolean,
    touchPhase: String,
    touchPosition: String,
    gesturePreview: String
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("触摸信息", style = MaterialTheme.typography.titleMedium)
        Text("$touchPhase  $touchPosition", color = MaterialTheme.colorScheme.primary)
        Text(
            text = when {
                latestError != null -> "错误：${latestError.code}"
                latestResult == null -> "尚未收到触摸结果"
                else -> latestResult.toDisplayText()
            },
            color = if (latestError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        Text(
            text = when {
                latestError != null -> "识别结果：未完成"
                latestResult == null -> "识别结果：$gesturePreview"
                else -> "识别结果：${latestResult.toClassificationDescription()}"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (latestError == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        FourClassResultPanel(latestResult)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("当前触摸模型", style = MaterialTheme.typography.titleMedium)
        Text(
            selectedModel?.toDisplayText() ?: "未加载触摸模型",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (isClassifierRunning) "分类器运行中" else "分类器未运行",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private val fourClassLabels = listOf(
    "thumb_light", "thumb_heavy", "index_light", "index_heavy"
)

private fun fourClassDescription(label: String): String = when (label) {
    "thumb_light" -> "拇指轻触"
    "thumb_heavy" -> "拇指重触"
    "index_light" -> "食指轻触"
    "index_heavy" -> "食指重触"
    else -> label.ifBlank { "未知类别" }
}

@Composable
private fun FourClassResultPanel(result: AispectTouchResult?) {
    Text("四分类模型反馈", style = MaterialTheme.typography.titleMedium)
    if (result == null) {
        Text("等待模型输出四分类结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        "${fourClassDescription(result.classLabel)}  confidence=${"%.2f".format(result.confidence)}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    val probabilitiesByLabel = result.labelOrder.mapIndexed { index, label ->
        label to result.probabilities.getOrNull(index).orZero()
    }.toMap()
    fourClassLabels.forEach { label ->
        val probability = probabilitiesByLabel[label]
        Text("${fourClassDescription(label)}（$label）：${probability?.let { "%.2f".format(it) } ?: "未提供"}",
            color = if (label == result.classLabel) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            })
    }
}

private fun Double?.orZero(): Double = this ?: 0.0

private fun AispectTouchResult.toDisplayText(): String =
    "${eventType} / ${classLabel.ifBlank { "UNKNOWN" }}  " + "confidence=${"%.2f".format(confidence)}  x=${
        "%.0f".format(x)
    }, y=${"%.0f".format(y)}"

private fun AispectTouchResult.toGestureLabel(): String = when (eventType) {
    AispectTouchEventType.TAP -> if (strength.name == "HEAVY") "重触" else "轻触"

    AispectTouchEventType.PRESS -> if (strength.name == "HEAVY") "重按压" else "按压"

    AispectTouchEventType.HOLD -> "长按"
    AispectTouchEventType.DRAG -> if (strength.name == "HEAVY") "重触" else "轻触"
    AispectTouchEventType.CANCEL -> "已取消"
    else -> "未知手势"
}

private fun AispectTouchResult.toClassificationDescription(): String =
    if (classLabel in fourClassLabels) fourClassDescription(classLabel) else toGestureLabel()

private fun AispectTouchModelInfo.toDisplayText(): String =
    "${displayName.ifBlank { id }}  $id v${version.ifBlank { "-" }}\n${inputChannels}通道 / ${frameCount}帧 / $windowMode"
