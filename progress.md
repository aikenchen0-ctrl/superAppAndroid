# 执行记录

## 2026-08-16：朋友圈续作

- 已读取现有计划、产品规范、定向红灯测试和 SCRM 接口；前一次计划补丁因上下文尾行同时变化未应用，本次改为在稳定标题处插入记录。
- 尚未修改朋友圈生产 UI，下一步按红绿测试替换可见入口。


## 2026-08-16：朋友圈

- 已确认最新目标、共享工作区约束和现有全屏浮层规范。
- 已定位右栏路由、目标页面和朋友圈接口全集，正在并行比对 IosFloat 的行为与 Android 现有实现；尚未修改朋友圈生产代码。

## 2026-08-16：朋友圈审查结果

- 已确认缺口：Android 只完成同步、发布、点赞、评论主链路，剩余高级操作停留在预览；iOS 已具备真实互动和动态操作。
- 两条只读子审查因远端流断开终止，已改用本地对照，未修改业务代码。

## 当前审查

- 已确认共享全屏宿主的根因，并开始审查直接路由页面。
- 前三次任务记录补丁仅因编码锚点不匹配而失败，未修改任何源码。
- SCRM运营回归契约先红后绿，已迁入 surface 全屏宿主并改为共享工具栏与剩余高度列表。
- 直连工作区与页面动画契约已先红：重复 Animatable/graphicsLayer 会和根 AnimatedVisibility 叠加，导致首帧内容移出屏幕。

## 2026-08-16：朋友圈收尾验证

- 已复现并修复账号隔离回归：同一 `circleId` 在不同微信账号下以可逆物理主键分区保存，内存更新同样按 `accountId + id` 匹配；历史空账号记录不会被其他账号写入误删。
- `MomentPostAccountIsolationContractTest` 已重新通过。下一步：复查素材库二级页协作改动，并运行朋友圈、素材库、迁移和 Kotlin 编译的定向验证。

## 2026-08-14

- 已将聊天根的全屏工作区进出场收敛为 `FloatingWorkspaceMotion`，底部抽屉保持原有行为。
- 已将 AI 自动回复、通讯录关系、左侧全部和好友管理迁回同一个 `BottomPanelMode` 聊天根；对应页面使用透明 M3 工具栏。
- 定向入口契约测试和 `:ubiki-accessibility:compileDebugKotlin` 已通过；编译仅报告既有的 Android API 弃用警告。
- 后续：迁移收藏库、视频号发布、素材库，并单独处理群信息仍在资料编辑宿主外层的根动画问题。

- 已读取任务、调试、测试驱动和界面规范。
- 已确认本轮范围与统一工作区视觉契约。
- 下一步：检查实际代码与契约测试，定位尚未统一的入口或局部动画。
- 已验证旧工具栏测试按旧 UI 契约失败，确认不是 Gradle 并发错误。
- 已以测试先行方式接入未回消息根 View 的 `FloatingWorkspaceMotion`，待重新运行定向测试和编译。
- 已修复首次 Expanded 状态的入场动画计算时序。
- 已让全部未回消息和单账号未回消息使用透明根，普通会话保留磨砂背景。
- 已把 30dp 顶部空间改为 M3 `TopAppBar.windowInsets`。
- 定向契约测试、模块 Kotlin 编译和 `app:compileDebugKotlin` 均通过；全量单测重试后仍有 25 个非本范围失败。

### 本轮：surface 背景与全尺寸工作区

- 已确认用户用最新要求替代此前的透明工作区要求。
- 已定位共享透明根与受限尺寸分支，下一步先用定向契约测试复现。
- 根因确认：共享工作区错误走透明表面，且工具功能仍走受限居中面板分支；常规聊天磨砂背景与本轮右侧工作区无关。
- 已先修改 6 个定向契约测试并运行 `:ubiki-accessibility:testDebugUnitTest`；23 个测试中 11 个按预期失败，均指向旧透明根或居中尺寸分支。
- 已新增全屏工作区回归契约，并确认其先在旧居中分类处失败后通过；`FloatingBottomPanel`、输入栏可见性和外层遮罩现统一消费 `isFullscreenWorkspace()`。
- 已新增礼物/SCRM 模式断言，礼物按预期先红；已将其提升到全屏宿主。AI 配置页共享工具栏契约也已先红，正在迁移页面根。
- 已复核共享全屏宿主：除表情和更多输入抽屉外，所有 BottomPanelMode 均由 `isFullscreenWorkspace()` 进入 `surface + fillMaxSize()` 分支；同步修正礼物仍为小面板的过时中文注释。

