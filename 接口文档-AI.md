# SCRM OpenAPI v1 AI 调用指南

本文是第三方 AI 和自动化客户端的首要调用规则。接口字段、枚举和请求模型以
`/openapi/docs/openapi-v1/swagger.json` 为准；完整人工索引见
`/docs/openapi-v1-interface-catalog.md`。

机器读取 Swagger 时固定遵循以下规则：

- 使用每个 operation 的稳定 `operationId` 作为 AI 工具名或 SDK 方法键，不根据中文摘要自行生成方法名。
- 实际 HTTP 请求仍以该 operation 的 `path + method` 为准，不把 `operationId` 拼成 URL。
- `requestBody.required=true` 时必须提交请求体；为 `false` 时按 schema 判断是否省略，不要凭示例猜必填性。
- 文件上传读取 `multipart/form-data` schema：`file` 是必填二进制字段；`/media` 的 `mediaType` 只能使用 schema enum。
- 每个 operation 的 `401` 与 `default` response 是公共错误边界；先判断 HTTP 状态，错误正文不保证统一 schema，`401` 正文可能为空。
- 少量高频 operation 提供具名 `operation-level examples`；示例只说明结构，必须替换全部大写占位值，不能把示例 ID 当真实资源，也不能据此推断未展示字段可省略。

## 1. 固定配置

调用方提供以下两个值：

```text
BASE_URL=https://部署地址
API_KEY=scrm_在网页Key管理页查看的完整密钥
```

所有 HTTP 请求统一使用：

```http
X-API-Key: API_KEY
Accept: application/json
```

JSON 写请求增加：

```http
Content-Type: application/json
```

规则：

- 只调用 `BASE_URL/openapi/v1/*`。
- 不调用网页登录内部 `/api/*`。
- 不发送 `X-Device-Token`、设备绑定码或网页登录 JWT。
- API Key 不写入源码、URL、异常正文、聊天消息或业务日志。
- API Key 只代表网站用户；服务端仍会检查角色、权限、设备和微信资产范围。
- 完整 Key 可由所属用户在 `/openapi-keys` 随时查看和复制；BOSS/管理员只能看到其他用户 Key 的摘要。

## 2. 必须先发现资源

按顺序调用：

```http
GET /openapi/v1/me
GET /openapi/v1/devices
GET /openapi/v1/wechat-accounts
GET /openapi/v1/capabilities?deviceUuid=DEVICE_UUID&weChatId=WECHAT_ID
```

`GET /openapi/v1/me` 返回当前 Key 对应网站用户自己的 `userId`、`userName`、`email`、`displayName`、`phoneNumber`、员工状态、`roles` 和 `permissions`。这些字段用于确认调用身份和能力，不是微信账号资料；微信账号仍从 `/wechat-accounts` 读取。

也可以先调用 `GET /openapi/v1/quick-start` 获取推荐设备、微信账号和文档地址，但执行
写操作前仍应确认设备与微信账号属于同一条绑定关系。

参数来源规则：

| 参数 | 唯一可信来源 | 禁止使用 |
|---|---|---|
| `deviceUuid` | `/devices[].uuid` | 设备名称、型号、旧缓存或占位符 |
| `weChatId` | `/wechat-accounts[].wxid`，且其 `clientUuid` 等于目标设备 | 昵称、手机号、备注、脱敏显示值 |
| `conversationId` | `/chat/bootstrap.conversations[].conversationId` 或联系人/群列表真实 ID | 好友昵称、群名称 |
| `contactId` | `/contacts/page.items[].id` 或 `/contacts.items[].id` | 数组下标、客户端自增值 |
| `friendId` | 联系人读取接口返回的真实 wxid；接口支持 `contactId` 时可省略 | 昵称、备注、脱敏 wxid |
| `chatRoomId` | 群列表返回的、以 `@chatroom` 结尾的实际值 | 群名称 |
| `messageId` | 历史、Cursor 或 WSS 消息中的服务端 `messageId` | 本地数组下标 |
| `quoteMsgSvrId` | 同一会话已同步消息的 `msgSvrId` | `messageId` 或其它会话消息 |
| `taskId` | 写接口响应 | 时间戳、自增猜测 |
| `cursor` / `sequence` | 服务端上一响应 | 客户端自行递增或按时间推算 |

任何必填 ID 找不到时停止调用，重新读取对应列表。不要根据示例值补全，不要选择“第一个看起来相似”的资源。

联系人读取规则：

- 单个微信账号按业务字段筛选时使用 `GET /openapi/v1/contacts?weChatId=...&page=1&pageSize=100`。
- 管理后台式虚拟列表使用 `GET /openapi/v1/contacts/page?offset=0&limit=100`；`weChatId` 可选，为空时读取当前 Key 可访问的全部账号。
- 读取我侧黑名单 tombstone 时只在 `/contacts/page` 增加 `blockedOnly=true`，不要把普通联系人列表与删除记录在客户端混合推断。
- `/contacts/page` 按不可变联系人 `id` 稳定排序；下一页使用响应的 `offset + limit`，不要按时间自行排序后分页。
- `Customer.View` 决定是否返回联系人行；真实 `wxid`、敏感资料、手机号和关系日志继续按各自权限裁剪。
- 单好友标签、删除、权限、资料修改和资料刷新接口都支持 `contactId` 安全寻址。拿到脱敏 wxid 时只提交 `/contacts/page.items[].id`；服务端只在当前设备账号范围内解析真实好友。两者同时提交时必须一致，否则请求会被拒绝。
- 删除好友优先使用 `DELETE /contacts/{contactId}/friend`；只有确实持有真实 wxid 时才使用 `DELETE /friends/{friendId}`。不要把脱敏值或昵称放入路径。
- 刷新单好友资料使用 `POST /friends/refresh-info`，提交 `deviceUuid + weChatId + contactId`；它只下发查询任务，任务回包后重新读取联系人详情或列表。
- 群内添加、单手机号添加和重发好友验证可携带 `customerProfileDraft`。草稿会先落库再下发 Android；手机号携带草稿时 `phones` 必须只有一个。响应出现 `ContactProfileDraftReconciliationRequired` 时表示设备动作已提交但草稿状态待对账，禁止自动重发。
- `/friends`、`/friends/in-chatroom`、`/friends/by-phone` 只有在 `permission=1/2/3/8` 时才可显式提交 `autoApplyPermissionAfterAccepted=true`。保存响应中的 `data.friendPermissionIntentId`，随后查询 `/friends/permission-intents/{intentId}`；状态为 `Unknown` 时禁止自动重试，先刷新联系人权限快照并人工确认。

