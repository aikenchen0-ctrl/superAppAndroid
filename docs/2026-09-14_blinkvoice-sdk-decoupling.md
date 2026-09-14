# BlinkVoice SDK 解耦架构

## 当前结论

BlinkVoice 现在由两条边界组成：

1. `blink-api`：纯 Java、平台无关的输入/状态/事件合同和确定性会话引擎。
2. `blinkvoice-visual-sdk`：Android 适配层，负责 CameraX、MediaPipe、ELA 计算和图像生命周期；Release AAR 同时打包 `blink-api` 主源码。

宿主 App 仍通过 `BlinkVoiceContinuousDetector` 使用相机能力。宿主不应引用 `detector`、`events`、`performance` 或 MediaPipe 类型；需要脱离相机验证时使用 `com.blinkvoice.visual.api.v2` 的纯会话接口。

本轮先完成纯引擎和发布边界，App 的现有相机入口暂未切换到 v2 会话引擎；这样可以先用离线回放验证新状态机，再替换线上事件路径，避免把未经标注数据验证的分类变化直接带入真机流程。

新增的 `BlinkElaSessionAdapter` 位于 Android SDK API 包，但只接受时间戳、ELA、是否有人脸和置信度；它不携带任何 Android/MediaPipe 对象。它是现有 `BlinkDetector` 与 v2 会话之间的候选桥接点，当前先用于单测和离线回放，尚未替换线上连续检测器。

`BlinkEventEvaluator` 和 `BlinkEvaluationMetrics` 位于 `blink-api`，按事件类型和起始时间容差做一对一匹配，输出 TP/FP/FN、Precision、Recall、F1、误触率、漏检率、平均起始延迟和 P95 绝对起始延迟。它不读取视频，也不依赖模型，适合被 CSV/视频回放工具调用。

建议回放记录采用一行一个事件的 CSV 合同：

```text
source,event_type,start_ms,end_ms,confidence
case-001,SINGLE_BLINK,100,180,1.0
case-001,DOUBLE_BLINK,420,650,1.0
```

标注集和预测集分别解析为 `List<BlinkEvent>` 后调用：

```java
BlinkEvaluationMetrics metrics = BlinkEventEvaluator.evaluate(
        expectedEvents,
        predictedEvents,
        40L
);
```

`40L` 是事件起始时间匹配容差示例，不是最终验收阈值；最终阈值应按采样帧率、动作类型和产品误触成本分别确定。

命令行回放入口位于 `blink-api`：

```powershell
.\gradlew.bat :blink-api:replayReport --args="expected.csv predicted.csv 40 report.json" --no-daemon --max-workers=1
```

不传第四个参数时，JSON 会写到标准输出；传入第四个参数时会自动创建父目录并写入报告文件。该工具只评估事件列表，不负责从视频提取 ELA。

如果已有规范化帧数据，可先用 `BlinkReplayFrameCsv` 和 `BlinkReplayRunner` 运行纯 v2 引擎，再把输出交给报告：

```text
source,timestamp_ms,face_present,left_eye_openness,right_eye_openness,face_confidence
case-001,0,1,0.90,0.90,0.95
case-001,100,1,0.05,0.05,0.95
case-001,180,1,0.90,0.90,0.95
```

其中 `left_eye_openness/right_eye_openness` 已归一化到 `[0, 1]`；ELA 角度到该规范化值的转换由 Android 侧 `BlinkElaSessionAdapter` 完成。回放时若要继续处理同一 case 的多个事件，应使用 `BlinkOptions.Builder().setStopOnEvent(false)`。

## 边界合同

```text
CameraX ImageProxy
        -> Android adapter
        -> MediaPipe FaceLandmarker
        -> ELA degrees
        -> normalized BlinkFrame [0, 1]
        -> BlinkSession
        -> BlinkState / BlinkEvent / BlinkDiagnostics / BlinkError
```

`BlinkFrame` 只包含时间戳、人脸存在、左右眼开合度和人脸置信度。它不持有 `Bitmap`、`ImageProxy`、`MPImage`、`FaceLandmarkerResult` 或 Android `Context`。因此可以在 JVM 单测、离线视频回放和未来 iOS/桌面适配器中复用同一状态机。

