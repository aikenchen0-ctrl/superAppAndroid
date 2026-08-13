# 调研发现与决策

## 2026-08-12 长按菜单补齐

- `MessageLongPressMenu.kt` 原实现顺序是话外音、放大、复制、转发、收藏、删除、多选、引用，并含提醒；已按规格固定 8 项顺序并移除提醒入口。
- `FloatingChatAiClient.generateDraft` 是现有唯一 AI 请求边界；话外音通过专用提示词和同一客户端完成，不新增 mock/fallback。
- `IrregularBalloonPopup` 已支持坐标状态、连接线和边缘避让；话外音通过透明消息锚点复用该机制。
- 文本放大没有现成宿主，新增独立全屏 Compose 阅读层；媒体放大继续走 `FloatingChatMediaPreviewBridge`。
- 真实 AI 结果必须包含情绪、立场、话外音三字段，缺失时显式失败。

## 2026-08-11 客户运营工作台设计初始发现

- Android 当前联系人资料页已展示客户画像、标签、共同群，但画像保存和批量标签仍是“仅 UI 预览”；设计不能把确认按钮直接绑定网络请求。
- 三类能力共享联系人上下文（设备、微信号、contactId/wxid），但写入语义不同：画像需要保存后 GET 回读，标签需要显示替换/合并语义，朋友圈权限需要明确单人/批量/按筛选影响范围。
- iOS `OpenAPICustomerProfileViewController` 与 `OpenApiContactLabelsPermissionsViewController` 提供可复用基线：编辑、二次确认、任务终态、保存后回读，以及后端受阻状态展示。
- 用户已选择方案 A：客户画像、标签和朋友圈权限都从联系人详情页进入，不以独立运营工作台作为首版主路径。
- Android `ScrmContactsPanel` 已具备协程、IO 调度、设备路由校验、`ScrmContactTaskRunner` 和详情刷新能力，新增写闭环应由 Host 调用这些端口，`ScrmContactProfilePanel` 只发 UI intent，避免网络逻辑进入 Composable。
- 客户画像 PUT 直接返回 `ScrmCustomerProfile`，不走 taskId；正确闭环是 PUT 成功后再 GET 回读并逐字段比较。标签和朋友圈权限返回任务结果，需要 `ScrmContactTaskRunner.submitAndAwait` 后再重新读取联系人详情。
- iOS 将客户画像映射标签与微信联系人标签严格区分；Android 当前画像请求会携带详情标签作为 `mappedLabelIds/names`，实现时必须避免用户误以为编辑画像会修改真实联系人标签。
- 官方权限口径可由 `ScrmContact.friendPermissionMask` 还原：0=普通好友、1=不看对方朋友圈、2=不让对方看我、3=两项同时、8=仅聊天；只有 `friendPermissionSynced=true` 才能把开关显示为可信状态，否则必须显示“未同步”，不能默认关闭。
- 首版范围决策：联系人详情只支持单联系人画像、完整标签集合和朋友圈权限；批量标签/批量权限继续留在 SCRM 运营工作台，避免危险范围混入日常详情页。
- 交互采用详情页分区 + 独立编辑子页，不继续扩展 `AlertDialog`。窄悬浮窗口里长表单需要稳定滚动、保存栏和错误恢复，完整子页比弹窗更可靠。

## 2026-08-11 iOS 追赶差距梳理初始发现