企业数据同步规则：

- `POST /openapi/v1/enterprise/users/sync` 同步企微用户；`POST /openapi/v1/enterprise/business-contacts/sync` 同步企业/业务联系人。两者不是同一协议，Body 都只提交 `deviceUuid`、`weChatId`。
- `POST /openapi/v1/enterprise/conversations/sync` 同步企微会话。Body 提交 `deviceUuid`、`weChatId`、`startTime`、`endTime`、`limit`、`offset`；时间使用 Unix 毫秒，不限定时两个时间都传 0，`limit` 只能 1-200，`offset` 不小于 0。
- 三个接口都只表示 Android 异步任务已受理。保存 `taskId` 并查询终态；成功后重新读取联系人或聊天会话，`pending/unknown/maybe-executed` 时禁止自动重复同步。

## 3. 调用业务接口

先读取 Swagger 中目标 operation 的 request schema，只发送模型声明的字段。示例：

```http
POST /openapi/v1/messages/text
X-API-Key: API_KEY
Content-Type: application/json

{
  "deviceUuid": "DEVICE_UUID_FROM_DEVICES",
  "weChatId": "WECHAT_ID_FROM_ACCOUNTS",
  "conversationId": "CONVERSATION_ID_FROM_BOOTSTRAP",
  "content": "MESSAGE_TEXT",
  "atIds": "",
  "remark": ""
}
```

`atIds` 和 `remark` 都是可选字符串。`atIds` 只用于群聊 @ 成员并沿用微信任务格式；没有 @ 时传空字符串或省略。
`remark` 是底层任务附加备注，不会替代消息正文；普通文本应留空，确有业务关联标识时再填写。

媒体调用固定为两步：

1. 调用 `/openapi/v1/media?mediaType=TYPE` 或对应媒体上传接口。
2. 使用上传响应中的 URL 调用图片、视频、语音或文件发送接口。

通用媒体上传必须使用 `multipart/form-data`，字段名固定为 `file`；`mediaType` 推荐放在查询参数中，只允许
`image`、`video`、`file`、`card-thumb`、`moment-media`、`finder-media`。兼容旧客户端在 multipart 中提交同名字段；两处同时提交必须一致，均省略时按 `file` 处理。

```bash
curl -X POST "BASE_URL/openapi/v1/media?mediaType=image" -H "X-API-Key: API_KEY" -F "file=@a.jpg"
```

语音上传 `/openapi/v1/media/voice` 同样要求 `file`，不提交 `mediaType`。

朋友圈中已同步的视频号动态/直播内容可复制为素材草稿：

```http
POST /openapi/v1/moments/{snsId}/copy-finder-material
{"deviceUuid":"DEVICE_UUID","weChatId":"WECHAT_ID","preferredType":"auto","materialName":"素材名称"}
```

`snsId` 必须来自当前账号的朋友圈同步结果；`deviceUuid` 和 `weChatId` 必须同时提交且属于同一绑定。不要提交页面 `xmlContent` 或 Finder JSON，服务端会读取数据库原始记录。响应只创建禁用草稿，不发布朋友圈或视频号。

手机必须能访问媒体 URL。不要把本机磁盘路径、网页临时对象 URL 或未上传的文件名作为媒体 URL。
语音上传响应中的 `voiceUrl` 是发送 `/messages/voice` 时使用的 AMR 地址；`previewUrl` 只供网页播放，不能作为微信语音发送地址。

个人二维码和 A8Key 调用分别使用 `/openapi/v1/wechat-tools/qrcode/personal`、`/openapi/v1/wechat-tools/a8key`。两者都是异步手机任务，保存 `taskId` 并按任务结果接口查询；不要从日志或审计中寻找原始二维码、URL、userName 或 MsgSvrId。结果被权限投影隐藏时停止，不要换 Key 或重复下发绕过。

收藏表情工具调用 `/openapi/v1/wechat-tools/emoji-detail`，只提交当前微信已收藏/已同步资源的 32 位十六进制 MD5。若表情来自已同步聊天消息，优先使用 `/openapi/v1/messages/{messageId}/emoji-detail`，由服务端解析 MD5 和消息上下文；不要从页面 XML 猜测或批量枚举 MD5。

手机微信退出登录使用 `POST /openapi/v1/wechat-tools/logout`，Body 必须同时提交当前在线的 `deviceUuid`、`weChatId` 和 `confirmLogout=true`。它会改变手机微信登录状态，不等于 `/wechat-accounts/{wxid}/soft-delete`。响应成功只表示登出任务已下发；等待 Android 上报账号状态，结果未知时不要自动重复登出。

好友检测按以下顺序调用：`POST /friends/detection/start` 启动、`POST /friends/detection/stop` 停止、`POST /friends/detection/result-sync` 请求最终结果、`GET /friends/detection/logs?weChatId=...` 读取已落库历史。默认且推荐 `onlyCheck=true`；该模式不会删除好友。`onlyCheck=false` 必须同时提交 `confirmDelete=true`，并要求调用身份额外具备删除好友权限。停止接口必须使用 start 返回的 `taskId`，不接受 `taskId=0`。历史接口只返回结构化摘要，不返回数据库 `DetailInfo` 原文。

