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
# 悬浮聊天性能优化发现（2026-08-17）

## 用户要求

- 以《卡顿原因.md》为主要调查线索，全方面优化悬浮聊天界面性能。
- 禁止改变消息渲染、接口逻辑、UI 样式、已实现功能或业务行为。
- 必须自行检测并持续完成到可验证的不卡顿状态。

## 初始发现

- 仓库根目录已有 `task_plan.md`、`findings.md`、`progress.md`，内容属于此前悬浮聊天相关协作任务；本轮必须保留历史。
- 当前 PowerShell 输出把既有中文内容显示为乱码，暂不对历史文件做转码，避免无关改动。
- 本轮属于性能问题调查，必须先形成稳定复现与根因证据，再进入生产实现。
- 方案设计技能通常要求逐段等待用户确认；用户已明确离开并授权自主完成，因此把该授权视为对严格禁区内最小性能方案的预先批准，仍会记录候选方案与取舍。
- 所有生产改动必须先有能够因缺失优化而失败的测试，并按单一假设逐项完成红绿验证。
- 实现策略遵循最小范围：不做邻接重构，不引入未请求的配置、fallback、功能或抽象。
- `PRODUCT.md` 已存在且有效，产品注册类型为 `product`；其原则明确要求工作区使用单一坐标系、Insets 仅在所属边界处理一次、UI 与业务端口分离。
- 仓库未提供 `DESIGN.md`；由于本次禁止修改 UI 样式，设计上下文缺失不会转化为视觉改动，也不自动生成新设计规范。
- 性能优化命令规范要求先测量、锁定最大瓶颈、再做前后对比；Web 专属建议不适用于当前 Android/Compose 工程，将映射为帧时间、主线程、重组和滚动性能证据。

## 《卡顿原因.md》初读结论

- 已有连续滚动基线：88 帧中 17 帧卡顿（19.32%），P90 150ms、P95 400ms、P99 2400ms；高输入延迟 121、慢 UI 线程 16、慢绘制命令 10。
- 已有媒体冷路径基线：首帧 550ms，并出现一次慢 UI、慢位图上传和慢绘制命令；需与持续滚动分开处理。
- 报告列出 26 个候选原因，其中最强证据集中于根组件失效范围、整棵 ComposeView 重建、同步本地加载、未读数据复制、嵌套非懒消息、坐标回写/连接线重绘与同步媒体准备。
- 用户禁区直接排除了修改消息气泡/消息渲染样式、改变接口请求或响应逻辑、滚动中使用低成本替代样式等方案。
- 可继续调查的独立域：未读数据派生、坐标/连接线状态写入、控制器窗口生命周期、UI 状态隔离、媒体准备线程与全屏面板底层工作。

## 代码检索证据