- 本轮权威输入为根目录 `接口文档.md`、`完成进度.md`、`任务计划.md`、`功能对照.md`，实现证据来自 `C:\WorkSpace\ios-float` 与当前 Android 源码。
- 前序文档已证明 Android 存在大量“客户端契约已实现但 UI 仅预览/未接入”的能力；本轮不能把这些误判为已追平 iOS。
- 用户要求接口任务优先，且禁止自动测试消息发送等写接口；差距清单需同时给出接口依赖、用户入口和人工验收方式。
- 既有 `功能对照.md` 和 `完成进度.md` 仍含“Android 54/640”旧口径；前序盘点已确认当前接口文档约 241 条路由且 Android 已补齐更多契约，旧数字不能继续作为完成率依据。
- iOS 源码具备独立的 `OpenAPIChatSynchronizer`、`OpenAPIIMSynchronizer`、消息渲染模块、支付、搜索、通话、联系人关系及 OpenAPI 工作台等目录；仍需从调用链判断真实接入程度。
- iOS 官方进度表（2026-08-02）中：客户档案读写、群成员同步/历史绑定、红包/转账状态与领取均为“待用户测试”；联系人标签/朋友圈权限为“受阻”；视频号为“部分接入”；高级好友添加与高级群管理为“待接入”；高级消息仅引用发送进入主流程。
- iOS 主聊天代码确有 `chat/bootstrap`、`chat/history`、`messages/changes` 只读同步链路，以及消息转发/合并转发、文本/图片/视频/语音/文件/网页卡片/公众号文章/笔记/小程序卡片的真实请求路径。
- iOS OpenAPI 工作台枚举了大量接口，但工作台入口或可拼装请求不能等同主悬浮聊天功能完成；追赶清单只把正式业务页真实调用列作 iOS 基线。
- iOS 支付状态、明细、领取代码要求真实 `msgSvrId` 并等待任务终态，但从未执行真实领取；Android 可以追赶代码与只读状态展示，真实领取必须继续人工验收。
- Android 当前已经实现 `chat/bootstrap`、`chat/history`、`messages/changes` 客户端路径，前序缺口文档还将它们列为 UI 真调用但缺真机证据；这三项已在代码层追平 iOS，剩余是只读真机验收和断线/游标行为验证，不应列为“待开发接口”。
- Android 已有消息批量、撤回、语音转文字、客户画像读写、标签批量和群筛选操作的客户端契约，但对应 UI 大多仍是请求预览；iOS 主聊天的转发/合并转发已走真实 `/messages/batch` 并等待任务终态，因此转发闭环是明确追赶项。
- Android 已有支付消息卡片渲染迹象，但未搜索到 `/payments/*` 客户端路由；相较 iOS，支付状态/明细读取与人工触发领取任务属于接口层实质缺口。
- Android `FloatingChatOverlayController` 已在首次加载执行 bootstrap，并对每个会话读取 history；缓存后读取 changes、按 sequence 分页合并，因此会话同步不是空壳。但当前会为全部群加载成员，iOS 已改为当前群按需同步，Android 还存在启动请求量/性能差距。
- Android 长按消息“更多”明确标注“仅 UI 预览”，转发、撤回、语音转文字、详情/原文/表情补拉和媒体下载均只组装参数；这与 iOS 主流程真实提交并等待任务终态有直接差距。
- Android 的普通转发代码先生成本地 outgoing/聊天记录消息，是否最终映射为远端 `/messages/batch` 仍需继续核对 outbox 分发器；不能只因本地 UI 可操作就判定已追平。
- Android `ScrmOutboxDispatcher` 只支持文本、图片、视频、语音、文件、网页/笔记/公众号/引用卡片，不支持 `ChatHistory`/合并转发，也不会自动转为 `/messages/batch`；因此当前普通/合并转发只能形成本地消息，未追平 iOS 的真实批量提交。
- Android 支付卡片、红包/转账编辑器和领取按钮已经有 UI，但发送只创建本地工具消息，领取只把 `claimedPaymentMessageIds` 设为 true；没有服务端请求却显示“已领取/已收款”，属于本地演示状态，不能记为 SCRM 完成，且上线前必须改为明确预览或真实接口结果。
- Android 已有当前聊天内 `ChatSearchPanel`，但未发现 iOS `SearchFeature` 等价的联系人/群聊/消息全局分类搜索；搜索完整度仍落后。
- Android 联系人画像保存和批量标签页面明确只组装请求，iOS 正式页面已实际调用保存并 GET 回读；这是接口契约已具备、业务闭环未追平的典型项。
- 接口文档支付域共 7 条且全部使用 POST：发送红包、领取红包、红包明细、红包状态、转账、领取/拒收转账、零钱查询。即使状态/明细/余额语义为读取，也属于敏感支付任务，自动化只做 fixture/契约，不发真实网络请求。
- iOS 正式支付服务主流程实际封装并调用 4 条：红包状态、红包明细、领取红包、领取转账；发送红包、转账和余额另在聊天/工作台代码中出现。iOS 的正式领取仍仅“待用户测试”。
- 现有 `任务计划.md` 已过期：仍写消息转发“iOS 也未实现”，但当前 iOS 已通过 `/messages/batch` 完成普通/合并转发；还把 Android 客户画像、标签等已补契约项写为完全未实现，需要同步修正。
- 现有 `完成进度.md` 也过期：仍称消息集合被清空且 bootstrap/history/changes 未接入，当前生产控制器已经真实加载和增量合并；完成度数字不能继续直接引用。
- 已交付 `iOS端SCRM功能追赶清单.md`，并把接口优先顺序固定为：支付假成功整改 → 支付状态/详情 → 转发/合并转发 → 客户画像回读 → 语音环境/好友申请刷新 → 标签权限与稳定消息操作 → 群成员按需同步 → Finder/批量运营。
- 文档验证无新增凭据、无 `git diff --check` 错误；本轮没有运行构建或真实接口测试，因为只修改文档且写接口/消息接口按用户要求禁止自动调用。

## 2026-08-11 接口盘点初始发现