### 素材库二级页面

- 已确认由本协作者独占 `MaterialLibraryActivityContent.kt`，避免与朋友圈接口审计冲突。
- 已将详情/编辑页迁移到共享 Material 3 toolbar，并把归档确认从 `AlertDialog` 改为页面内联 `Surface`。
- 新增 `MaterialLibraryWorkspacePresentationTest`，先红后绿；定向 Gradle 测试通过。期间 Kotlin 增量缓存被并发任务删除，编译自动回退为非增量并成功完成。
- 最终组合验证通过：`MaterialLibraryWorkspacePresentationTest` 与 `FloatingWorkspaceSurfaceLayoutContractTest` 均通过；`git diff --check` 未发现空白错误。
- `FloatingWorkspaceSurfaceLayoutContractTest` 的两个失败均为旧源码字符串断言，现改为验证集中策略与精确 `TopAppBar` 调用，8 项测试通过。
- 眨眼测试已通过红绿测试：根 `Color.Transparent` 改为 `MaterialTheme.colorScheme.surface`。
- 拍照预览改为共享 AppBar，移除独立 30dp Spacer；扫一扫改为共享 AppBar，并以 surface 底板承接 CameraX 预览，防止相机出帧前透出下层应用。
- `BlinkVoiceFullscreenOverlayPresentationTest`、`FloatingChatPhotoOverlayPresentationTest` 与 `FloatingChatCameraM3ContractTest` 均通过。
- 智能抠图兼容页根也已改为 `surface`；最终组合命令覆盖 app 与 accessibility 的全屏工作区、动画、拍照、眨眼和扫一扫契约，并完成两个模块 Kotlin 编译，返回 BUILD SUCCESSFUL。

## 2026-08-16：朋友圈发布请求对标补充

- 重新运行 `MomentsPublishOptionsContractTest`，发布范围、受众、提醒和位置选项契约通过。
- 对照 IosFloat 的真实 `publishMoment` 请求，发现 Android 漏发顶层与 payload 的 `sendSlow=true`，且位置文本只会序列化 POI 名称；已先写红灯测试，再在真实请求组装中补齐两层慢发标记、完整 POI 字段和默认坐标/poiId 的显式 JSON 编码。
- `ScrmApiClientTest.momentsEndpointsPostSwaggerJsonBodies` 与 `MomentsPublishOptionsContractTest` 已重新通过；下一步运行完整朋友圈定向回归、Kotlin 编译和差异检查。

## 2026-08-16：朋友圈收尾验证完成

- 最终定向回归通过：朋友圈工作区、iOS 行为映射、素材库页面、账号隔离、数据库迁移、SCRM 请求体与共享 surface 工作区契约均已执行；新增 `notVisible` 真实 HTTP JSON 断言也包含在最终运行中。
- `:ubiki-accessibility:compileDebugKotlin` 返回 `BUILD SUCCESSFUL`；仅保留既有 Compose 图标弃用警告。
- 朋友圈目录未发现 `AlertDialog`/`Dialog` 或自动重发实现；`git diff --check` 未发现空白错误。接口文档的范围值与 iOS 实际请求不一致时，Android 保持与 iOS 的 `partVisible`/`notVisible` 实现一致。

## 2026-08-16：消息选项按 messageId 转文字