- `allAccountHomeConversation()` 当前对 contacts、groupContacts、messages 分别执行一次 `flatMap`，每类都创建中间/结果集合。
- `homeOverviewMessageGroups()` 当前使用 `LinkedHashMap<Pair<String?, String>, HomeOverviewMessageGroup>` 保序，但同组每追加一条消息都执行 `existing.copy(messages = existing.messages + message)`；同组消息多时累计复制呈平方增长。
- 右侧账号轨通过 `snapshotFlow` 把每次可见项布局映射为新的 `RightRailVisibleAccountItem` 列表，再调用 `updateVirtualAccountAvatars()`；viewport 的 `onGloballyPositioned` 同时写本地 Compose 状态和 connector 状态，需进一步检查等值短路是否完整。
- 根 UI 的 `inputText` 由 `FloatingChatOverlayUi` 顶层状态持有，并同时传给正常输入栏和底部抽屉 composer；单纯下沉状态可能影响插入快捷内容、AI 文本、引用与双入口一致性，属于高风险重构，不能凭静态报告直接实施。
- 当前已有连接线状态单测和 controller/mount/animation 契约测试，可在现有边界新增红灯测试，而无需修改消息 renderer。
- 连接线状态并非完全未优化：当前实现已有 `updateIfChanged`、`updateVirtualIfVisuallyChanged`，并有测试证明虚拟头像批量只失效一次、相同边界不递增版本、同一离屏固定边缘移动不触发失效；报告中的“每次回调必重绘”需要按当前代码重新验证，不能重复实现已有优化。
- `ConnectorOffscreenIndex.fromMessages()` 为每个消息索引保存 `seenBefore.toSet()` 与 `seenAfter.toSet()`，构建期可能累计复制大量集合；该索引若随消息变动重建，可能是独立的 O(n²) 内存/CPU 热点，需要检查其 `remember` 生命周期和可替代表示。
- `CoordinateChatBody.kt` 对同一 `(homeOverviewVisible, visibleMessages, accountIdsByMessageId)` 依赖分别调用两次 `homeOverviewMessageGroups()`：一次生成 messageId→connectorId 映射，一次保存分组列表；这会把已知分组分配热点直接翻倍。该文件当前有用户未提交改动，必须先查看 diff 再决定是否触碰。
- `ChatConnectorLayer` 的 Canvas 每次绘制都会为普通会话每个可见 item 创建 `listOfNotNull`，并为每条可见消息重新构建 `ConnectorTargetKey`；这些 key 只依赖消息/选择/账号配置，可安全移出逐帧路径并按依赖缓存。
- 每个 connector group 的逐帧几何链会执行：bubble `map + sortedBy`、branch Y `map + listOfNotNull`、message branch `map`、hook pair `listOfNotNull + map + sortedBy`、hook `mapIndexed`、trunk segment list，再创建多类 data class；现有几何测试已经精确锁定输出，可在保持像素几何不变的条件下减少中间分配。
- Canvas 已复用 `Path`、Paint、分组 map/list/set 和 offscreen destination map，因此优化应聚焦尚未复用的 key/单项 list/几何中间集合，避免重复已有工作。
- `ConnectorCoordinateState` 的用户/账号/消息/viewport 更新全部已有精确 `Rect` 等值判断；滚动时 bubble 坐标真实变化仍会合理递增 version。不能通过阈值节流或延迟更新来减少失效，因为那会改变连接线跟手语义。
- `retainMessageBounds()` 使用 `filterNot` 产生临时 key 列表，但它属于消息集合变化路径而非逐滚动帧，可作为次级分配优化，优先级低于 Canvas。
- 现有 `FloatingChatMessageUiContractTest` 已锁定未读分组的首次出现顺序、同联系人/账号聚合和消息顺序；可以复用这些行为断言，新增大规模性能红灯而不改变业务输出。
- Controller 构造时仍同步执行四类持久化读取（localMessages、momentPosts、contactProfiles、groupProfiles），随后立即启动 SCRM runner/refresh；其中本地消息已有测试限制为仅加载当前选中线程，报告中“读取全部联系人线程”已过期，但同步首帧 I/O 仍存在。
- `showState()` 对同状态非 force 已有直接返回和 Z-order 刷新；force 路径会无条件 `dismissView()` 并重新创建 lifecycle owner、ComposeView 与整棵内容。已确认 force 至少由收藏发送和权限结果触发，是否能转为 runtime state 更新需要继续追踪所有调用点。
- Collapsed→Expanded 非 force 已有 `restoreRetainedExpandedView()` 快路径；不能把报告中的“每次展开都重建”直接泛化到当前代码。
- SCRM 响应当前先 `runtimeState.deliverConversationUpdate()`，且 `scrmConversationRefreshRecreatesExpandedFloatingChatOverlay()` 明确返回 false；报告中“API 回填必重建 overlay”已过期。
- `floatingChatShouldRefreshExpandedWindowZOrder()` 也恒为 false；正常同状态 show 不会 remove/add 窗口。force 仍用于外部本地消息、权限恢复和外观刷新，应按原因区分，不能整体删除。
- 并行审计发现未读总览仍在 `CoordinateChatBody` 构建 `ConnectorOffscreenIndex`，而 `ChatConnectorLayer` 的 homeOverview 分支明确不读取该索引；索引当前复制每个位置的前/后缀 Set，这在未读总览是零收益纯浪费。
- `homeUnreadThreadSummaries` 候选线程路径会逐线程调用 `visibleMessagesForThread` 并每次过滤整份 messages，形成 O(T×M)；仓库已有 `AccountScopedMessageIndex` 模式可复用以保持筛选语义。
- 未读总览还存在计算后未消费的 `homeUnreadAvatarContacts`，以及总览场景先扫描得到 `threadMessages` 随后丢弃的路径，需结合 `CoordinateChatBody` 当前用户 diff 判断能否安全移除。

