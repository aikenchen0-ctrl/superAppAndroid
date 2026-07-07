# BlinkVoice Mobile Delivery Package

本包用于直接发送给手机端开发同事，包含 Android 安装体验包、Android SDK AAR、iOS Swift Package 源码 SDK，以及双端接入文档。

## 目录说明

```text
android/install-apk/
```

给 Android 手机直接安装体验 demo 使用。手机上安装 `blinkvoice-demo-debug.apk` 即可。

```text
android/sdk-aar/
```

给 Android App 开发接入使用。宿主 App 引入 `blinkvoice-visual-sdk-release.aar`。

```text
ios/BlinkVoiceVisual/
```

给 iOS App 开发接入使用。该目录是 Swift Package 源码 SDK。

```text
docs/
```

双端接口调用说明：

- `android-sdk-usage.md`
- `ios-sdk-usage.md`

## 重要结论

- Android 直接安装测试：使用 `android/install-apk/blinkvoice-demo-debug.apk`。
- Android App 嵌入 SDK：使用 `android/sdk-aar/blinkvoice-visual-sdk-release.aar`。
- iOS App 嵌入 SDK：使用 `ios/BlinkVoiceVisual` Swift Package。
- Android 和 iOS 的事件 raw value 保持一致：`SINGLE_BLINK`、`DOUBLE_BLINK`、`LONG_CLOSE`。
- 当前版本未包含摄像头启动速度优化。

## 验证状态

- Android SDK 单元测试、demo app 单元测试、AAR release 构建、APK debug 构建已在 Windows 环境验证通过。
- iOS 需要在 macOS / Xcode 环境执行 `swift test` 和真机相机验证。
