# 工作进度

## 2026-08-12 长按菜单补齐

- 已完成：8 项菜单顺序、提醒入口移除、话外音 AI 状态链路、异形弹层锚定、图片/视频媒体放大、文本大字号放大。
- 已验证：新增长按菜单/AI/请求取消定向测试通过；`:ubiki-accessibility:compileDebugKotlin` 通过。
- 最终验证：定向长按/AI 测试、`:ubiki-accessibility:compileDebugKotlin`、`:app:compileDebugKotlin`、`:app:assembleDebug`、`git diff --check` 均通过。
- 待验证：宽回归仍有 3 个既有 UI 契约失败；真实 AI 配置、媒体资源和设备视觉交互尚未验收。

## 2026-08-11 客户运营工作台设计

- 已启动：围绕客户画像、联系人标签和朋友圈权限设计正式 UI 闭环。
- 已确认：当前阶段只做设计，不调用任何写接口，不修改业务代码。
- 已加载 `PRODUCT.md`，产品定位为克制、可靠、可确认的悬浮工作台；设计需保持 UI 与 SCRM 业务端口分离。
- 用户已选择联系人详情内聚合方案 A；已核对 Android Host/任务轮询与 iOS 保存回读逻辑。
- 已固定首版范围：仅单联系人操作；批量能力留在运营工作台。已确认权限掩码和 `friendPermissionSynced` 的可信状态规则。
- 已完成 `docs/superpowers/specs/2026-08-11-客户画像标签朋友圈权限设计.md`，覆盖信息架构、编辑交互、数据流、错误状态、代码边界和验收标准；占位符与 diff 检查通过。
- 用户已批准规格；已创建 `docs/superpowers/plans/2026-08-11-客户画像标签朋友圈权限实施计划.md`，包含 6 个可验证任务和安全验收边界。

## 2026-08-11 iOS 追赶差距梳理

- 已启动：对照四份 SCRM 文档、iOS 悬浮聊天实现与 Android 当前实现。
- 已确认边界：本阶段只做读取、证据核对和文档同步，不调用任何发送或写入接口。
- 已确认工作区存在其他未提交改动；本轮不回滚、不覆盖无关内容。
- 已读取三份较小目标文档并枚举 iOS 悬浮聊天相关源码；发现既有对照文档的接口总数与当前文档不一致，后续以路由和调用证据逐项修正。
- 已读取 iOS 接口接入进度并搜索真实路由调用；已将 iOS 状态分为主流程真接入、工作台可调用、仅契约/页面和待用户真机四类。
- 已读取 Android 前序缺口计划并反查关键源码：确认聊天三段式读取已追平；消息转发 UI 和支付接口仍明显落后 iOS。
- 已核对 Android 会话控制器和消息操作面板：同步链路是真调用，扩展消息操作均为预览；发现群成员全量加载相较 iOS 按需加载存在性能差距。
- 已确认 Android 转发与支付当前均存在“本地可操作但未调用 SCRM”的状态，后续清单将其列为 P0/P1，而不是按 UI 存在判定完成。
- 已核对支付接口文档与 iOS 服务实现，并定位三份根目录文档的过期陈述；下一阶段将形成新基线并同步修正。
- 已完成 `iOS端SCRM功能追赶清单.md`，并同步 `功能对照.md`、`任务计划.md`、`完成进度.md` 的最新 SCRM 对齐结论。
- 验证完成：`git diff --check` 通过；目标文档存在性/一级标题检查通过；未发现新增凭据文本；未执行任何真实接口调用。

## 2026-08-11 接口实现与 UI 对接盘点

- 已启动：读取接口文档、Android SCRM 源码、测试和 UI 调用入口。
- 已确认交付字段：实现状态、测试状态、UI 对接状态、UI 操作步骤、证据路径。
- 已确认安全边界：不执行真实写接口，不修改 SCRM 或 UI 业务代码。
- 已运行 SCRM 单元测试：113/113 通过，全部为本地 MockTransport/fixture/纯逻辑测试。
- 已运行 SCRM UI 相关契约测试与朋友圈素材运行时测试：10/10 通过。

## 2026-08-10

### 阶段 1：需求与资料调研

- **状态：** 已完成
- 已完成：建立文件化计划；盘点项目模块、现有蓝图目录及 9 份参考资料。
- 已完成：读取综合需求文档、接口说明与 DOCX 文本；识别 HTML 保存文件的正文提取限制。
- 待完成：阅读项目既有计划和实现说明，核对当前已完成能力。

### 阶段 2：信息架构与范围规划

- **状态：** 已完成
- 规划交付文档：索引、定位与范围、场景功能地图、悬浮聊天、手势控制、AI 助手、消息中枢、工作流、数据模型、技术架构、开发里程碑、验收发布与安全合规。

### 阶段 3：编写蓝图文档

- **状态：** 已完成
- 已在 `框架蓝图` 创建 13 份中文 Markdown：索引、定位、场景、悬浮聊天、手势、AI、SCRM、AIfx、数据模型、技术架构、里程碑、验收、风险。

### 阶段 4：审校与验证

- **状态：** 已完成
- 已确认 13 份文档全部存在一级标题；索引链接有效；未发现空文件与参考接口凭据拷贝；关键主题在多文档中保持一致。

## 验证记录

| 检查项 | 结果 |
|---|---|
| 文件化计划已建立 | 通过 |

## 错误记录

| 错误 | 处理方式 |
|---|---|
| 文档转换工具并行探测失败 | 将改用独立的可执行文件探测命令。 |
| 凭据扫描无匹配导致验证聚合命令退出 | 改为显式输出“未发现”，继续完成链接和文件检查。 |

- 2026-08-10：已完成 `GET /openapi/v1/contacts/{friendId}/common-chatrooms` 分页读取。Android 解析共同群列表、群名称、成员数及好友群内角色，支持 `weChatId`、分页、搜索和删除数据过滤参数；契约单测通过，待真机验收，不调用群同步或群管理写接口。

- 2026-08-10：已完成 `GET /openapi/v1/contact-labels` 标签字典读取。Android 返回标签 ID、名称、颜色、说明及联系人/画像统计，支持所属微信号与已删除数据筛选；契约单测通过，待真机验收，不调用标签同步或任何写接口。

- 2026-08-10：已完成 `GET /openapi/v1/contacts/wxids` 好友 wxid 快捷读取。Android 支持微信号、搜索、好友状态、标签、客户等级、来源渠道、画像 Key 和画像存在性筛选，解析 `wxids/count`；契约单测通过，待真机验收，不执行后续批量写操作。

