# SCRM 接口缺口清单与 UI 完善项

## 1. 判定方法

本清单将缺口分成三类：

1. **客户端未完善**：`接口文档.md` 有路由，但 SCRM Kotlin 源码没有对应客户端实现或路由证据。
2. **客户端已有、UI 未完善**：`ScrmApiClient` 或扩展契约已有方法，但 UI 只做预览、只读，或没有入口。
3. **UI 已接入但验收不完整**：UI 会调用真实客户端，但目前只有 Mock/fixture 测试，没有真实服务和真机证据。

动态 action 可能复用一个客户端方法覆盖多个 HTTP 路由，因此以下“未发现路由”是静态扫描结论，最终实现判断仍应以请求方法和 action 映射为准。

## 2. 客户端未完善的接口

### 2.1 账号、设备与运行环境

- `accounts/{accountId}/automation-settings` 读取/更新及红包群配置（4 条）。
- 设备控制：`devices/forbidden-words`、`devices/restart-wechat`、`devices/screenshot`、设备 configuration/apply、app upgrade、delete、phone-actions、微信账号 refresh（约 13 条）。
- 设备位置和代理：`location-query`、`location-spoof`、`poi-query`、设备 proxy profiles/status/apply/stop、runtime-config snapshot/refresh（约 12 条）。
- `runtime-config`、`realtime/info`、`realtime/token`。

这些接口不属于当前悬浮聊天最小闭环，但后续若做设备运维、实时推送或多设备管理，必须补齐权限、任务状态和审计。

### 2.2 联系人和好友

- `friend-requests/pull`：好友申请主动刷新。
- 好友检测：`friends/detection/start`、`stop`、`logs`、`result-sync`。
- 扩展加好友入口：`friends/from-phonebook`、`friends/in-chatroom`、`friends/name-card`。
- `friends/permission-intents/{intentId}` 的权限变更意图查询。

现有 `ScrmApiClient` 已覆盖部分好友查询和操作，但上列路径在源码中没有稳定的路由证据，不能按“已有好友功能”推断全部完成。

### 2.3 消息、媒体和群邀请

- `media/voice/environment`：语音发送前的环境能力检查。
- `messages/{messageId}/emoji-detail`、`pull-original`、`revoke`、`voice-trans-text`：客户端扩展方法存在的域，但具体 HTTP 路由和真实服务回读仍需核对。
- `group-invitations`：独立的入群邀请审批记录读取。
- 消息媒体短期访问令牌、通话录音访问令牌、设备截图访问令牌、微信登录二维码访问令牌等 MediaAccess 路由尚未进入 Android UI。

### 2.4 朋友圈、批量运营和平台能力

- 朋友圈批量计划全套：`moments/batch/plans`、暂停/恢复/取消、立即触发、重试、同步待处理/朋友圈/评论、单项详情和人工确认。
- `moments/render-preview`、`moments/delete`、`moments/friend/pull`、`moments/interactions/*`、`moments/sticky`、`moments/template`、`moments/visibility`。
- Finder 全域：历史、评论、点赞、收藏、关注、提及、导航、播放、帖子、用户页、下载和结果查询（17 条）。
- 群发：`mass-send/text`、历史查询和历史同步。
- 企业、支付、电话、代理、微信登录、微信工具、审计、测试计划等域均未进入当前 Android SCRM 主流程。

## 3. 客户端已有但 UI 未完善

### 3.1 联系人运营 UI

源码契约已存在，但 `ScrmContactProfilePanel` 当前只组装请求并提示“未发送”：

- 创建/重命名/删除标签、同步标签。
- 按筛选批量打标签。
- 客户画像草稿和客户画像保存。
- 好友朋友圈权限单个/批量/按筛选设置。
- 联系人备注、描述、电话修改和显式刷新好友资料。
- 联系人管理分页页没有完整的独立 UI 工作流。

完善标准：显示当前设备、微信账号、联系人 ID/昵称、影响数量；写操作必须二次确认、显示执行中和 taskId，并支持失败重试与结果回读。

### 3.2 消息操作 UI

`ScrmMessageOperationPreviewPanel` 和 `ScrmMessageComposerPanel` 已有入口或预览，但未发送真实请求：

- 转发单条/合并转发。
- 撤回消息。
- 语音转文字。
- 消息详情、原文补拉、表情详情。
- 媒体下载及短期访问令牌。
- 收藏表情发送、小程序卡片发送。
- 卡片模板虽可只读读取，但缺少从模板选择到发送的完整流程。
- 按筛选批量发送仍是预览，未形成独立的目标预览、内容编辑、确认、任务列表页面。

### 3.3 群管理 UI

`GroupInfoHost` 已真实执行核心操作，但以下入口在 `ScrmGroupOperationPreviewPanel` 中仍是请求预览：

- 按筛选建群、按筛选邀请/踢人。
- 添加/移除管理员、转让群主。
- 新消息免打扰、群备注、保存通讯录、群置顶。
- 群内昵称、群验证、二维码入群。
- 群接龙、邀请审批、入群邀请刷新。

其中免打扰、置顶、保存通讯录和昵称目前还会改变本地悬浮窗口状态，UI 需要明确标示“本地设置”或接通服务端后再显示远端成功。

### 3.4 朋友圈 UI

朋友圈基础同步、发布、点赞、评论和素材管理已接入，但以下体验仍不完整：

- 删除朋友圈、可见范围/权限和置顶。
- 互动消息未读数、已读、清空和主动拉取。
- 朋友圈好友刷新和单条详情补拉。
- 批量计划的草稿、预览、暂停/恢复、重试和人工确认。
- Finder 内容与朋友圈素材之间的复制/发布闭环。

## 4. 已接入 UI 但测试证据不足

以下链路已有真实调用代码，但当前证据主要来自 Mock/fixture，应安排真机和真实服务验收：

- 设置页保存配置、测试连接、设备/微信账号选择。
- chat bootstrap、history、changes 的真实账号读取和断线恢复。
- 文本、图片、视频、语音、文件和卡片发送的服务端 task 终态。
- 联系人同步、查找好友、好友验证/添加/删除、好友申请处理。
- 创建群、邀请/移出成员、改名、群公告、群二维码和退出群聊。
- 朋友圈同步、发布、点赞、评论及素材创建/复制/归档。

## 5. 推荐完善顺序

1. 先完成 P0 真机验收，确认当前已接入链路可用且错误可见。
2. 再实现消息操作 UI，因为这些入口已经存在，补齐确认和任务状态的成本最低。
3. 接着完成联系人画像/标签和好友资料 UI，形成客户运营闭环。
4. 最后扩展群高级管理、朋友圈批量计划、Finder、设备运维和支付等高风险或独立业务域。