## `CoordinateChatBody` 冲突复核

- 当前未提交 diff 只在消息点击处增加 `messageSupportsVoiceTranscription()` 判断（约第 420 行）；性能热点位于 195～309 行，范围不重叠，可用小补丁保留用户改动。
- `homeUnreadAvatarContacts` 在 212～214 行创建后没有任何读取，删除只移除死计算。
- `threadMessages` 无条件调用 `visibleMessagesForThread()`，但 home overview 的 `visibleMessages` 随即改用 summary messages；可在 homeOverviewVisible 时跳过扫描，输出不变。
- 两次 `homeOverviewMessageGroups()` 依赖完全相同，可先计算 groups，再由 groups 单次构建 connectorId map，保持分组和映射一致。
- offscreen index 无条件按 `visibleMessages` 构建；home overview Canvas 会清空 offscreenEdges，故该分支可返回共享空索引，不改变连接线。
- 空离屏索引当前可用 `ConnectorOffscreenIndex(emptyList(), emptyList())` 表达；后续若压缩索引结构，可提供单例 `empty()`，避免总览重组重复创建壳对象。
- 离屏索引构建逻辑在 `ConnectorCoordinateState.fromMessages()` 和 `OffscreenConnectorTargetResolver.buildOffscreenConnectorIndex()` 重复存在，实际 UI 使用后者；本轮不做无关清理，测试和实现聚焦真实调用点。
- `AccountScopedMessageIndex` 已有“大历史只读取一次”的 `CountingList` 测试模式，适合给未读 summary 新增确定性红灯：断言源消息元素读取数量为 O(M)，避免用不稳定毫秒阈值。

## 未读索引语义

- `homeUnreadCandidateSelections()` 自身顺序扫描消息一次并按首次出现线程保序；当前随后每个候选再次调用 `visibleMessagesForThread()`，造成主要重复读取。
- 不能直接把现有 `AccountScopedMessageIndex` 用于未读 summary：其无显式线程 fallback 有数量截断与“first global connected”兜底，可能给没有消息的候选线程错误复用其他线程消息。
- 新索引必须精确复刻 `FloatingChatPrototype`：默认群取 `threadContactId == null || == defaultGroupId`；其他群取精确 thread；私聊优先精确 thread，只有精确为空时才按原顺序合并 null-thread 的匹配 User、匹配 Account 和 None。
- 确定性复杂度测试可断言 `CountingList` 元素读取不超过 `2M`：一次建立候选，一次建消息索引；旧实现约为 `(T+1)M`，无需依赖机器时间。

## 工作区状态

- 最近提交集中在消息渲染和接口完善，最新提交为 `6071623`（2026-08-16）。
- 工作区存在大量未提交的消息渲染、媒体和 SCRM 相关改动，包括 `MessageRow.kt`、多个 renderer、`NativeMediaSurface.kt` 和 `ScrmFloatingChatBridge.kt`；均视为用户或其他协作者所有，不覆盖、不回退。
- 本轮优先选择不触碰这些重叠文件的可验证优化；若根因只能在重叠文件中修复，必须先精确比较并保留现有改动。

## 性能验证环境

- 已连接真机：OnePlus 9（LE2110，ADB 设备 `d0512adb`），可执行真实安装、滚动与 `gfxinfo`/Perfetto 验证。
- 现有 macrobenchmark 仅覆盖应用 `coldStart()`，不包含悬浮聊天打开、输入或滚动，因此不能直接证明聊天优化。
- 原始滚动 artifact 的 GPU P90/P95/P99 仅为 8/9/11ms，而 UI 帧 P90/P95/P99 为 150/400/2400ms，支持主要瓶颈在 UI/输入排队而非持续 GPU 吞吐；`Total ViewRootImpl = 3` 也说明测试时存在多个窗口根。
- 原始 artifact 显示 GPU 内存 17.73MB，与报告中媒体冷路径的约 80.73MB 属于不同场景，后续必须分别测量。
- 真机已安装 `com.paifa.ubikitouch`，`UbikiAccessibilityService` 当前处于 Enabled services，可直接使用仓库的 `scripts/adb-smoke-test.ps1` 与 `docs/ADB_TESTING.md` 中边缘滑动坐标打开悬浮聊天。
- 真机当前已有一个全屏 `com.paifa.ubikitouch` accessibility overlay，窗口 flags 包含 `BLUR_BEHIND`、`HARDWARE_ACCELERATED`，`blurBehindRadius=54`，且与 MainActivity 同时可见；这验证了报告中的额外窗口合成条件，但模糊属于现有 UI 样式，不能在本轮通过关闭它换取性能。
- 设备物理分辨率为 1080×2400，仓库脚本默认滑动坐标与设备匹配；脚本本身只验证默认边缘返回动作，并非聊天滚动基准。

