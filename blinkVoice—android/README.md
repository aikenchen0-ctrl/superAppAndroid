# BlinkVoice Android 交付说明

本目录是 BlinkVoice 的 Android 独立工程，已经从原始总工程中抽离出来，可以单独打开、构建、安装测试，也可以把 SDK AAR 接入到其它 Android App。

## 目录内容

```text
blinkVoice—android/
├─ app/                         Android demo App，用于手机直接安装测试
├─ blinkvoice-visual-sdk/        Android 眨眼识别 SDK 源码模块
├─ gradle/                      Gradle wrapper 文件
├─ release/
│  ├─ install-apk/
│  │  └─ blinkvoice-demo-debug.apk
│  └─ sdk-aar/
│     └─ blinkvoice-visual-sdk-release.aar
├─ build.gradle
├─ gradlew.bat
├─ settings.gradle
└─ README.md
```

## 交付物

给 Android App 接入时使用 AAR：

```text
release/sdk-aar/blinkvoice-visual-sdk-release.aar
```

给手机直接安装测试时使用 APK：

```text
release/install-apk/blinkvoice-demo-debug.apk
```

不要把 demo APK 当成 SDK 接入。APK 只用于测试页面和真机体验，AAR 才是给宿主 App 集成的 SDK 包。

## 当前功能

当前 Android 端已经具备以下能力：

- 可嵌入 SDK 模块。
- SDK 调起接口。
- SDK 返回结果接口。
- 取消原因返回。
- 纯视觉识别眨眼，不依赖 USB。
- 单次眨眼、快速眨两下、长闭眼三分类。
- demo App 可直接安装到 Android 手机测试。
- 宿主 App 接入示例代码。
- 手机端诊断面板和调试日志。
- APK 侧自动 EAR 阈值建议，用于真机调试。

当前未做摄像头启动速度优化。

## 识别事件

| 事件枚举 | 含义 |
| --- | --- |
| `SINGLE_BLINK` | 单次眨眼 |
| `DOUBLE_BLINK` | 快速眨两下 |
| `LONG_CLOSE` | 长闭眼 |

当前 SDK 单次调起只返回一个最终事件。分类优先级为：

```text
LONG_CLOSE > DOUBLE_BLINK > SINGLE_BLINK
```

默认识别规则：

| 事件 | 规则 |
| --- | --- |
| `SINGLE_BLINK` | 一次短眨，闭眼持续 `30ms ~ 260ms`，且在 `650ms` 双眨总窗口内没有完成第二次短眨 |
| `DOUBLE_BLINK` | 两次短眨，且从第一次短眨开始到第二次短眨结束 `<= 650ms` |
| `LONG_CLOSE` | 连续闭眼 `>= 500ms`，立即返回闭眼事件 |

默认 EAR 阈值：

| 阈值 | 默认值 | 说明 |
| --- | --- | --- |
| `earCloseThreshold` | `0.22f` | 平均 EAR 低于该值进入闭眼状态 |
| `earOpenThreshold` | `0.25f` | 平均 EAR 高于该值恢复睁眼状态 |

灰区规则：

- 闭眼 `< 30ms`：忽略，避免把单帧噪声当眨眼。
- 闭眼 `261ms ~ 499ms`：不输出事件，避免慢眨和长闭眼混淆。
- 无人脸持续 `500ms`：重置当前识别状态。

## 直接安装 APK 测试

连接 Android 手机并打开 USB 调试后，在本目录执行：

```powershell
adb install -r .\release\install-apk\blinkvoice-demo-debug.apk
```

安装后打开 `EyeBlinkDetect`，授权摄像头，点击页面中的 `测试 SDK 调起接口`，即可测试 SDK 调起和返回结果。

demo 页面返回结果示例：

```text
SDK result: 快速眨两下, duration=270ms
```

## 宿主 App 接入 AAR

把 AAR 放到宿主 App：

```text
app/libs/blinkvoice-visual-sdk-release.aar
```

宿主 App 的 `app/build.gradle` 增加依赖：

```gradle
dependencies {
    implementation files('libs/blinkvoice-visual-sdk-release.aar')

    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.activity:activity:1.9.3'
    implementation 'androidx.activity:activity-ktx:1.9.3'
    implementation 'androidx.appcompat:appcompat:1.6.1'

    def camerax_version = "1.3.1"
    implementation "androidx.camera:camera-core:$camerax_version"
    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:$camerax_version"

    implementation 'com.google.mediapipe:tasks-vision:0.10.14'
}
```

宿主 App 需要启用 AndroidX：

```properties
android.useAndroidX=true
android.enableJetifier=true
```

SDK 的 Manifest 已声明相机权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

SDK 页面启动时会申请运行时权限。宿主 App 可以提前申请，也可以交给 SDK 页面申请。

## App 端调用代码

推荐使用 AndroidX Activity Result API：

