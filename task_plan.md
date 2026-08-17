# 悬浮聊天统一工作区计划

## 本轮续作：朋友圈 Material 3 工作区

- [x] 已核对用户确认的视觉规范、红灯测试和既有 SCRM 接口实现。
- [ ] 替换右侧“朋友圈”可见入口为共享 surface/AppBar、分页和高性能时间线。
- [ ] 补齐回复与本人评论删除的页面交互，并保持任务失败可见且不自动重发。
- [ ] 运行定向测试、Kotlin 编译和变更检查。


## 本轮：朋友圈全屏工作区

- [x] 发现：定位 IosFloat 的朋友圈页面、交互流程和已对接接口，并核对 Android 的现有入口。
- [x] 契约：补充覆盖入口、M3 全屏工作区、分页列表及接口调用的定向回归测试。
- [x] 实现：在既有浮层宿主中完成朋友圈的 Material 3 页面、数据加载和关闭回退逻辑。
- [x] 验证：运行定向单测、Kotlin 编译和变更范围审查；遇到并发 Gradle 占用时等待后重试。
- **状态：** complete

## 本轮：已同步语音消息按 messageId 转文字

- [x] 明确入口仅位于已同步语音消息的首层消息选项，本地待发送录音不具备 SCRM messageId。
  - [x] 红灯验证消息感知操作列表、真实接口任务执行、刷新替换和 voiceText 展示契约。
  - [ ] 实现“转文字”操作、异步任务轮询、处理中 taskId 续查、终态刷新与明确错误反馈，并移除待发送录音的无效入口。
- [ ] 运行定向测试、模块 Kotlin 编译和 `git diff --check`，复核未误用微信 msgSvrId。

### 本轮约束

- 路径参数只能使用 `FloatingChatMessage.remoteMessageId`，禁止使用 `remoteMessageServerId`。
- 仅 `Voice` 且 `remoteMessageId > 0` 的消息显示“转文字”。
- 转写任务不得静默成功、吞错或自动重提；任务终态后刷新当前聊天以读取服务端 `voiceText`。
- 保持共享悬浮根节点内的 Material 3 消息选项，不新增 Dialog，避免 BadTokenException。

## 当前轮次

- [x] 审查共享宿主、未读路由与现有改动，确认透明根和受限尺寸的来源。
- [ ] 覆盖所有全屏模式和直接路由页面的回归契约。
- [ ] 修正审查发现的残留 surface 或剩余高度问题。
- [ ] 运行定向测试与 Kotlin 编译并复核验收标准。

## 目标

将悬浮聊天的未回消息、全部未回消息、搜索和扫码页面统一到“UI组件”使用的根级悬浮工作区：透明根容器、工具栏自身 30dp 顶部内边距、左侧返回按钮，以及仅一层的实体属性入退场动画。

## 阶段

- [x] 发现：读取现有统一工作区链路和协作者结论。
- [x] 根因：确认工具栏旧测试仍要求被禁止的独立状态栏和页面动画，且未回消息根 View 未复用共享运动规范。
- [x] 测试：将工具栏测试切换为新契约，并已观察到共享运动规范断言按预期失败。
- [x] 实现：未回消息根 View 的初始、入场和退场位置已统一委托给 `FloatingWorkspaceMotion`。
- [x] 验证：定向单测、`ubiki-accessibility` Kotlin 编译和 `app:compileDebugKotlin` 通过；全量单测等待后仍有 25 个无关失败。

## 本轮补充：surface 背景与全尺寸工作区

- [x] 发现：共享全屏宿主 `FloatingBottomPanel` 明确为透明背景，部分工具功能仍落入 `widthIn/heightIn` 的居中面板分支。
- [x] 测试：将全屏工作区契约切换为 `MaterialTheme.colorScheme.surface` 与 `fillMaxSize()`，并确认旧实现按预期失败。
- [ ] 实现：将功能工作区统一改为 surface 和全尺寸，保留 Emoji、More 等输入抽屉；礼物和 SCRM 编辑入口正在迁移为全屏工作区。
- [ ] 验证：运行定向单测与 Kotlin 编译；并检查不再残留全屏透明根。

## 约束

- 不使用 Dialog 或第二个普通 Activity Window 承载工作区。
- 不删除或回退其他协作者的改动。
- 保持 Material 3、LazyColumn/现有高性能列表实现和已有接口行为。
- 仅根级容器负责位移动画；页面内部不得再次位移。

## 错误记录