- 2026-08-10：已完成 `POST /openapi/v1/contacts/labels` 写入契约。Android 提供设备、微信号、联系人 ID/wxid 和完整标签集合请求模型；接口注释明确这是替换语义、空集合会清空标签，并记录人工测试与回读验收步骤。仅完成编译验证，严禁 AI 自动调用，待人工测试。

- 2026-08-10：已完成 `POST /openapi/v1/contacts/labels/batch` 写入契约。Android 提供指定好友列表、标签 ID/名称、合并模式和 1..200 数量限制，并解析汇总数量、有效标签及逐好友任务结果。仅完成编译验证；首次人工测试须使用单个测试好友、`mergeExisting=true`、`maxCount=1`，严禁 AI 自动调用。

- 2026-08-10：开始集中补齐悬浮聊天 SCRM 扩展接口契约。已完成现有 Android 方法与 OpenAPI 路由差异盘点，后续按联系人与权限、消息操作、群管理三个域实施；明确不触碰 UI，所有服务端写操作只编译、不自动调用。
- 2026-08-10：文件化规划恢复脚本因系统 `python.exe` 无法启动而失败一次，已改用 Git 差异与现有三份规划文件恢复上下文。
- 2026-08-10：联系人与权限扩展契约代码已落地；首次 Kotlin 编译被其他 AI 正在修改的 `FloatingChatRuntimeSections.kt:7` UI 编译错误阻断。本任务不修改 UI，保留错误并继续接口定义。

- 2026-08-10：已完成联系人与权限扩展契约：标签创建/同步/删除、按筛选批量打标、联系人管理分页、客户画像草稿/保存、删除好友、朋友圈权限单个/批量/按筛选、好友资料编辑与刷新。全部标记待 UI 对接；写操作不自动调用。
- 2026-08-10：已完成消息操作扩展契约：卡片模板、收藏表情、小程序卡片、明确目标/按筛选批量发送、会话状态/历史/MsgSvrId 同步、清空本地聊天记录，以及稳定消息 ID 的转发、撤回、详情补拉、媒体下载和语音转文字。仅卡片模板为只读接口。
- 2026-08-10：已完成群管理扩展契约：按筛选建群、入群邀请处理、群接龙、二维码入群、管理员、筛选拉人/踢人、通知/备注/通讯录/群内昵称/置顶/验证和群主转让。全部待群详情或群设置 UI 对接，写操作不自动调用。
- 2026-08-10：扩展接口 Kotlin 编译已通过；当前准备运行完整 Debug APK 构建。编译输出仅有既有 Android API 弃用警告。

- 2026-08-10：`:app:assembleDebug --no-daemon` 构建成功。整个模块单测未运行：安全审查确认其范围可能包含写接口契约测试，与用户禁止测试写入接口的约束冲突；本轮仅以编译和 APK 构建验证。
- 2026-08-10: Stage 1 UI integration completed: contact detail now performs read-only GET contact detail loading, renders customer profile and label chips, and provides confirmation-only request assembly for profile and batch-label writes. No SCRM write request is sent; awaiting lead confirmation and manual acceptance.
- 2026-08-10: Message SCRM UI preview added. Long-press -> More now exposes forward, revoke, voice transcription, detail pull, media download, original pull, and emoji detail request previews. Each request is assembled only after confirmation and is not sent automatically.
- 2026-08-10: Group SCRM UI preview added. Group info now exposes confirmation-only previews for filter member operations, manager changes, ownership transfer, room switches, nickname, and QR join. No group mutation is sent automatically.

# 2026-08-11

- 2026-08-11：联系人详情页已修正入口路由。“编辑资料”现在打开备注、描述和电话的请求预览表单；“朋友圈权限”单独进入带已同步状态校验的权限编辑页。移除了详情页中不读取服务端状态的临时权限开关，避免默认值被误认为实际权限。联系人定向单测与 Debug Kotlin 编译均通过。

- 2026-08-11：消息高级操作预览已补齐参数校验状态。长按消息进入“更多”后，转发必须填写目标会话 ID，下载媒体必须填写媒体资源 ID；所有操作均会先核验远端消息 ID 与当前账号 SCRM 路由。校验通过只显示“参数已校验，可组装请求；不会发送”，不调用真实写接口。新增 `ScrmMessageOperationPreviewValidationTest`，定向单测通过。

- 会话级消息操作预览已完成 GREEN 验证：共享消息类型映射补齐后，`:ubiki-accessibility:testDebugUnitTest --tests 'com.paifa.ubikitouch.accessibility.floatingchat.message.ScrmConversationOperationPreviewTest'` 通过，主模块编译同时通过；未调用真实 SCRM 写接口。
- 新增会话级消息操作预览：未读数、历史消息、消息 ID、未读列表同步和“清空全部本地聊天记录”均可从 SCRM 运营工作台进入。清空操作要求勾选并输入 `清空全部`，消息 ID 同步强制不超过 10 分钟；所有动作仅构造请求预览。新增 `ScrmConversationOperationPreviewTest` 已按预期在 RED 阶段失败，随后主模块编译被其他并行改动引入的 `FloatingChatAi.kt`、`MessageBubbleStyle.kt`、`MessageContent.kt` 非穷尽消息类型 `when` 阻断，尚未获得 GREEN 测试证据。
- 群管理“按筛选建群/邀请/踢出”补齐关键词、标签 ID、标签名称、客户等级和来源渠道表单，并将非空条件带入请求预览；标签 ID 仅接受正整数英文逗号列表，非法输入会明确阻止组装，不会静默丢弃。新增 `GroupMemberFilterTest`，主源码编译和该定向单测均通过；全程未调用 SCRM 写接口。
- 群管理预览补齐“同意自己入群邀请”的参数绑定：邀请来源 `talker` 现在从专用输入框传入 `ScrmAgreeChatRoomInviteRequest`，不再误用成员 wxid；仅组装预览，不调用写接口。` :ubiki-accessibility:compileDebugKotlin --no-daemon --max-workers=1 '-Pkotlin.incremental=false' --console=plain` 已通过。SCRM 定向单测运行到测试源码编译，但被无关的 `ContactPanelPolicyTest.kt:14` 未解析 `contactEditPanelReservesStatusBarHeightDp` 阻断。

- 联系人资料浮层：左侧联系人头像长按继续使用现有 `ContactEditorTarget.User` 路由，但用户资料承载层已改为 MATCH_PARENT 全屏浮层，去除卡片宽高限制、圆角、边框和阴影；群资料仍保持居中卡片。Debug APK 已安装到设备 `d0512adb` 并完成启动验证。