## 真机视觉基线

- `artifacts/device-before.png` 确认当前全屏悬浮聊天已展开，中央消息列表、左右轨道、连接线、顶部工具区和底部输入栏均同时可见。
- 当前会话包含文本、位置、语音通话和视频通话等多种消息，适合作为“不改变消息渲染”的回归截图基线。
- 中央滚动手势可限定在约 x=500、y=700..1700，避开左右轨道与底部输入栏；后续固定相同坐标和次数采集 `gfxinfo`。

## 优化前滚动基线

- 固定场景：清空 `gfxinfo` 后，在中央消息区 x=520 交替执行 12 次 y=1650↔650、260ms 的滑动。
- 第 1 轮：350 帧，151 帧卡顿（43.14%）；P50/P90/P95/P99 为 26/61/93/150ms；Missed Vsync 85，高输入延迟 371，慢 UI 148，慢绘制 140，慢位图上传 0。
- 该轮持续 GPU/图片并非主导，慢 UI 与慢绘制几乎逐卡顿帧出现；需要另外两轮确认重复性。
- 第 2 轮：520 帧，153 帧卡顿（29.42%）；P50/P90/P95/P99 为 23/34/38/57ms；高输入延迟 700，慢 UI 153，慢绘制 147。
- 第 3 轮：562 帧，137 帧卡顿（24.38%）；P50/P90/P95/P99 为 22/34/36/44ms；高输入延迟 814，慢 UI 136，慢绘制 134。
- 三轮均明显超过 5% 目标；首轮存在冷路径峰值，暖态仍稳定在 24.38%～29.42%。后续比较应同时报告首轮和两轮暖态，不用单轮最佳值掩盖差异。
- 已定位现有性能/契约测试入口：`benchmark/.../FloatingChatBenchmark.kt`、`ConnectorCoordinateStateTest.kt`、`FloatingChatConnectorGeometryTest.kt`、`FloatingChatOverlayMountStateTest.kt`、`FloatingChatOverlayControllerContractTest.kt` 和动画契约测试。

## 约束分类

| 类型 | 内容 |
|---|---|
| 用户约束 | 优化必须完整、自主验证并直接交付 |
| 禁区约束 | 消息渲染、接口逻辑、UI 样式、功能和业务均不可改变 |
| 基底约束 | Android/Compose 生命周期、主线程和状态一致性必须遵守 |
| 代偿约束 | 优化不能以可见延迟、数据陈旧、错误吞没或功能降级换取 |

## 连接线直接 writer 子任务