| 阶段 | 错误 | 处理 |
| --- | --- | --- |
| 本轮验收 | 眨眼测试根透明、拍照独立状态栏占位、扫一扫缺少 surface 底板 | 已改为共享 surface/AppBar；CameraX 预览保留在 surface 底板之上，定向测试与 Kotlin 编译通过。 |
| 发现 | `impeccable` 命令不可用 | 按已确认的“UI组件”参考实现和项目 Material 3 组件继续，不安装新工具。 |
| 测试 | `ToolbarWorkspaceContractTest` 仍断言独立 30dp Spacer 与页面级动画 | 已替换为共享 toolbar、透明根和根级动画契约。 |
| 全量测试 | `897 tests completed, 25 failed`，失败集中在其他功能、数据库和渲染契约 | 等待 15 秒后重试，失败集合稳定且不涉及本轮定向测试，保留并记录。 |
| 本轮范围变更 | 旧计划要求透明背景，与用户最新的 `surfaceColor` 要求冲突 | 以最新用户要求为准，旧透明契约仅作为历史记录。 |
| 定向测试红灯 | 23 个定向测试中 11 个失败 | 失败均为新 surface/全尺寸静态契约，证明测试覆盖的是缺失行为，进入生产修复阶段。 |
| 定向测试红灯 | 全屏工作区新增契约先后在旧居中分类、礼物小面板和 AI 配置旧工具栏处失败 | 已确认三类根因，分别收敛模式分类、礼物/SCRM 页面根和 AI 配置页。 |
| 本轮生产编译（第 1 次） | 并行 Gradle 进程导致 Kotlin 增量缓存快照缺失、storage already registered | 未当作源码失败；等待协作者任务完成后改用非增量编译重试，不删除共享 build 目录。 |
| UI 契约红灯（第 1 次） | PowerShell 将未引用的 `-Pkotlin.incremental=false` 解析成错误任务名 | 改为引用完整属性参数，避免重复同一命令错误。 |
| UI 契约红灯（第 2 次） | 生产 Kotlin 编译通过，但并发遗留的 `classes.jar` 报 `zip END header not found` | 协作者已停止构建；改用 Gradle `--rerun-tasks` 重建 compile JAR，不手工删除共享缓存。 |

## 本轮接手：素材库二级页面统一工作区

- [x] 确认详情页、编辑页仍使用自定义 toolbar，列表页仍使用 AlertDialog。
- [x] 使用共享 FloatingWorkspaceTopAppBar，并将归档确认改为页面内联状态。
- [x] 运行素材库与工作区定向测试、Kotlin 编译并记录结果。
- **状态：** complete
# 悬浮聊天性能优化计划（2026-08-17）

## 目标

依据《卡顿原因.md》消除悬浮聊天界面的可验证性能瓶颈，同时不改变消息渲染、接口逻辑、UI 样式、现有功能和业务行为。

## 当前阶段

阶段 4：工程回归与 Phase 2 同源真机验收

## 阶段

- [x] 阶段 1：读取卡顿报告、仓库规范和相关实现，建立性能基线与可复现路径
- [x] 阶段 2：追踪调用与状态流，确认根因并对照仓库内已验证模式
- [x] 阶段 3：先编写会失败的性能/结构回归测试，再实施最小改动
- [ ] 阶段 4：运行定向测试、静态检查、编译及完整相关回归（进行中）
- [ ] 阶段 5：复核禁止项、差异范围和性能证据，完成交付

## 硬性约束

- 禁止修改消息渲染。
- 禁止修改接口逻辑。
- 禁止修改 UI 样式。
- 禁止修改已经实现的功能和业务。
- 不新增静默 fallback、吞错、mock 成功或掩盖根因的兼容分支。

## 关键问题

1. 《卡顿原因.md》中的每个结论能否在当前代码和运行路径中得到证据支持？
2. 哪些高频工作发生在主线程、重组路径或滚动热路径中？
3. 如何用自动化测试证明优化前失败、优化后通过且业务契约不变？
4. 哪些验证命令能够同时覆盖性能结构、功能回归和构建完整性？

## 决策

| 决策 | 理由 |
|---|---|
| 先调查根因再修改生产代码 | 避免用缓存、限流或降级掩盖真实瓶颈 |
| 仅做可由文档与代码证据支持的最小优化 | 严格控制行为与业务风险 |
| 保留既有计划历史，仅添加本轮区块 | 工作区已有协作记录，不覆盖用户或其他协作者内容 |

## 错误记录