- 客户画像、单联系人标签与朋友圈权限：状态层提交于 `451b07a`，编辑页和 Host 闭环提交于 `b061fbd`。画像 PUT 后 GET 回读，标签/权限 POST 后等待任务终态并 GET 详情回读；聚焦单元测试和 `:app:assembleDebug` 已通过。未触发任何真实写接口，待人工使用测试联系人验收。

- 已创建 `框架蓝图/13-SCRM接口实现测试与UI对接总览.md`、`14-SCRM接口UI操作步骤与验收清单.md`、`15-SCRM未对接接口与后续开发计划.md`。
- 已更新框架蓝图阅读指南和 SCRM 消息中枢文档，纠正过时的“约 54 个契约”表述。
- 已完成文件存在性、敏感信息扫描和 `git diff --check` 验证；未执行真实服务或真机写接口验收。
- 已新增 `框架蓝图/16-SCRM接口缺口清单与UI完善项.md`，列出客户端缺失接口、UI 仅预览接口和真实验收缺口，并完成链接与敏感信息检查。
- 已新增 SCRM 运营工作台 UI：从“更多”进入，提供消息、联系人、群管理、朋友圈四个分段，以及已接入/预览/待接入状态和展开式操作预览；本轮未调用 SCRM 接口。
- 已补充动作草稿态：转发目标会话、标签/筛选、画像备注和确认勾选，并保留生成请求预览按钮；未执行发送。
- 已优化联系人资料页的画像编辑和批量标签 UI，增加微信风格分组提示、预览状态说明和标签输入草稿；仍不发送写请求。
- 已优化群管理预览面板，按成员权限、群设置和入群分组，加入影响范围提示与确认勾选；编译通过，仍不发送写请求。
- 已补充朋友圈工具菜单，提供互动消息、可见范围、删除/补拉、批量计划和素材发布入口，统一显示 UI 预览状态；编译通过，未改变现有朋友圈同步与互动调用。
- 已优化消息长按“更多”预览面板，按消息处理和内容媒体分组，加入目标摘要、转发输入、确认勾选和预览状态；编译通过，未执行消息写接口。
- 已优化底部 SCRM 发送工具面板，统一账号/会话摘要、风险提示、确认态和卡片模板展示；编译通过，非只读发送仍只生成请求预览。
- 已将 SCRM 运营工作台连接到现有 UI：卡片模板、批量发送和朋友圈素材可从操作预览直接进入对应面板；其余未接入动作继续保留预览。
- 已补充收藏表情和小程序卡片的工作台入口，并直接导航至对应 UI 表单；编译与 diff 检查通过。
- 已补充联系人运营与朋友圈工具到现有通讯录/朋友圈面板的导航；需要精确消息或群上下文的操作仍保留预览。
- 已扩展客户画像编辑 UI，增加来源详情、画像标识、社交账号和购买记录字段，并保留仅预览写入边界；编译通过。
- 已为群通知、置顶、保存通讯录和入群验证增加开关草稿态，预览请求会携带所选开关值；编译通过，仍不发送接口。
- 已为消息详情补拉和媒体下载增加原文开关、媒体资源 ID 输入与参数预览绑定；编译通过，未调用消息接口。
- 已补充 SCRM 工作台的路由状态提示，缺少账号或会话时禁用现有工具导航；编译与 diff 检查通过。
- 已将朋友圈高级工具菜单升级为选择、影响说明、确认勾选和生成预览的完整 UI 草稿态；编译通过，仍不执行朋友圈写接口。
- 已在联系人资料页增加朋友圈权限三项开关及请求预览。Kotlin 增量编译缓存缺文件后已自动回退全量编译并通过。
- 已新增好友资料编辑 UI，支持备注、描述和电话，并与客户画像编辑明确分层；仅组装 `ScrmModifyFriendProfileRequest` 预览，编译通过。
- 已新增标签管理 UI，支持新建或重命名标签的名称与 ID 输入，并只组装 `ScrmSaveContactLabelRequest` 预览；编译通过。
- 已在标签管理弹窗增加不可撤销删除的预览入口，仅填写有效标签 ID 后启用；编译通过，未执行删除接口。
- 已补充当前账号标签同步的预览入口，使用 `ScrmAccountMutationRequest` 组装范围；编译通过，未执行同步接口。
- 本轮尝试运行 SCRM 单元测试时，被工作区既有 `ScrmContactEditorStateTest.kt` 缺少 `FriendPermissionsDraft` 等符号阻断；主源码此前已编译通过，未修改该无关测试。
- 已新增好友资料“预览刷新”入口，对应 `ScrmRefreshFriendInfoRequest`。随后一次编译被 Gradle daemon 外部停止，重试因并行构建进程超时，暂未获得本次改动的最终编译证据。
- 已新增群接龙 UI 入口和接龙内容输入，使用当前群 ID 组装 `ScrmSendJielongRequest` 预览；后续全量 Kotlin 编译通过。
- 已补充刷新/审批入群邀请 UI：审批要求服务端消息 ID 和邀请内容，使用当前群 ID 组装预览。当前整模块编译仍受并行修改引入的 `ContactEditorPage` 未定义符号阻断。
- 已修复联系人面板扩展 API 的静态类型接线，显式转换为 `ScrmContactManagementApi` 并保留清晰错误；随后完整 Kotlin 编译通过。
# 2026-08-10

- Bottom More panel SCRM message entries added: favorite emoji, WeApp card, read-only card templates, and filter batch-send preview. Send entries assemble requests only. Focused unit-test verification is blocked by an unrelated shared-worktree compile error: `ScrmFloatingChatBridge.kt` cannot resolve `scrmStableColor`.
# 2026-08-11 悬浮聊天消息类型对齐

