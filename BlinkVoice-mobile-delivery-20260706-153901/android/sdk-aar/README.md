# BlinkVoice Visual SDK App 端接入说明

本文档给 App 端开发使用，说明如何接入 BlinkVoice 纯视觉眨眼识别 AAR，并通过 Java 接口调起识别页、接收识别结果。

## 1. SDK 能力范围

当前 SDK 提供的是“调起式识别”能力：

- App 调用 SDK 接口打开识别页面。
- SDK 打开前置摄像头并进行纯视觉眨眼识别。
- SDK 识别到目标事件后关闭页面，并把结果返回给 App。

当前返回三类事件：

| 事件枚举 | 含义 |
| --- | --- |
| `SINGLE_BLINK` | 单次眨眼 |
| `DOUBLE_BLINK` | 快速眨两下 |
| `LONG_CLOSE` | 闭眼 |

当前阶段不包含连续流式回调，不包含摄像头启动速度优化。摄像头启动速度优化会作为下一阶段处理。

## 2. AAR 文件

SDK AAR 产物：

```text
blinkvoice-visual-sdk-release.aar
```

当前工程中的产物路径：

```text
C:\Users\血饮\Desktop\bv\blinkVoice\eye_blink_visual\blinkvoice-visual-sdk\build\outputs\aar\blinkvoice-visual-sdk-release.aar
```

如需重新构建 AAR，在 `eye_blink_visual` 目录执行：

```powershell
.\gradlew.bat --no-daemon :blinkvoice-visual-sdk:assembleRelease
```

## 3. App 工程接入方式

### 3.1 放入 AAR

把 `blinkvoice-visual-sdk-release.aar` 放到宿主 App 的：

```text
app/libs/blinkvoice-visual-sdk-release.aar
```

### 3.2 配置 Gradle

宿主 App 的 `app/build.gradle` 增加：

```gradle
android {
    compileSdk 34

    defaultConfig {
        minSdk 24
    }
}

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

说明：

- 如果后续改成内部 Maven 发布，可以只依赖 Maven 坐标，由 Maven POM 自动带出传递依赖。
- 如果当前直接使用本地 AAR 文件，宿主 App 需要显式添加上面的 AndroidX、CameraX、MediaPipe 依赖。
- 宿主 App 即使主要使用 Java，也可以直接依赖 `kotlin-stdlib`，不要求业务代码改成 Kotlin。

### 3.3 AndroidX 要求

宿主 App 的 `gradle.properties` 需要启用 AndroidX：

```properties
android.useAndroidX=true
android.enableJetifier=true
```

### 3.4 权限要求

SDK 的 Manifest 已声明摄像头权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

SDK 页面启动时，如果没有摄像头权限，会自动发起运行时权限申请。宿主 App 不需要自己提前申请摄像头权限，但也可以在业务入口前提前申请。

## 4. App 端只使用这些公开接口

App 端只需要使用 `com.blinkvoice.visual.api` 包下的接口：

```java
import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.api.BlinkResultContract;
import com.blinkvoice.visual.api.BlinkVoiceCapture;
```

不要直接调用以下内部包：

```text
com.blinkvoice.visual.ui
com.blinkvoice.visual.detector
com.blinkvoice.visual.events
```

这些包属于 SDK 内部实现，后续可能调整。

## 5. 推荐调用方式：ActivityResultLauncher

适合新项目或已经使用 AndroidX Activity Result API 的项目。

```java
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkEventType;
import com.blinkvoice.visual.api.BlinkResultContract;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<BlinkCaptureOptions> blinkLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        blinkLauncher = registerForActivityResult(
                new BlinkResultContract(),
                this::handleBlinkResult
        );
    }

    private void startBlinkCapture() {
        BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
                .setAutoFinishOnEvent(true)
                .build();

        blinkLauncher.launch(options);
    }

    private void handleBlinkResult(BlinkCaptureResult result) {
        if (result == null) {
            // 用户取消、权限拒绝、相机不可用、模型加载失败等情况会返回 null。
            return;
        }

        BlinkEventType eventType = result.getEventType();
        long durationMs = result.getDurationMs();

        switch (eventType) {
            case SINGLE_BLINK:
                // 单次眨眼
                break;
            case DOUBLE_BLINK:
                // 快速眨两下
                break;
            case LONG_CLOSE:
                // 闭眼
                break;
        }
    }
}
```

业务入口处调用：

```java
startBlinkCapture();
```

## 6. 兼容旧项目：startActivityForResult

如果宿主 App 还在使用 `startActivityForResult`，可以这样接：

```java
import android.content.Intent;
import com.blinkvoice.visual.api.BlinkCaptureResult;
import com.blinkvoice.visual.api.BlinkVoiceCapture;

private static final int REQ_BLINK_CAPTURE = 2001;