| 错误 | 次数 | 处理 |
|---|---:|---|
| 计划文件在终端中显示乱码 | 1 | 保留原字节与历史内容，仅通过补丁添加新的 UTF-8 区块 |
| 并行检索包含不存在的 `ubiki-accessibility/src/androidTest`，导致聚合调用退出 1 | 1 | 改为仅扫描实际存在的测试目录，并让结果独立返回 |
| PowerShell 下把裸 `*.md` 传给 `rg` 触发路径语法错误 | 1 | 后续统一使用 `rg -g '*.md'` 或显式路径，不再重复裸通配符 |
| 当前 PowerShell 不支持 `Set-Content -AsByteStream`，截图管道失败 | 1 | 改用设备端 `adb shell screencap` 后单独 `adb pull`，避免文本管道损坏 PNG |
| direct writer 子任务首次计划补丁上下文少了 Markdown 表格前导符 | 1 | 修正为精确 UTF-8 表格行并拆分补丁，避免影响共享计划 |
| RED 状态补丁把错误记录行放在子任务清单之后，导致上下文不匹配 | 1 | 读取文件尾部后按实际区块分别更新 |
| 禁用调用扫描零匹配时 `rg` 返回 1，导致并行聚合输出被丢弃 | 1 | 后续将退出码 1 显式转换为 `NO_FORBIDDEN_MATCHES` 成功结果 |
| PowerShell 在尾随空白脚本中把 `$file:` 解析为作用域变量 | 1 | 改为 `${file}:` 明确变量边界后再执行 |
| 同步计划的组合补丁命中协作者刚追加的文件尾，导致上下文失配 | 1 | 重新读取三个文件尾并拆成精确补丁，不重复旧上下文 |
| 工具编排脚本误用 PowerShell 的 `1..3` 作为 JavaScript 范围 | 1 | 改为 JavaScript 数组 `[1, 2, 3]` 后成功复核，不重复混用语法 |
| 扩大到 `FloatingChatMessageUiContractTest` 的组合回归出现 191 项中 4 项失败 | 1 | 断言分别属于既有群成员轨、运行时线程 ID、左轨 padding 和右轨工具枚举，均不经过本轮热路径；保留失败证据，不修改无关业务代码 |

## 子任务：连接线直接 Path writer（阶段 2）

- [x] 范围确认：旧 `ChatConnectorGeometry` 保持不变并作为 oracle；仅修改连接线层、新增独立 writer 与专属测试。
- [x] RED：先写 CommandSink golden/参数化等价测试，并确认旧生产路径按预期失败。
- [x] GREEN 实现：使用可复用 primitive scratch 与无逐帧分配的 Path adapter，保持全部几何及绘制顺序。
- [x] 静态复核：确认 draw 路径不再创建 Tree/Brace/Branch/Pair/List/Offset 对象图，并由父代理串行跑绿灯。

---

## 阶段 4 补充：Phase 2 反证与继续定位

- [x] 复算三轮同协议 overlay framestats：Phase 2 为 `310/1677 = 18.49%`，高于 Phase 1 的 `294/1809 = 16.25%`，现有优化尚未达到验收目标。
- [x] 独立审查 Phase 1 改动并修复 `MediaPlayer` 构造异常逃出既有失败边界的回归，完成定向红绿验证。
- [x] 核对 Compose UI Geometry 1.7.6 源码，确认 `Rect.center` 与 direct writer 使用同一浮点运算顺序，审查所报 1 ULP 偏差不成立。
- [ ] 采集并按帧关联 UI 线程、RenderThread 与 FrameTimeline 的 trace，定位 Slow UI/Slow issue draw 联合背压的仓库自有调用点。
- [ ] 仅对有运行时证据且位于禁止项之外的根因新增红灯契约并实施单一最小修复。
- [ ] 重跑工程回归、APK 构建、真机功能与三轮同协议性能验收，再进入阶段 5。

### 阶段 4 新增错误记录

| 错误 | 次数 | 处理 |
|---|---:|---|
| PowerShell 下再次把 `build.gradle*` / `**/build.gradle*` 作为裸路径传给 `rg`，聚合检索退出 1 | 1 | 改为 `rg -g '*.gradle' -g '*.gradle.kts' -g '*.toml' .`；后续禁止向 PowerShell 传裸通配路径。 |

| Perfetto 服务进程无法写入 `/data/local/tmp` | 1 | 改用服务可写的 `/data/misc/perfetto-traces`，采集成功后通过 ADB 拉取。 |
| 官方 `commondatastorage.googleapis.com` 解析失败 | 1 | 改用同一官方对象的 `storage.googleapis.com` 别名，校验 SHA-256 后本地解析。 |
| Trace Processor SQL 中 `id` 列未限定导致歧义 | 1 | 后续全部使用 `s.id` / `t.id` 等限定列名，不重跑原查询。 |