- 已新增 `悬浮聊天消息渲染对照.md`：以 iOS 的 13 个 renderer group 为准，逐一对应 30 类 iOS 消息、Android 模型、远端 `messageType` 映射、当前状态和分批验收顺序。后续不再将“枚举已注册”误判为“UI 已对齐”；本轮仅处理只读渲染，不调用 SCRM 写接口。
- 首批渲染分发已落地：新增 `MessageRendererGroup`，将消息展示从集中式枚举分支改为 iOS 同构的 text/media/voice/document/location/profile 等 renderer group。`Emoji` 不再被错误交给图片组件；含远端 URL 的 GIF 贴纸复用媒体缩略图管线，无 URL 时保留明确占位。
- 验证：`:ubiki-accessibility:compileDebugKotlin --no-daemon --max-workers=1` 成功；`:app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.incremental=false' --console=plain` 成功，Debug APK 已安装到 `d0512adb`。定向单测编译仍被无关的 `MomentMaterialPublishPreviewTest.kt` 缺少 `MomentMaterialPublishPreviewDraft` 阻断，未修改该朋友圈测试。
- 第二批链接渲染已完成：网页、公众号文章、小程序、视频号直播、音乐和收藏各自保留来源类型；卡片优先使用 `thumbnailUrl/resourceUrl` 作为右侧预览，缺少媒体时才显示对应类型标识。模块编译和全量 Debug APK 构建通过，已重新安装到 `d0512adb`；未调用任何 SCRM 写接口。
- 第三批已完成支付、通话、接龙、群公告的 renderer group 分发与卡片信息层级调整。当前增量编译被工作区并发的 `MomentsToolPanels.kt:1565/1611/1616` 类型错误阻断，未改动该无关朋友圈模块；因此本轮没有生成或安装新 APK，待该文件恢复可编译后重新验证。
- 阻断已消失，`:ubiki-accessibility:compileDebugKotlin --no-daemon --max-workers=1` 和 `:app:assembleDebug --offline --no-daemon --max-workers=1 '-Pkotlin.incremental=false'` 均成功；Debug APK 已使用 `adb -s d0512adb install -r -t` 安装成功。第三批不调用任何真实支付、通话或消息写接口。
- 已新增 `悬浮聊天SCRM接口人工验收测试清单.md`：整理设备/账号/联系人基础读取、bootstrap/history/changes、客户画像、联系人详情、共同群、标签字典、wxid 列表和单个/批量标签写入共 11 项接口，包含 UI 操作路径、验收标准、写接口人工发送与回读规则，以及测试记录模板。

- 已对照 `C:\WorkSpace\ios-float\ChatModels.swift` 与 Android `FloatingChatMessageType`，确认 Android 原先缺少：Emoji、贴纸/GIF、拍摄照片、实时位置、群邀请、网页链接、公众号文章、视频号视频、视频号直播、音乐分享、收藏分享、红包、转账、AA 收款、优惠券、语音通话、视频通话、接龙消息、群公告。
- 已在 Android 核心模型加入上述 19 种类型，并在样本会话中为每种类型加入只读示例。
- 已在悬浮聊天消息分发中加入统一富消息卡片，展示类型标题、摘要、详情及资源链接；不进入发送队列、不调用写接口。
- 已补齐消息策略的未知类型分支，避免 AI 摘要和气泡样式因新增类型中断。
- 验证：`:ubiki-core:test --tests "*sampleConversationContainsOneExampleForEverySupportedMessageType"` 与 `:ubiki-accessibility:compileDebugKotlin` 通过；仅有项目既有 Android API 弃用警告。
- 已修复 SCRM 历史消息桥接：按 iOS/微信 `messageType` 映射图片、语音、GIF、位置、名片、视频、链接、文件、聊天记录、转账、红包和通话；空内容使用类型化占位文案，不再统一显示“消息”。
- 已提取 JSON 中的 `content/text/title/description/url` 等字段，填入消息摘要、详情和资源地址；仍保持只读，不调用发送接口。
- `:ubiki-accessibility:compileDebugKotlin`、`:app:assembleDebug` 通过，APK 已安装到 `d0512adb`。定向单测被工作区既有 `MomentBatchPublishPlanDraft` 缺失引用阻断，未修改该无关问题。
- 二次修复：补齐 SCRM 历史消息的 `media[]`、`extensions[]`、`voiceText` 载荷；图片/拍摄照片与视频号视频复用现有媒体预览，资源 URL、文件扩展名和大小进入消息模型。
- 已将朋友圈“批量发布计划”从通用提示升级为本地草稿预览：支持计划名称、素材/文案摘要、目标账号或范围、计划时间、草稿/待确认/暂停状态和预计影响数量；明确显示“仅生成预览，未创建、未执行”，确认后仍不调用写接口。
- 新增 `MomentBatchPublishPlanPreviewTest`，已先验证 RED；GREEN 阶段被工作区既有 `ScrmFloatingChatBridge.kt:209` 的 `remoteMessageServerId` 参数错误阻断，本轮未修改该无关接口问题。`git diff --check` 通过。
- 已补充朋友圈“互动消息：未读与已读”详情预览：可选择读取未读或标记全部已读、填写目标范围和预估未读数；标记已读时必须确认，并明确显示待确认、未执行。未读数仍以接口读取结果为准，本地输入仅用于预览，不调用读取、清空或已读写接口。
- 新增 `MomentInteractionPreviewTest`。两组朋友圈预览测试均先于模型实现创建；最新运行已通过主源码 Kotlin 编译，随后因共享 Android 构建产物中的 `Overscan$Companion.class` 在 library bundle 阶段失败，测试未实际执行。本轮未清理共享 build 目录。
- 已补充朋友圈“可见范围与置顶”详情预览：支持所有朋友、不给指定朋友看、仅指定朋友可见三种范围，支持目标范围、预计影响账号数和置顶草稿；所有变更均需确认，明确标注“待确认，未执行”，未调用可见范围或置顶接口。
- 新增 `MomentVisibilityPreviewTest`。测试尝试被共享 Gradle 临时目录文件锁阻断，错误为无法删除 `ubiki-accessibility/build/tmp/kotlin-classes/debug`；未清理或终止其他构建进程。
- 已补充“删除动态与好友刷新”预览：删除必须填写动态 ID、输入“删除动态”并确认范围，刷新仅生成好友范围预览；两者均不会调用真实删除或刷新接口。
- `MomentBatchPublishPlanPreviewTest`、`MomentInteractionPreviewTest`、`MomentVisibilityPreviewTest` 与 `MomentCleanupPreviewTest` 已在共享构建恢复后统一通过；`git diff --check` 通过。
- 已补充“从素材复制发布”详情预览：必须填写素材 ID，支持文案摘要、目标范围与计划时间，并要求范围确认；明确显示“仅生成素材发布预览，未复制、未发布”，未调用素材复制或朋友圈发布接口。
- 新增 `MomentMaterialPublishPreviewTest` 并已验证 RED；GREEN 阶段主源码 Kotlin 编译通过，但被共享 Android library bundle 临时 class 文件阻断，测试尚未开始。本轮未清理共享 build 目录。
- `MomentMaterialPublishPreviewTest` 已在构建环境恢复后通过；朋友圈高级工具五项预览测试均已通过，主源码编译与 `git diff --check` 均通过。
- 已将朋友圈素材“保存”改为创建预览，文案为空时禁止生成，名称和分类显示默认值；复制、归档、创建三个素材操作现在均从 UI 预览入口开始，不调用对应写接口。
- `MomentMaterialCreatePreviewTest` 与 `MomentMaterialOperationPreviewTest` 已通过，主源码编译和 `git diff --check` 通过。
- 已在 SCRM 运营工作台增加“接口验收状态”摘要，明确区分本地测试已通过、真实服务待验收、写操作仅预览；不伪造真实服务成功结果。`:ubiki-accessibility:compileDebugKotlin` 通过。
# 2026-08-11 支付模块 iOS 流程对齐（第一阶段）

