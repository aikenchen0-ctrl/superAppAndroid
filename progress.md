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