---

## 续作：全屏悬浮聊天 toolbar 等权布局与备注输入层

### 目标

在实际显示的 `FloatingChatWorkspaceHeader` 中实现左右各占一半的 toolbar：左侧为返回按钮（右上未回红点）、约五个中文字符宽度的会话标题走马灯和编辑按钮；右侧从视觉左至右为二维码入口、约三个中文字符宽度的账号走马灯和搜索按钮。保持现有搜索、二维码工作区、添加好友/群聊和资料编辑接口不变。

### 范围与约束

- [x] 已确认 `CoordinateChatBody` 内的 `ChatTopToolbar` 在真机路径中由 `showTopToolbar = false` 关闭；真实显示路径是 `FloatingChatOverlayUi.kt` 的 `FloatingChatWorkspaceHeader`。
- [ ] 先新增活跃 header 的红灯契约，覆盖等权 Box、按钮顺序、红点、走马灯和既有 callback。
- [ ] 在共享 `FloatingWorkspaceTopAppBar` 内增加仅供聊天 header 使用的内容槽，保留现有 surface、30dp Insets 和高度。
- [ ] 私聊编辑继续打开现有联系人资料页，并在同一悬浮根内显示备注输入层；群聊编辑继续复用既有群信息工作区。
- [ ] 运行定向单测、Kotlin 编译、APK 构建、差异检查与真机截图；性能 A/B 仅在聊天列表场景锁定后重新采集。

### 明确不做

- 不修改消息渲染、连接线路径、SCRM/网络接口或聊天搜索实现。
- 不替换二维码、搜索、添加好友/群聊的既有工作区。
- 不创建 Android `Dialog` 或第二个 Window；备注输入层直接绘制在已有悬浮 Compose 根中，避免 `BadTokenException`。

---

## 本轮：本人消息撤销接口闭环

### 目标

在悬浮聊天中实现“点击本人已同步消息 → 撤销 → 确定撤销 → 确定”，提交真实 SCRM 撤回任务，并仅在任务明确完成后刷新消息所属会话。

### 阶段

- [x] 设计：确认展示条件、二次确认、账号作用域、任务轮询和服务端刷新策略。
- [x] 计划：完成菜单、任务适配器、根内确认层、宿主接口闭环和验证步骤。
- [x] RED：先补菜单、确认层、账号作用域和撤回任务失败测试。
- [x] GREEN：实现最小生产代码并让定向测试通过。
- [x] VERIFY：撤销定向测试、Kotlin 编译与差异检查通过；扩大消息回归存在 1 项 HEAD 既有渲染字符串契约失败，已单独记录。

### 约束

- 仅本人发送且 `remoteMessageId > 0` 的消息显示撤销。
- 不提前隐藏消息，不吞错，不新增静默 fallback，不误用本地消息 ID。
- 确认层绘制在现有悬浮 Compose 根内，不创建 Android `Dialog` 或第二个 Window。

### 验证结果

- 四组撤销定向验证在 15 秒内返回 `BUILD SUCCESSFUL`。
- `:ubiki-accessibility:compileDebugKotlin` 返回 `BUILD SUCCESSFUL`。
- `git diff --check` 通过，仅有工作区既有 LF/CRLF 提示。
- 消息包扩大回归 56 项中 1 项失败：HEAD 中 `MessageContent` 使用 `selected = multiSelectMode`，旧测试仍断言源码字符串 `selected = selected`；未修改该无关契约。
# 本轮：扫码入口创建群聊

- [x] 范围：复用现有联系人列表与 `chatRoomApi.createChatRoom`，不增加群名步骤。
- [ ] RED：扫码菜单、联系人建群模式与宿主路由契约先失败。
- [ ] GREEN：增加“创建群聊”入口、全屏多选页和真实接口接线。
- [ ] VERIFY：定向测试、Kotlin 编译、差异检查。

约束：不新增 Dialog/Window，不模拟成功，不吞掉任务失败，不覆盖仓库内其他未提交改动。

完成状态：

- [x] RED：新增入口、宿主路由、共享工具栏与直接返回契约均先观察到预期失败。
- [x] GREEN：扫码菜单、全屏多选、真实建群任务和返回行为已实现。
- [x] VERIFY：定向测试、SCRM API 测试、Kotlin 编译与补丁格式检查通过。