- 父代理已批准架构：纯 `CommandSink` writer 负责稳定排序与 move/line/quad 命令，旧 geometry 仅作为测试 oracle；生产侧使用 remember 的 Path adapter 和 primitive scratch。
- 强制等价场景包括用户/账号、多条乱序 bubble、上下离屏、avatar 离屏与 group tree；浮点命令必须逐项完全一致。
- 禁止修改 `ChatConnectorGeometry`、Paint、绘制顺序或 UI；生产 draw 期不得创建 sink 或 lambda。
- oracle 先将 bubble anchor 钉到 viewport 后按 y 稳定排序；trunk 范围合并可见 anchor、上下离屏边界、avatar 原始/钉边 y。
- brace hooks 按 center.y 稳定排序；首 hook 的 verticalDirection 为 +1，其余为 -1；半径同时受请求半径、横向空间和相邻中心半间距约束，最小为 1px。
- 当前生产层仍维护 `MutableList<ChatConnectorBranch>` 的 group member 直接分支，并由 group tree helper 构造 Tree/Brace/Branch 对象图，第二阶段必须一并移除 draw 期对象分配。
- 最小实现可只维护按 `(x,y)` 成对存储的可扩容 `FloatArray`：先稳定插入排序 bubble anchors，再把 avatar hook 视为插入到首个 `anchorY >= avatarY` 的位置，可完全复现旧 `sortedBy` 的同 y 稳定性而无需第二组对象。
- 旧 Path 命令顺序是 trunk 的 `moveTo/lineTo` 在前，再按 center.y 排序的 hooks；每个有效 hook 为 `moveTo(curveStart)`、`quadTo(center,horizontalStart)`、`lineTo(branchEnd)`。
- production 适配器设计为 remember 的 `PathConnectorCommandSink`，writer 和 sink 均不在 draw 帧内构造；group-member 直线由同一 sink 直接写入，移除 `ChatConnectorBranch` 列表。
- 边界审查发现：无可见 bubble 且 avatar 离屏时，旧 tree 只把钉边后的 avatar y 纳入 trunk；writer 不能用原始 avatar y 初始化范围。已先加入该 oracle 场景，再按 branchYs 原顺序累计。
- 本地 Compose 1.7.6 源码确认 `Rect.center.y` 的公式是 `top + height / 2.0f`；writer 使用 `top + (bottom - top) / 2.0f` 保持相同浮点运算顺序，并把参数化场景改为小数坐标验证。
- 当前 layer 与新 writer 的禁用符号扫描为零匹配：生产链不再出现旧 tree/brace/rounded helper、Branch 或 Offset 构造；两个 Path sink 和 writer 均在 Canvas 外通过 `remember` 创建。
- 新 writer 使用同一 scratch 顺序复用处理 group tree 与各 connector key；group-member 直线虽然在可见消息遍历时直接写入，但仍落在独立 `connectorPath`，最终保持 tree path 先绘、direct path 后绘的旧顺序。

## 第二阶段补充审计

- Phase 1 APK 的逐 ViewRoot framestats 已补齐同协议三轮：`round2`、`round3`、`round4`，均使用同一设备、同一聊天、x=520、y=1650↔650、260ms 的 12 次往返滚动，并在每次 swipe 后保留 120ms 主机间隔；`round1` 总帧数不同，仅作为历史样本。正式对比必须按匿名 overlay ViewRoot 单独复算，不能用进程级混合数据替代。
- 设备 framestats 每个 ViewRoot 的 Total/Janky/HISTOGRAM 汇总覆盖整段采样，但详细 `PROFILEDATA` CSV 环形缓冲只保留最近 120 帧；报告可以使用逐根汇总全量比例，逐阶段分解只能声明为最近 120 帧窗口。
- 普通消息滚动时，气泡真实坐标变化仍会写 `messageBubbles` 并递增连接线版本，这是连接线逐帧精确跟随所必需，禁止节流、阈值过滤或延迟补线。
- 总览头像轨在组合期读取全局 `connectorState.version`，会被头像、viewport 等非消息 bounds 更新误失效；低风险候选是拆分 message-bounds 与 anchor 版本，让头像轨只订阅 message-bounds 版本，同时保持 Canvas 订阅全部变化。
- 将总览头像连续位移移到布局阶段可能进一步减少重组，但风险高于版本拆分；只有 Phase 2 同源帧数据仍不达标且先取得严格位置、显隐与重组红灯证据时才实施。
- `retainMessageBounds()`、左右轨 `snapshotFlow` 和根 `messageListScrolling` 不是中央滚动逐帧热点；独立媒体 bounds state 位于消息渲染禁区，本轮不触碰。
- 扩大回归中的 4 项失败与交接的既有失败集合一致：群成员期望 4 人但当前数据为 8 人、运行时线程 ID 出现既有双前缀、左轨底部 padding 旧期望为 294 但实现返回 238、右轨旧期望 15 个工具但当前枚举返回 23 个。相关生产文件没有本轮性能差异，禁止为清零而回退现有业务。
- Phase 1 正式同协议 cohort（round2～4）全部来自匿名 overlay ViewRoot，MainActivity 为 0 帧：合计 1809 帧、294 帧 deadline missed，jank 为 16.25%；单轮为 19.25%、16.64%、13.63%，P95 为 32/29/29ms。详细 CSV 只覆盖每轮最近 119 个有效完成帧。
- Phase 2 APK 构建时间为 2026-08-17 09:10:16，大小 83,899,466 字节，SHA-256 为 `81EC98F6D4861690524C1B8A2A7CD80EA6B1ACFE51AD7783799A0B3F0C5311DD`；设备安装时间为 09:10:44。
- 安装后全屏 accessibility overlay 已恢复，窗口仍为 `fillxfill`、`BLUR_BEHIND`、`HARDWARE_ACCELERATED`、`blurBehindRadius=54`；`device-after-phase2-expand.png` 显示会话、消息、轨道、连接线和输入栏均在原位置。

