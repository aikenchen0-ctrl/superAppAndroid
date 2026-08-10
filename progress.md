# 悬浮聊天接口完善进度

## 2026-08-10

- 已启动头像优先专项：范围限定为 Android 悬浮聊天。
- 已读取 `完成进度.md` 与 `任务计划.md`；发现完成度文档存在底部栏状态滞后。
- 已记录路径错误：`IOS与安卓进度对照.md` 不存在，待定位实际文件名。
- 已定位实际功能对照文档：`功能对照.md`。
- 已确认 iOS 使用宽容头像字段提取；Android UI 加载已存在，当前调查聚焦 Android 接口模型的头像字段解析能力。
- 根因确认：Android 远程实体只解析 `avatar`，会忽略后端常用 `avatarUrl/headImgUrl/headimgurl/imageUrl`；协议相对头像 URL 也未被规范化。
- 已完成接口实体别名解析、悬浮聊天映射和联系人面板读取统一头像字段；` :ubiki-accessibility:test` 通过。
- 真机反馈：头像仍未显示。已新增 `UbikiAvatar` 诊断日志，覆盖接口实体、界面映射和远程图片下载边界；待收集一次真实复现日志后确定根因。
- Debug APK 已构建：`app/build/outputs/apk/debug/app-debug.apk`。
- 已更新本地 Debug 配置中的 SCRM 自动引导密码；新的诊断 APK 已构建，等待真机重新验证认证及头像链路。
- 当前暂停点：用户安装新 APK 并复现后，依据 `UbikiAvatar` 日志确认头像数据是否进入接口实体、界面映射和图片下载阶段。

## 下一步接口规划

- 已确认下一项不是发送消息，而是补齐 iOS 已有的只读会话同步：`GET /chat/bootstrap` -> `GET /chat/history` -> `GET /messages/changes`。
- Android 当前 `ScrmReadApi` 尚无上述契约；在头像真机验收通过前不实现，避免同时改变数据源和 UI 显示链路。
- 发送文本、图片、语音等接口只保留人工测试步骤，不由本轮自动化验证触发。
- 已完成头像 fallback：主悬浮聊天头像优先显示远程图片，否则显示昵称首字，空昵称显示 `?`；单元测试已通过。
- 消息显示排查：`scrmFloatingChatConversation` 当前明确将 `messages` 设为空列表，Android 尚未接入 iOS 对应的 `GET /chat/bootstrap`、`GET /chat/history`、`GET /messages/changes`；因此真实用户消息暂时不会进入悬浮聊天，未使用 mock 或假消息修饰该状态。
- 本轮 UI/缓存：底栏背景改为全屏宽度、控件贴容器顶部，内容左右 60dp 留白；头像缓存改为异步持久化到应用专属 `Android/data/<包名>/files/floating-chat-avatars`，模块契约测试通过。