- 已接手上一轮录音确认层与消息操作测试改动，确认最新需求覆盖“本地录音确认层转文字”：真实入口改为点击已同步语音消息后的消息选项。
- 已完成只读链路审计：接口与 HTTP 实现存在，缺口位于消息操作回调、任务轮询、聊天刷新和转写结果覆盖旧缓存；下一步先运行新增契约测试确认红灯。
- `MessageStandardActionsTest` 已按预期红灯：仅缺失 `MessageLongPressAction.Transcribe` 与 `messageLongPressActionsFor`，Gradle 配置和生产源码编译均正常，确认进入最小实现阶段。
- 已补充无效 `0/负数 messageId` 和 action 回调关闭菜单契约；第二次红灯同时确认缺少筛选函数、枚举值和 `onTranscribeMessage` 构造参数，失败原因与预期一致。
- 已实现消息感知操作列表和 `Transcribe` 业务回调分支；当前尚未接入真实任务执行器，因此暂不宣称该定向测试转绿，继续完成 SCRM 与 Overlay 装配。
- 并行执行器测试已写入，覆盖真实 remoteMessageId、单次提交、终态/处理中/未知结果和输入校验；为解除各红灯测试互相阻塞，`onTranscribeMessage` 暂设为明确抛错默认值，最终接线时必须删除。
- 已新增 UI 装配契约，要求使用 `message.remoteMessageId`、`session.messageOperationApi` 和执行器成功终态刷新，并禁止转写回调引用 `remoteMessageServerId`；待执行器生产类落盘后观察该契约自身红灯。
- 已按最新入口要求删除待发送录音确认层的 `statusMessage/onTranscribe`、限制文案和相关 Overlay 状态；根节点内确认层继续保留“取消/发送”，不新增 Dialog 或 Window。
- 第一次生产 Kotlin 编译在并行 Gradle 期间失败，根因是 `shrunk-classpath-snapshot.bin` 被并发清理及 Kotlin storage 重复注册；日志未出现本轮源码编译错误。按用户的多 AI 约束等待，后续使用非增量模式重试。
- 并行协作者已开始落盘 `ScrmVoiceTranscriptionTaskRunner.kt`，刷新链也已在 Controller/Bridge 及 Bridge 测试中产生增量改动；主线程暂不触碰这些文件，等待其完成定向验证后复核。
- 已补充 `ScrmApiClientTest` 的真实转写 HTTP 契约：固定 SCRM messageId 路径、POST 方法、帐号 JSON body 与无效 ID 请求前失败。
- UI 契约红灯第一次因 PowerShell Gradle 属性未引用而未执行；修正后生产源码编译通过，但测试编译命中并发遗留的损坏 `classes.jar`，尚未进入断言。下一步由 Gradle 自身强制重建该产物后再跑。
- 已恢复本轮上下文：执行器、消息菜单、HTTP 路由及刷新替换已落盘；当前只补齐 Overlay 的真实调用、并发防重、全状态反馈与成功刷新，再做串行回归。
- UI 装配契约已取得有效红灯：2 个断言分别锁定 Overlay 真实转写/成功刷新接线和 Controller 刷新回调注入；同一次构建中生产 Kotlin 编译成功，排除共享 Gradle 产物损坏。
- 首次实现后的编译失败已定位：`FloatingChatMessage.remoteMessageId` 按模型契约为 `Long?`，因为本地消息没有远端主键；Overlay 尚未在边界收窄为空值/非正数。既有菜单使用同一可空契约，修复只在回调入口提取正数 `Long`。
- Overlay 已接入当前账号路由、IO 会话加载、单次提交/原 taskId 轮询、五类结果提示、同 messageId 并发防重和成功刷新；Controller 已注入既有刷新入口，消息操作回调改为必传。UI 契约 3 项及生产 Kotlin 编译返回 `BUILD SUCCESSFUL`。
- 组合定向回归已返回 `BUILD SUCCESSFUL`：覆盖消息菜单、UI 装配、转写 runner、selected session、Controller 合并刷新、Bridge voiceText 优先级、真实 HTTP 路由和待发送录音确认层。
- 测试报告汇总为 54 tests、0 failures、0 errors；`git diff --check` 退出码为 0，仅报告工作区既有 LF/CRLF 转换提示。首次回调静态扫描因仍匹配旧的无标签 lambda 字符串而脚本报错，已改为匹配当前 `transcribe@` 标签后重跑，不把该次输出作为验证结论。
- 独立审查发现 PROCESSING 结果的 taskId 尚未跨点击保留，存在再次 POST 的风险；实现阶段重新打开。计划复用 Controller 持有的 `FloatingChatOverlayRuntimeState` 保存账号/消息对应 taskId，并给 runner 增加只续查既有 taskId 的路径。
- 续查与缓存刷新契约已取得有效红灯：测试编译仅因 `awaitExistingTask`、runtime task 映射方法和始终刷新选中 route 的策略函数不存在而失败，生产源码同轮编译成功。
- 合并账号路由契约时发现并发协作者已先补入 `voiceTranscriptionAccountId` 测试与 `(String) -> Unit` UI 断言；首个补丁因旧上下文不匹配而安全失败，随后按当前文件合并，未覆盖其改动。
# 2026-08-17：悬浮聊天性能优化