附近地点查询使用 `POST /openapi/v1/devices/{deviceUuid}/poi-query`，Body 提交 `latitude`、`longitude`、可选 `keyword` 和 `scene`。纬度范围为 -90 到 90，经度范围为 -180 到 180；普通调用固定 `scene=general`，产品页面只使用 `device-control/moment-post/finder-post`。保存同步响应的 `taskId`，从任务终态 `data.poiList` 读取地点；结果未知时不要随机修改 scene 绕过同坐标冷却，也不要自动重发。

设备截图和手机记录使用 `/openapi/v1/devices/screenshot`、`/openapi/v1/phone/state`、`/openapi/v1/phone/sms/send`、`/openapi/v1/phone/sms/sync`、`/openapi/v1/phone/call-logs/sync`。这些都是异步 Android 任务，必须保存 `taskId`；同步回包成功后，再读取 `/openapi/v1/phone/sms` 或 `/openapi/v1/phone/call-logs`。时间字段使用 Unix 毫秒，列表必须提交明确 `weChatId`。截图预览用 `/devices/{deviceUuid}/screenshots/{taskId}/access-token`，其中 taskId 必须来自截图回包；通话录音用 `/call-recordings/{id}/access-token`，id 来自通话记录列表。不要提交或缓存截图 URL、recordUrl、短信号码、正文或 IMEI。

微信登录分为“环境配置”和“Start”两阶段，禁止直接用 Start 猜测或修改环境。只对明确选择开启或关闭的项目先完成下发和终态等待；保持现状的项目不下发。最后调用 `POST /openapi/v1/wechat-login/start`：`useProxy/useLocationSpoof/useTabletSpoof` 为 true 时验收启用、false 时验收关闭、显式 null 时保持现状并跳过该项检查；字段省略时为兼容旧调用方默认 true。若返回 `data.kind=wechat-login-environment-not-ready`，直接展示 `message/failureCodes` 并停止重试。只有 Start 成功后才进入会话轮询。

微信登录二维码只用 `/wechat-login/{sessionId}/qr-code/access-token`。sessionId 必须来自 start/active/get 响应；不要提交 `qrCodeUrl`。只在会话状态为 `qr_ready` 且未过期时绑定返回的短期 `url`，其它状态停止展示并继续按会话状态机处理。

设备微信违禁词使用 `/openapi/v1/devices/forbidden-words`，提交完整 `words` 数组；它是替换语义，不是增量追加，空数组表示清空。清空微信端本地聊天记录使用 `/openapi/v1/messages/clear-all`，必须提交 `confirmClearAllChatMessages=true`；该动作不删除 SCRM 服务端消息，结果未知时禁止自动重试。群接龙使用 `/openapi/v1/chatrooms/jielong`，目标群必须来自当前账号群列表，只有续接已有接龙时才提交真实 `msgSvrId`。

批量聊天与原生群发必须按语义选择：已有明确会话时使用 `/openapi/v1/messages/batch`，按标签或画像筛选好友时使用 `/openapi/v1/messages/batch-by-filter`；只有确实需要微信群发助手文本时才使用 `/openapi/v1/mass-send/text`。原生群发只接收 `deviceUuid`、`weChatId`、`targets` 和纯文本 `content`，不要向它提交卡片或媒体。微信平板模式通常没有群发助手入口，此时使用 `/messages/batch`。群发历史先调用 `/openapi/v1/mass-send/history/sync`，等待 Android 回包后再用 `/openapi/v1/mass-send/history/query` 读取；发送或同步出现 `unknown/maybe-executed` 时先查询 taskId 和历史，禁止自动重发。

群聊成员或高级管理调用前固定执行：`POST /openapi/v1/chatrooms/refresh`，再读取 `/openapi/v1/chatrooms/{chatRoomId}/members`。
邀请成员的 `intValue` 只允许 0/1，默认 0；需要邀请确认时按业务要求传 1，踢人固定传 0。转让群主只传一个 `memberWxid`；增删管理员单次最多 3 个，目标都必须来自最新成员快照，而且当前登录微信必须是群主。群内昵称和群备注最多 31 个字符且不能包含换行；群备注 `text=""` 表示清空。

朋友圈时间线使用 `GET /openapi/v1/moments/timeline?deviceUuid=...&weChatId=...&count=50` 读取；它不会触发手机同步。需要新数据时先调用 `/moments/sync`，等待任务终态，再读取时间线。时间线和互动的 `nextCursor` 都是不透明值，只能原样用于同一设备、微信账号和筛选条件。

好友朋友圈拉取优先使用 `/openapi/v1/contacts/page` 返回的 `contactId`。朋友圈互动先调用 `GET /openapi/v1/moments/interactions`，之后已读或清理只提交响应中的 `noticeId`；不要自行构造 `circleId/commentId`。朋友圈 `circleId` 可能为负数，只有 0 无效；删除动态、删除评论和清理互动都属于高影响操作，出现 pending/unknown 时停止自动重试。
批量计划真正执行时仍走朋友圈统一下发入口和可见范围最终保护；空标签/好友未命中同样会被拒绝，不会降级为公开发布。

朋友圈批量计划恢复固定先调用 `GET /openapi/v1/moments/batch/plans/{planId}`，只使用响应中的 `planId`、`items[].id` 和状态：

