# 悬浮聊天统一工作区发现

- “UI组件”基线为 `FloatingChatOverlayUi` 的根级 `AnimatedVisibility` 与 `FloatingBottomPanel` 透明容器，页面使用 `FloatingWorkspaceTopAppBar`。
- 当前用户指定范围为：未回消息、全部未回消息、工具栏搜索、工具栏扫码。
- 预期视觉契约：工具栏自身 `paddingTop = 30.dp`，无独立状态栏 `Spacer`；透明背景；左返回按钮；进入由下向上、关闭由上向下，且只由根实体做一次动画。
- 现有协作者摘要称这四类页面已部分改为共享工具栏，仍须以当前工作区代码和定向测试验证。
- 旧 `ToolbarWorkspaceContractTest` 仍要求独立 `Spacer(Modifier.height(30.dp))` 和页面级 `translationY`，与新规范冲突；测试已更新为共享 toolbar、透明根和根级运动断言。
- `FloatingChatOverlayController.showState` 原先在 `updateOverlayState(nextState)` 后计算 `state != Expanded`，导致首次挂载 Expanded 时入场动画条件为假；现在在发布状态前保存 `previousState` 并计算条件。
- 未回消息路由默认继承磨砂背景，默认配置约 67% alpha；`AllAccountsUnread` 与 `SingleAccountUnread` 现在明确禁用该根背景，普通会话仍保留用户配置。
- 共享 `FloatingWorkspaceTopAppBar` 现在通过 Material 3 `windowInsets = WindowInsets(top = 30.dp)` 承载顶部空间，不再把 30dp 放在外层 padding。
- 定向测试和 app Kotlin 编译通过；模块全量测试等待重试后仍有 25 个与本轮无关的既有契约失败。