private void startBlinkCapture() {
    Intent intent = BlinkVoiceCapture.createIntent(this);
    startActivityForResult(intent, REQ_BLINK_CAPTURE);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode != REQ_BLINK_CAPTURE) {
        return;
    }

    BlinkCaptureResult result = BlinkVoiceCapture.parseResult(resultCode, data);
    if (result == null) {
        // 用户取消、权限拒绝、相机不可用、模型加载失败等情况会返回 null。
        return;
    }

    switch (result.getEventType()) {
        case SINGLE_BLINK:
            // 单次眨眼
            break;
        case DOUBLE_BLINK:
            // 快速眨两下
            break;
        case LONG_CLOSE:
            // 闭眼
            break;
    }
}
```

## 7. 返回结果字段

`BlinkCaptureResult` 字段说明：

| 方法 | 类型 | 说明 |
| --- | --- | --- |
| `getEventType()` | `BlinkEventType` | 事件类型 |
| `getStartTimeMs()` | `long` | 事件开始时间，基于 `SystemClock.elapsedRealtime()` |
| `getEndTimeMs()` | `long` | 事件结束时间，基于 `SystemClock.elapsedRealtime()` |
| `getDurationMs()` | `long` | 事件持续时长，单位毫秒 |
| `getConfidence()` | `float` | 置信度字段，当前固定为 `1.0f` |

示例：

```java
BlinkEventType type = result.getEventType();
long startTimeMs = result.getStartTimeMs();
long endTimeMs = result.getEndTimeMs();
long durationMs = result.getDurationMs();
float confidence = result.getConfidence();
```

## 8. 配置项

默认配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `earCloseThreshold` | `0.22f` | 低于该 EAR 值认为眼睛闭合 |
| `earOpenThreshold` | `0.25f` | 高于该 EAR 值认为眼睛重新睁开 |
| `doubleBlinkWindowMs` | `350` | 两次短眨之间的最大间隔 |
| `longCloseMinMs` | `700` | 判定闭眼事件的最小时长 |
| `minShortBlinkMs` | `16` | 短眨最小时长 |
| `maxShortBlinkMs` | `280` | 短眨最大时长 |
| `noFaceResetMs` | `500` | 无人脸后重置状态的时间 |
| `autoFinishOnEvent` | `true` | 识别到事件后是否自动关闭 SDK 页面 |
| `eventTypes` | 全部事件 | 允许返回哪些事件 |

常用配置示例：

```java
BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
        .setAutoFinishOnEvent(true)
        .setLongCloseMinMs(700L)
        .setDoubleBlinkWindowMs(350L)
        .build();
```

只监听快速眨两下：

```java
import java.util.EnumSet;

BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
        .setEventTypes(EnumSet.of(BlinkEventType.DOUBLE_BLINK))
        .build();
```

## 9. 返回 null 的情况

以下情况 App 端会拿到 `null`：

- 用户返回或关闭 SDK 页面。
- 用户拒绝摄像头权限。
- 设备没有可用前置摄像头。
- CameraX 绑定失败。
- MediaPipe 模型加载失败。
- `resultCode` 不是 `Activity.RESULT_OK`。

当前第一阶段没有细分错误码。后续如果业务需要，可以扩展为返回取消原因或错误原因。

## 10. 手机端验证方式

当前工程里的 demo app 已经接入了 SDK 调起验证入口。

验证步骤：

1. 连接 Android 手机并打开 USB 调试。
2. 在 `eye_blink_visual` 目录执行：

```powershell
.\gradlew.bat --no-daemon :app:installDebug
```

3. 打开手机上的 `EyeBlinkDetect`。
4. 授权摄像头。
5. 点击页面底部的 `测试 SDK 调起接口`。
6. SDK 页面打开后，执行单次眨眼、快速眨两下或闭眼。
7. SDK 页面自动关闭并返回 demo app。
8. demo app 底部显示类似：

```text
SDK result: 快速眨两下, duration=270ms
```

## 11. 常见问题

### 11.1 编译时报找不到 CameraX 或 MediaPipe 类

如果宿主 App 是直接接入本地 AAR 文件，需要在宿主 App 的 `app/build.gradle` 手动添加第 3.2 节里的依赖。

### 11.2 调起后提示权限拒绝

检查宿主 App 是否允许摄像头权限。SDK 会自己申请权限，但用户拒绝后本次识别会取消，App 端会收到 `null`。

### 11.3 页面调起后没有识别结果

优先检查：

- 是否使用前置摄像头。
- 环境光是否足够。
- 人脸是否在画面中。
- 眼睛是否被遮挡。
- 是否只配置了某一种 `eventTypes`，导致其他事件被过滤。

### 11.4 本地 Gradle 单元测试出现 ClassNotFoundException

如果在 Windows 中文用户目录下运行测试出现该问题，可以通过 ASCII 路径目录联接执行：

```powershell
New-Item -ItemType Junction -Path C:\work\blinkVoice-current -Target C:\Users\血饮\Desktop\bv\blinkVoice
cd C:\work\blinkVoice-current\eye_blink_visual
.\gradlew.bat --no-daemon :blinkvoice-visual-sdk:testDebugUnitTest
```
