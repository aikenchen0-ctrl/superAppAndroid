# 悬浮聊天统一工作区发现

## 本轮续作：朋友圈

- `MomentsTimelinePanelPresentationTest` 已在实现前断言共享 `FloatingWorkspaceTopAppBar`、surface 根、`PrimaryTabRow + HorizontalPager`、`rememberLazyListState + snapshotFlow` 和按 ID 的列表项；旧实现按预期不满足。
- 现有 `ScrmMomentsSync.kt` 已具备真实动态详情回读、点赞、评论、回复参数与本人评论删除任务。页面重构必须复用这些函数，失败只显示在当前工作区，不能自动重发。
- `FloatingBottomPanel` 目前直接调用旧时间线，新的工作区需要接收 `onClose` 并由共享聊天根关闭，避免额外 Window 和 BadTokenException。


## 本轮：朋友圈

- 用户要求将右侧“朋友圈”完整还原为 Android 浮层工作区：Material 3 风格、共享顶部返回栏、surface 全屏根、根级实体进出场动画、高性能分页与列表、以及与 IosFloat 对等的接口调用。
- 最新全局视觉规范以 `surface` 为可见浮层根背景；30dp 顶部安全区由共享 AppBar 的 `windowInsets` 承担，不新增页面 `Spacer`。
- 工作区处于多 AI 协作状态，已有大量未提交修改；本轮只触碰朋友圈相关文件和新增的定向测试，不回退其他变更。
- 初步代码检索确认右栏动作 `FloatingChatToolAction.Moments` 路由到 `BottomPanelMode.Moments`，目标主体为 `MomentsTimelinePanel`；素材库是独立的 `MomentMaterials` 路由，不能把两者混为一个页面。
- 接口文档列出朋友圈专用链路：`GET /moments/timeline`、`POST /moments/sync`、`POST /moments`、`POST /moments/like`、`POST /moments/comments`、`POST /moments/comments/delete`、`POST /moments/detail`、`GET /moments/interactions`、`POST /moments/interactions/read`、`POST /moments/interactions/clear`、`POST /moments/visibility`、`POST /moments/sticky` 与 `POST /moments/delete`。写操作需先走 capabilities，并尊重异步任务未知结果不可自动重发的约束。

## 本轮：朋友圈审查结论

- Android 现有 `MomentsTimelinePanel` 已真实调用同步、发布、点赞和评论，但主页面仍使用小字号、粗标题、手工弹出操作菜单和“接口接入后再执行”的预览分支，未达到用户要求的 Material 3 工作区与接口对标。
- iOS `MomentsViewController` 使用高性能列表预取、10 条分段加载、缓存优先后远程刷新、发表、同步、点赞、评论、删除评论、媒体预览和链接跳转；Android 应保留 Compose `LazyColumn` 的等价性能模型，并将列表分页和接口状态显式接入。
- 两个只读子审查因协作服务流断开而中止，未修改任何文件。本地源代码已提供足够对照依据，继续本地执行。

## 当前审查

- 共享全屏宿主在居中尺寸分支之前应用 surface 与 fillMaxSize。
- 未回消息路由需要局部使用 surface，普通会话的磨砂背景不在本轮改动范围。
- 剩余审查范围是 FloatingChatOverlayUi 的直接路由页和工具栏子页的余高分配。
- 直连全屏页应只有根 AnimatedVisibility 承担实体位移；子页 Animatable/graphicsLayer 会叠加整页高度位移。
- SendName 仍有独立状态栏占位和 LazyColumn.fillMaxSize，必须改为共享 AppBar 和 weight(1f) 剩余高度。