- 已完成确认页微信零钱回读入口：用户点击“零钱”后才调用 `wallet-balance`；同步回包展示格式化余额，异步回包只展示任务 ID，不伪造余额。
- 支付状态中心已接入用户主动触发的 `tasks/recent` 只读查询，并筛选支付相关任务显示 taskId、状态和安全说明。
- 新增钱包余额 fixture 解析测试；`PaymentFlowTest`、完整 Debug APK 构建通过，APK 已安装到 `d0512adb`。
- 已完成支付详情浮层的只读状态回读：用户点击“刷新支付状态”后，红包按服务端消息 ID 调用 `red-packets/detail-by-message`，UI 显示加载、任务处理中、可领取、已领取、已过期、已领完、拒收、失败和待人工核对状态。
- 转账详情不会错误调用红包接口；当前接口文档未提供按消息查询转账详情的只读路由，UI 明确提示应使用任务回读。领取和收款点击不再写本地“已成功”状态，改为人工验收提示。
- 验证：关闭 Kotlin 增量编译后，`PaymentFlowTest` 通过；Debug APK 构建成功并已安装到 `d0512adb`。没有调用真实支付、领取、转账或钱包接口。
- 已新增支付模块设计与实现计划：`docs/superpowers/specs/2026-08-11-支付模块对齐设计.md`、`docs/superpowers/plans/2026-08-11-支付模块对齐计划.md`。
- Android SCRM 已补齐红包、红包领取、红包详情/状态、转账、转账收取、钱包余额 7 组支付 API 的请求模型、路由和幂等键调用逻辑；写接口仅保留人工触发入口。
- 新增支付金额分/元转换、任务 `processing/success/failed/unknown` 状态映射、红包/转账详情状态解析；`unknown` 不允许自动重发。
- 红包/转账 Composer 已改为 iOS 风格两步确认：填写 -> 确认摘要 -> 生成请求；确认摘要显示账号流程和钱包余额查询占位，不自动调用真实接口。
- SCRM 运营工作台消息页新增“支付状态中心”流程预览，展示红包、转账、钱包任务范围及人工只读刷新边界。
- 自动验证：`PaymentFlowTest`、支付写接口幂等键/路由测试通过；真实支付接口未测试。
# 2026-08-11 悬浮聊天左侧会话轨道

- 已实现当前会话头像上缘的用户画像眼睛图标和下缘红色三点会话详细设置图标；非当前会话不会渲染这些入口。
- 已实现当前会话停止 5 秒后在轨道内插入转发和朋友圈操作位，切换会话或开始滚动会立即取消停留状态。转发和朋友圈仅进入既有工具面板，不调用写接口。
- 已复用现有 pinned avatar 覆盖层：当前会话滚出左侧轨道上下边界时，头像及其选中操作固定在边缘；回到可视区域后由列表项继续渲染。
- 已为会话摘要增加可选地区和标签行，模型未提供数据时不显示空行。
- 已核对聊天消息区：LazyColumn、详细气泡、系统消息无气泡、勾选式多选和底部转发/收藏/删除/关闭操作均已存在。本轮未修改 renderer，防止影响既有 30 类消息渲染。
- 验证：`ChatLayoutStateTest.selectedRailActionsAppearOnlyAfterFiveSecondsWithoutScroll` 已先失败再通过；`:ubiki-accessibility:compileDebugKotlin --no-daemon --max-workers=1 '-Pkotlin.incremental=false'` 成功。未构建 APK，未调用任何写接口。
## 2026-08-11 悬浮聊天标准界面完善

- 已完成 `标准.txt` 与当前 UI 的逐项审查。
- 已确定按消息交互、气泡、消息类型、右侧工作流的顺序实施。
- 未调用任何写接口，未构建 APK。
- 首次扩展“放大”操作后编译发现 `FloatingChatMessageType` 缺少导入，已按根因补充导入。
- 消息主操作已固定为标准八项：话外音、放大、复制、转发、收藏、删除、多选、引用。
- 多选栏可见操作已调整为转发、合并转发、收藏、删除、关闭，勾选控件移动到消息下边缘中央。
- 气泡最大宽度由 99% 收紧到 88%，详细气泡发送者名称增加半透明衬底、阴影和边缘偏移。
- SCRM 只读映射补充 appmsg type 57 引用、type 5/H5、type 2002 领取红包识别。
- 右侧 16 个标准业务名称已对齐，第 17 项保留“文件”；AIFF 第二行使用横向 marquee。
- 定向测试 `MessageStandardActionsTest`、`RightRailWorkflowLabelTest` 已通过。
- 最终验证通过：`:ubiki-accessibility:testDebugUnitTest` 两个定向测试类、`:app:compileDebugKotlin`。
- 未构建或安装 APK；未调用任何发送、支付、红包、转账写接口。
- 真机待验收：详细气泡标题的透明质感、气泡左右最大边界、AIFF marquee 节奏、右侧头像快速连续点击稳定性。
- 右侧头像连续点击优化：复现 30 次快速点击未抓到 `FATAL EXCEPTION`，但观察到 18-72ms 主线程帧耗时。
- 根因定位为账号切换重复构造账号作用域会话，以及近期缓存命中后仍重复安排只读刷新；头像加载器原本已有内存 LRU、磁盘缓存和 in-flight 合并。
- 新增 `AccountScopedConversationCache`，同一源会话下按账号复用联系人、群聊和消息树；源会话变化时通过 Compose `remember` 自动整体失效。
- 同账号或空账号 ID 的重复点击在状态切换前直接忽略。
- SCRM 账号只读会话增加 15 秒明确新鲜期，近期缓存命中不重复 GET，过期后仍正常刷新。
- 回归测试 `AccountAvatarSwitchPolicyTest` 和缓存新鲜度测试通过；未调用任何写接口。
- 最新验证：重新执行 `:ubiki-accessibility:testDebugUnitTest`（`AccountAvatarSwitchPolicyTest`、`FloatingChatOverlayControllerContractTest`）通过，`:app:compileDebugKotlin --no-daemon --max-workers=1 -Pkotlin.incremental=false` 通过；未构建或安装 APK，未调用发送、支付、红包、转账等写接口。
- 真机复测前提：需安装本次编译产物后，再用 ADB 连续点击右侧账号头像，对比 `AndroidRuntime` 崩溃栈和跳帧日志；当前证据已确认并优化的是重复重组、重复只读刷新和旧回包覆盖风险。
- 追加 ANR 根因证据：设备日志出现 `signal 3`、`Wrote stack traces to tombstoned`，随后进程结束；主线程热点位于 `FloatingChatPrototype.pairedAccountFor` 的账号与消息嵌套扫描。已改为单次消息遍历建立账号集合和线程账号映射，避免账号数乘消息数的扫描放大；`:ubiki-core:test` 与 `:app:compileDebugKotlin` 已通过。当前设备尚未安装包含本次修复的新 APK。
- 已按 `C:\WorkSpace\ios-float` 的 `API_FRONTEND_INTEGRATION_PROGRESS.md`、`AppKitRegistry.swift`、Payments/Wallet/Calls/Search/Moments/OpenAPIWorkbench/OperationLab/MessageRender 模块重新盘点 Android SCRM。`完成进度.md` 顶部新增 2026-08-11 权威结论、模块状态总表、接口优先的 P0-P3 计划和验收口径；旧百分比表已标为历史快照。未修改功能代码、未调用接口、未构建 APK。
# 2026-08-11 右侧头像大量消息 ANR 修复