---

## Phase 2 性能反证与审查结论（2026-08-17）

- Phase 2 三轮 overlay 全程合计 `1677` 帧、`310` 帧 jank，卡顿率 `18.49%`；Phase 1 正式 cohort 为 `1809` 帧、`294` 帧 jank，卡顿率 `16.25%`。绝对增加 `2.23pp`，当前结果不能宣称优化完成。
- Phase 2 合并 CPU 分位为 `19/28/30/40ms`，GPU 为 `6/11/13/16ms`；与 Phase 1 相比没有持续 GPU 吞吐变化。Slow UI 与 Slow issue draw 大量同帧出现，下一步需用 FrameTimeline/Perfetto 关联 UI 到 RenderThread，`gfxinfo` 本身无法继续归因到函数。
- `simpleperf` 初步报告显示 Lazy 预取/首次组合、measure/layout 和纹理上传均有样本，但大量 app 代码处于 ART interpreter；在获得仓库自有方法调用链前，不据此修改 Lazy 行为或消息 renderer。
- 第一阶段独立审查发现异步语音改造把 `MediaPlayer()` 构造移到 `runCatching` 外，改变旧错误边界。新增契约已先红；生产实现现把构造放回保护块，并在构造后配置失败时继续释放实例，定向测试转绿。
- direct writer 审查所报中心点 1 ULP 偏差不成立：当前 `androidx.compose.ui:ui-geometry-android:1.7.6` 的 `Rect.center` 源码为 `top + height / 2.0f`，与 writer 的 `top + (bottom - top) / 2.0f` 同序；审查给定分数坐标加入旧几何 oracle 后仍通过，未修改生产公式。

## Perfetto 根因证据（2026-08-17）

- 固定 12 次往返滑动的 15 秒 Perfetto trace 已保存在 `artifacts/ubiki-chat-phase2-scroll.perfetto-trace`，使用官方 Trace Processor v57.2 本地解析，未上传设备数据。
- 目标进程的主线程记录 `99` 次 `Recomposer:recompose`，总计 `895.954ms`，平均 `9.050ms`，最高 `41.492ms`；其中 `Compose:recompose` 最高 `23.040ms`。这是当前最强的可操作根因证据。
- 同一 trace 中 `AndroidOwner:measureAndLayout` 仅 `26` 次、平均 `2.608ms`、最高 `3.071ms`；常规 `draw` 最高 `5.688ms`，触摸处理最高 `3.944ms`。因此不以节流坐标、降低绘制频率或修改输入手势作为下一步方案。
- RenderThread 全屏绘制平均 `3.738ms`、最高 `10.405ms`，存在 GPU wait 峰值但不足以单独解释大量主线程长帧；优先回溯重组失效范围。
- Profile 还显示 `MessageRow` 内 `RenderEffect.createBlurEffect`，但该路径属于用户明确禁止的消息渲染与样式范围，仅记录为残余风险，不修改。

## LazyList 预取假设（2026-08-17）