- “UI组件”基线为 `FloatingChatOverlayUi` 的根级 `AnimatedVisibility` 与 `FloatingBottomPanel` 透明容器，页面使用 `FloatingWorkspaceTopAppBar`。
- 当前用户指定范围为：未回消息、全部未回消息、工具栏搜索、工具栏扫码。
- 预期视觉契约：工具栏自身 `paddingTop = 30.dp`，无独立状态栏 `Spacer`；透明背景；左返回按钮；进入由下向上、关闭由上向下，且只由根实体做一次动画。
- 现有协作者摘要称这四类页面已部分改为共享工具栏，仍须以当前工作区代码和定向测试验证。
- 旧 `ToolbarWorkspaceContractTest` 仍要求独立 `Spacer(Modifier.height(30.dp))` 和页面级 `translationY`，与新规范冲突；测试已更新为共享 toolbar、透明根和根级运动断言。
- `FloatingChatOverlayController.showState` 原先在 `updateOverlayState(nextState)` 后计算 `state != Expanded`，导致首次挂载 Expanded 时入场动画条件为假；现在在发布状态前保存 `previousState` 并计算条件。
- 未回消息路由默认继承磨砂背景，默认配置约 67% alpha；`AllAccountsUnread` 与 `SingleAccountUnread` 现在明确禁用该根背景，普通会话仍保留用户配置。
- 共享 `FloatingWorkspaceTopAppBar` 现在通过 Material 3 `windowInsets = WindowInsets(top = 30.dp)` 承载顶部空间，不再把 30dp 放在外层 padding。
- 定向测试和 app Kotlin 编译通过；模块全量测试等待重试后仍有 25 个与本轮无关的既有契约失败。

## 本轮补充：surface 与全尺寸

- 最新视觉契约替代此前的透明根要求：所有真正的 floatActivity/全屏工作区根背景使用 `MaterialTheme.colorScheme.surface`。
- `FloatingBottomPanel.kt` 是聊天内全屏功能的共享宿主；`isFullscreenWorkspace` 需要覆盖 `isCenteredToolFeaturePanel()`，使工具工作区走 `fillMaxSize()` 而非受限的 `widthIn/heightIn`。
- 底部输入抽屉、提示气泡、眨眼采集小窗口不是全屏工作区，不能被该规则扩大。
- 根因已确认：`FloatingBottomPanel` 仅把一部分模式判为 `isFullscreenWorkspace`，其余 `isCenteredToolFeaturePanel()` 模式被限制在 330-430dp 宽、560dp 高；同时全屏宿主显式使用 `Color.Transparent`。
- 需保持主聊天普通会话的可配置磨砂背景不变。用户所称的 floatActivity 是右侧 iconButton 打开的工作区，不包含常规会话本体。
- 独立功能入口的根 `Column`/`Box` 也显式透明，必须改为 `MaterialTheme.colorScheme.surface`；内部按钮、长按遮罩、媒体预览等透明层不在本轮范围内。
- 新的模式级根因：旧 `isCenteredToolFeaturePanel()` 同时控制外层尺寸、居中对齐、遮罩和输入栏可见性，导致子页面即使调用 `fillMaxSize()` 也只能填充受限父容器。
- 统一改用 `BottomPanelMode.isFullscreenWorkspace()` 后，非输入抽屉都由 `FloatingBottomPanel` 提供 `surface + fillMaxSize()`；输入栏可见性也通过同一判定收敛。
- 仍需收尾：礼物与 SCRM 表单内容自身缺少共享工具栏和 surface 根，AI 配置页仍有独立 30dp 占位，必须分别迁移后再做最终扫描。

## 素材库二级页面审计

- `MaterialLibraryActivityContent` 列表页已使用共享 `FloatingWorkspaceTopAppBar`，但详情页和编辑页仍以 58dp `Row` 自定义 toolbar，导致状态区、标题颜色和返回行为与 UI组件不一致。
- 归档操作仍通过 `AlertDialog` 展示确认，违反全屏悬浮工作区不使用 Dialog 的约束；改为列表页内联 `Surface` 确认条，保持当前页面和异步归档状态可见。

## 朋友圈发布请求对标补充

- IosFloat `OpenApiMomentAPI.publishMoment` 同时在根请求和 `payload` 发送 `sendSlow: true`；Android 现已在 `publishScrmMoment` 中保持相同行为。
- iOS 有位置时发送 `name/city/address/lat/lng/poiId` 全部字段。Android 的发表页将单一位置文本同步写入三个可读字段，并对默认 `lat/lng/poiId` 使用 `@EncodeDefault`，避免默认 JSON 配置将这些字段省略。
- `partVisible` 与 `notVisible` 仍沿用 iOS 实际发送值；接口文档中出现的 `whoVisible/whoInvisible` 与 iOS 已接入值不一致，因此本轮以真实 iOS 请求为准并保留定向 JSON 契约。
- 详情页与编辑页的内容列表已经使用 `weight(1f).fillMaxWidth()`，只需替换顶部承载，不改动 SCRM 请求逻辑。
