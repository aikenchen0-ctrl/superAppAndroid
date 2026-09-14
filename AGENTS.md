# Compose UI 工程规则

本文档是 `superAppAndroid` 及其 Compose 模块的执行约束。目标是让 Compose 负责声明式渲染和局部交互，让 View、无障碍服务、悬浮窗和业务状态保持清晰的 seam。新代码必须遵守；修改旧代码时，先保持行为，再逐步收敛到这些规则。

## 0. 证据来源与使用方式

本规范不是“记住几个 Compose 技巧”，而是以可复核资料为依据建立性能决策。证据优先级如下：

本轮检索日期：`2026-09-10`。官方网页、GitHub PR、Stack Exchange API 和可公开访问的社区入口均做了可达性核验；部分 Stack Overflow 页面直连受反爬限制，因此以 Stack Exchange API 的题目和链接为准。社区页面可能随时间变化，链接可达不等于观点已经被官方采纳。

1. Android Developers 官方文档、官方 Codelab、Android Developers Blog：作为 API 语义、生命周期和性能结论的首要依据。
2. AndroidX、Now in Android、Compose Samples 的源码与 PR：作为真实工程的结构和回归案例，必须结合当前版本重新验证。
3. Stack Overflow 等问答：用于发现常见误区和最小复现，不能单独证明某种方案在本项目一定更快。
4. V2EX、Linux.do、NodeSeek、Telegram 等社区：用于发现关键词、设备差异和实测线索；正文不可访问、只有标题或转述的帖子只记为线索，不得直接写成硬规则。

任何性能结论都要记录：设备/系统、Compose BOM、构建类型、数据规模、交互路径、测量工具和前后数值。没有这些信息，只能写“待验证假设”。

### 已核验的核心资料