- 状态：阶段 1 进行中。
- 已确认目标与五项禁止修改范围。
- 已读取任务执行、文件化规划、系统化调试、TDD 和完成前验证规范。
- 已运行会话恢复检查；工具报告原生 Codex 会话解析尚未实现，无可恢复上下文。
- 已发现并保留既有计划历史，未修改任何产品源码。
- 已完整读取系统化调试、方案设计、TDD 与最小改动规范；下一步开始项目上下文和性能证据调查。
- 已通过前端性能技能的产品上下文门槛并读取 `optimize` 与 product 注册参考；确认本轮不涉及视觉设计和图片资产。
- 已完整读取《卡顿原因.md》、仓库目录、近期提交和工作树状态；确认既有基线与 26 个候选原因，并发现消息渲染/媒体/SCRM 路径存在大量协作中的未提交改动。
- 首次并行代码检索因包含不存在的测试目录而退出；已确认实际测试入口，后续改用存在性明确的目录。
- 第二次检索确认未读分组的重复列表复制热点、三类 `flatMap`、右轨布局快照分配和根输入状态位置；尚未修改生产代码。
- 深入读取连接线状态与测试后确认已有等值短路和批量失效优化；发现离屏索引前后集合累计复制，以及同一未读分组输入在 `CoordinateChatBody` 中计算两次的新线索。
- 已确认 OnePlus 9 真机在线；现有 benchmark 只有冷启动，后续需用 ADB/现有脚本建立悬浮聊天定向前后基线。
- 已确认真机应用与无障碍服务均可用；一次文档检索因 PowerShell 裸通配符失败，已记录并改用 `rg -g` 规则。
- 已核对真机 WindowManager 状态：悬浮聊天全屏 overlay 当前可见并启用背景模糊；该样式保持不变，只作为测量环境条件记录。
- 第一次截图因当前 PowerShell 缺少 `AsByteStream` 参数失败，未生成有效图片；已切换为 ADB 原生截图/拉取流程。
- 已成功保存并查看真机优化前截图 `artifacts/device-before.png`，确认聊天、多种消息、连接线与双侧轨道均在测试场景内。
- 已完成优化前真机固定滚动第 1 轮，复现 43.14% 卡顿率并保存 `artifacts/gfxinfo-before-optimization-round1.txt`。
- 已完成第 2、3 轮相同滚动基线，暖态卡顿率分别为 29.42% 和 24.38%；三轮原始 `gfxinfo` 均已保存。
- 已完成连接线 Canvas 与几何链审计，确认逐帧存在稳定可移出的 key 构造和多层集合/data class 分配，且现有几何测试可守住 UI 输出。
- 已确认坐标状态已有严格等值短路，拒绝采用阈值节流；未读分组已有输出顺序契约，可用于守住线性化改造的业务等价性。
- 已核对 Controller 当前实现与契约：部分报告结论已被缓存/保留视图路径修正，但 force 刷新仍整棵重建，构造期四类持久化读取仍同步。
- 并行未读审计发现总览分支构建但不消费的 O(n²) 离屏索引、按线程重复扫全消息的 O(T×M) 路径，以及两项未使用派生结果；主线程开始复核这些证据。
- 已复核 `CoordinateChatBody` 用户 diff，仅触及第 420 行点击逻辑；195～309 行的四项纯计算优化可独立补丁，不覆盖协作改动。
- 已定位空离屏索引表达和 `CountingList` 确定性复杂度测试模式，为下一阶段红灯测试做准备。
- 已完整梳理未读线程 fallback 语义，确认必须新建精确索引而不能误用带截断兜底的既有索引。
- 连接线阶段 2 已接手：读取 TDD、文件化规划与最小改动规范，并将父代理给出的 direct writer 架构视为已批准设计；尚未修改本阶段生产代码。
- 已先新增 `ChatConnectorPathWriterTest`：用旧 geometry 生成 move/line/quad oracle，覆盖用户、账号、乱序/扩容、上下消息离屏、avatar 上下离屏、group tree、空树与 group-member 直线；另有生产源码分配契约。尚未运行 Gradle 红灯。
- 第二阶段定向红灯有效：13 秒内在 `compileDebugUnitTestKotlin` 仅因 `ChatConnectorPathWriter`、`ChatConnectorCommandSink` 与对应 recorder override 目标缺失而失败；生产 Kotlin 为 up-to-date，现进入最小实现。
- 已新增 `ChatConnectorPathWriter.kt` 并接入 layer：primitive anchor scratch、稳定排序、直接 trunk/hook 命令、remembered Path sinks、group-member 直接写入均已落盘；旧 geometry 文件未改。静态推演补充了“空可见项 + 离屏 avatar”边界并修正 trunk 累计。
- 已完成首轮源码扫描：layer/writer 中旧 geometry 调用、Branch 与 Offset 构造均为零匹配；未运行 Gradle 绿灯，继续复核现有测试契约与浮点/排序边界。
- 已恢复本轮技能与书面计划上下文，并通过产品性能预检；`PRODUCT.md` 有效，`DESIGN.md` 缺失但本任务禁止视觉变化，因此图片与形状设计门槛不适用。
- 已补采 Phase 1 APK 的 framestats 第 2、3、4 轮；因既有 round1 总帧数与当前协议不同，正式 Phase 1 cohort 固定为同样包含 120ms swipe 间隔的 round2～4，round1 仅保留为历史样本。独立审计正在按匿名悬浮窗 ViewRoot 复算，避免 MainActivity 混入。
- 第二阶段只读审计确认总览头像轨订阅全局连接线版本会产生额外失效，并排除左右轨、`retainMessageBounds()` 和根滚动布尔值为中央逐帧主因；是否实施版本拆分将由 direct writer 同源 A/B 决定。
- 已启动第一阶段独立规范复核，重点检查未读 fallback/顺序、离屏索引全位置等价与异步语音生命周期；连接线生产文件保持单一代理所有权，主线程不并发编辑。
- direct writer 首轮串行绿灯通过：`ChatConnectorPathWriterTest`、`ChatConnectorLayerPerformanceContractTest`、`FloatingChatConnectorGeometryTest` 与生产 Kotlin 编译均在 21 秒内返回 `BUILD SUCCESSFUL`；下一步扩大到坐标、未读、总览和语音组合回归。
- 扩大到 191 项 `FloatingChatMessageUiContractTest` 组合回归时出现 4 项已知失败，分别属于群成员轨、运行时更新、左轨和右轨；已读取 XML 与断言确认不经过本轮优化文件，不据此修改业务。下一步排除已知整类旧契约后运行本轮 8 组定向测试。
- 本轮 8 组性能、几何、坐标、总览与语音定向测试在 14 秒内组合返回 `BUILD SUCCESSFUL`。
- `:app:assembleDebug` 在 1 分 19 秒内成功；Phase 2 APK 已覆盖安装，服务启用且全屏悬浮窗恢复。截图 `artifacts/device-after-phase2-expand.png` 显示原消息、轨道、连接线和输入栏正常，准备执行预热及三轮同协议 framestats。