- 权威接口源：`接口文档.md`（约 500KB）；辅助映射：`SCRM_UI接口数据映射.md`。
- Android 主要实现位置：`ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/scrm/`。
- UI 主要入口：`FloatingChatOverlayUi.kt` 与 `floatingchat/contacts`、`floatingchat/tools`、`floatingchat/moments`、`floatingchat/group`。
- 必须区分真实调用、确认后仅组装请求、只读展示和完全未接入四种 UI 状态。
- 自动测试不得真实执行 POST/PUT/DELETE；写接口的“测试通过”只能来自不发网的 fixture/序列化/路由/请求装配测试。
- `接口文档.md` 版本为 v1，生成日期 2026-08-10，覆盖 `/openapi/v1/*`，写请求常返回 `taskId` 并要求轮询任务终态。
- 现有 `SCRM_UI接口数据映射.md` 已记录联系人、消息、群管理三类 UI，但多数写动作明确属于“请求已组装，未发送”，不能记成真实 UI 对接完成。
- 接口文档还包含设备、视频号、朋友圈、代理、登录、实时连接等大量非悬浮聊天主流程接口；最终文档需要区分“悬浮聊天范围”与“接口文档全量范围”。
- 接口目录共 241 条路由。数量最多的业务域为朋友圈 40、消息 30、群聊 28、设备 24、好友 18、视频号 17、联系人 13；Android 当前实现显著集中在聊天、联系人、群聊、朋友圈和任务。
- `ScrmApiClientTest` 已有大量 MockTransport/fixture 契约测试，覆盖基础发现、聊天同步、消息发送、联系人/群聊、朋友圈素材和任务；是否“通过”仍需本次实际执行测试后判定。
- `ScrmApiClient` 同时实现基础读取、任务、消息、朋友圈、联系人、群聊，以及三个扩展契约（联系人管理、消息操作、群管理）；扩展契约注释明确要求后续 UI 对接并禁止自动执行高风险写动作。
- 联系人、消息和群管理扩展源码中“存在请求模型/客户端方法”不等于 UI 已真实调用。现有映射文档表明其中一部分仅做本地请求预览。
- `GroupInfoHost.kt` 已真实调用群成员邀请、移出、群改名、拉取二维码、发布群公告和退出群聊，并通过 `ScrmContactTaskRunner.submitAndAwait` 轮询任务；这些不能再写成“全部仅预览”。
- 群资料中的免打扰、置顶、保存通讯录、群内昵称等当前仍只修改悬浮窗口本地状态；另有 `ScrmGroupOperationPreviewPanel` 提供扩展群操作的请求预览。
- `ScrmContactsPanel.kt` 已真实对接联系人列表、好友申请、联系人详情/画像读取、联系人同步、查找好友、重新验证、手机号/场景加好友、删除好友、处理好友申请和创建群聊；任务型操作会等待 task 终态。
- `ScrmContactProfilePanel.kt` 的画像保存和批量标签目前仅组装请求并显示“未发送”；不能标为真实写入 UI。
- `ContactRemoteTaskActions.kt` 已真实对接联系人资料页删除好友、从群成员添加好友。
- 消息长按扩展操作全部为请求预览；底部“更多”的收藏表情、小程序卡片和筛选批量发送也只组装请求。只有卡片模板通过 UI 发起真实只读 GET。
- 朋友圈 UI 已真实调用朋友圈同步、互动消息同步、发布、点赞、评论；朋友圈素材 UI 已真实调用列表、详情、创建、复制和归档接口。
- 普通聊天发送不直接在 Compose 中调用 API，而是经输入动作 -> 本地 outbox -> `ScrmOutboxDispatcher`，再调用文本、图片、视频、语音、文件、卡片和引用发送接口，并由任务跟踪器查询终态。
- `ScrmApiClient` 当前有 111 个 override 方法，对应接口文档 241 条路由中的一部分；不能用“方法数 111”直接等价为“实现 111/241”，因为部分方法复用动态 action 路由，部分文档路由不是 Android 当前产品范围。
- `ScrmApiClientTest` 约 36 个正式测试（另有测试辅助函数），覆盖原有核心客户端；2026-08-10 新增的联系人/消息/群扩展客户端方法没有找到直接 MockTransport 测试，当前证据只能标为“已实现、编译通过、专项测试缺失”。
- 2026-08-11 实际运行 `com.paifa.ubikitouch.accessibility.scrm.*`：18 个测试类、113 个测试全部通过，0 failure、0 error、0 skipped。该结果证明客户端核心契约、Bridge、outbox、任务跟踪和设置逻辑，但不代表真实服务器或真机验收通过。
- 另运行 8 个与 SCRM UI 相关的聊天契约测试和 2 个朋友圈素材运行时测试，共 10/10 通过；覆盖账号路由、联系人页契约、群成员真实 API 标记、朋友圈远端源、发送状态和素材请求装配。Compose 真机点击流程仍未做仪器化验收。
- App 设置页存在 `ScrmSettingsPanel.kt`，可保存/测试 SCRM 地址与 API Key、选择设备和微信账号；悬浮聊天从该账号路由加载数据。
- `框架蓝图/06-SCRM消息中枢与同步.md` 仍写“约 54 个契约”，已明显过时，需要改为链接到本次基于 2026-08-10 接口文档的现状盘点。

## 用户需求

- 参考 `C:\Users\Administrator\Desktop\我的提示词\任务文档` 中的所有文档。
- 在项目“框架蓝图”中编写 Android 端计划书，并梳理对应用的理解。
- 当前已知设计集中于“悬浮聊天、侧边手势”，需要补全可用的产品与开发设计。
- 尽量产出多份、名称可直接反映功能或开发流程的中文 Markdown 文档。

## 调研发现

- 项目为多模块 Android 工程：`app`、`ubiki-accessibility`、`ubiki-overlay`、`ubiki-core`、`adbcore`、`blinkvoice-visual-sdk` 与 `benchmark`。
- 项目现有源码与测试命名显示，应用已覆盖侧边手势、悬浮聊天、联系人/群组/朋友圈、图片视频文档、AI 语音、SCRM、无障碍保活、ADB 保活和快捷设置等能力。
- `框架蓝图` 目录已存在，适合作为面向产品和开发的正式设计文档位置。
- 参考目录共 9 份资料：Markdown 4 份、HTML 2 份、TXT 1 份、DOCX 1 份、另含 1 份数据模型 Markdown；需逐一阅读。
- `任务重点.md` 与 `任务重点.docx` 的内容一致，已明确产品定位、现状差距、分期和主要合规风险，可作为本次蓝图的核心输入。
- `接口.txt` 提供 SCRM OpenAPI 文档与调试入口；其中包含登录凭据，后续蓝图仅记录接口边界，不复制凭据。
- 两份 HTML 是保存下来的网页文档，源文件主要由页面运行资源和嵌入图像组成，直接文本提取无有效正文；其同类主题已在“任务重点”汇总中覆盖（单账号数据模型、Agent 状态机）。
- `任务重点` 建议先聚焦 B 端企业版：以消息中枢为 P0，AIfx 与可复用交互框架为 P1，流量联盟与 iOS 扩展为 P2；Android 应用应采用“AI 建议 + 用户确认”的默认执行方式。