- 确定失败且服务端标记可重试：`POST .../items/{itemId}/retry`，Body 可带 `scheduledAt` 和 `reason`。
- 结果未知：先调用 `POST .../sync-pending`；也可分别调用 `sync-moments`、`sync-comments`，Body 为 `{"onlyWaitingSync":true}`。这些同步接口返回 `taskId`，必须查询任务终态后再读计划详情。
- 仍然未知且已在手机微信中人工确认未发布：调用 `manual-confirm-not-published`，必须填写 `reason`；它只重新排队，不会立即下发。
- 已在手机微信中人工确认发布成功：调用 `manual-confirm-published`，提交真实 `circleId` 和 `reason`；不要用时间戳或本地数组下标代替 `circleId`。
- 待执行项需要提前：调用 `trigger-now`，它只调整排程并等待调度器领取。
- 需要校准单项详情：调用 `POST .../items/{itemId}/pull-detail`，Body 的 `getBigMap` 默认 `false`；保存返回的 `taskId`。

计划项控制返回最新 `MomentPostPlanDto`。任何 `404/409/429`、`wait-timeout`、`maybe-executed` 或再次未知都应停止自动操作并重新读取计划，不得循环调用重试、立即触发或人工确认接口。

视频号评论列表和评论/回复都必须同时提交同一条动态的 `feedId + nonceId + feedAuth`，值只能来自已确认的视频号结果或手机端上下文，不能猜测、互换或只填前两个字段。回复评论时同时提交 `replyCommentId`、`replyUsername`、`replyNickname`；`findText` 是 SmRun 私有扩展透传字段，普通评论或回复保持空字符串。发布前先用 `/openapi/v1/media?mediaType=finder-media` 上传素材，再用 `/openapi/v1/finder/posts/template` 预检；真正发布提交 `/openapi/v1/finder/posts`，`medias` 至少一项。

视频号历史只读使用 `GET /openapi/v1/finder/history`，明确提交同一设备账号的 `deviceUuid`、`weChatId`，`resultType` 只允许 `all/mention/userpage/comment`，`count` 最大 100；可按 `success`、`taskId`、`receivedFrom`、`receivedTo` 过滤。导出使用 `/openapi/v1/finder/history/export`，需要额外导出权限；返回内容已经审计和脱敏，不包含原始 payload。真实空历史是成功空数组，HTTP 400/403/404 必须按返回的稳定 `code` 处理，不能换账号或换 Key 绕过。

视频号普通作品点赞或取消点赞使用 `POST /openapi/v1/finder/likes`。请求只提交：

```json
{
  "deviceUuid": "设备 UUID",
  "weChatId": "当前在线微信 wxid",
  "feedId": 123456789,
  "type": 1,
  "isCancel": false
}
```

调用前必须由用户在手机上打开同一微信号、同一 `feedId` 的目标作品页面，让 Android 捕获微信原生短时上下文。第三方不要提交或保存 `sessionBuffer`、`likeId`、`actionToken`；这些不是 OpenAPI 合同。`isCancel=false` 表示点赞，`true` 表示取消。接口等待 Android `Tsk202` 最终回包：上下文缺失时重新打开目标作品后可人工重试一次；`wait-timeout`、`maybe-executed` 或“最终结果未知”时先刷新作品状态，禁止自动重试。

让手机进入视频号首页或提交原生搜索，使用 `POST /openapi/v1/finder/navigation`：

```json
{
  "deviceUuid": "设备 UUID",
  "weChatId": "当前在线微信 wxid",
  "action": "search",
  "keyword": "搜索关键词"
}
```

`action` 只允许 `home`、`search`；`home` 忽略 `keyword`，`search` 要求 1-100 个字符。当前精确映射只支持微信 8.0.69。成功结果的 `data.schema` 为 `finder-navigation-result-v1`；读取 `action`、`activity`、`submitted`、`requestObserved`。服务端不返回关键词正文。`shouldAutoRetry=false`，失败或超时后先查看手机当前页面再决定是否人工重试。

视频号普通视频作品的本地观看使用 `POST /openapi/v1/finder/watches`：

```json
{
  "deviceUuid": "设备 UUID",
  "weChatId": "当前在线微信 wxid",
  "feedId": 123456789,
  "targetWatchMillis": 8000
}
```

`targetWatchMillis` 范围为 `1000-60000`。调用前必须在手机打开同账号、同 `feedId` 的普通视频作品并等待播放器出现。成功结果的 `data.schema` 为 `finder-watch-result-v1`、`resultLevel=watched_local`，同时固定 `serverFeedAcknowledged=false`：它只证明 Android 采样到播放器处于播放状态、进度持续增长且本地累计达到目标时长，不证明微信服务端已给该 Feed 增加观看量。上下文缺失可重新打开作品后人工重试；`unknown/maybe-executed` 时等待迟到终态，不要自动重试。

读取、播放、暂停或切换手机当前作品使用 `POST /openapi/v1/finder/playback`：

```json
{
  "deviceUuid": "设备 UUID",
  "weChatId": "当前在线微信 wxid",
  "feedId": 123456789,
  "action": "status"
}
```

`action` 只允许 `status`、`play`、`pause`、`next`。手机必须停留在同账号、同 `feedId` 的普通视频作品；接口不会搜索、打开初始作品或传输视频。`next` 会在当前 Finder 窗口派发一次上滑，只有确认同账号的新 Feed 覆盖窗口中心后才成功。成功结果的 `data.schema` 为 `finder-playback-result-v1`，读取 `sourceFeedId`、`feedId`、`playing`、`positionMs`、`durationMs`、`gestureSubmitted`、`feedChanged`；后续控制必须使用返回的新 `feedId`。`code=FinderNextUnknownAfterGesture` 表示手势可能已生效但新 Feed 未确认，禁止自动重试，先截图或人工读取当前画面。

删除当前微信账号自己的视频号评论使用 `POST /openapi/v1/finder/comments/delete`。Body 必须提供 `deviceUuid`、`weChatId`、`feedId`、`commentId` 和同一作品的 `nonceId`；调用方还需具备 `finder.delete_comment` 权限，设备需开启 Finder 写任务。手机必须停留在目标作品的视频号页面，当前 exact 链只支持微信 8.0.69。结构结果为 `finder-delete-comment-result-v1`；`FinderDeleteCommentResultUnknown` 表示请求可能已经提交，必须先重新拉取评论状态，禁止自动重试。