---

## 2026-08-17：Phase 2 反证与回归审查

- 独立复算 Phase 2 三轮同协议 framestats：`310/1677 = 18.49%`，较 Phase 1 的 `16.25%` 上升 `2.23pp`；当前阶段明确未完成，继续定位 Slow UI/Slow issue draw 联合背压。
- direct writer 浮点审查提出 1 ULP 风险；先加入审查给定分数坐标并运行旧几何 oracle，测试仍为绿。随后读取当前 Compose UI Geometry 1.7.6 源码，确认 `Rect.center` 本就采用 `top + height / 2`，已撤掉无效测试假设且未改生产公式。
- 语音播放器错误边界契约先红：3 项中只有 `MediaPlayer` 构造未处于 `runCatching` 内失败。最小修复后同一 3 项测试在 20 秒内 `BUILD SUCCESSFUL`，构造、配置和异步准备异常继续显式进入失败状态并释放已创建播放器。
- 一次依赖检索因向 PowerShell `rg` 传入裸 `build.gradle*` 通配路径失败；已改为 `-g` 过滤规则并取得 Compose 版本与本地 sources.jar 证据。

## 2026-08-17：Perfetto 重组定位

- 以既定 x=520、y=1650↔650、260ms 的 12 次往返滑动，在 OnePlus 9 成功采集 15 秒 Perfetto trace；首次输出目录权限不足后改用 `/data/misc/perfetto-traces`，不把失败采样作为性能结论。
- 使用官方 Trace Processor v57.2，SHA-256 已校验为 `100334B6091596FBC97F872556849A5747BF47A7F7190C485BA8CEA8D2409C7B`。trace 留在本地 artifacts，未上传。
- 证据显示主线程 `Recomposer:recompose` 99 次累计 895.954ms、最高 41.492ms；`Compose:recompose` 最高 23.040ms。测量布局、普通绘制与输入分发均显著低于该成本，下一步回溯状态订阅而非盲目继续优化 Canvas。