## 设计决策

| 决策 | 理由 |
|---|---|
| 以用户任务闭环组织设计 | 防止功能清单停留在零散交互，明确从唤起、理解、执行、反馈到配置的完整路径。 |
| 不把参考资料中的自动营销与账号控制直接列为默认能力 | 涉及平台规则、隐私和账号风险；计划中将以显式授权、人工确认、可审计为先决条件。 |

## 资源

- 项目根目录：`C:\WorkSpace\UbikiTouch`
- 参考目录：`C:\Users\Administrator\Desktop\我的提示词\任务文档`

## 问题记录

| 问题 | 处理方式 |
|---|---|
| HTML 保存文件无法直接提取有效正文 | 记录其载体限制，并以内容相同的已整理参考文档作为主题依据。 |

## 2026-08-10 悬浮聊天 SCRM 接口差异

- Android 已具备基础账号、设备、能力、任务、会话 bootstrap/history/changes、常用消息发送、联系人/群列表与少量联系人和群写接口。
- 联系人域主要缺口：标签创建/同步/删除、筛选批量标签、联系人管理分页、客户画像草稿/保存、朋友圈权限单个/批量/筛选批量、好友资料刷新与编辑。
- 消息域主要缺口：卡片模板、收藏表情/小程序卡片、批量发送、同步历史/未读/已读/MsgSvrId，以及按稳定消息 ID 的转发、撤回、详情补拉、媒体下载和语音转文字。
- 群域主要缺口：按筛选建群/邀请/踢人，群备注、置顶、通知、通讯录、验证、群内昵称、管理员和群主转让等管理契约。
- 用户明确要求 UI 由另外两个 AI 负责，因此本任务只新增接口、模型、维护注释和文档，不接入任何界面或交互状态。
## 2026-08-11 标准界面完善发现

- 消息菜单缺少“话外音”和“放大”，现有 `Reminder`、`ScrmOperations` 可保留在扩展操作中，但标准八项应作为主操作。
- 多选栏当前仅有转发、收藏、删除、取消，且勾选框位于消息左侧；标准要求五项操作和气泡下边缘中央勾选。
- `MessageRow` 对非群成员消息使用 `fillMaxWidth(0.99f)`，不满足内容自适应和统一最大宽度。
- SCRM 桥接只明确识别少量微信类型，未知类型回退为文本；标准专用类型需进入核心模型和 renderer。
- 右侧 17 项当前是通用工具枚举的复用列表，名称与标准工作流不一致；需要独立 tile 展示模型。
- “附身推广”当前跳转联系人面板，不是附身账号选择流程。
# 2026-08-11 iOS 对照完成进度重整

- `完成进度.md` 当前数据滞后：仍将左侧眼睛/三点、停留按钮、右侧工作流、长按八项、客户画像/标签/朋友圈权限闭环、支付接口等标为缺失或仅本地预览，与近期代码和 `progress.md` 不一致。
- 旧文档把“31 种消息类型存在枚举/渲染分支”直接记为 100%，没有区分真实 SCRM 类型映射、素材可用性、交互闭环和真机视觉验收。
- 后续统一使用五层状态：接口契约、正式 UI、真实调用/回读、自动验证、真机人工验收。
- iOS 顶层功能证据包括 `Features/Payments`、`Wallet`、`Calls`、`Search`、`Favorites`、`Moments`、`OpenAPIWorkbench`、`OperationLab`、`Modules/MessageRender`；Android 需要逐项寻找正式入口，不能仅按文件名判断。
# 2026-08-11 右侧头像大量消息 ANR 初始发现

- 当前点击入口位于 `FloatingChatOverlayUi.kt` 的 `onAccountAvatarClick`，会计算目标账号、目标线程并更新选中状态。
- 既有进度记录表明此前已观察到连续点击时 18-72ms 主线程帧耗时，并做过账号作用域会话缓存与 15 秒只读刷新新鲜期优化。
- 既有设备证据还记录过 `signal 3`、tombstoned 栈抓取，主线程热点曾位于 `FloatingChatPrototype.pairedAccountFor` 的账号与消息嵌套扫描；当前提交已声称改为单次消息遍历，但尚需从源码、测试与当前 APK 基线重新确认。
- 仍需重点排查：点击时是否重建所有账号作用域会话、对全量消息执行 `copy/filter/groupBy/distinct`，以及 Compose 状态切换是否让这些计算同步发生在主线程。
- 本轮不调用真实接口，尤其禁止发送消息和其他写请求。
- 已确认剩余根因：头像点击回调直接调用 `accountScopedConversation`，随后 Compose 重组又经 `AccountScopedConversationCache` 再构建一次；单次构建内部还会按最多 2 个群和 5 个联系人分别全量过滤消息，形成重复主线程扫描。
- 20,000 条历史消息、2 个账号的回归测试在旧实现下超过 60 秒；改为单次线程索引后，测试体执行时间为 0.089 秒，计数列表恰好读取 20,000 个元素一次。
- 索引保持原有语义：默认群合并无 thread ID 与默认群 ID 并保持源顺序；私聊优先精确 thread，缺失时按 User/Account/None 连接目标取最近 6 条，完全无匹配时回退全局 User/Account 前 4 条；群聊缺失时回退无 thread ID，再回退源消息前 4 条。
- 独立审查发现私聊回退初版有语义偏差：旧实现应从目标匹配消息中显示最近 6 条，完全无匹配时再使用全局 User/Account 消息前 4 条。已用两个 RED 用例确认，并改为有界保留各类别最近 6 条及全局前 4 条。
## 2026-08-12 聊天消息内容与格式审计