- 已恢复仓库上下文，确认工作区只有用户既有的 `项目会话.txt` 修改，本轮不会触碰。
- 已定位头像点击入口，并发现仓库中已有两轮相关性能优化记录；当前进入根因复核，不把历史记录直接视为修复完成。
- 已建立本轮阶段计划、成功标准和接口测试约束。
- 已确认当前提交包含 `pairedAccountFor` 单遍消息扫描优化，但仍需核对同一头像切换帧中的调用次数和其他全量消息派生计算。
- RED 证据：20,000 条消息回归测试在旧实现下超过 60 秒测试上限。
- 已实现单次消息线程索引与点击/重组共享账号会话缓存；首次 GREEN 被既有测试签名兼容问题阻断，已补回纯逻辑重载。
- GREEN 证据：20,000 条消息定向回归测试通过，测试体耗时 0.089 秒；消息列表只遍历一次。
- 回归验证：`AccountAvatarSwitchPolicyTest` 5 项和 `FloatingChatMessageUiContractTest.accountAvatarClickSwitchesToIndependentAccountWorkspace` 通过。
- 构建验证：`:app:compileDebugKotlin`、`:app:assembleDebug`、`git diff --check` 通过，Debug APK 已安装到 `d0512adb`。
- 真机验证：现有只读数据约 20 个账号、286 个联系人；右侧头像快速轮换点击 40 次后进程 PID 保持 `30835`，日志无 ANR、`signal 3`、`FATAL EXCEPTION` 和 Choreographer 跳帧记录。
- 接口边界：未点击或测试发送消息、支付、红包、转账及其他写操作。
- 独立审查发现私聊回退初版遗漏“最近 6 条”和“全局连接消息前 4 条”语义；两个新增测试已先验证 RED，随后修正有界索引策略。
- 私聊两项回退测试已转为 GREEN；完整头像策略、账号工作区契约和最终 Debug APK 构建均通过，复审未发现新问题。
- 最终 APK 已覆盖安装到 `d0512adb`；追加 20 次头像轮换后 PID `2098` 存活，ANR/崩溃/signal 3/跳帧日志匹配数为 0。
# 2026-08-12 聊天消息内容与格式审计

- [x] 完成主消息渲染链路和消息类型分组静态审计。
- [x] 确认结构化字段内容判定与媒体类型判定存在覆盖缺口。
- [in_progress] 添加失败测试并实施最小修复。
- [x] 完成定向测试、模块测试、编译/APK 构建和只读验证（完整回归的既有基线失败另行记录）。

## 本轮交付

- 修复结构化消息字段误判为空：位置、名片、引用、聊天记录、文件名、链接标题等字段现在可作为有效显示内容。
- 修复媒体失效判定覆盖：拍摄照片、视频号视频与普通图片/视频一致；动态表情有资源时走图片预览，无资源时保留 GIF 占位卡。
- 修复卡片格式：链接地址不再伪装成缩略图；链接、小程序、接龙、动态表情、文件均有稳定标题；位置地址、文件大小、名片副标题/详情为空时不绘制空行；无引用源时不绘制空引用块。
- 新增 `MessageDisplayPolicyTest`，覆盖结构化位置、媒体类型、引用/聊天记录、链接缩略图和卡片标题回退。

## 验证证据

- 通过：`:ubiki-accessibility:testDebugUnitTest --tests ...MessageDisplayPolicyTest`。
- 通过：消息渲染分组、详细气泡、SCRM 浮窗桥接与消息提取相关定向测试（消息显示相关新增/既有测试均通过）。
- 通过：`:app:compileDebugKotlin`、`:app:assembleDebug`；Debug APK 已安装到 `d0512adb`。
- 通过：设备只读启动检查，进程保持存活，最近日志无 `FATAL EXCEPTION` / `ANR`；未点击发送、支付、红包领取、转账等写操作。
- 已知基线失败：完整回归中的 `ChatExtractionContractTest` 2 项和 `FloatingChatMessageUiContractTest` 3 项为工作区既有迁移/UI 断言，未由本轮消息格式改动引入。
# 2026-08-12 Android 支付模块真对接

- 已读取 Android 支付 UI、SCRM 客户端/模型/任务状态解析与 iOS `PaymentKit.swift` 入口。
- 已只读下载后端 OpenAPI spec，确认 7 条支付路由、请求字段、异步任务返回和幂等键规则；未调用任何真实支付业务接口。
- 已定位假成功根因：发送仍创建本地工具消息，领取未进入服务端任务，零钱/红包详情仅提交查询任务但缺少统一终态编排。
- 下一步：先添加契约与编排失败测试并确认 RED，再修改生产代码。

# 2026-08-12 视频号 Finder 模块与头像 ANR 收尾

- 已从交接状态恢复工作区，读取 `task_plan.md`、`findings.md`、`progress.md` 并核对大量并行用户改动；本轮不回滚无关内容。
- 已复核头像点击数据流、账号会话缓存和 20,000 条消息性能回归测试，确认生产点击使用共享缓存版本。
- 已核对 Finder 契约、API、任务等待、四个页面、右侧工具枚举和底部面板，确认主要缺口是消息可信路由、SCRM 映射、工作区与宿主接线。
- 已加载产品设计上下文；Finder 采用现有居中底部工作区和 `OverlayTokens`，不新增视觉体系或自动网络请求。
- 未调用发布、点赞、评论、导航、发送消息、支付等真实写接口。