微信笔记或媒体详情补偿使用 `POST /openapi/v1/messages/{messageId}/pull-detail`。`messageId` 必须来自聊天历史/实时消息的 SCRM 服务端主键；请求体只提交 `deviceUuid`、`weChatId` 和可选 `getOriginal`。不要提交 talker、friendId、本地 MsgId、MsgSvrId、md5、CDN URL 或 Key，服务端会从可信消息快照解析。任务成功受理后等待 Android 回包，再重新读取该会话。

红包详情、状态、领取和转账收取同样只使用聊天历史、HTTP Cursor 或 WSS 返回的服务端 `messageId`。调用 `detail-by-message`、`status-by-message`、`take-by-message` 时只提交接口定义中的 `deviceUuid`、`messageId` 和可选 `refuse`；不要读取、保存、猜测或提交 `hbUrl`、`nativeUrl`、`msgKey`、`transferId`、本地 MsgId 或 MsgSvrId。服务端会在资产和权限校验后从可信消息快照临时解析原始凭据。

收藏表情缺图使用 `POST /openapi/v1/messages/{messageId}/emoji-detail`。请求体只提交 `deviceUuid`、`weChatId`；服务端会验证目标确实是表情消息，并从可信正文、媒体哈希或扩展记录解析 32 位 MD5、MsgSvrId 和会话。不要提交或猜测 MD5、friendId、MsgSvrId、CDN URL、AESKey。成功受理后保存 `taskId`，等待 1273 自动衔接 CDN 下载和媒体回填，再重新读取会话；终态未知时停止自动重试。

微信原始媒体下载使用 `POST /openapi/v1/messages/{messageId}/media/download`。下载消息主媒体时 Body 只提交 `deviceUuid`、`weChatId`；指定附件时从聊天历史/实时消息读取 `media[].mediaId` 或 `extensions[].extensionId`，二选一提交。不要读取、保存或提交 CDN URL、Key、FileId、文件类型、talker 或 MsgSvrId，服务端会校验选择器归属并解析。受理响应应为 `resultContract=task_result`；保存 `taskId` 并轮询响应中的 `taskResultUrl`。终态成功后重新读取聊天历史取得已回填媒体；pending/unknown 时停止，不要重复下载。

已回填媒体的播放使用 `POST /openapi/v1/messages/{messageId}/media/access-token`。普通附件提交 `mediaId`，高级消息提交 `extensionId + resourceIndex`；`resourceKind` 只允许 `original` 或 `voice-preview`。请求中禁止出现 URL、物理路径、CDN Key、FileId、talker 或 MsgSvrId；只绑定返回的短期 `url`。

聊天数据同步使用以下正式接口：

- `POST /openapi/v1/messages/sync/ids`
- `POST /openapi/v1/messages/sync/history`
- `POST /openapi/v1/messages/sync/read-state`
- `POST /openapi/v1/messages/sync/unread-list`
- `POST /openapi/v1/messages/sync/conversation-unread`

`ids` 和 `history` 的时间是 Unix 毫秒；`ids` 窗口最大 10 分钟，`history` 不限定时间时 `startTime=0,endTime=0`，`count` 只能 1-200。`read-state` 和 `conversation-unread` 的 `conversationId` 必须来自当前微信账号已同步的好友或群聊；不要猜测 ID。`unread-list` 不提交会话。所有同步接口都先保存 `taskId`，等待终态后重新读取聊天历史/会话列表，结果未知时禁止自动重复同步。

需要 `Idempotency-Key` 的接口：

- `POST /openapi/v1/messages/{messageId}/forward`
- `POST /openapi/v1/messages/forward`
- `POST /openapi/v1/payments/lucky-money`
- `POST /openapi/v1/payments/remittance`
- `POST /openapi/v1/payments/lucky-money/take-by-message`
- `POST /openapi/v1/payments/transfers/take-by-message`
- 每次业务意图生成一个不超过 128 字符的随机键。
- 网络重试同一业务意图时复用原键。
- 新业务意图、修改金额/目标/备注或修正支付密码时必须生成新键。
- 同键不同请求收到 HTTP 409 后停止，不要换键绕过冲突。

## 4. 异步任务闭环

大多数写接口同步成功只表示服务端接受或下发任务。调用方必须保存 `taskId`，并使用发起任务的同一
API Key 查询：

```http
GET /openapi/v1/tasks/{taskId}
```

任务状态机：

| `status` | 含义 | 调用方动作 |
|---|---|---|
| `processing` | 已受理，等待 Android 回包 | 有界轮询，保留原 `taskId` |
| `success` | Android 明确执行成功 | 提交本地成功状态 |
| `failed` | Android 明确执行失败 | 展示 `resultCode/message`，修正原因后由业务方决定是否发起新任务 |
| `unknown` | 结果未知或可能已执行 | 停止自动重发，先查询聊天或对应业务状态 |

必须同时读取：`success`、`status`、`resultUnknown`、`resultCode`、`message` 和 `nextStep`。

写接口受理响应还必须先读取 `resultContract`：`task_result` 轮询 `taskResultUrl`；`external_state` 读取
`resultResource`；`untracked` 表示没有通用任务终态，应按当前接口说明刷新权威业务状态。受理响应中的
`operationId` 只属于 external-state 关联，不是 Swagger operationId。

以下结果禁止自动重发：

- `wait-timeout`
- `maybe-executed`
- `PublishedNeedSync`
- `status=unknown`
- `resultUnknown=true`

`taskResultUrl` 和 `recentTaskResultsUrl` 是相对路径，必须用当前 `BASE_URL` 解析，不能信任响应外的其它主机。

## 5. HTTP 错误处理