- 主消息区入口为 `MessageRow` -> `MessageContent`，按 `MessageRendererGroup` 分发到文本、媒体、文件、位置、资料、链接、聊天记录、支付、通话和通知卡片。
- `messageUnavailableStateFor` 原先只检查 `text/detail/resourceUrl`。位置标题、名片名称、文件名、引用内容、聊天记录预览行、缩略图等结构化字段有值时，仍可能被错误判定为“消息暂不可查看”。
- 媒体失效判断原先只包含 `ImageThumbnail` 和 `VideoPreview`，没有覆盖 `CapturedPhoto`、`StickerGif`、`ChannelsVideo`，与实际渲染分组不一致。
- 本轮只修改只读显示判定与显示格式，不调用发送、支付、红包领取或转账写接口。
# 2026-08-12 支付真对接发现

- 后端实际 Swagger spec：`http://112.74.164.233:42718/openapi/docs/openapi-v1/swagger.json`，已只读保存为 `后端支付OpenAPI.json`。
- 7 个支付接口均返回 `TaskResult`；`success=true` 仅代表请求被受理，最终结果必须通过 task result 或业务状态读取确认。
- `/payments/lucky-money` 与 `/payments/remittance` 请求字段为 `deviceUuid/weChatId/friendId[/roomId]/moneyFen/paymentPassword/wish|memo`；金额由调用者提供，服务端约束红包 1-20000 分、个数 1-100，转账金额大于 0。
- `/payments/wallet-balance` 是只读 POST，字段 `deviceUuid/weChatId/flag`。
- 红包状态、详情、领取和转账收取 DTO 只接受 `deviceUuid` + 服务端 `messageId`（int64）；OpenAPI 明确禁止外部传递原始支付链接/交易号。
- `Idempotency-Key` 必须用于发送红包、发送转账、领取红包、收转账；同一业务意图重试必须复用原键，最长 128 字符。
- Android `ScrmApiClient` 已具备 7 条路由和 `postIdempotent`，但 `PaymentComposerPanel` 的确认结果仍回到 `FloatingChatOverlayUi` 的 `addToolMessage` 本地演示卡片，领取回调目前只显示人工验收提示。
- 当前消息同步模型已有 `remoteMessageServerId`；本地工具卡片没有真实服务端消息 ID，不能用于查询或领取。

# 2026-08-12 Finder 与头像 ANR 收尾发现

- 当前头像点击生产路径已使用 `remember(profiledConversation) { AccountScopedConversationCache(...) }`，点击与后续重组共享缓存，不再在同一帧重复构造账号作用域会话。
- `AccountScopedMessageIndex` 将 20,000 条消息单次遍历为线程索引，并保留私聊“匹配目标取最近 6 条、无匹配取全局连接消息前 4 条”的旧语义。
- Finder 独立模块已包含 6 条 `/openapi/v1/finder/*` 契约、四个 Compose 页面和任务终态等待器，但尚未接入 `FloatingBottomPanel` 和 `FloatingChatOverlayUi`。
- `FloatingChatMessage` 已增加 `finderUserName`；现有 SCRM 桥接尚未识别 appmsg type 51/63，也未把原始 JSON/XML 的 `sphUserName` 映射到模型。
- 右侧工具策略、显示名称、图标、顺序和 `BottomPanelMode.Finder` 已加入；实际底部面板缺 Finder 分支，因此入口仍不可用。
- 当前产品上下文要求克制、可靠、可确认；Finder 应复用现有 `OverlayTokens` 和底部工作区，所有写操作保持显式确认与可见错误。

# 2026-08-12 悬浮聊天头像显示与异步缓存发现

- 仓库已有头像数据字段 `FloatingChatContact.avatarUrl`，SCRM 桥接也存在联系人、群成员和账号头像派生映射；因此需要判断缺失发生在字段取数、URI 标准化、模型映射还是 Compose 加载层，不能直接归因于后端无数据。
- 仓库已有自研 `MediaThumbnailBitmapLoader`，搜索结果显示其包含内存缓存、持久化头像缓存、并发信号量和 in-flight 合并；本轮应优先复用/修正该链路，避免引入第二套图片框架。
- 当前产品为 Android 悬浮工作台，视觉目标是稳定、可扫描；头像加载态应维持固定尺寸并使用现有首字/颜色占位，不做视觉重设计。
- `PRODUCT.md` 已加载；仓库没有 `DESIGN.md`。本轮沿用现有组件和 tokens，不创建新视觉体系。
- 工作区已有大量悬浮聊天与头像相关未提交修改，后续修改必须逐文件核对差异并只做最小增量。
- 会话轨道、群头像九宫格、群成员选择、联系人头像和右侧账号头像均已接入 `rememberAsyncAvatarBitmap` 或 `rememberAsyncImageThumbnailBitmap`；头像完全不显示不是单一入口遗漏，更像共享加载器或共享 URI 处理问题。
- 当前测试主要断言 URI 识别、解码尺寸和源码结构，尚未看到对异步请求状态迁移、并发合并、失败后重试、磁盘缓存命中的行为级覆盖。
- `MediaThumbnailBitmapLoader` 的搜索证据显示它会先标准化 URI，再查持久化缓存，然后远程解码并写入内存/磁盘；需重点核对 Compose effect 键、缓存命名空间、失败冷却和实际 HTTP 行为。