# 2026-08-12 悬浮聊天头像显示与异步缓存

- 已加载调试、设计澄清、TDD、文件规划、UI 质量和完成前验证工作流。
- 已检查工作区、最近提交和现有任务记录；确认存在大量用户未提交修改，本轮不会回滚。
- 已加载 `PRODUCT.md`，确认头像修复应保持现有悬浮工作台布局与组件语言。
- 已开始检索头像字段、SCRM 映射、Compose 渲染和媒体加载缓存链路；尚未修改业务代码。
- 已确认主要头像 UI 入口共用现有异步加载器；下一步聚焦统一加载器、URL 标准化和数据映射，而不是逐个头像组件重复补逻辑。
- 一次只读源码聚合命令因 PowerShell 数组类型不匹配失败，未修改业务文件；后续改为明确行区间读取。

# 2026-08-12 iOS 对 Android 全量功能与样式差异审计

- 已读取并应用 `using-superpowers`、`planning-with-files`、`brainstorming`、`dispatching-parallel-agents`、`writing-plans`、`impeccable`、`verification-before-completion` 和 `task` 的相关流程。
- 已检查 Git 工作区和两端根目录，确认 Android 当前状态包含大量用户/其他任务的未提交改动，本轮只做只读审计和目标文档编辑。
- 已并行启动 Android 实现、iOS 参考实现、文档与 UI 三条独立审计线。
- 已加载 `PRODUCT.md`；未发现 `DESIGN.md`，视觉结论将使用两端源码与现有 artifacts 交叉验证。
- 已确立状态分层、平台专属处理和禁止真实写请求的审计边界。
- 已完成两端生产/测试文件索引，并读取 Android 最近提交和 iOS 文件更新时间；`ios-float` 不是 Git 仓库，无法使用其提交历史。
- 已抽取既有完成/缺失声明、Android 占位信号和 iOS 演示信号，确认旧完成率口径与当前源码存在冲突。
- 已开始沿 Android 工具分派和底部面板检查宿主可达性，发现部分动作仍显式走 `AddSimulatedMessage`。
- 已进一步确认 `simulatedMessageToolActions()` 当前为空；右侧多项可见工作流实际落入 `None`，属于无反馈入口。
- 已核对聊天搜索、联系人搜索和转发链路：聊天搜索仍为 Preview；联系人搜索有真实 SCRM 路径；合并转发仍仅本地生成。
- 已将 Finder 状态从旧文档的“完全缺失”修正为“已接宿主、需继续核验字段/终态/真机”。
- 已抽查通话视觉 artifact，确认其为本地状态预览而非 Android 生产界面，后续只作为辅助视觉线索。
- 已完成第一轮 token、布局常量和无障碍语义检索；Android 有集中 token，但仍存在硬编码分散与语义覆盖不足风险。
- 独立文档/UI 审计已报告旧进度矛盾、无效/重复截图、性能证据边界和当前可见对比度/缺字问题；已纳入最终证据口径。
- 已完成 Android、`C:\WorkSpace\ios-float`、现有文档、OpenAPI、样式、交互和无障碍的静态审计，交付文件为 `任务进度.md`。
- 已整理 F-01 至 F-55 共 55 个功能差异、U-01 至 U-20 共 20 个样式/交互/无障碍差异、6 个开发波次、29 个唯一主任务和 45 个原子检查点。
- 已写入 AI 总控提示词、A-K 11 个任务族提示词和人工验收模板；每次执行被限制为一个内部前置已完成且启动门禁满足的原子检查点。
- 已补齐 F-55/A3：区分直接 DTO、顶层 `TaskResult`、批量 `items[]` 任务引用，以及 `task_result/external_state/untracked` 三种发现合同、合法 `taskId=0`、精确同源 URL 和独立 `final` 终结语义。
- 已将 D1 前置改为 A3/C1/C3，并在 E/F/G/H/I 的真实写状态链中统一复用 A3；不允许业务层自行拼任务 URL或把 HTTP/聚合受理状态当最终成功。
- 最终反对者审阅提出 3 项 Important：跨波次主任务状态不可机器判定、K1 ADR 与迁移边界冲突、H 的两个 iOS 路径不完整；均已修复。二次复核又发现 K1 无迁移分支和 A2 冻结聚合歧义，也已修复。
- 最新结构校验：F=55、U=20、主任务=29、原子检查点=45、提示词=11，75 个差异 ID 无重复/遗漏；45 个节点、108 条显式依赖边可完整拓扑排序且无环；Markdown 围栏成对。
- 最终文件校验：`任务进度.md` 为 UTF-8 无 BOM，1303 行、137958 字节，SHA-256 为 `EEB2CA2E15E2EEB14629E965FD8989092FC68E375952C720A2E5CE58B866FC6D`；无替换字符、tab、尾随空白或占位词，末尾换行存在。
- 同一 SHA-256 快照的最终内容门禁为 Critical 0、Important 0；29 个主任务、45 个检查点和 108 条依赖边均有效，A2@R3 固定集合、K1 无代码决议分支及 H 的 4 个 iOS 路径复核通过。
- 路径复核覆盖 108 个重点文件型 token，确定缺失且会误导执行 AI 的路径为 0；Android/iOS 独立复核均为 Critical 0、Important 0。
- 本轮未运行 Android 完整 Gradle 构建/测试、真机或真实 SCRM 验收，也未调用发送、发布、点赞、评论、加删好友、群管理、支付、领取或转账接口。

# 2026-08-13 iOS 对 Android 差异文档增量完善