| HTTP | 处理规则 |
|---|---|
| 400 | 请求字段错误。读取 `code/title/detail/message`，修正后再形成新请求。 |
| 401 | API Key 缺失、错误、过期或停用。停止调用并更换有效 Key。 |
| 403 | 用户、权限、设备、微信资产或运行态能力不允许。停止调用并重新读取 `/me`、`/capabilities`。 |
| 404 | 资源不存在或不属于当前用户。重新读取资源列表，不猜测 ID。 |
| 409 | 状态冲突、保护窗口、幂等冲突或 Cursor 越界。按稳定错误码处理。 |
| 410 | Cursor 已过期。丢弃旧 Cursor，重新 bootstrap。 |
| 429 | 频率受限。只按 `retryAfterSeconds` 等待，不并发放大请求。 |
| 5xx | 服务端异常。保留 request ID、endpoint、HTTP 状态和脱敏响应，不自动执行高敏写操作。 |

网络超时不等于请求未执行。写请求超时后先用原 `taskId`、原 `Idempotency-Key` 或业务读取接口确认状态。

## 6. 聊天读取与 WSS 状态机

HTTP Cursor 是可靠来源，WSS 只降低延迟。每个客户端实例只维持一条聊天 WSS。

### 6.1 建立基线

```http
GET /openapi/v1/chat/bootstrap?deviceUuid=DEVICE_UUID&weChatId=WECHAT_ID
```

原子提交 `conversations` 和 `baselineSequence`，然后从该 sequence 继续读取变化。

### 6.2 HTTP 补偿

```http
GET /openapi/v1/messages/changes?deviceUuid=DEVICE_UUID&weChatId=WECHAT_ID&afterSequence=SEQUENCE&limit=100
```

第一页记录 `headSequence`。如果 `hasMore=true`，后续页固定把这个值作为 `untilSequence`，并把
`nextSequence` 作为下一页 `afterSequence`。处理完成后持久化最后确认的 sequence。

### 6.3 建立 WSS

```http
POST /openapi/v1/realtime/token
Content-Type: application/json

{"clientInstanceId":"STABLE_CLIENT_INSTANCE_ID"}
```

使用响应的 `accessToken` 连接：

```text
wss://DEPLOY_HOST/ws/openapi/chat?access_token=SHORT_TOKEN
```

连接后发送：

```json
{"action":"subscribe","deviceUuid":"DEVICE_UUID","accountId":"WECHAT_ID","afterSequence":123,"capabilities":["chat.messages","contacts.changed","conversations.changed","task.status.changed"]}
```

收到 `subscribed` 后才把新 `subscriptionRevision` 标记为 active。订阅切换期间：

- 旧 revision 的帧不写入新作用域。
- pending revision 的消息不提前提交。
- 相同 sequence 的重复事件按 sequence 去重。
- 断线后先用 HTTP Cursor 补偿，再重连并订阅。
- `subscribed` 后，每个已订阅资源 capability 会立即收到一条 `changeType=baseline` 的 `resource.changed`。这不是新业务变化；按 `resource` 调用对应 HTTP GET，提交当前权威快照。
- 后续同资源通知可能 latest-wins 合并。`task.status.changed` 是 OpenAPI Key/User 的 owner-wide 通道，帧内设备账号来自任务自身，不要求等于当前聊天作用域；它没有重连 baseline。每个写接口的 `taskId` 必须先按 `clientInstanceId + API Key 摘要` 持久化，断线后通过 `/openapi/v1/tasks/{taskId}` 恢复，只有顶层 `final=true` 才从 pending 删除。
- 收到当前 active revision 的 `task.resync.required`（`code=TaskStatusOverflow`）时，只逐个 GET 当前 Key owner 本地已持久化且尚未终结的 `taskId`。不要扫描未知任务，也不要因此启动固定间隔轮询。纯任务客户端可只订阅 `task.status.changed`，省略设备账号和聊天 Cursor。

资源唤醒固定映射：

| `resource` | 权威 GET | `baseline/snapshot` 规则 |
|---|---|---|
| `contacts` | `/openapi/v1/contacts/page?weChatId=...` | 从 `offset=0` 重新分页 |
| `conversations` | `/openapi/v1/chat/bootstrap?deviceUuid=...&weChatId=...` | 只替换会话投影；不要因资源唤醒重置已提交的聊天 Cursor |
| `friend_requests` | `/openapi/v1/friend-requests?weChatId=...` | 读取当前列表 |
| `group_invitations` | `/openapi/v1/group-invitations?weChatId=...` | 读取当前列表 |
| `contact_labels` | `/openapi/v1/contact-labels?weChatId=...` | 读取当前标签字典 |
| `phone_records` | `/openapi/v1/phone/sms?weChatId=...`、`/openapi/v1/phone/call-logs?weChatId=...` | `sms/call_logs` 只读对应接口；`baseline/snapshot` 两者都读 |
| `moments` | `/openapi/v1/moments/timeline?deviceUuid=...&weChatId=...`、`/openapi/v1/moments/interactions?deviceUuid=...&weChatId=...` | `timeline/interaction` 只读对应接口；`baseline/snapshot` 两者都读 |
| `finder` | `/openapi/v1/finder/results?deviceUuid=...&weChatId=...` | 读取当前已持久化结果 |

`moments.changed` 与两个朋友圈权威 GET 使用同一个“朋友圈同步/读取”权限；只有互动写权限不足以订阅该资源。

重建规则：

| 事件/错误 | 动作 |
|---|---|
| HTTP `CursorBeyondHead` | 重新 bootstrap |
| HTTP `CursorExpired` | 重新 bootstrap，并按需读取有限历史 |
| WSS `resync.required` | 只处理当前 active revision，重新 bootstrap |
| WSS `task.resync.required` | 只处理当前 active revision；逐个查询当前 Key owner 本地尚未终结的 `/openapi/v1/tasks/{taskId}` |
| WSS `AuthorizationRevoked` | 清理该设备账号作用域并停止重连 |
| WSS `RealtimeControlRateLimited` | 按 `retryAfterSeconds` 等待 |

