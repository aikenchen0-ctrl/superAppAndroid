# 触摸 CNN 模型四分类

## 1. 文档范围

本文说明 `univerge-touch` 模块中触摸 CNN 四分类模型的作用、模型加载方式、触摸输入采集、推理触发条件，以及最终输出数据类型。

主要实现位于：

- `univerge-touch/src/main/java/com/zhifaios/eyes/touch/AispectTouchClassifier.java`
- `univerge-touch/src/main/java/com/zhifaios/eyes/aispect/AispectCollectionController.java`
- `univerge-touch/src/main/java/com/zhifaios/eyes/aispect/AispectImpactCNNClassifier.java`
- `univerge-touch/src/main/java/com/zhifaios/eyes/touch/AispectTouchResult.java`

## 2. 模型作用

模型用于根据一次手指触摸期间采集的触摸轨迹、触摸接触面积和手机运动传感器数据，判断两种属性：

1. 手指类型：拇指（`thumb`）或食指（`index`）。
2. 触摸力度：轻触（`light`）或重触（`heavy`）。

因此模型输出四个互斥类别：

```text
thumb_light   拇指轻触
thumb_heavy   拇指重触
index_light   食指轻触
index_heavy   食指重触
```

标准类别顺序由模型的 `labelOrder` 给出，当前内置因果模型为：

```text
["thumb_light", "thumb_heavy", "index_light", "index_heavy"]
```

必须使用 `labelOrder[i]` 解释 `probabilities[i]`，不能只根据数组下标猜类别。

## 3. 模型文件和加载

内置模型位于 `src/main/assets/aispect_impact_models`，由模型目录、权重 JSON 和归一化参数 JSON 共同组成。

当前目录包含：

| 模型 | 主要特征 | 帧数 | 类别数 |
| --- | --- | ---: | ---: |
| `lemon_tao_full4_enhanced_patch21_no_confidence` | IMU + 接触区域特征 | 15 | 4 |
| `causal_touch_relative_v1_all_devices_fit_20260904` | IMU + 触摸相对特征 | 9 | 4 |

调用 `AispectTouchClassifier.start()` 后，SDK 会启动识别并异步预加载当前选中的模型。模型选择可能来自已验证的远程模型分配；没有可用远程模型时使用内置模型。

普通模型运行手写的 CNN 前向计算：

```text
Conv1D -> BatchNorm -> ReLU -> Pool
       -> Conv1D -> BatchNorm -> ReLU -> Pool
       -> 全连接层 -> Softmax
```

因果时间网格模型使用 `AispectTimeGridGroupNormRuntime`，在固定的硬件时间网格上推理。当前因果模型的时间点为 `-15、-10、-5、0、+5、+10、+15、+20、+25 ms`。

## 4. 如何触发识别

### 4.1 初始化和启动

```java
AispectTouchClassifier classifier =
        new AispectTouchClassifier(context);

classifier.setListener(new AispectTouchListener() {
    @Override
    public void onTouchResult(AispectTouchResult result) {
        // 处理四分类结果
    }

    @Override
    public void onTouchError(AispectTouchError error) {
        // 处理传感器、模型或输入错误
    }
});

classifier.start();
```

`start()` 只负责开启识别和模型预加载，不会立即产生分类结果。

### 4.2 转发 MotionEvent

宿主 View 的每个触摸事件都必须转发：

```java
@Override
public boolean onTouchEvent(MotionEvent event) {
    classifier.handleMotionEvent(event, getWidth(), getHeight());
    return true;
}
```

输入链路如下：

```text
MotionEvent
  -> AispectTouchFrame（坐标、压力、尺寸、时间等）
  -> IMU ImpactFrame（加速度、旋转速率、重力等）
  -> SignalWindow
  -> CNN 推理
  -> ImpactPrediction
  -> AispectTouchResult
```

`MotionEvent` 的历史点和当前点都会采集。触摸帧包含：

- `x`、`y`、`xNorm`、`yNorm`
- `pressure`、`size`
- `touchMajor`、`touchMinor`
- `toolMajor`、`toolMinor`
- `orientation`
- 事件时间、传感器时间和接收时间
- 指针数量、工具类型、字段可用性标记

多指输入会被输入质量检查拒绝；明显拖动或取消事件也不会作为有效的按压力度样本输出。

## 5. 触摸事件和推理时机

交互引擎首先将原始触摸动作转换为 `TouchKind`：