# 2026-08-12 iOS 对 Android 全量功能与样式差异审计发现

- 当前审计必须以磁盘工作区为准：Android 存在大量未提交的悬浮聊天、Finder、支付、消息渲染和弹层改动，`HEAD` 不能代表真实现状。
- 根目录已有 `功能对照.md`、`完成进度.md`、`界面标准.txt`、`悬浮聊天界面优化方案.md`、`侧边手势未实现功能清单.md` 等线索文档，但历史记录明确存在状态滞后，需逐项回查生产入口。
- iOS 参考项目包含 `Features`、`Modules`、`Services`、`Shared` 以及多个大型聊天控制器/消息组件文件；需要区分生产可达能力与实验、演示、仅模型实现。
- `PRODUCT.md` 定义产品为克制、可靠、可确认的 Android 悬浮工作台；项目没有 `DESIGN.md`，样式审计不能凭空创造新视觉体系。
- 本轮“未实现”不仅包括没有源码，还包括有 API/模型但 UI 不可达、有 UI 但只生成请求预览/本地假成功、缺少任务终态/错误恢复、以及关键视觉/交互状态不等价。
- 纯 iOS 平台能力需要映射成 Android 平台等价方案，不能机械复制 Live Activity、UIKit window 等实现细节。
- `完成进度.md` 同时包含 2026-08-11 历史百分比和 2026-08-12 新增实现；同一文件内仍有“Finder 域和页面缺失”与磁盘已有 `floatingchat/finder/` 全套文件的冲突，因此旧百分比和后半段静态表不能作为当前真值。
- `完成进度.md` 自己已提出五层状态：接口契约、正式 UI、真实调用/回读、自动验证、真机验收；本轮继续沿用，但新增“宿主可达性”单独检查，避免页面存在却没有入口。
- Android `ToolActionDispatch` 明确把一组工具动作分派为 `AddSimulatedMessage`，说明工具名称、图标和卡片渲染不等价于业务完成；需逐项追到真实端口或明确禁用态。
- Android `finder/`、支付、消息 renderer 等目录是 2026-08-12 工作区新状态，旧对照文件尚未同步；所有结论需以当前文件和宿主接线为准。
- iOS 的 OpenAPI Workbench 也包含 demo ID、防误发送校验和本地模拟交互；这些演示/验收页面不能整体作为 Android 产品端必须复制的“已完成用户功能”，只抽取其中已有真实 API 闭环。
- Android 项目最近提交说明消息渲染曾进行大规模追赶，但当前工作区又有大量未提交演进；静态文件数量和提交信息都不能替代逐入口验证。
- Android Finder 已实际接入 `BottomPanelMode.Finder`、`FloatingBottomPanel` 和主 `FloatingChatOverlayUi`，旧文档“域和页面缺失”已过期；剩余应审计真实消息字段映射、配置错误、任务终态和真机写操作，而不是从零重建。
- Android 右侧固定显示 18 项工作流动作，但 `toolActionDispatchFor` 只为 Assistant、Search 之外的少数标准工具建立分派；Search、Notes、Reminder、Wallet、Share、Pin、Device、Voice 等可见动作当前落入 `ToolActionDispatch.None`，点击无行为。
- `simulatedMessageToolActions()` 当前为空，因此旧的“模拟工具动作”分支已不再被显式使用；更严重的现状是未覆盖动作静默 `None`，需要显式实现、禁用或从可见列表移除，不能保留无反馈入口。
- Android 会话内搜索位于 `CoordinateChatBody.kt`，结果源仍是硬编码 `PreviewChatSearchResults` 4 条；联系人面板搜索已经有真实 SCRM 加载路径，两者状态必须分开记录。
- Android 单条和逐条转发已调用 `MessageForwardingActions.addForwardedMessage` 并提示以消息状态为准；合并转发明确提示“已生成合并聊天记录（本地）”，尚不是真实 SCRM 合并转发。
- Android 未找到 iOS `MergedForwardDetailViewController` 的等价只读详情页，也未找到 `RelayEditorViewController` 的顺序编辑、备注和删除条目能力。
- Android 通话消息渲染、联系人/群成员“语音/视频通话”入口和 AI 实时语音是三件不同能力；普通联系人通话入口部分回调仍为空，不能用 AI 语音能力替代普通音视频通话闭环。
- `artifacts/calls-ui-final-desktop.png` 是桌面审阅原型，页面文案明确说明“不接 SCRM 或 WebRTC”；它可用于状态枚举和布局讨论，但不能作为 Android 生产通话已实现的证据。
- 文档/UI 独立审计发现：根目录 `任务计划.md` 当前为 0 字节，但 Git 差异显示这是相对 `HEAD` 的整文件未提交删除；既有文档仍链接它。本轮不恢复或覆盖该用户改动，只在最终文档提示导航断链风险。
- `完成进度.md` 内部自相矛盾：顶部写长按 8 项已完成，历史表仍写缺“话外音/放大”；一处写 31 类消息 100%，`悬浮聊天消息渲染对照.md` 又明确多类仅部分对齐且待真机视觉验收。
- `docs/FLOATING_CHAT_PERFORMANCE_BASELINE.md` 明确缺 P95 设备数据和发布门禁测量，不能把单一 ANR 回归修复扩展为整体性能已完成。
- 历史视觉证据存在重复/无效：`debug-current.png` 实际不是有效 PNG；若干截图哈希相同；标称 IME 的截图未显示键盘。后续不得用这些文件证明视觉或输入法验收。
- 现有截图可见发送失败提示在浅色背景上对比不足、部分图标出现缺字方框，左右轨道/连线/工具区会和消息主体竞争空间；需进入视觉与可访问性修复波次。
- Android 已有集中 `OverlayTokens`，但大量功能文件仍各自定义尺寸/颜色；项目没有 `DESIGN.md`，应先整理现有 token/组件映射，再做跨端样式对齐，避免逐页复制 iOS 硬编码值。
- iOS 自身的组件化文档也说明首阶段多为包装旧 Bubble，且参考目录缺 Xcode 工程文件、非 Git 仓库，无法在当前环境复现 iOS 构建；因此 iOS 只作为静态源码参考，不宣称其全部功能已运行验证。
- OpenAPI 写响应至少分三类：直接业务 DTO、顶层 `TaskResult`、批量 envelope 内的 `items[].taskId/taskResultUrl`。直接 DTO 不进入任务发现器；批量响应不能被强制解码成顶层 taskId，也不能把 `successCount` 当最终业务成功数。
- 顶层 `TaskResult` 的结果发现合同分为三支：`task_result` 只轮询服务端返回并经同源校验的精确 `taskResultUrl`；`external_state` 即使 `taskId=0` 也读取 `resultResource`；`untracked` 或合同为空且 `taskId=0` 进入接口指定的业务权威回读。`taskId=0` 合法，HTTP `success` 不等于业务成功。
- `OpenApiTaskResultDto.final` 独立于 `status`。`success/failed/unknown` 可以停止当前轮询，但只有 `final=true` 才表示任务整体终结并能从持久 pending/resync 集合删除；`unknown + final=false` 必须保留为可恢复、可人工只读核对且禁止重提的未终结状态。
- Android `ScrmTaskResult` 当前未建模 `final`，`resolveScrmTaskResult()` 只看 `status/resultUnknown`，`toTaskRecord()` 又会对非 Pending 结果写 `completedAt`；这会让尚未终结的 unknown 任务丢失后续 resync，是 F-55/A3 的 P0 根因。
- D1 的只读 renderer/字段矩阵本身不依赖结果发现，但任何真实发送能力声明必须以前置 A3 为准；D1 主路由已调整为 `A3/C1/C3`。
- 最终反对者审阅确认，29 行唯一 ID 路由不能同时承担跨波次执行状态；已保留 29 个主任务管理 75 个差异 ID 的唯一所有权，新增 45 个原子检查点管理阶段状态、证据和机器可判定依赖。
- 检查点状态固定为未开始、进行中、待人工、受阻、已完成；普通前置只接受已完成。`启动需` 缺失时不允许选择，`完成需` 缺失时允许完成代码/Mock 后停在待人工，不会授权真实写操作。
- 45 个检查点和 108 条显式依赖边可完整拓扑排序，无未定义引用或依赖环；初始可执行检查点为 A2@R0、B2@A0、G1@S0。
- K1 已拆为纯文档 ADR 的 K1@D0 和独立决议 K1@M4；后者支持“评审确认无需迁移/删除”的无代码完成分支，不会为了推进质量门禁强制造代码改动。
- H 提示词中的四个 iOS 参考均改为 `C:\WorkSpace\ios-float` 下真实存在的绝对路径。