| 主题 | 可执行结论 | 来源 |
| --- | --- | --- |
| 性能总览与诊断路径 | 先区分 composition、layout、draw 和主线程工作，再决定优化点；不要只看重组次数 | [Compose performance](https://developer.android.com/develop/ui/compose/performance) |
| 延迟读取状态 | 把变化状态尽量读到真正需要它的叶子节点；必要时用 lambda/`derivedStateOf` 缩小重组范围 | [Best practices: defer reads](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| 稳定性 | 使用 Compose Compiler stability reports；对确实不可变的数据建模，必要时使用 immutable collections 或 stability configuration，不要为了少重组滥用 `@Stable`/`@Immutable` | [Stability](https://developer.android.com/develop/ui/compose/performance/stability)、[Diagnose stability](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)、[Fix stability](https://developer.android.com/develop/ui/compose/performance/stability/fix) |
| Strong Skipping | Kotlin `2.0.20+` 默认启用 Strong Skipping；它不是取消状态建模和稳定 key 的理由，仍要避免错误的可变对象、过大的重组范围和 composition 副作用 | [Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping) |
| Compose 三阶段 | 让只影响绘制的高频值停留在 draw/`graphicsLayer`，避免把逐帧值提升成布局状态 | [Phases](https://developer.android.com/develop/ui/compose/phases) |
| Effect 纪律 | composition 中不直接做网络、写文件、导航或业务提交；使用正确 key 的 `LaunchedEffect`/`DisposableEffect` | [Side-effects](https://developer.android.com/develop/ui/compose/side-effects) |
| Lazy 列表 | 长列表使用 Lazy 容器；提供稳定唯一 `key`，混合 item 提供 `contentType`，不要把 index 当身份 | [Lists and grids](https://developer.android.com/develop/ui/compose/lists) |
| Compose 生命周期 | Composable 会进入/离开 Composition，不能把“组合过一次”当成永久生命周期；资源必须跟随生命周期释放 | [Lifecycle of composables](https://developer.android.com/develop/ui/compose/lifecycle) |
| View 互操作 | `ComposeView`/`AndroidView` 要定义 owner、composition strategy、factory/update 和释放顺序 | [Compose in Views](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views)、[Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose) |
| 状态与 state holder | UI state 由明确 owner 持有，Composable 通过不可变状态和事件读取；不要让页面函数成为所有业务状态的仓库 | [State holders](https://developer.android.com/topic/architecture/ui-layer/stateholders)、[State in Compose](https://developer.android.com/develop/ui/compose/state) |
| 运行时心智模型 | 组合可重启、跳过、取消，Composable 函数必须无副作用且可重复执行 | [Mental model](https://developer.android.com/develop/ui/compose/mental-model) |
| Lifecycle Flow | Activity/Fragment 中优先 `collectAsStateWithLifecycle()`；服务/Overlay 例外必须有明确宿主生命周期 | [Lifecycle-aware coroutines](https://developer.android.com/topic/libraries/architecture/coroutines#lifecycle-aware) |
| 性能验证 | 用 Macrobenchmark、Baseline Profile 和真实设备验证启动、滚动、交互延迟；不要以 debug 构建或主观流畅度下结论 | [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)、[Baseline Profiles](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)、[Compose performance Codelab](https://developer.android.com/codelabs/jetpack-compose-performance) |

### 已核验的工程案例与社区讨论

- [Now in Android PR #1964](https://github.com/android/nowinandroid/pull/1964)：搜索输入导致上层链路重组的案例。PR 讨论中有人指出“直接传 `State` 不是唯一答案”，因此本项目优先把状态读取延迟到叶子或传稳定 lambda，不能机械复制某一种参数形式。
- [Compose Samples PR #1606](https://github.com/android/compose-samples/pull/1606)：稳定性配置和 `@Immutable` 的实际改动，同时暴露了重复 `composeCompiler` 配置和 singular/plural DSL 混用等工程问题。稳定性配置必须和当前 Kotlin/Compose Compiler DSL 版本一起验证。
- [Compose Samples PR #1700](https://github.com/android/compose-samples/pull/1700)：社区 review 明确指出 composition 阶段直接执行 `removeFromQueue()` 是副作用；同时指出 Lazy 列表 key 不唯一会导致运行时崩溃，而不是“只是少了优化”。
- [Stack Overflow：延迟读取阶段](https://stackoverflow.com/questions/72457805/jetpack-compose-deferring-reads-in-phases-for-performance)：用于理解状态读取位置与重组/布局/绘制阶段的关系。
- [Stack Overflow：`remember` 与 `derivedStateOf`](https://stackoverflow.com/questions/70144298/compose-remember-with-keys-vs-derivedstateof)：用于区分“缓存计算结果”和“只在结果变化时通知”的语义；二者不能互换。
- [Stack Overflow：LazyColumn 卡顿案例](https://stackoverflow.com/questions/70592694/laggy-lazy-column-android-compose)、[重复 key 崩溃](https://stackoverflow.com/questions/69843588/android-compose-lazycolumn-illegalargumentexception-key-was-already-used)：用于回归清单，不把单一回答当作通用基准。
- [Android Developers Blog：Compose 1.10 性能改进](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)：官方说明可暂停组合和 Lazy 预取等运行时改进；升级 BOM 可能带来收益，但仍需在本项目真机和 release-like 构建上测量。

### 中文社区与开放社区检索入口

这些入口用于继续追踪“Compose 重组、稳定性、Lazy 列表、AndroidView、悬浮窗、低端机卡顿、基准测试”等关键词。当前无法稳定读取正文的站点不作为本规范的单独证据：

- [V2EX Android 节点](https://www.v2ex.com/go/android)
- [V2EX 热门主题 API](https://www.v2ex.com/api/topics/hot.json)
- [Linux.do 搜索 Compose](https://linux.do/search?q=Compose)
- [NodeSeek 搜索 Compose](https://www.nodeseek.com/search?keyword=Compose)
- [Telegram Android Developers 频道](https://t.me/s/androiddevelopers)
- [Telegram Jetpack Compose 频道入口](https://t.me/jetpackcompose)
- [Hacker News：Compose performance 讨论检索](https://hn.algolia.com/?q=Jetpack%20Compose%20performance)

推荐检索词：`defer reads`、`recomposition scope`、`stability configuration`、`strong skipping`、`LazyColumn key contentType`、`derivedStateOf overhead`、`snapshotFlow lifecycle`、`ComposeView disposal`、`AndroidView reuse`、`graphicsLayer draw phase`、`Macrobenchmark baseline profile`、`Compose low end device jank`、`悬浮窗 Compose 性能`、`重组范围`、`LazyColumn 卡顿`。

## 1. 适用范围与工程基线

- 适用于 `app`、`univerge-accessibility`、`univerge-overlay`、`univerge-heavy-drag-compose` 和任何包含 `@Composable` 的 Kotlin 源文件。
- 当前基线：Kotlin `2.0.21`、Compose Compiler `2.0.21`、Compose BOM `2024.12.01`、AGP `8.7.3`、`compileSdk 36`、`minSdk 26`、JDK `17`。
- 官方博客提到的 Compose 1.10/BOM `2025.12.00` 是后续性能参考，不代表本项目可以直接升级；升级前必须检查 Kotlin/AGP/Material/测试兼容性，并用同一真机基准比较。
- Android 构建必须使用 Windows JDK 17 直接启动 Gradle Wrapper；不要用 Bash/WSL 执行 `gradlew`。
- Compose 模块不得反向依赖 `app`。通用交互模块只能依赖它的 core contract 和平台 adapter；Aispect SDK 只能在 app 防腐层接入。
- 本文档是约束，不是要求一次性重写所有历史 UI。大文件拆分和性能治理按实际改动范围逐步完成。

## 2. 先确定是否真的需要 Compose

Compose 不是所有 UI 的默认最优解。开始修改前先回答：

1. 该界面是否需要高频状态更新、复杂局部交互或声明式组合？
2. 是否已有稳定的 View、相机预览、系统悬浮窗或无障碍宿主，迁移成本是否大于收益？
3. 是否需要复用现有 View 的测量、焦点、输入法或无障碍语义？

选择规则：

- 纯 Compose 页面优先使用 Compose。
- 已经是 View 树且只需局部声明式内容时，使用 `ComposeView`，并设置明确的 `ViewCompositionStrategy`；不要为了一个小区域重写整棵 View 树。
- 相机、播放器、WebView、系统控件等保留 View，通过 `AndroidView` 封装；资源生命周期必须由 `factory`、`update` 和宿主生命周期共同管理。
- 同一交互不要同时由 View listener、Compose pointer handler 和 SDK 各自消费。必须指定唯一事件入口，其他层只接收领域事件。
- `pointerInteropFilter` 只用于确实需要原始 `MotionEvent` 的适配层，例如 `univerge-heavy-drag-compose`；普通点击、拖动、滚动优先使用 Compose 原生 pointer API。

### Compose 与 View 的性能边界

Compose 不会自动优于 View。两者的主要成本不同：

| 场景 | 优先选择 | 原因 |
| --- | --- | --- |
| 静态表单、设置页、局部状态交互 | Compose | 声明式组合快，局部状态容易表达，开发和维护成本低 |
| 超长消息/联系人列表 | Compose `Lazy*` 或成熟 View 列表 | 两者都可虚拟化；关键是稳定 key、增量状态和 item 复杂度，不是框架名称 |
| 高频逐帧动画、相机/视频/地图、复杂自定义绘制 | View 或 Compose 的绘制层混合 | 需要直接控制绘制、硬件层或已有成熟组件；不要让每帧状态触发大范围重组和重新测量 |
| 系统悬浮窗、无障碍服务、跨窗口坐标 | 混合方案 | View 负责窗口和宿主生命周期，Compose 负责局部内容；必须显式管理 owner 和事件入口 |
| 既有稳定 View 且没有明确收益的页面 | 保留 View | 迁移本身会带来测量、焦点、输入法、无障碍和回归成本 |

Compose 难以超过 View 的常见原因不是 Compose 本身“慢”，而是：状态粒度过大导致大范围重组；在重组中重复筛选、排序或创建对象；测量回调不断写状态形成反馈；复杂 item 没有稳定身份；或把原本由 View 虚拟化/复用的内容一次性组合出来。优化顺序固定为：

1. 先确认瓶颈属于 composition、layout、draw、主线程 I/O 还是窗口/平台调用。
2. 缩小状态读取范围，拆分 Composable 和状态 holder，稳定列表身份。
3. 再减少测量和绘制成本，必要时把高频位移下沉到 `graphicsLayer`。
4. 最后才考虑缓存、`SubcomposeLayout`、自定义 Layout 或退回 View。

任何“Compose 比 View 快/慢”的结论都必须带设备、数据量、帧窗口、构建类型和基准结果；没有测量不得写成架构定论。

## 3. 状态分层：UI 状态、业务状态、瞬态事件

### 3.1 状态所有权

- Composable 是状态的读取者和事件发送者；状态所有权放在离交互最近且生命周期正确的 state holder、ViewModel 或宿主 controller 中。
- 页面级 Composable 只编排 state holder 和子树，不同时承担网络、持久化、模型推理、窗口管理和全部视觉细节。
- 对外暴露不可变 `UiState` 和事件函数；不要把 `MutableState`、可变集合或 controller 内部对象泄漏给子 Composable。
- `remember` 只缓存与 Composition 生命周期相同的纯 UI 对象。需要跨配置变化或进程恢复的状态使用 `rememberSaveable` 或持久化状态；需要跨页面/业务生命周期的状态放到 ViewModel/状态仓库。
- 不要用 `remember` 把一次性外部输入永久冻结。输入来自参数时，要明确 key，或用 `LaunchedEffect`/状态同步处理后续变化。

### 3.2 可观察集合

- 影响 UI 的集合只能使用不可变快照替换，或 `SnapshotStateList`、`SnapshotStateMap`、`mutableStateOf` 包装后的不可变集合。
- 禁止：`remember { mutableSetOf() }`、`remember { mutableMapOf() }`、`remember { mutableListOf() }` 后直接增删并期待 UI 自动更新。
- 当前已发现的违规实例：`univerge-accessibility/.../FloatingChatOverlayUi.kt` 中 `transcribingRemoteMessageIds` 和 `revokingRemoteMessageIds` 使用普通 `mutableSetOf`，异步增删不会触发重组。修改相关逻辑时必须改为状态集合或显式版本状态。
- 只用于计算缓存且不直接决定 UI 的普通可变集合可以存在，但必须有明确注释说明其不会被读取来驱动渲染。

### 3.3 派生状态

- 便宜的纯计算直接写表达式；昂贵或需要稳定观察的计算使用 `remember(keys)` 与 `derivedStateOf`，不要无差别包裹所有表达式。
- `derivedStateOf` 只在输入状态变化频率高、输出变化频率低时使用；不要把普通 `remember` 当成性能装饰。
- 列表筛选、排序、映射应在状态层或明确的 `remember` 中完成，并使用稳定 key；不要在每个 item 的 Composable 中重复对全量列表计算。

## 4. 重组与性能规则

### 4.1 Composable 设计

- 保持 Composable 小而单一：输入数据、输出 UI、事件回调。单个文件或函数同时包含页面状态机、网络调用、持久化和数百个视觉分支时，应拆出 state holder、screen、section 和 item。
- 当前高风险大文件包括：
  - `univerge-accessibility/.../FloatingChatOverlayUi.kt`（约 3377 行、约 87 个 `remember`）；
  - `app/.../MainActivity.kt`（约 1924 行）；
  - `univerge-accessibility/.../floatingchat/chat/ChatSessionRail.kt`（约 1189 行）；
  - `univerge-accessibility/.../floatingchat/contacts/ScrmContactsPanel.kt`（约 2323 行）。
- 新功能不得继续把状态和 UI 追加到这些根文件；优先建立局部 state holder 和独立文件。
- 参数使用稳定、最小的数据类型。不要把整个大对象、可变 List 或 controller 作为无必要的参数传给深层子树。
- 回调使用语义明确的事件函数；避免在每次重组时创建携带大量闭包的对象。必要时使用 `rememberUpdatedState` 保持回调最新而不重启长生命周期 effect。

### 4.2 列表和大数据

- 长列表、消息流、联系人列表必须使用 `LazyColumn`/`LazyRow`，而不是 `Column + verticalScroll` 一次性组合所有 item。
- 每个 `items` 必须提供稳定、唯一的 `key`；内容类型明显不同的列表提供 `contentType`。
- item 内不要依赖 index 作为身份；删除、插入、排序时必须保持业务 ID 稳定。
- 计算列表前先限定数据范围；搜索、分组和排序应在状态层完成，不要在 item 渲染阶段做网络或磁盘 I/O。
- 需要保持滚动位置时使用稳定的 `rememberLazyListState`；自动滚动必须由明确的业务事件触发，不能因普通重组反复执行。

### 4.3 高频绘制与动画

- 高频动画、拖动位移和绘制优先更新 `graphicsLayer` 或绘制参数，不要为每一帧改变布局尺寸、触发全树测量。
- `onGloballyPositioned`、`boundsInRoot` 和窗口坐标采集属于测量/交互基础设施，应只在边界变化时写状态；避免每帧写入会导致反馈式重组。
- 大量阴影、模糊、复杂 `drawWithContent` 和嵌套透明层必须在真机性能检查后采用；不要用视觉效果掩盖布局层级过深。
- 先用稳定 key、状态缩小和局部重组解决性能，再考虑 `SubcomposeLayout`、自定义 Layout 或缓存位图；不得为了“优化”引入无法验证的复杂缓存。

## 5. Effect、协程和生命周期

- `LaunchedEffect(key)` 只做与 Composition 绑定的协程工作。key 必须表达真正的重启条件，禁止使用不断变化的大对象导致反复取消/重启。
- 一次性动作使用显式事件和 `LaunchedEffect`，不要在 Composable 函数体直接启动网络、写文件、发消息或调用窗口 API。
- `DisposableEffect` 必须成对释放 listener、注册表、观察者、ComposeView 资源和外部句柄；key 变化时要确认旧资源先释放。
- 外部 Flow 在 Activity/Overlay 中默认使用 `collectAsStateWithLifecycle()`；只有明确是长期存活且不依赖 Lifecycle 的宿主才允许 `collectAsState()`，并写明理由。
- 当前存在未使用生命周期收集的调用：`app/.../ContactRelationsActivity.kt`、`univerge-accessibility/.../ContactRelationsOverlayController.kt`、`.../LeftSidebarOverlayController.kt`、`.../floatingchat/chat/ChatSessionRail.kt`。后续修改这些文件时优先迁移，并补 `lifecycle-runtime-compose` 依赖。
- IO、数据库、网络和模型推理放到 `Dispatchers.IO`/专用 dispatcher；回到主线程只发布状态或更新 UI。不要在 Composable 或主线程同步读取大文件。
- effect 中捕获的参数若可能更新，使用 `rememberUpdatedState` 或把参数纳入 key；不要让 effect 继续使用旧回调。
- 关闭页面、Overlay 或服务时必须先取消协程和 observer，再释放 View/Window；不得依赖 GC。

## 6. Compose 与 View、Overlay、无障碍互操作

- `ComposeView` 必须设置 `ViewCompositionStrategy`，并绑定正确的 LifecycleOwner、SavedStateRegistryOwner；Overlay 自建宿主要明确 owner 的销毁时机。
- `AndroidView(factory = ...)` 只创建 View；`update = ...` 只同步可变属性，禁止每次重组重复添加 listener、启动相机或创建 controller。
- View 到 Compose 的事件只能经过一个 adapter；adapter 将平台事件转换为不可变领域事件，业务层不直接读取 `MotionEvent`。
- 坐标系统必须注明来源：View local、Compose root、Window 或屏幕坐标。使用 `boundsInRoot()` 时，源和目标必须属于同一 root；跨窗口要显式转换。
- 触摸、拖动、滚动、无障碍手势必须定义优先级和消费责任。未声明交互的组件必须保持原生点击、滚动和焦点行为。
- `univerge-heavy-drag-compose` 的 `HeavyDragHost` 是唯一原始触摸入口；它内部使用 `pointerInteropFilter` 是有意的适配例外。不要在业务 Composable 旁边再次把同一 `MotionEvent` 转给 SDK。
- SDK 只负责分类，Compose/核心拖拽层只接收通用分类结果；禁止把 Aispect SDK 类导入通用 Compose 或 core 模块。

## 7. 输入、语义和可访问性

- 交互组件必须同时考虑触摸、键盘/遥控器焦点和无障碍语义；不要只通过颜色或动画表达状态。
- 可点击、可拖动、可关闭、可展开组件提供明确 `contentDescription`、role、stateDescription 或自定义 semantics；测试页和调试页也遵守。
- 触摸目标满足项目的最小可触面积；点击区域可以通过 `minimumTouchTargetSize` 或外层语义区域扩大，但不可改变视觉布局合同。
- 手势冲突必须有测试：轻触、长按、重触、滚动、多指、取消、失焦和快速抬起至少覆盖核心状态机。
- 不要在 Composable 内直接依赖设备方向、屏幕密度或窗口尺寸的单次读取；使用 `LocalConfiguration`、`BoxWithConstraints` 或布局回调，并避免把每次测量写回导致循环重组。

## 8. 状态恢复、测试和可观测性

- 配置变化后必须区分：应恢复的用户输入/滚动位置、应重新加载的业务数据、应取消的瞬态任务。
- ViewModel/仓库状态测试优先于截图测试；纯计算、状态机、坐标和目标命中放到 JVM 单测。
- Compose UI 测试验证语义树和用户行为，不依赖源码字符串断言。源码合同测试只能作为迁移期补充，不能替代行为测试。
- 每个复杂交互至少有：未声明组件原生行为、轻触路径、重触/长按路径、取消路径、重组后回调仍最新、生命周期销毁后无回调。
- 性能回归至少记录：首屏时间、滚动帧稳定性、重组范围、主线程耗时和内存峰值；不要只以“APK 能构建”判定优化成功。
- 生产日志不得打印完整消息、token、设备隐私或高频逐帧事件。调试日志要有开关，并在 release 中默认关闭。

## 9. 当前项目的重点整改顺序

### 已确认的证据与风险

| 证据路径 | 当前现象 | 风险 | 处理要求 |
| --- | --- | --- | --- |
| `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt:711-712` | 异步中的消息 ID 使用普通 `mutableSetOf` | 集合增删不触发重组，按钮忙状态可能不更新或与实际请求不同步 | 改为 `mutableStateSetOf`/不可变集合替换，并补异步完成、失败和重组测试 |
| `app/src/main/java/com/paifa/univerge/app/ContactRelationsActivity.kt:63`、`univerge-accessibility/.../ContactRelationsOverlayController.kt:199`、`.../LeftSidebarOverlayController.kt:174-175`、`.../floatingchat/chat/ChatSessionRail.kt:120` | 使用 `collectAsState()` 收集 Flow | 页面不可见时仍可能收集；Overlay 生命周期异常时可能继续工作 | Activity/Overlay 默认改为 `collectAsStateWithLifecycle()`；长期服务例外必须写理由 |
| `univerge-accessibility/.../FloatingChatOverlayUi.kt` | 单文件约 3377 行、约 87 个 `remember`，混合状态、异步动作和大量 UI 分支 | 任意状态变化可能扩大重组范围，难以定位卡顿和泄漏 | 按 state holder、screen、section、item 分阶段拆分 |
| `app/src/main/java/com/paifa/univerge/app/FloatingChatCameraActivity.kt:224,263` | 程序化创建 `ComposeView`，未像其他 Overlay 一样显式设置 composition strategy | View 重建/移除时组合释放语义不明显 | 修改该文件时显式选择并测试 `ViewCompositionStrategy`，确认相机与组合的销毁顺序 |
| `univerge-heavy-drag-compose/.../HeavyDragHost.kt:53` | 根级使用 `pointerInteropFilter` | 若业务再次接管同一事件，会造成重复消费、滚动失效或轻触误拖 | 保持唯一原始入口；业务只声明 source/target 和领域回调 |

这些是代码审计证据，不代表本次必须一次性修完。除非用户明确要求修复，否则 agent 只应按当前任务修改文档，并把代码整改作为独立变更。

按风险和收益排序：

1. 修复 `FloatingChatOverlayUi.kt` 中影响 UI 的普通可变集合，并为异步状态建立可观察的状态合同。
2. 给 Activity、Overlay 和 Sidebar 的 Flow 收集补生命周期绑定，确认 Overlay 自建 LifecycleOwner 的有效范围。
3. 将 `FloatingChatOverlayUi.kt`、`MainActivity.kt` 等根 Composable 拆成 state holder、screen、section 和 item；每次只迁移一个闭环功能。
4. 审查所有 `onGloballyPositioned`、`pointerInput`、`AndroidView` 和 `ComposeView` 的注册/释放、坐标系和事件消费责任。
5. 为高频消息列表补稳定 key/contentType，移除渲染阶段的重复筛选、排序和同步 I/O。
6. 在真机上建立基线后再做性能优化；不得用盲目 `remember`、全局缓存或禁用重组来掩盖状态建模问题。

## 10. Agent 提交前验收清单

- [ ] 是否说明了状态的唯一所有者和生命周期？
- [ ] 是否避免在 Composable 函数体执行副作用？
- [ ] 是否所有影响 UI 的集合都可观察且不可变边界清楚？
- [ ] 是否给 Lazy 列表提供稳定 key，避免 index 身份？
- [ ] 是否使用生命周期感知的 Flow 收集，或记录了例外理由？
- [ ] 是否检查了重组范围、测量回写和高频状态更新？
- [ ] 是否明确 Compose/View/SDK 的单一事件入口和坐标系？
- [ ] 是否验证轻触、重触、滚动、多指、取消、失焦和销毁？
- [ ] 是否有真机或基准数据支持性能结论？
- [ ] 是否只修改了本任务范围内的文件，并同步了相关测试/文档？

## 11. 推荐检查命令

在 `superAppAndroid` 目录使用 JDK 17：

```powershell
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe'
& $java -classpath gradle\wrapper\gradle-wrapper.jar `
  org.gradle.wrapper.GradleWrapperMain `
  :app:testDebugUnitTest :univerge-heavy-drag-core:test `
  :univerge-heavy-drag-android:testDebugUnitTest `
  :univerge-heavy-drag-compose:testDebugUnitTest `
  --no-daemon --max-workers=1
```

构建前必须额外检查：

```powershell
git diff --check
rg -n "collectAsState\(" app\src\main univerge-accessibility\src\main univerge-overlay\src\main
rg -n "remember\s*\{\s*mutable(Set|Map|List)Of" app\src\main univerge-accessibility\src\main univerge-overlay\src\main
```

性能问题进入修复阶段时，必须先打开 Compose Compiler reports/metrics（按当前 Kotlin/Compose Compiler DSL 配置），并结合 Layout Inspector、System Trace、Macrobenchmark 或 Baseline Profile 采样。至少保留：

- 触发路径和复现数据；
- composition/layout/draw 或主线程阻塞的归因；
- 改前、改后同设备同构建类型的帧时长、jank、启动或滚动指标；
- 若启用 stability configuration、`@Immutable`、Strong Skipping 或 immutable collections，记录其合同和回归测试。

发现规则例外时，不要静默绕过：在代码附近写明生命周期、稳定性、坐标或事件消费的原因，并补测试。