历史翻页使用 `/openapi/v1/chat/history` 返回的 opaque `nextCursor`。不要解析、修改或跨会话复用该 Cursor。

## 7. 代理、设备快照与虚拟定位

- `GET /openapi/v1/devices/{deviceUuid}/configuration`：读取设备持久目标态覆盖层；需要敏感配置权限，`host/port/fileUpUrl` 只列入 `redactedKeys`。
- `GET /openapi/v1/runtime-config?deviceUuid=...`：读取全局默认叠加设备覆盖后的期望值、Android 主进程值和微信/Sogu 进程最终值。
- `POST /openapi/v1/runtime-config`：兼容入口；补丁式写入设备目标态，不修改全局默认，定向下发并返回更新后的同一结构。
- `GET /openapi/v1/proxy/profiles`：读取脱敏档案摘要。
- `POST /openapi/v1/proxy/profiles`：新增或修改档案；`username/password/configJson` 只写不读，查询响应不会返回原文。
- `DELETE /openapi/v1/proxy/profiles/{profileId}`：只删除未绑定设备的档案。
- `GET /openapi/v1/proxy/assignments?deviceUuids=uuid1,uuid2`：`deviceUuids` 必填，服务端逐台校验资产范围。
- `POST /openapi/v1/proxy/apply`、`POST /openapi/v1/proxy/stop`：请求体必须显式提交 `deviceUuids`，禁止先读取全量设备再猜测目标。响应 `success=true` 仅表示全部目标均已写入当前在线 Android 通道；逐台读取 `data.targets[].taskId/profileId/profileVersion/expectedState/accepted/sent/code`，随后以同一 `taskId` 轮询 assignments。Apply 只有 `running + vpnRouteReady + permissionRequired=false` 才是终态；Stop 只有 `stopped + isEnabled=false + vpnRouteReady=false + trafficObserved=false` 才是终态。
- `GET /openapi/v1/devices/{deviceUuid}/configuration`：读取设备目标态覆盖层；用 `redactedKeys` 判断传输字段是否存在，不读取原值。
- `POST /openapi/v1/devices/{deviceUuid}/configuration/apply`：请求体直接提交 `boolConfs/intConfs/strConfs`；服务端按键持久化设备目标态。设备在线时立即下发；离线时仍返回保存成功，并在下次 HTTP 登录或 TCP 重连时自动重放。未知键拒绝，字符串原文不会回显。
- `POST /openapi/v1/devices/{deviceUuid}/runtime-config/refresh`：请求新快照；顶层 `taskId=0`，`data.snapshotTaskId` 只用于日志关联。
- `GET /openapi/v1/devices/{deviceUuid}/runtime-config/snapshot`：只返回服务端已脱敏的 Android 实际快照。
- 折叠机诊断中先看 `diag_wxTabletSpoofDisplayMetricsPatchExpected`：为 `false` 时，当前模式按设计只改 `Configuration`，此时 `diag_wxTabletSpoofDisplayProfileSelfCheckMetricsMatched=false` 不表示失败；只有预期值为 `true` 时才要求 DisplayMetrics 自检或命中证据。
- 验收地图/WebView 子进程时，先要求 `diag_wxTabletSpoofZygiskSubprocessProfileApplied=true`，再读取 `diag_wxTabletSpoofZygiskSubprocessReportedProcessNames` 和 `diag_wxTabletSpoofZygiskSubprocessReceiptCount`。进程名字段是同一三元回执下最多八个具名微信子进程的 JSON 数组；必须看到实际地图/WebView 进程名，不能仅凭 `:push` 等任意子进程回执判定通过，旧画像列表也不能作为当前画像的验收证据。
- `POST /openapi/v1/devices/{deviceUuid}/app-upgrade`：必须携带 `Idempotency-Key`；下发服务端已验证的 SmRun 发布物，响应返回持久化 `taskId`，随后通过任务结果读取下载、安装和微信 Hook 刷新阶段。网络重试必须复用原键，`unknown/install_unconfirmed` 时禁止自动生成新键重发。Body 只接受可选 `weChatId`，不接受客户端指定 APK 地址。同设备已有未终结升级时返回 HTTP 409；同一 owner 可收到已有 `taskId` 并继续查询，跨 owner 冲突不会公开不可查询的任务号。禁止换键绕过设备级互斥。
- `GET /openapi/v1/devices/{deviceUuid}/app-upgrade/latest`：管理员按设备读取最近一次升级的 durable 安全摘要。
- `GET /openapi/v1/devices/app-upgrades/latest`：管理员一次读取全部已归属设备的最近升级摘要；用于页面首次进入、人工刷新和明确 resync，不作为固定轮询接口。
- `POST /openapi/v1/devices/{deviceUuid}/app-upgrade/{taskId}/reconcile-not-installed`：管理员在设备侧核对后显式提交 `confirmNotInstalled=true` 和原因，关闭仍为 `unknown + final=false` 的旧升级任务；该接口不自动重试升级。
- `POST /openapi/v1/devices/{deviceUuid}/delete`：只在用户明确指定设备并确认删除时调用，Body 必须包含 `confirmDelete=true`。
- `POST /openapi/v1/devices/{deviceUuid}/wechat-accounts/refresh`：使用空 Body 下发 3050，不提交 `weChatId`；等待 3051 后重新读取设备和账号列表。
- `POST /openapi/v1/devices/{deviceUuid}/phone-actions`：只使用文档动作名，不提交 proto 整数；除 `upload-log` 外必须 `confirm=true`。
- `POST /openapi/v1/devices/{deviceUuid}/location-spoof`：完整提交 `enabled/latitude/longitude/city`；`latitude` 是纬度，`longitude` 是经度。
- `GET /openapi/v1/devices/{deviceUuid}/location-spoof?afterReceivedAt=...`：等待 `pending=false` 后检查 `runtimePhase`。`prelaunch_ready` 可继续发起受控网页登录并由 Android 等待启动后回执；`runtime_ready + runtimeReady=true` 表示当前微信进程已确认；`blocked` 或 `processState=unknown` 时停止。

