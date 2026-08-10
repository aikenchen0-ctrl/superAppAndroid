# 悬浮聊天接口完善发现

## 当前事实

- `完成进度.md` 记录了悬浮聊天完成度，但其中底部栏 `+` 入口仍为“部分实现”，与当前代码已有 `MoreInputButton` 不一致。
- Android 已存在头像 URL、远程状态映射、头像加载及相关单测；需要沿数据流确认真实接口字段是否抵达 UI。
- 用户要求先完成头像显示，其余接口功能需在该功能真机验收后逐项推进。
- 功能对照文档实际为 `功能对照.md`，已确认它只针对悬浮聊天补齐顺序。
- Android UI 端 `CompactAvatar` 已通过 `rememberAsyncAvatarBitmap(resolvedAvatarImageUri(...))` 加载远程 `contact.avatarUrl`，不是首要缺口。
- 修复后 `ScrmFloatingChatBridge` 会将四类 SCRM 实体的 `displayAvatarUrl` 映射为 `FloatingChatContact.avatarUrl`。
- iOS `OpenApiDisplay` 支持 `avatarUrl`、`headImgUrl`、`headimgurl`、`imageUrl` 等多字段及 `contact/member/sender` 嵌套路径；Android 远程模型的单一 `avatar` 字段很可能丢失真实接口头像。
- 修复前 Android SCRM 模型只声明 `avatar`；Kotlin JSON 解码会忽略 `avatarUrl/headImgUrl/headimgurl/imageUrl`，导致映射层收到空头像。现已增加别名字段和统一取值属性。
- Android `normalizedRemoteImageUri` 现已将协议相对地址规范化为 HTTPS，并继续升级特定微信域名的 HTTP 地址。

## 待确认

- API 分页/详情响应是否存在 `data/result/payload` 嵌套头像对象；当前模型修复先覆盖已经收到的实体字段。