# 2026-08-13 差异文档增量复核发现

- Android 当前源码没有提供足以将任一 F/U 差异升级为“已完成”的新证据，现有六个开发波次仍满足契约基础、核心消息、联系人/群、业务工作流、视觉交互、质量收口的依赖顺序。
- TalkBack 缺口是双重屏蔽：`FloatingChatOverlayController.kt` 将悬浮根节点设为 `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`，`FloatingChatOverlayUi.kt` 又使用空的 `clearAndSetSemantics { }`；U-01 不能只修其中一处。
- Finder 在线消息路由虽已接入，但 `finderUserName` 没有完整进入 `LocalChatMessage` 及数据库双向转换；进程重启后的离线路由身份仍不完整。该合同归 `A1/F-04`，`F3/F-39` 只负责消费和验证。
- 朋友圈素材域已有真实 API 函数，但生产按钮仍只生成预览；因此 F-36 保持“部分实现”，不能用 API 存在替代可达真实闭环。
- Finder 页面仍缺统一加载、离线、错误和恢复状态，U-13 保持未完成。
- iOS 普通消息 SQLite 没有生产/Demo 来源和新鲜度标记，启动流程仍会安装 Demo 附件；F-01/F-04 必须先建立可信来源和离线身份合同。
- iOS 0.45 秒长按排序仅存在于当前会话控制器内，控制器重建后恢复默认；F-09 仍是未持久化的局部交互。
- iOS 好友主页已移除“发消息/电话/跟进”按钮，AI 跟进仍是本地演示；F-16 的 Android 对齐目标不能假设这些入口在参考端已有真实闭环。
- iOS 全局搜索可以命中群消息，但群资料页没有群内查找、清空和举报闭环；F-21 继续按拆分能力验收。
- `C:\WorkSpace\ios-float` 没有 Xcode 工程或 Swift Package 元数据，本环境只能做静态源码审计，不能声称 iOS 构建或运行验证。
- 单检查点执行卡需要同时记录认领前状态、源码基线、文件范围、RED/GREEN、自动门禁、人工证据和释放/接管，才能防止多个 AI 把行政状态更新误当成功能事实变化。
- 认领没有自动过期；原执行者释放，或协调者/用户确认后显式接管，是避免陈旧心跳造成错误并发执行的最低治理要求。
- `任务进度.md` 自身 SHA-256 不能写回文件内部作为同一快照证明，否则形成自引用哈希；最终指纹只放在外部验收记录和交付回复中。
- 即使用户授权真实业务验收，AI 也只准备代码、Mock、步骤并接收人类证据，不直接调用真实写接口。
- “受阻”只能在普通内部前置已满足自动实施条件后，由当前检查点自身缺少“启动需”触发；预先知道未来缺产品/后端/设备时仍应保持“未开始”，否则确定性选择算法会反复扫描尚未到阶段的远期项。
- 波次表是阶段摘要，不是检查点门禁。前一波次的“待人工”可以在自动证据完整时解锁后续代码、Mock、fixture 和文档准备，但真实设备、真实服务、人工写入以及 F/U 最终关闭仍要求普通前置全部“已完成”。
- `HUMAN_WRITE` 不是免测试档案：其中任何生产行为实现都必须像 CODE 一样先有行为/状态 RED，再做 GREEN 和 Mock；只有只消费新人类证据的回合不制造新 RED。
- F-35 的可执行验收必须包括分页去重、首屏/追加错误、离线缓存、刷新恢复以及图片 URL 失效；F-44 只能接真实零钱读取或禁用/隐藏，不能复制 iOS `demoItems`；U-19 必须让无效 PNG、重复哈希冒充不同状态和无真实键盘的 IME 证据确定性失败。
- K1、K2 与普通通话是不同所有权：K1 只处理 F-07 架构，K2 只处理 F-10/F-11/F-31，普通通话仅由 I3@D5/F-50 决策；ActivityKit、伪灵动岛、拖放/套索/3D 气泡只是平台背景，不是 K 的新交付项。
# 2026-08-13 悬浮聊天错误数据与头像缺失诊断