```java
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureOutcome;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.api.BlinkOutcomeContract;

public final class BlinkVoiceHostActivity extends AppCompatActivity {
    private ActivityResultLauncher<BlinkCaptureOptions> blinkCaptureLauncher;

    /**
     * 初始化宿主页面，并注册 SDK 调起结果回调。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blinkCaptureLauncher = registerForActivityResult(
                new BlinkOutcomeContract(),
                this::handleBlinkOutcome
        );
    }

    /**
     * 宿主 App 释放自己的相机后，启动一次 SDK 识别流程。
     */
    private void startBlinkCapture() {
        stopHostCameraIfNeeded();
        blinkCaptureLauncher.launch(buildBlinkCaptureOptions());
    }

    /**
     * 构造传给 SDK 的最新 App 侧识别规则。
     */
    private BlinkCaptureOptions buildBlinkCaptureOptions() {
        return new BlinkCaptureOptions.Builder()
                .setEarCloseThreshold(0.22f)
                .setEarOpenThreshold(0.25f)
                .setDoubleBlinkWindowMs(650L)
                .setLongCloseMinMs(500L)
                .setMinShortBlinkMs(30L)
                .setMaxShortBlinkMs(260L)
                .setNoFaceResetMs(500L)
                .setAutoFinishOnEvent(true)
                .setDebugLoggingEnabled(true)
                .setDebugOverlayEnabled(true)
                .build();
    }

    /**
     * 处理 SDK 页面返回，并在返回后恢复宿主 App 相机。
     */
    private void handleBlinkOutcome(BlinkCaptureOutcome outcome) {
        restartHostCameraIfNeeded();
        if (outcome == null || !outcome.isSuccess()) {
            handleBlinkCanceled(outcome != null ? outcome.getCancelReason() : null);
            return;
        }
        handleBlinkResult(outcome.getResult());
    }

    /**
     * 将 SDK 识别到的事件分发到宿主 App 的具体业务动作。
     */
    private void handleBlinkResult(BlinkCaptureResult result) {
        if (result == null) {
            handleBlinkCanceled(null);
            return;
        }

        BlinkEventType eventType = result.getEventType();
        switch (eventType) {
            case SINGLE_BLINK:
                handleSingleBlink(result);
                break;
            case DOUBLE_BLINK:
                handleDoubleBlink(result);
                break;
            case LONG_CLOSE:
                handleLongClose(result);
                break;
        }
    }

    /**
     * 处理权限拒绝、相机不可用、用户退出等 SDK 取消情况。
     */
    private void handleBlinkCanceled(String cancelReason) {
        // TODO: 替换为宿主 App 自己的提示、日志或重试逻辑。
    }

    /**
     * 处理单次眨眼。
     */
    private void handleSingleBlink(BlinkCaptureResult result) {
        // TODO: 执行 SINGLE_BLINK 对应业务。
    }

    /**
     * 处理双眨眼。
     */
    private void handleDoubleBlink(BlinkCaptureResult result) {
        // TODO: 执行 DOUBLE_BLINK 对应业务。
    }

    /**
     * 处理长闭眼。
     */
    private void handleLongClose(BlinkCaptureResult result) {
        // TODO: 执行 LONG_CLOSE 对应业务。
    }

    /**
     * 打开 SDK 相机页前释放宿主 App 自己持有的相机。
     */
    private void stopHostCameraIfNeeded() {
        // TODO: 如果宿主使用 CameraX，可在这里执行 cameraProvider.unbindAll()。
    }

    /**
     * SDK 相机页返回后重新启动宿主 App 自己的相机。
     */
    private void restartHostCameraIfNeeded() {
        // TODO: 如果宿主页面需要继续预览，可在这里重新 bindToLifecycle()。
    }
}
```

## 返回数据

`BlinkCaptureOutcome` 用于区分成功和取消：

| 方法 | 说明 |
| --- | --- |
| `isSuccess()` | 是否识别成功 |
| `getResult()` | 成功时返回 `BlinkCaptureResult` |
| `getCancelReason()` | 取消时返回原因，例如权限拒绝、相机不可用、用户退出 |

`BlinkCaptureResult` 字段：

| 方法 | 类型 | 说明 |
| --- | --- | --- |
| `getEventType()` | `BlinkEventType` | 三分类事件 |
| `getStartTimeMs()` | `long` | 事件开始时间 |
| `getEndTimeMs()` | `long` | 事件结束时间 |
| `getDurationMs()` | `long` | 持续时间，单位毫秒 |
| `getConfidence()` | `float` | 当前固定为 `1.0f` |

## 手机端诊断

调试版默认开启手机端诊断面板，SDK 相机页会显示关键识别链路，方便真机测试时不接电脑也能看到状态。

诊断面板包含：

```text
face / eye
leftEAR / rightEAR / avgEAR
close/open 阈值
seenOpen
classifier phase
closedMs / wait2Ms
event
last reason
```

调试完成后可以关闭：

```java
BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
        .setDebugOverlayEnabled(false)
        .setDebugLoggingEnabled(false)
        .build();
```

Logcat 搜索：

```text
BlinkVoiceDebug
```

正式环境建议关闭日志，避免相机帧日志刷屏。

## 重新构建

在本目录执行：

```powershell
.\gradlew.bat --no-daemon :blinkvoice-visual-sdk:testDebugUnitTest :app:testDebugUnitTest :blinkvoice-visual-sdk:assembleRelease :app:assembleDebug
```

构建后产物路径：

```text
app/build/outputs/apk/debug/app-debug.apk
blinkvoice-visual-sdk/build/outputs/aar/blinkvoice-visual-sdk-release.aar
```

如果在 Windows 中文路径下运行单元测试出现 `ClassNotFoundException`，建议映射到 ASCII 路径后执行，例如：

```powershell
New-Item -ItemType Junction -Path C:\work\blinkVoice-android -Target C:\Users\血饮\Desktop\bv\blinkVoice—android
cd C:\work\blinkVoice-android
.\gradlew.bat --no-daemon :blinkvoice-visual-sdk:testDebugUnitTest :app:testDebugUnitTest :blinkvoice-visual-sdk:assembleRelease :app:assembleDebug
```

## 注意事项

- `local.properties` 是当前电脑的 Android SDK 路径。其他开发者打开工程时，如路径不同，需要修改 `sdk.dir`。
- 如果宿主 App 自己也在使用摄像头，调起 SDK 前必须释放宿主相机资源，SDK 返回后再恢复，否则部分机型可能出现相机不可用、黑屏或闪退。
- 当前 AAR 是本地构建产物；后续如果要长期稳定接入 GitHub，建议发布到 Maven 仓库或 GitHub Release。
