# SCRM UI 接口数据映射

本文档记录 Android 悬浮聊天 UI 当前对 SCRM 契约的接入边界。接口层仍由 `ScrmApiClient` 提供，UI 不拼接 URL。

## 联系人资料与标签

| UI 入口 | 读取接口 | 数据到 UI | 写入行为 |
|---|---|---|---|
| 联系人详情 | `getContactDetail(contactId)` | `customerProfile`、`labels`、`commonChatRooms`、`relationLogs` | 无 |
| 客户画像区域 | `getCustomerProfile(contactId, weChatId)` | 等级、来源、备注、画像标签 | “组装请求”仅创建 `ScrmSaveCustomerProfileRequest`，不发送 |
| 标签区域 | `ScrmContactDetail.labels` | 标签名称、颜色、数量 | “编辑标签”仅创建 `ScrmBatchSetContactLabelsByFilterRequest`，固定 `mergeExisting=true`、`maxCount=1` |

人工验收步骤：打开悬浮窗右侧“联系人”，选择联系人，等待详情加载；确认画像、标签、共同群聊和关系动态出现。点击“编辑画像”或“编辑标签”，检查二次确认内容和“请求已组装，未发送”状态，确认 Logcat 没有写接口请求。

## 消息操作

当前消息长按菜单保留 UI 对接入口。转发、撤回、语音转文字、消息详情、媒体下载、补拉原文、收藏表情详情均必须在确认后组装对应 request，再由人工联调接入任务。收藏表情、小程序卡片、卡片模板位于底部“更多”面板；批量发送必须使用独立的批量面板，首次人工测试只允许一个会话。

已接入入口：消息长按 -> 更多。面板从消息 `remoteMessageServerId` 和当前账号路由组装 `ScrmForwardMessageRequest`、`ScrmMessageOperationRequest`、`ScrmMessageDetailPullRequest` 或 `ScrmMessageMediaDownloadRequest`。确认按钮不会调用任何 SCRM 写接口，只显示“已组装，未发送”。

人工验收步骤：长按一条消息，检查目标消息 ID、会话 ID 和操作名称；打开任一写操作确认框后取消，确认没有请求发出。卡片模板可使用只读 `getCardTemplates` fixture 验证列表和字段展示。

## 群管理

群资料页使用本地群状态展示，并为公告、备注、置顶、新消息通知、保存通讯录、群内昵称、入群验证、群二维码保留接口动作入口。成员管理页的邀请、踢出、管理员、群主转让和按筛选操作必须先展示筛选条件、预计影响人数和不可逆提示，再组装 request。建群、踢人、退群、转让群主禁止自动测试。

已接入入口：群资料页 -> 群管理操作。面板展示目标群、群 ID、预计影响人数，并为筛选建群、筛选邀请/踢出、管理员、群主转让、通知、置顶、通讯录、群内昵称、验证和二维码入群组装对应 request。所有确认仅生成本地预览状态，未发送。

人工验收步骤：打开群资料或群成员页，进入目标动作，确认影响范围和二次确认内容；取消后检查任务列表没有新增写任务。只读群成员/群列表可以使用 fixture 或真实 GET 验证。

## 状态与日志

UI 状态统一展示 loading、成功、失败、结果未知和超时。`401`、`403`、`429`、网络异常分别显示可重试或重新登录提示。日志只保留接口名称、目标 ID、taskId 和失败原因，不输出 API Key、密码或完整敏感内容。
# 2026-08-10 Message More Panel Update

New UI entries: favorite emoji send, WeApp card, read-only card templates, and standalone filter batch send. Emoji and WeApp UI only assemble `ScrmSendEmojiRequest` / `ScrmSendWeAppCardRequest`; batch send assembles `ScrmBatchSendMessageByFilterRequest` with `maxCount=1`. No write API is called. Card templates use the existing `messageApi as ScrmMessageOperationApi` `getCardTemplates` GET path. Manual acceptance: open an SCRM conversation, tap More, choose an entry, confirm request assembly, and verify no POST task appears. For templates, tap Load and inspect the returned list or explicit error state.