- Compose BOM `2024.12.01` 解析为 Foundation `1.7.6`。官方 `LazyListState` 源码说明其默认 `LazyListPrefetchStrategy` 会在滚动期间预组合相邻 item，且可由 `rememberLazyListState(prefetchStrategy = ...)` 替换。
- 当前同协议 simpleperf 报告中 `AndroidPrefetchScheduler.run` 占 `7.85%` CPU 样本；调用图进入 `LazyLayoutItemContentFactory`、`MessageCoordinatePane` 项 lambda 和 `MessageRow`。这与 Perfetto 中的 Compose 重组成本相符。
- 假设：对单条消息组合成本较高的聊天列表，默认滚动预取在当前设备上与可见帧争用主线程；不请求预取可减少 `Recomposer:recompose` 长帧。该假设尚未验证，必须先红灯契约、单变量 A/B 和真机帧率复测。若可见帧或三轮帧率恶化，撤销该候选。

## 全屏悬浮聊天 toolbar 续作（2026-08-17）

- 用户最新明确布局覆盖 toolbar 局部“不得改 UI”的旧限制：左右各一个等权 Box；左侧视觉顺序为返回、标题、编辑，右侧视觉顺序为二维码、账号、搜索；标题和账号分别约五、三个中文字符宽度并使用走马灯。
- 真正显示的 header 是 `FloatingChatOverlayUi.kt` 的 `FloatingChatWorkspaceHeader`，它通过 `FloatingChatRuntimeSections.topContent` 挂载；`CoordinateChatBody.ChatTopToolbar` 在唯一生产调用中传入 `showTopToolbar = false`，因此修改后者无法满足真机需求。
- `FloatingChatWorkspaceHeader` 当前复用 `FloatingWorkspaceTopAppBar`，后者负责 `surface`、30dp `WindowInsets` 和默认高度。新布局应扩展共享 AppBar 的内容槽，而不是在外部另起 toolbar，确保现有全屏工作区视觉基线不变。
- 私聊现有编辑回调设置 `contactEditorTarget = ContactEditorTarget.User(contact)`，由 `ProfileEditorOverlayHost` 打开已有全屏联系人资料页；群聊已有路径设置 `groupInfoWorkspaceTarget` 并打开 `BottomPanelMode.GroupInfo`。两条路径不调用新接口。
- 联系人资料页已有备注持久化入口 `LocalContactProfile.remark` 与 `profilePersistenceActions.updateContactProfile`。备注输入层可在同一 Compose 根上调用该既有持久化入口，避免 Android Dialog Window 与 `BadTokenException` 风险。
- Foundation 1.7.6 的 `basicMarquee()` 默认仅运行三次，且内容不超宽时不启动，动画通过局部 layer 放置而不逐帧失效父级；适合本次固定宽度标题/账号需求。

## 本人消息撤销（2026-08-17）

- 消息点击与长按共用 `MessageLongPressMenuOverlay`；操作列表由 `messageLongPressActionsFor` 统一生成，适合在此限定 `fromMe && remoteMessageId > 0`。
- 真实接口已存在：`ScrmMessageOperationApi.revokeMessage` 请求 `POST /openapi/v1/messages/{messageId}/revoke`，路径必须使用 `FloatingChatMessage.remoteMessageId`。
- 语音转写已经实现消息账号作用域解析、任务并发保护、后台轮询和成功刷新，可复用其宿主模式，但撤回采用独立小型适配器，避免改动已工作的转写状态机。
- 悬浮根已有不创建第二个 Window 的约束；撤销确认应使用根内遮罩与 Material Surface，而非 Android Dialog Window。
- `ScrmApiClient.revokeMessage` 已正确映射为 `POST /openapi/v1/messages/{messageId}/revoke`；新增契约验证设备 UUID 与微信账号只进入 JSON body。
- 扩大消息回归的唯一失败来自 HEAD 既有源码字符串断言：生产代码为 `MessageRenderState(selected = multiSelectMode)`，测试仍查找 `selected = selected`，不经过本轮撤销路径。

## toolbar 复核补充（2026-08-17）

- 25dp IconButton 只是在语义节点中表现为图标大小，实际生产代码还显式关闭了 `LocalMinimumInteractiveComponentSize`；因此窄屏点击风险是真实命中区域风险，不是单纯截图误差。
- 修复采用默认 IconButton 交互尺寸，并让标题 Box 在左右等权半区的剩余宽度内 `widthIn(max = 110.dp)`，避免 360dp 窗口中左侧内容越界。
- Compose 测试中 `hasContentDescription` 命中的是合并后的图标语义节点，不能直接用其 bounds 断言外层 48dp；改用四个控件实际 `performClick` 回调验证，尺寸由源码契约和设备截图复核。
- `FloatingChatRuntimeSections` 上的既有 `clearAndSetSemantics` 会清除后代语义。全局移除会改变悬浮窗语义隔离并扩大消息树，本轮按“不改既有功能/性能边界”保留，作为残余可访问性风险记录。

