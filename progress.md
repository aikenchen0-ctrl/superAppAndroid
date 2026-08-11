# 工作进度

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