## 2026-08-17：续作状态恢复

- 已重新读取性能专项计划、发现记录、进度记录和工作区状态。会话恢复脚本确认当前 Codex 会话没有可自动解析的旧上下文，已以仓库内记录和真机 trace 作为事实来源继续。
- 工作区包含大量与消息渲染、SCRM、媒体和其他页面相关的协作者改动；后续仅调查浮窗聊天滚动状态、坐标状态及其专属性能测试，禁止回退或覆盖其他变更。
- 当前结论仍为：Phase 2 同协议真机卡顿率 `18.49%` 高于 Phase 1 的 `16.25%`，不能宣称优化已完成。正在追踪滚动写入状态到上层 Compose 重组的完整调用链。

## 2026-08-17：滚动重组链路补证

- 读取 `卡顿原因.md`、消息坐标主体、两侧轨道和 simpleperf 调用图后确认：已有 trace 的主矛盾仍是 Compose 重组，而非普通测量、输入分发或 direct Path writer。
- simpleperf 的消息项调用图出现 `LazyLayoutItemContentFactory` 与 `AndroidPrefetchScheduler.run` 的预组合路径，并进入 `MessageCoordinatePane`/`MessageRow`。该发现只说明预取参与了滚动成本，尚未证明禁用或削减预取会改善用户可见帧，因此不直接修改策略。
- 下一步：量化预取、坐标状态订阅与连接线绘制各自的时间占比，随后只针对已验证根因新增红灯契约和最小修复。

## 2026-08-17：消息列表预取单变量验证

- 根据 Foundation 1.7.6 源码与 simpleperf `AndroidPrefetchScheduler.run` 的 `7.85%` 样本，新增 `MessageListPrefetchPerformanceContractTest`。初次运行按预期有 2 项失败，因 `NoMessageListPrefetchStrategy` 尚不存在。
- 最小实现只为 `CoordinateChatBody` 的主消息列表传入私有无预取策略，策略不调用 `schedulePrefetch`。未改消息内容、消息 renderer、接口、样式、业务状态或其他 LazyColumn。
- 同一契约与 `:ubiki-accessibility:compileDebugKotlin` 已在 10 秒内返回 `BUILD SUCCESSFUL`。下一步为真机同协议 A/B，若未改善则撤销该单变量。

## 2026-08-17：预取 A/B 部署

- `:app:assembleDebug` 在 1 分 4 秒内返回 `BUILD SUCCESSFUL`，debug APK 已通过 `adb install -r -t` 覆盖安装到 OnePlus 9，保留设备数据。
- 无障碍服务和应用进程均在线。首次尝试从收起态恢复聊天时误进入系统最近任务界面，该状态不纳入任何性能统计；后续将先恢复既定全屏聊天场景并截图核对后再执行固定手势。

## 2026-08-17：预取 A/B 协议校正

- 全屏聊天截图已确认消息、连接线、左右轨道和输入栏均正常。首组三轮误把“12 次交替 swipe”执行成 12 对 swipe，得到约 1200 帧/轮，是历史正式 cohort 约 559 帧/轮的两倍。
- 误协议三轮为 `199/1223`、`159/1237`、`213/1207`；这些样本只保留为诊断记录，禁止与 Phase 1/2 比较或作为完成证据。
- 根据相同设备 120Hz 与手势时长反推，正式协议应为 12 次总 swipe，即 6 次上下成对。后续三轮按该帧量级重采。

## 2026-08-17：无预取正式三轮

- 按校正后的 12 次总 swipe 协议完成三轮，匿名悬浮窗根每轮为 `99/658` (`15.05%`)、`93/658` (`14.13%`)、`83/658` (`12.61%`)；合计 `275/1974 = 13.93%`。
- 该结果只作为候选优化数据，历史 Phase 1/2 的帧量级和设备状态不同。为排除环境偏差，下一步在同一设备和同一会话中恢复默认预取做三轮对照，然后再恢复无预取实现。

## 2026-08-17：对照场景污染

