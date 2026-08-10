# ubikiTouch

ubikiTouch 是一个基于 Android 无障碍服务的边缘手势与悬浮交互项目。它通过屏幕边缘的透明触控区域识别手势，并将手势映射为返回、主页、通知、截图、音量控制或启动应用等系统操作。

## 主要功能

- 左右边缘及底部手势识别，可配置触控区域、灵敏度和手势动作。
- 基于 `AccessibilityService` 的系统操作执行。
- 悬浮聊天窗口，支持相机、媒体、文档、定位和语音等交互能力。
- 手势暂停、应用黑名单、横屏禁用、键盘显示时禁用等控制选项。
- 通过快捷设置磁贴快速暂停或恢复边缘手势。
- 集成 ADB 核心模块和 BlinkVoice 视觉 SDK。

## 项目结构

- `app`：示例应用及主要界面。
- `ubiki-core`：手势模型与分类逻辑。
- `ubiki-overlay`：透明边缘触控层与触摸事件处理。
- `ubiki-accessibility`：无障碍服务、动作执行和配置管理。
- `adbcore`：ADB 通信、配对及保活能力。
- `blinkvoice-visual-sdk`：视觉与语音相关 SDK 模块。
- `benchmark`：性能基准测试。

## 环境与构建

需要 Android Studio、Android SDK 36、JDK 17。执行以下命令构建并运行检查：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test lintDebug assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用注意

首次使用需要在系统设置中手动启用本项目的无障碍服务，并根据系统策略允许应用在后台运行或加入电池优化白名单。项目不能也不会自动获取无障碍权限。

## 相关文档

- [ADB 测试说明](docs/ADB_TESTING.md)
- [SDK 集成说明](docs/SDK_INTEGRATION.md)
- [悬浮聊天组件 API](docs/FLOATING_CHAT_COMPONENT_API.md)
- [边缘控制参考](docs/EDGE_CONTROL_REFERENCE.md)