设备覆盖层是单台设备的权威目标态，全局配置只为未覆盖键提供默认值；全局设置下发、`capabilities` 能力判断和真实任务服务端门禁都会先叠加设备覆盖。在线设备的配置验收顺序固定为 `configuration/apply -> runtime-config/refresh -> runtime-config/snapshot`。保存刷新响应的 `baselineReceivedAt`，只有 snapshot 的 `receivedAt` 更新后才判断实际值。离线设备只确认 apply 返回“目标态已保存”，不要调用 refresh；等待设备登录或重连后再刷新快照。不要用 `snapshotTaskId` 调用通用 tasks API，也不要因快照暂未更新而自动重复下发。位置和平板画像必须提交完整预设；高风险值会额外要求敏感配置权限。折叠机关闭态还必须确认 `diag_wxTabletSpoofZygiskTargetStateKnown=true`、`diag_wxTabletSpoofZygiskDiskAuditCompleted=true`、`diag_wxTabletSpoofZygiskDiskConfigEnabled=false`、`diag_wxTabletSpoofZygiskDiskConfigMatchesExpected=true` 且 `diag_wxTabletSpoofZygiskDiskConfigStale=false`。

应用升级返回持久化 `taskId>0`，可通过任务结果和上述 latest 摘要恢复。`install_unconfirmed` 是 `unknown + final=false`，只有设备重新上报目标版本、收到后续明确终态，或管理员完成“不存在目标版本”的人工核对后才关闭。删除设备的顶层 `taskId` 仍为 0，按设备列表中目标 UUID 已消失确认。任何超时、断线或未知结果都先读状态，不自动重复下发。

手机动作白名单是 `upload-log/upload-file/clean-app-cache/clean-wechat-cache/clean-file-url-cache/phone-call/reboot/restart-client`。只有文件上传和拨号允许非空 `value`。重启微信调用专用 `restart-wechat`；任何 `UiTap/UiText/UiKey/UiScreenshot/UiLoginCredential/UiSwipe/UiLoginSemanticAction` 都属于网页登录会话内部动作，不从通用手机动作接口尝试。

代理或定位 POST 返回成功只表示服务端已受理或下发。代理还要读取 assignments，定位还要读取新快照；没有新快照时停止自动重发。

运行态权限响应中的三份字典不可混用：

- `configuredBoolConfs`：全局默认叠加设备覆盖后的目标值，只表示“希望设备采用什么值”。
- `deviceOverrideBoolConfs`：只包含该设备明确覆盖的布尔键；全局默认变化不能覆盖这些键。
- `effectiveBoolConfs`：Android/SmRun 主进程最近回报的实际值。
- `soguEffectiveBoolConfs`：微信/Sogu 主进程对 12 项任务能力和 10 项微信原生权限最终读取到的值。

验收微信进程权限时，必须同时满足 `hasSoguRuntimeSnapshot=true`、`soguRuntimeSnapshotComplete=true`、`soguRuntimeSnapshotCurrentProcess=true`，再逐键比较 `configuredBoolConfs` 和 `soguEffectiveBoolConfs`。任一条件不满足，或字典缺少某个键，都表示状态未知，不表示该权限为 `false`。`warnings` 非空时先处理警告，不自动重复 POST。

## 8. 高影响操作

删除好友、退群、拉黑、会话删除、账号退出、设备重启、朋友圈发布、视频号写入和资金相关操作均不得由
AI 自主选择目标或循环重试。调用前必须具备：

1. 明确的用户业务指令。
2. 从读取接口获得的真实目标 ID。
3. `/capabilities` 显示对应能力可用。
4. 可审计的幂等键或任务记录。
5. 对 `unknown` 结果停止自动重试的处理分支。

## 9. 文档选择

- AI 首要规则：`/docs/openapi-v1-ai-guide.md`
- 机器结构：`/openapi/docs/openapi-v1/swagger.json`
- Swagger UI：`/openapi/docs`
- 完整接口目录：`/docs/openapi-v1-interface-catalog.md`
- 人工接入说明：`/docs/openapi-v1-integration.md`

生成调用代码前先读取 AI 指南和 OpenAPI JSON。指南解决调用顺序与状态机，JSON 决定当前版本的准确字段。
## 微信账号记录软删除

`POST /openapi/v1/wechat-accounts/{wxid}/soft-delete` 是高影响数据库操作，要求调用者拥有目标账号资产和 `wechat.account.logout` 权限。它只隐藏 SCRM 中的账号记录，**不等于手机微信退出登录**，也不会删除聊天、朋友圈或支付历史。

需要让手机微信真正退出登录时使用 `/openapi/v1/wechat-tools/logout`，不要用软删除代替；需要清理 SCRM 账号索引时使用本节软删除接口，不要先登出再猜测数据库状态。

```bash
curl -X POST "$BASE_URL/openapi/v1/wechat-accounts/$WXID/soft-delete" \
  -H "X-API-Key: $SCRM_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"confirmSoftDelete":true,"reason":"管理员确认移除账号记录"}'
```

成功结果码为 `deleted` 或 `already_deleted`；后者也是成功的幂等终态。`400` 表示确认或原因无效，`403` 表示缺少权限，`404` 表示账号不存在或不在当前资产范围。请求结果未知时先重新调用 `GET /openapi/v1/wechat-accounts`：目标账号已经不在列表中即可按删除完成处理，不要自动连续重发高影响请求。