- 工作区已有大量用户未提交修改，本轮保留所有现状，根因确认前只做只读诊断和任务记录。
- 文档 `docs/FLOATING_CHAT_COMPONENT_API.md` 将 `/devices`、`/wechat-accounts`、`/contacts`、`/chatrooms` 和 `/chat/bootstrap` 标为账号/会话/头像数据源。
- 生产映射集中在 `ScrmFloatingChatBridge.kt`，头像 UI 共用 `MediaThumbnailBitmapLoader.kt`；加载器已有 `UbikiAvatar` 诊断日志，可用于区分数据缺失与网络/解码失败。
- 初步假设：所有入口同时无头像，优先检查上游 DTO 字段兼容和 URL 标准化/加载策略，不先逐个修改 UI 组件。

## 证据闭环

- OpenAPI JSON 的 `OpenApiChatConversationDto` 字段为 `id(Int32)`、`conversationWxid`、`conversationType`、`displayName`、`displayAvatar`、`unreadCount`、`messageCount`、置顶/免打扰及最后消息摘要；Android DTO 字段一致，`ScrmApiClientTest` fixture 已验证 `displayAvatar` 和 `unreadCount` 可解析。
- OpenAPI 的联系人、群聊、群成员 DTO 只有单一 `avatar` 字段；Android 额外兼容 `avatarUrl/headImgUrl/headimgurl/imageUrl` 不会造成字段丢失。`normalizedRemoteImageUri` 只转换微信头像域名的 HTTP -> HTTPS，不会过滤普通 HTTPS URL。
- `FloatingChatOverlayController.kt:1039-1067` 的 bootstrap 消费只创建 history 和 backend ID 映射；未保存 `displayName/displayAvatar/unreadCount/lastMessageContent`。
- `ScrmFloatingChatBridge.kt:178,418-447` 用固定 `ScrmUnreadDemoMessageCount = 30` 生成首页未读演示消息；`CoordinateChatBody.kt:185-200` 和 `ChatThreadState.kt:736-796` 直接消费该字段。
- `FloatingChatOverlayController.kt:99` 初始 conversation 是 `FloatingChatPrototype.sampleConversation()`；刷新失败只日志记录，未设置错误状态。样例模型包含大量 `https://aiff.app/...` 资源，真机 `UbikiAvatar` 只看到 `host=aiff.app`，没有真实头像域名。
- iOS 对照实现 `OpenAPIChatSynchronizer.swift` 解析并保留 bootstrap descriptor，`ChatWindow+OpenApiMessageSync.swift:505-520` 将 `descriptor.displayAvatar` 写入会话上下文；Android 当前少了这一层。
- 本轮定向 Gradle 测试被工作区既有错误阻断：`FinderContractsTest.kt:99` 的 `validatedFinderPostRequest` 未解析，未进入目标测试体。