## 本轮错误记录（2026-08-17）

| 错误 | 根因 | 处理 |
| --- | --- | --- |
| `ContactProfileEditorOverlayHost.kt` 找不到 | 文件实际名为 `ProfileEditorOverlayHost.kt` | 使用 `rg` 定位真实文件后继续，只读无源码变更 |
| `assertWidthIsAtMost` unresolved | 当前 Compose UI test 依赖没有该 matcher | 删除不存在的 matcher，保留有效点击/静态契约 |
| `matchParentSize` unresolved | 当前 Compose 版本未暴露该扩展 | 改用已有 `fillMaxSize()` 遮罩层 |
| 单测编译找不到 `messageOperationAccountId` | 共享工作区增量编译缓存未吸收协作者最新源文件 | 使用 `compileDebugKotlin --rerun-tasks` 强制重建，确认源码可编译 |
# 2026-08-17 创建群聊发现

- 扫码全屏页由 `ToolbarWorkspaceFullScreen` 渲染，当前已有“扫一扫”“添加好友”“扫码加群”。
- 联系人页 `ScrmContactsPanel` 已有完整建群流程：加载真实联系人、多选、提取去重 wxid、调用 `POST /openapi/v1/chatrooms` 并通过 `ScrmContactTaskRunner` 等待任务结果。
- 最低风险路径是给联系人页增加直接打开建群模式的参数，由扫码页导航过去；无需新增接口客户端或复制任务轮询。
- 全屏视觉基准是 `FloatingWorkspaceTopAppBar`、`MaterialTheme.colorScheme.surface` 和根节点 `fillMaxSize()`。
## 2026-08-18 群聊聊天信息界面发现

- 当前 `GroupInfoHost` 已接入改群名、公告、备注、群内昵称、消息通知、置顶、保存通讯录、二维码、邀请/移出成员和退群接口。
- 当前 `GroupInfoScreen` 仍是“资料/成员/设置”三 Tab，不符合单页 5 列设置列表规格；二维码调用后也未消费 `ScrmContactTaskOutcome.data`。
- 共享 `FloatingWorkspaceTopAppBar` 已通过 `WindowInsets(top = 30.dp)` 保留状态区，根 `FloatingChatOverlayUi` 已提供自下而上进入、自上而下退出动画。
- 模块已依赖 ZXing 3.5.3，可直接从后端二维码内容生成 Bitmap，不需新增库。
- 工作树正在进行包目录迁移：旧 `com/paifa/ubikitouch` 路径删除，新 `com/paifa/univerge` 路径未跟踪，但 Kotlin package 暂仍是旧命名；本轮只编辑新目录中的实际源码。
## 2026-08-18 包名前缀迁移发现

- `app` 主源码已移动到 `src/main/java/com/paifa/univerge/app`，但 `app/build.gradle.kts` 仍是旧 namespace/applicationId，Manifest 仍混有旧 accessibility 组件名和 taskAffinity/action。
- `ubiki-accessibility` 主源码已在 `src/main/kotlin/com/paifa/univerge/accessibility`，但 package/import 文本仍约 2993 处旧前缀；旧目录已不存在，导致部分源码契约测试按旧路径读取失败。
- `ubiki-core` 和 `ubiki-overlay` 主源码尚未移动，仍在旧目录；模块 namespace 也仍为旧前缀。
- `adbcore` 使用独立 `com.adbcore`，不属于 `com.paifa` 前缀，不应擅自改名。
- 生成目录 `build/bin/.gradle/.cxx` 不参与迁移，避免污染或修改二进制产物。

### 结果

- 主工程模块名为 `univerge-core`、`univerge-overlay`、`univerge-accessibility`，应用 id 为 `com.paifa.univerge`。
- 代码和配置范围内旧前缀残留为 0；文档中的旧安装包记录保留为历史事实。