- 已从交接摘要、现有三份规划文件和 Git 状态恢复现场；确认 `任务进度.md` 为未跟踪文件，工作区还有大量其他任务改动，本轮不回滚或格式化无关内容。
- 已增量复核 Android 的 TalkBack 双重屏蔽、Finder 身份持久化、朋友圈素材生产入口和 Finder 状态页，以及 iOS 的消息来源、长按排序、好友主页、群搜索能力边界。
- 已完善 `任务进度.md` 的下一检查点确定性选择规则、单检查点执行卡、并发认领/释放/接管规则、审计快照刷新协议和文档结构门禁。
- 已明确所有真实写操作只能由人类测试者执行；AI 只能准备代码、本地 fixture/Mock、验收步骤并接收证据，用户授权不改变该边界。
- 已校正 A1 与 F3 的 Finder 身份持久化所有权：A1 建立合同，F3 只消费和验证；没有升级任何 F/U 状态，也没有重排六个开发波次。
- 已启动三条只读独立复核：源码事实与依赖、A-K 提示词可执行性、结构/DAG/编码；代理不得修改文件，主线程统一处理结果。
- 当前尚未宣称最终验收通过；待复核意见汇总后将重新计算编号、所有权、依赖图、编码/空白规则、行数、字节数和 SHA-256。
- 本轮没有运行 Android Gradle、Xcode、真机或真实服务验收，没有调用任何真实写接口。
- 已接收第一路提示词/调度审阅，核对后修正 5 类 Important：初始受阻状态、波次双层前置、HUMAN_WRITE 自动段 TDD、统一交付证据格式、K 与普通通话所有权。
- 已把 `D2@D0`、`E2@A3`、`I3@D5`、`K2@D5` 恢复为“未开始”并预登记外部门禁；第 9.1 节 D2/K2 派生状态同步为“未开始”。没有升级任何 F/U 状态，也没有重排六个波次。
- 已补全提示词 F 的 F-35 素材分页/加载/空/错误/图片失效/刷新恢复，提示词 G 的 F-44 Wallet 零钱读取或禁用二选一，提示词 J 的 U-19 伪证据失败测试，以及提示词 K 的三个互斥分支。
- 工具错误已补记到 `task_plan.md`：聚合 `rg` 无匹配、JavaScript 反引号解析、PowerShell 字母范围和 F/G 组合补丁锚点失败；这些失败均未造成业务代码或不完整文档写入。
- 当前仍在等待三路只读复核的最终结果，尚未运行最终结构/编码/哈希验收，也未宣称完成。
# 2026-08-13 悬浮聊天错误数据与头像缺失诊断

- 已读取并应用系统化调试、根因追踪、文件规划和代码质量规则。
- 已检查 Git 状态：悬浮聊天、SCRM、测试和规划文件均存在大量既有修改，本轮不会回滚。
- 已建立本轮诊断阶段、成功标准和只读接口边界。
- 已完成首轮头像符号检索，定位 DTO、桥接映射、UI 加载器和接口总览文档；尚未修改业务代码。

## 本轮诊断结论

- [x] 对照 OpenAPI JSON、Android DTO、SCRM API client 和 iOS bootstrap 消费方式。
- [x] 追踪 bootstrap 摘要到 history/bridge/UI 的字段流，确认摘要字段在控制器组装时丢失。
- [x] 追踪头像 URL 到 `normalizedRemoteImageUri` 和 `MediaThumbnailBitmapLoader`，排除统一 URL 过滤为首要根因。
- [x] 确认首页未读使用固定 30 条 `scrmUnreadDemoMessages`，不是服务端 `unreadCount`。
- [x] 读取真机只读日志：仅出现 `UbikiAvatar host=aiff.app` 样例资源；未出现真实头像域名。
- [x] 运行定向单测命令；被既有 `FinderContractsTest.kt:99` 编译错误阻断，未声称测试通过。

## 本轮交付判断

问题根因已定位到“bootstrap 摘要数据未进入 Android 运行时模型”和“真实刷新失败时样例会话仍可见”两处；头像加载器不是首要故障点。下一轮修复必须先写失败测试，再修改摘要模型/映射和样例失败状态。
# 2026-08-13 悬浮聊天数据来源诊断日志

- 仅修改悬浮聊天数据刷新诊断链路，不改现有后端映射、fallback 或界面行为。
- 新增统一日志标签 `UbikiChatData`，覆盖初始 prototype、刷新请求/排队/失败、账号与设备响应、路由选择、联系人/群分页、bootstrap、历史消息、缓存命中、bridge 映射与 UI 状态提交。
- 账号、设备、微信号、会话号只记录 SHA-256 的 8 位短指纹；不记录 API Key、密码、联系人名称或原始标识。
- 新增 `FloatingChatDataDiagnosticsTest`，先确认辅助函数缺失的 RED，再实现数据来源判定与标识脱敏。
- `:ubiki-accessibility:compileDebugKotlin` 在禁用 Kotlin 增量编译后通过；首次增量构建遇到共享缓存占用并回退失败。
- 定向测试通过，但使用现有 init script 排除了工作区原有且缺少 `validatedFinderPostRequest` 的 `FinderContractsTest.kt`；未声称完整测试套件通过。
- 最终复跑时又被工作区并发出现的 `BottomToolPanels.kt:16` Compose `weight` 访问错误阻断；本轮不越界修改该工具面板，最终状态不声称全模块最新快照编译通过。
- 2026-08-13 用户提供设备日志后确认：SCRM `devices=21/accounts=20` 正常，刷新失败根因是 `ScrmFloatingChatBridge.kt:408` 对 JSON 数组调用 `jsonPrimitive`，导致 `JsonArray ... is not a JsonPrimitive`，界面因此保留 prototype 数据。
- 按 TDD 新增数组字段回归测试，先 RED 后 GREEN；修复 `scrmFloatingJsonUrlValue`，仅对 `JsonPrimitive` 读取 URL，并递归遍历 `JsonObject`/`JsonArray`。
- `ScrmFloatingChatBridgeTest` 全类通过。
- 2026-08-13 继续诊断“十年账号未回列表末尾出现公众号卡片”：当前源码的未回筛选严格要求 `Text + Bubble + 非本人`，卡片类型不应进入该视图。新增 `stage=rendered_messages`，记录实际 route、总览状态和渲染消息类型计数，用于区分未回组装异常、导航误入普通会话或设备 APK 版本不一致；诊断摘要单测通过。
- 2026-08-13 用户要求输出完整业务字段以便逐条对照消息渲染规则：新增 `raw_message`、`mapped_message`、`account_list_item`、`contact_list_item`、`rendered_message_details`、`rendered_message` 日志，输出昵称、微信 ID、原始 `messageType/content`、映射类型、presentation、文本、详情与资源地址；认证凭据仍不输出。日志调用使用 `runCatching` 包装，避免 Android JVM 单测因 `Log.i` 未 mock 而失败；bridge 与导航定向测试通过。
- 2026-08-13 修正明细日志刷屏：后台 SCRM 刷新/预取会遍历全部账号与历史消息，故移除 bridge 中的全量 `raw_message/mapped_message/列表` 输出；仅保留 `CoordinateChatBody` 在悬浮聊天实际打开并组合渲染时的 `rendered_message_details/rendered_message` 明细日志。相关 bridge 与导航测试通过。