```text
ACTION_DOWN       -> SAMPLE，并开始跟踪
ACTION_MOVE       -> SAMPLE；移动距离超过阈值后产生 DRAG_START/DRAG
ACTION_UP（非拖动） -> LIGHT_TAP 或 HEAVY_TAP
ACTION_UP（拖动）   -> DRAG_END
ACTION_CANCEL     -> CANCEL
按住超过 holdDurationMs -> LIGHT_HOLD
```

### 5.1 因果模型触发

当当前模型的 `windowMode=press` 且 `captureDelayMs>0` 时，按下事件会启动因果预测定时任务。

触发条件：

1. 已收到 `ACTION_DOWN`。
2. 当前模型是受支持的因果特征契约。
3. 传感器数据覆盖完整时间网格。
4. 手指没有在捕获终点之前抬起。

当前模型通常等待到 `ACTION_DOWN` 后 `+25 ms`，然后输出一次 `PRESS` 结果。若手指在 `+25 ms` 前抬起，会产生 `collector_causal_window_rejected`，不会有分类结果。

### 5.2 释放模型触发

对于普通 `release` 模型，`ACTION_UP` 后 SDK 会等待必要的传感器后滚窗口，构建完整 `SignalWindow`，再执行 CNN 推理。默认还会检查：

- 线性加速度传感器可用；
- 采样率在配置范围内（默认约 120 到 320 Hz）；
- 窗口帧数达到最小值（默认 15）；
- 触摸没有超出最大移动距离。

推理完成后会产生 `collector_diagnostic_ready` 或 `collector_event_saved` 状态。

## 6. 输出对象：AispectTouchResult

外部监听器通过 `onTouchResult(AispectTouchResult result)` 接收结果。字段如下：

| 字段 | Java 类型 | 说明 |
| --- | --- | --- |
| `eventType` | `AispectTouchEventType` | `SAMPLE`、`PRESS`、`TAP`、`HOLD`、`DRAG`、`CANCEL` 或 `UNKNOWN` |
| `strength` | `AispectTouchStrength` | `LIGHT`、`HEAVY` 或 `UNKNOWN` |
| `fingerType` | `AispectFingerType` | `THUMB`、`INDEX` 或 `UNKNOWN` |
| `classLabel` | `String` | 原始模型标签，如 `thumb_heavy` |
| `confidence` | `double` | 最大类别概率，即 `predictedProbability` |
| `probabilities` | `double[]` | 全部类别的 Softmax 概率 |
| `labelOrder` | `String[]` | 概率数组的类别顺序 |
| `modelId` | `String` | 实际使用的模型 ID |
| `modelVersion` | `String` | 实际使用的模型版本 |
| `eventTimeMillis` | `long` | 触摸事件时间，单位毫秒 |
| `x`、`y` | `float` | 触摸坐标 |
| `liftOffset` | `Float` | 抬起时相对最后采样点的位移，可能为 `null` |

示例：

```text
labelOrder    = [thumb_light, thumb_heavy, index_light, index_heavy]
probabilities = [0.05, 0.80, 0.10, 0.05]
```

对应结果为：

```text
classLabel = thumb_heavy
confidence = 0.80
fingerType = THUMB
strength   = HEAVY
```

数组在结果对象构造时会复制，调用方可以安全读取。

## 7. 标签到枚举的映射

`AispectTouchResultMapper` 使用以下规则：

```text
thumb_* -> fingerType=THUMB
index_* -> fingerType=INDEX
*_heavy -> strength=HEAVY
*_light -> strength=LIGHT
```

事件类型映射为：

```text
LIGHT_TAP / HEAVY_TAP -> TAP
LIGHT_HOLD / HEAVY_HOLD / HEAVY_PRESS -> HOLD
DRAG_START / DRAG / DRAG_END -> DRAG
```

## 8. 无结果和错误情况

以下情况通过 `onTouchError()` 返回，不会产生正常分类结果：

- `SENSOR_UNAVAILABLE`：运动传感器不可用；
- `MODEL_UNAVAILABLE`：没有可加载模型；
- `INFERENCE_FAILED`：推理失败；
- `TOUCH_SEQUENCE_INVALID`：触摸序列缺失或无效。

结果状态使用 prediction sequence 去重。同一次因果触摸已经输出 `PRESS` 后，后续触摸结束状态不会重复回调相同预测。

## 9. 使用建议

业务代码应始终同时读取 `classLabel`、`confidence`、`probabilities` 和 `labelOrder`。推荐根据 `confidence` 设置业务阈值；需要兼容旧二分类模型时，不要假设一定存在 `fingerType`，因为标签为 `heavy/light` 时 `fingerType` 会是 `UNKNOWN`。

生命周期结束时调用：

```java
classifier.stop();
classifier.close();
```