- 默认预取 APK 已覆盖安装并恢复聊天截图；第一轮对照重置后执行手势时，采样结束画面进入“群信息”面板，且窗口/PID 发生变化，证明触摸场景没有被稳定锁定。
- 该对照轮及其帧数据不纳入任何性能结论。后续先用返回操作和单次 swipe 截图确认仍在消息列表，再继续同机 A/B；若无法稳定锁定场景，将以 Perfetto/静态证据为主并明确真机数据限制。

## 2026-08-17：全屏悬浮聊天 toolbar 续作

- 用户新增确认的需求是局部 toolbar 布局与备注输入交互：两侧等权 Box、左侧返回红点/标题走马灯/编辑，右侧二维码/账号走马灯/搜索，且保持搜索、二维码和既有资料入口业务不变。
- 已完整审计实际显示路径，确认 `FloatingChatWorkspaceHeader` 才是 `floatFullScreenChatView` 可见 toolbar；`CoordinateChatBody.ChatTopToolbar` 当前以 `showTopToolbar = false` 关闭，不能作为实现目标。
- 已确认私聊编辑、群聊群信息和联系人备注持久化的既有回调链；备注输入将实现为同一悬浮根内的输入层，不创建 Android Dialog 或新增 Window。
- 两名并行只读审计代理因协作服务流断开而失败，未修改工作区；改由主线程继续本地审计和验证。

## 2026-08-17：本人消息撤销启动

- 用户确认交互与 A 方案：本人已同步消息显示“撤销”，二次确认后调用真实接口，只有服务端任务完成才刷新会话。
- 已提交设计文档 `1631f0b`，并完成中文实施计划；自审时将确认实现调整为现有 Compose 根内模态层，以遵守悬浮窗口不新建 Window 的约束。
- 已确认相关生产文件存在未提交协作改动；后续使用局部补丁叠加，不覆盖 toolbar、语音转写或其他消息渲染改动。
- 已按 TDD 完成菜单展示/分发、消息账号作用域、根内确认层和撤回任务适配器；每组测试均先观察到目标缺失红灯，再补最小实现转绿。
- 四组撤销定向验证与生产 Kotlin 编译通过，`git diff --check` 无错误；未在真实账号执行破坏性的撤回写测试。
- 验证期间先后遇到共享测试结果文件锁、`classes.jar` 并发写入损坏和强制重建超过 60 秒上限；未删除共享缓存，改用热 Gradle daemon 拆分验证后通过。
- 消息包扩大回归运行 56 项，唯一失败为 HEAD 既有 `MessageRendererRegistryTest` 字符串断言不匹配；未修改无关渲染代码或测试。

## 2026-08-17：toolbar 窄屏交互修正与验证

- 首轮静态与 Compose 红灯确认原 toolbar 为 25dp 命中区域；在 360dp 窄屏中这会偏离 Material 默认交互尺寸并挤压等权区域。
- 将左侧标题槽改为剩余空间内最多 110dp，恢复默认 IconButton 最小交互尺寸；右侧二维码、账号、搜索顺序和既有回调保持不变。
- 备注输入层新增遮罩命中拦截和系统返回键关闭，仍使用已有 Compose 根和 `updateContactProfile`，未创建第二个 Window。
- `basicMarquee` 增加 `ExperimentalFoundationApi` opt-in；强制重编译 `:ubiki-accessibility:compileDebugKotlin --rerun-tasks` 成功。
- `FloatingChatToolbarLayoutContractTest` 与 `FloatingChatComposeUiTest`（含窄屏四控件点击）最新组合运行成功。
- 既有根 `.clearAndSetSemantics {}` 保留，原因是其承担悬浮窗语义隔离和性能边界；本轮不扩大到消息主体语义树。
# 2026-08-17 创建群聊

- 已完成只读定位：扫码菜单、悬浮宿主、联系人数据、建群 API 和现有测试均已确认。
- 已确定实现路径：扫码菜单事件 -> 联系人全屏建群模式 -> 现有真实 SCRM 建群任务。
- 下一步：先写并运行失败测试。

- 已完成两轮红绿验证：首次 4 项中新增 3 项按预期失败后转绿；导航契约新增后 5 项中 1 项按预期失败后转绿。
- 最终串行运行扫码/建群契约、`ScrmApiClientTest` 与 `compileDebugKotlin`，Gradle 返回 `BUILD SUCCESSFUL`。
- `git diff --check` 无补丁错误，仅报告仓库现有 LF/CRLF 转换提示。