## 纯 API 用法

```java
BlinkListener listener = new BlinkListener() {
    @Override
    public void onEvent(BlinkEvent event) {
        // 处理 SINGLE_BLINK、DOUBLE_BLINK 或 LONG_CLOSE
    }
};

try (DefaultBlinkSessionFactory factory = new DefaultBlinkSessionFactory()) {
    BlinkSession session = factory.create(new BlinkOptions.Builder().build(), listener);
    session.start();
    session.submitFrame(new BlinkFrame(0L, true, 0.9d, 0.9d, 0.95d));
    session.submitFrame(new BlinkFrame(100L, true, 0.05d, 0.05d, 0.95d));
    session.submitFrame(new BlinkFrame(180L, true, 0.9d, 0.9d, 0.95d));
    session.close();
}
```

默认工厂使用串行回调线程；`DefaultBlinkSessionFactory(Executor)` 允许测试或宿主注入自己的回调执行器。`stop()` 会清空当前动作并允许再次 `start()`，`close()` 才是不可逆终态。

## 准确率改进点

- Android 检测层从 EAR 比值切换为眼睑角度 ELA，默认闭眼/睁眼角度为 `10/14`。
- 分类器加入自适应睁眼基线、相对闭眼判定、微重开和浅双眨路径。
- 灰区闭眼会清空旧的 pending 短眨，避免跨动作伪双眨。
- 浅双眨路径同步 `eyesClosed` 与状态阶段，避免调试状态和事件状态分叉。
- 异步 MediaPipe 结果按输入帧对象绑定元数据，时间戳、旋转和推理耗时不再读取“最后一帧”共享字段。
- 乱序帧、非法 ELA、无人脸超时和 stop/start 会被显式处理。

## 验证证据

| Evidence | Finding | Path |
|---|---|---|
| `blink-api` 纯 Java 测试通过 | 平台无关会话可独立处理单眨、双眨、长闭眼、乱序和重启 | `blink-api/src/test/java/com/blinkvoice/visual/api/v2/DefaultBlinkSessionTest.java` |
| `blinkvoice-visual-sdk` `test` 和 `assembleRelease` 通过 | Android 适配层、ELA、连续生命周期和 AAR 打包一致 | `blinkvoice-visual-sdk` Gradle 输出 |
| `BlinkEventEvaluatorTest` 通过 | 事件级离线指标的匹配和边界定义可复现 | `blink-api/src/test/java/com/blinkvoice/visual/api/v2/BlinkEventEvaluatorTest.java` |
| `BlinkReplayRunnerTest` 通过 | 规范化帧可实际驱动 v2 引擎并产生预测事件 | `blink-api/src/test/java/com/blinkvoice/visual/api/v2/BlinkReplayRunnerTest.java` |
| `BlinkReplayCliTest` 通过 | 可通过 Gradle JavaExec 生成 JSON 报告文件 | `blink-api/src/test/java/com/blinkvoice/visual/api/v2/BlinkReplayCliTest.java` |
| AAR `classes.jar` 包含回放入口 | CSV 解析、帧回放和 JSON 报告随发布产物分发 | `app/libs/blinkvoice-visual-sdk-release.aar` |
| AAR `classes.jar` 包含 `com.blinkvoice.visual.api.v2` | 解耦合同随发布产物分发 | `app/libs/blinkvoice-visual-sdk-release.aar` |
| App 使用 `BlinkVoiceContinuousDetector` 且不导入 detector/events/MediaPipe | 宿主依赖集中在高层 API | `app/src/main/java/com/paifa/univerge/app` |

## 尚未宣称完成的指标

当前还没有真机或标注视频集，因此不能宣称 Precision、Recall、F1、误触率、漏检率或 P95 延迟已达目标。下一阶段应建立带时间戳的离线回放合同，并对以下维度分别统计：光照、眼镜、头部旋转、单眼闭合、浅眨、连续快眨、无人脸恢复和相机帧丢弃。

App 全量编译当前还受到 `univerge-accessibility` 中既有 `BottomGestureBarOverlayController` API 不匹配影响；该错误不来自 BlinkVoice AAR 或新解耦 API。SDK 模块和纯 API 模块的独立验证不受该问题影响。
