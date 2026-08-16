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

## 已同步语音消息转文字链路

- 接口为 `POST /openapi/v1/messages/{messageId}/voice-trans-text`，其中 `messageId` 对应 SCRM 主键 `FloatingChatMessage.remoteMessageId`；`remoteMessageServerId` 是微信 `msgSvrId`，不能作为路径参数。
- Android 已有 `ScrmMessageOperationApi.transcribeVoiceMessage` 和 HTTP 实现，但生产 UI 尚无调用点；旧的 SCRM 操作预览只组装请求且误用了 `remoteMessageServerId`。
- 真实入口应放在消息点击后的首层 `MessageLongPressMenuOverlay`，并仅对已同步语音消息显示；本地待发送录音没有服务端消息 ID，确认层中的“转文字”是死入口，应移除。
- 转写接口返回异步任务，必须轮询任务状态；任务完成后刷新聊天。刷新链还需确保同 ID 新消息替换缓存旧对象，并让 Voice 详情优先展示 `voiceText`，否则结果会被旧 content 遮住。
- 消息操作已经拆分为 `MessageLongPressMenu`、`MessageLongPressActions` 与 `MessageInteractionOverlayHost`，新增行为应沿这三层传递，避免把业务请求塞进可组合菜单本身。
- SCRM 层已有 `ScrmContactTaskRunner`、`ScrmMomentTaskRunner`、`PaymentTaskRunner` 等轮询范式，并统一使用 `resolveScrmTaskResult` 解释服务端终态；语音转写执行器应复用这套状态语义。
- `FloatingChatOverlayUi` 已集中构造 `MessageLongPressActions`，适合作为 UI 与转写执行器的唯一连接点；`MessageLongPressMenuOverlay` 只负责按消息筛选和渲染操作，业务调用仍留在 action 回调中。
- `ScrmSelectedSession` 当前显式暴露 read/contact/chatRoom/message/moment/task API，但缺少 `ScrmMessageOperationApi`。应把 message-operation API 作为具名依赖加入 session，由同一 `ScrmApiClient` 注入，避免运行时强制转换。
- `FloatingChatOverlayController` 已持有唯一的 `refreshScrmConversationFromApi()` 刷新入口，并在展开、切换账号和发送任务处理后复用。转写终态刷新应由 Controller 以回调注入 Overlay，避免 UI 复制缓存合并或网络刷新逻辑。
- 新增 `MessageLongPressAction` 不会破坏收藏列表操作分支，`FavoriteCollectionOverlayHost` 对非收藏专用操作已有显式 `else -> Unit`；主消息操作的 exhaustive `when` 则必须新增真实转写回调。
- 普通消息气泡的 `combinedClickable.onClick` 已直接调用 `onLongPressMessage` 打开同一操作层，Overlay 的 `onMessageClick` 也打开该层；因此新增首层 action 同时覆盖用户所说的“点击消息”和既有长按入口，无需新增第二套菜单。
- Overlay 已统一在 `Dispatchers.IO` 内加载 `ScrmSelectedSession` 和执行设备任务；语音转写应遵循同一协程边界，并由主线程更新 Toast/在途状态及触发 Controller 刷新。
- 并行新增的 `ScrmVoiceTranscriptionTaskRunnerTest` 已锁定执行器 API：`transcribeAndAwait(remoteMessageId, deviceUuid, weChatId)`，结果状态区分 `SUCCEEDED`、`SUBMISSION_FAILED`、`TASK_FAILED`、`PROCESSING`、`RESULT_UNKNOWN`，并验证只提交一次后轮询既有 taskId。
- `FloatingChatOverlay` 生产调用点只有 Controller 一处，新增 `onRefreshConversation` 注入不会扩散到多套宿主；现有源码契约测试已有双工作目录 `sourceFile` 模式，可用于验证真实 UI 接线而不启动悬浮 Window。
- 当前待发送录音确认层仍保留 `statusMessage/onTranscribe` 和只报“不支持”的死入口；最新需求已把真实入口指定到已同步语音消息选项，因此该状态、按钮与 Overlay 错误文案都应删除，确认层恢复“取消/发送”。
- `ScrmApiClient.transcribeVoiceMessage` 已正确复用 `postMessageOperation(messageId, "voice-trans-text", request)`，并对非正数 ID 失败；但现有测试没有断言真实 URL 和 JSON body，需要补充 HTTP 传输契约以防把 msgSvrId 误接到 path。
- 刷新链修复已保持最小范围：`scrmMergeReadOnlyMessages` 对相同远端 key 原位替换 incoming，同时保留本地独有消息；Voice 映射只调整为 `voiceText > content > 默认文案`，没有改变其他消息类型。
- Controller 的刷新门已经支持 in-flight 时排队下一次请求；Overlay 成功回调直接调用该入口不会打断正在进行的刷新，也无需创建新 Window 或重建悬浮根。
- UI 装配红灯已稳定落到两项缺口：`FloatingChatOverlay` 缺少刷新回调参数及真实 `onTranscribeMessage`，Controller 唯一生产调用点也未注入该回调。实现使用当前 `selectedAccount.id` 映射出的 `ScrmFloatingAccountRoute` 作为请求账号参数，并以 `remoteMessageId` 作为同消息并发键。
- 语音消息按现有展示策略进入 Card 分组，普通点击经 `onMessageClick` 设置 `longPressMessage`，长按经 `onLongPressMessage` 设置同一状态；两种操作均复用根节点内的消息选项层。UI 契约测试需精确匹配 `transcribe@` 标签，禁止 `substringAfter` 在缺失分隔符时静默退回整文件。
- 独立审查发现 PROCESSING 生命周期风险：runner 会保留已提交的 `taskId`，但当前 UI 结束协程后丢弃它；下一次用户点击会再次按 messageId 提交。应在明确终态前保留 messageId -> taskId，并复用原 taskId 查询，不能靠延长超时或再次写请求掩盖。
- 成功刷新还有缓存短路：`loadScrmConversationForSession` 先用 `scrmInitialConversationRoutesToLoad` 排除已缓存选中账号，导致 `loadScrmAccountConversation` 的 cache-hit changes 分支不可达。显式刷新必须始终加载选中 route；该加载函数自身再决定走 bootstrap 或 changes。
- IosFloat 没有专属 voice-trans-text 入口，不能照搬缺失行为；其通用消息操作以消息 `recipientAccountID` 优先、当前账号兜底，异步 helper 只轮询原 taskId 且未知结果禁止自动重发。Android 应沿用此归属语义，并保留比 iOS 更完整的 remoteMessageId 与 voiceText 展示链。
- 转写完成时的刷新必须携带任务开始账号；Controller 的 pending gate 还需保存待刷新账号 ID，否则并发刷新结束后无参重试会改为刷新当时当前账号。
