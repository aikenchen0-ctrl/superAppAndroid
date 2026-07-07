# BlinkVoice iOS SDK 接入说明

本文档给 iOS App 端开发使用。当前 iOS 端已经提供 Swift Package 源码 SDK、调起接口、返回数据接口和三分类事件输出能力。

## 1. 当前状态

iOS SDK 路径：

```text
ios/BlinkVoiceVisual
```

Swift Package：

```text
BlinkVoiceVisual
```

当前支持事件：

| Swift case | Raw value | 含义 |
| --- | --- | --- |
| `.singleBlink` | `SINGLE_BLINK` | 单次眨眼 |
| `.doubleBlink` | `DOUBLE_BLINK` | 快速眨两下 |
| `.longClose` | `LONG_CLOSE` | 闭眼 |

Raw value 与 Android 端保持一致，便于后端或业务层统一处理。

当前不包含摄像头启动速度优化。

## 2. 接入方式

在 Xcode 中添加 Swift Package：

1. 打开宿主 iOS App 工程。
2. 选择 `File > Add Package Dependencies`。
3. 使用当前 GitHub 仓库地址。
4. 选择或指向包目录：

```text
ios/BlinkVoiceVisual
```

如果 Xcode 无法直接解析仓库中的嵌套 package，开发阶段可以先把下面目录作为本地 package 添加：

```text
ios/BlinkVoiceVisual
```

## 3. 权限配置

宿主 App 的 `Info.plist` 必须配置相机权限说明：

```xml
<key>NSCameraUsageDescription</key>
<string>BlinkVoice needs camera access to recognize blink events.</string>
```

## 4. 基础调用

```swift
import BlinkVoiceVisual
import UIKit

final class ViewController: UIViewController {
    func startBlinkCapture() {
        BlinkVoiceCapture.start(from: self) { result in
            guard let result else {
                // 用户取消、权限拒绝、相机不可用或未识别到事件。
                return
            }

            switch result.eventType {
            case .singleBlink:
                break
            case .doubleBlink:
                break
            case .longClose:
                break
            }

            print("durationMs:", result.durationMs)
        }
    }
}
```

## 5. 带配置调用

```swift
let options = BlinkCaptureOptions(
    earCloseThreshold: 0.22,
    earOpenThreshold: 0.25,
    doubleBlinkWindowMs: 350,
    longCloseMinMs: 700,
    minShortBlinkMs: 16,
    maxShortBlinkMs: 280,
    noFaceResetMs: 500,
    autoFinishOnEvent: true,
    eventTypes: Set(BlinkEventType.allCases)
)

BlinkVoiceCapture.start(from: self, options: options) { result in
    guard let result else {
        return
    }

    print(result.eventType.rawValue)
}
```

## 6. 只监听某一种事件

示例：只监听快速眨两下。

```swift
let options = BlinkCaptureOptions(eventTypes: [.doubleBlink])

BlinkVoiceCapture.start(from: self, options: options) { result in
    guard result?.eventType == .doubleBlink else {
        return
    }

    // 处理快速眨两下。
}
```

## 7. 返回数据

`BlinkCaptureResult` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `eventType` | `BlinkEventType` | 三分类事件 |
| `startTimeMs` | `Int64` | 事件开始时间，基于系统 uptime |
| `endTimeMs` | `Int64` | 事件结束时间，基于系统 uptime |
| `durationMs` | `Int64` | 持续时间，单位毫秒 |
| `confidence` | `Float` | 当前固定为 `1.0` |

## 8. 是否可以直接接入

可以。iOS App 端添加 Swift Package 后即可调用 `BlinkVoiceCapture.start`。

需要注意：

- iOS 端使用 Apple Vision landmark；Android 端使用 MediaPipe。两端事件语义一致，但 iOS 阈值建议在真机上校准。
- 当前 Windows 环境无法运行 `swift test`，需要在 macOS / Xcode 环境验证。
- 当前未做摄像头启动速度优化。

macOS 上建议执行：

```bash
cd ios/BlinkVoiceVisual
swift test
```

真机验证步骤：

1. 宿主 App 添加 Swift Package。
2. 配置 `NSCameraUsageDescription`。
3. 调用 `BlinkVoiceCapture.start`。
4. 授权摄像头。
5. 分别测试单次眨眼、快速眨两下、闭眼。
6. 确认业务回调收到 `BlinkCaptureResult`。
