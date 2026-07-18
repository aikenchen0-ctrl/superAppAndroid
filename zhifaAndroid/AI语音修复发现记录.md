# AI 语音修复发现记录

- 用户提供的 `162929` 页面是产品动态，不是接口协议。
- 新版鉴权文档 `1816214` 说明使用 `x-api-key`，无需 App ID。
- TTS 已通过真实 HTTP 200 验证。
- 当前实时客户端仍请求旧自有网关并解析文本事件，不符合豆包二进制 WebSocket 协议。
- 当前声音复刻按钮只改变本地状态，没有录音文件和上传请求。
- 当前声音智能体页面是占位说明。
- 当前能力验证只真实探测 TTS，其余保留 `NotChecked`。
- 用户提供的 APP ID 与 Access Token 已通过真实 WebSocket 探测，收到官方二进制 `ConnectionStarted(50)` 事件。
- 实时语音固定资源 ID 为 `volc.speech.dialog`，固定 App Key 为 `PlgvMymc7f3tQnJ6`。
