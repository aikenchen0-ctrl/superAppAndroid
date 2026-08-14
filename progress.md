# 执行进度

## 2026-08-14
- 状态：进行中
- 已完成：读取工作区状态，定位 iOS 群邀请实现、Android 浮窗入口、SCRM 接口文档。
- 当前：提取 iOS 行为和 API 字段，核对 Android 已有能力与并发改动。
- 发现：Android `ScrmApiClient` 已有 `getGroupInvitations`、`pullChatRoomInvitations`、`agreeChatRoomInvite`、`approveChatRoomInvite` 方法；右侧入口仍指向底部面板。

## 验证结果
| 检查 | 结果 |
|---|---|
| 初始状态检查 | 工作区已有并行未提交修改，已保留 |
| 批量源码提取 | PowerShell 组合语法失败，已改用拆分读取 |
| 群邀请契约测试 | 通过 |
| :ubiki-accessibility:compileDebugKotlin | 通过 |
| Group invitation tab and animation verification | Passed: TabRow, HorizontalPager, LazyColumn, 30dp status spacer, translationY enter/exit |

## 2026-08-14 网页链接
- 已新增右侧“网页链接”独立 WebLink action 和全屏 Material 3 工作区。
- 已接入 `POST /openapi/v1/messages/link-card`，仅以 SCRM 任务返回更新发送状态。
- 验证通过：`WebLinkFullScreenContractTest`、`ScrmApiClientTest`、`:ubiki-accessibility:compileDebugKotlin`。

## 2026-08-14 悬浮聊天全屏动画
- 已在 `FloatingChatOverlayController` 共享根视图加入实体 `ComposeView.translationY` 入场和退出属性动画。
- 主界面展开按钮、无障碍 `ExpandFloatingChat`、未读总览和具体账户会话均复用该 controller 根视图。
- 收起会先完成向下退出动画，再恢复折叠窗口；完全关闭会先退出再移除窗口。
- 验证通过：`FloatingChatOverlayAnimationContractTest`、`FloatingChatOverlayMountStateTest`、`FloatingChatOverlayControllerContractTest`、`:ubiki-accessibility:compileDebugKotlin`。
