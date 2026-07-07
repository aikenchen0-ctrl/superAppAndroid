# BlinkVoice Android SDK 接入说明

本文档给 Android App 端开发使用。当前 Android 端已经具备可嵌入 SDK、调起接口、返回数据接口和三分类事件输出能力。

## 1. 当前状态

Android SDK 源码模块：

```text
eye_blink_visual/blinkvoice-visual-sdk
```

核心公开包：

```text
com.blinkvoice.visual.api
```

当前支持事件：

| 事件枚举 | 含义 |
| --- | --- |
| `SINGLE_BLINK` | 单次眨眼 |
| `DOUBLE_BLINK` | 快速眨两下 |
| `LONG_CLOSE` | 闭眼 |

当前不包含摄像头启动速度优化。

## 2. 交付物选择

给 Android App 接入时，应交付 AAR SDK，不是 APK。

APK 只用于直接安装到手机体验 demo：

```text
release/android/blinkvoice-demo-debug.apk
```

AAR 由 SDK 模块构建生成：

```powershell
cd eye_blink_visual
.\gradlew.bat --no-daemon :blinkvoice-visual-sdk:assembleRelease
```

生成路径：

```text
eye_blink_visual/blinkvoice-visual-sdk/build/outputs/aar/blinkvoice-visual-sdk-release.aar
```

## 3. 宿主 App 配置

把 AAR 放到宿主 App：

```text
app/libs/blinkvoice-visual-sdk-release.aar
```

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

宿主 App 需要启用 AndroidX：

```properties
android.useAndroidX=true
android.enableJetifier=true
```

## 4. 权限

SDK 的 Manifest 已声明相机权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

SDK 页面启动时会申请运行时权限。宿主 App 可以提前申请，也可以交给 SDK 页面申请。

## 5. 推荐调用方式

使用 AndroidX Activity Result API。

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
            // 用户取消、权限拒绝、相机不可用、模型加载失败等情况。
            return;
        }

        BlinkEventType eventType = result.getEventType();
        long durationMs = result.getDurationMs();

        switch (eventType) {
            case SINGLE_BLINK:
                break;
            case DOUBLE_BLINK:
                break;
            case LONG_CLOSE:
                break;
        }
    }
}
```

## 6. 返回数据

`BlinkCaptureResult` 字段：

| 方法 | 类型 | 说明 |
| --- | --- | --- |
| `getEventType()` | `BlinkEventType` | 三分类事件 |
| `getStartTimeMs()` | `long` | 事件开始时间 |
| `getEndTimeMs()` | `long` | 事件结束时间 |
| `getDurationMs()` | `long` | 持续时间，单位毫秒 |
| `getConfidence()` | `float` | 当前固定为 `1.0f` |

## 7. 只监听某一种事件

示例：只监听快速眨两下。

```java
import java.util.EnumSet;
import com.blinkvoice.visual.api.BlinkCaptureOptions;
import com.blinkvoice.visual.api.BlinkEventType;

BlinkCaptureOptions options = new BlinkCaptureOptions.Builder()
        .setEventTypes(EnumSet.of(BlinkEventType.DOUBLE_BLINK))
        .build();
```

## 8. 是否可以直接接入

可以。Android App 端接入 AAR 后即可调用上述接口。

需要注意：

- 不要把 demo APK 当 SDK 接入。
- 当前 AAR 是本地构建产物，若要从 GitHub 长期稳定接入，建议后续发布到 Maven 仓库或 GitHub Release。
- 当前未做摄像头启动速度优化。
