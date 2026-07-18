# AI语音功能设计

## 1. 已确认范围

AI语音仅作为当前悬浮聊天页功能栏中的一个入口，不新增主 App 页面。

入口位置：

```text
聊天页 -> 功能栏 MoreToolPanel -> AI语音
```

保留现有输入栏的普通语音录制能力。该入口继续负责用户原声语音消息，不与 AI 语音入口重复。

## 2. AI语音入口

在现有四列功能栏中新增：

```kotlin
PanelTool("AI", "AI语音")
```

点击后进入 `AiVoicePanel`，提供五项能力：

1. 实时对话：用户和 AI 进行全双工实时语音对话。
2. 克隆语音：将输入文字生成用户自己的克隆声音，并发送给当前聊天对象。
3. 克隆音色通话：用户和 AI 实时通话，AI 使用用户克隆音色回应。
4. 声音智能体：创建、选择和配置使用声音的 AI 智能体。
5. AI语音消息：将输入文字生成普通 AI 音色的语音，并发送给当前聊天对象。

功能按上述顺序排列。功能名称面向用户动作，不展示模型或供应商名称。

## 3. 交互设计

### 3.1 普通语音消息

保留现有 `Voice` 输入图标及 `RealVoiceInputPanel`：

```text
录音 -> 停止 -> 上传 -> 发送语音消息
```

继续使用 Android 原生录音与当前 SCRM 上传、发送链路。

### 3.2 实时对话与克隆音色通话

两类通话复用同一通话界面和状态机，只在模型会话配置的音色来源上不同。

```text
连接中 -> 正在聆听 -> 用户说话 -> AI回应 -> 用户打断 -> 正在聆听
```

通话页必须显示明确状态文字，不能只靠波形或颜色表达。提供麦克风开关、结束通话、字幕开关。连接失败时显示原始可操作错误，并提供“重新连接”和“结束”。

克隆音色不可用、过期或绑定失败时，必须停止并提示用户处理，不允许静默改用普通 AI 音色。

### 3.3 首次创建克隆音色

当用户选择“克隆语音”或“克隆音色通话”，但没有可用音色时，在当前悬浮面板内完成：

```text
说明与授权 -> 录制声音样本 -> 上传与创建 -> 创建成功 -> 回到原始功能
```

创建过程保持同一面板尺寸。失败停留在当前步骤，展示真实原因并允许重试。创建成功后保存服务端返回的音色标识。

### 3.4 语音消息生成

“克隆语音”和“AI语音消息”共用一个生成器：

```text
选择音色类型 -> 输入文本 -> 生成 -> 试听 -> 发送给当前聊天对象
```

生成结果必须在发送前试听。切换音色或修改文本后，之前的生成结果失效。发送对象固定为当前聊天对象，避免误发。

### 3.5 声音智能体

声音智能体使用列表入口和分步创建：

```text
名称 -> 角色说明 -> 知识库 -> 音色 -> 好友权限 -> 保存
```

默认不授予好友访问权限，必须由用户显式选择授权范围。

## 4. 模型与接入策略

### 4.1 指定策略

所有云端 AI 语音能力统一使用 API 接入，不采用云厂商 Android SDK。

```text
Android -> 自有后端 -> 云端语音 API
```

Android 负责录音、播放、音频焦点、音频路由、WebSocket 客户端、通话 UI 和状态展示。后端负责云厂商鉴权、密钥保管、会话参数、模型调用、音色资产和供应商适配。

严禁在 APK 内写入云厂商长期 API Key、Access Token、App Key 或密码。

### 4.2 首选模型

| 需求 | 首选能力 | 接入方式 |
| --- | --- | --- |
| 非克隆全双工 AI 对话 | 豆包端到端实时语音 O2.0 | WebSocket API |
| 克隆语音消息 | 豆包声音复刻 V3 + 豆包 TTS | 后端 API |
| 克隆音色智能体 | 豆包端到端实时语音 SC2.0 或对话 API + 克隆 TTS | 后端 API |
| 克隆音色全双工通话 | 豆包端到端实时语音 O2.0 | WebSocket API |
| 用户普通语音消息 | Android 原生录音 | 本地录音 + 当前上传链路 |
| AI 普通语音消息 | 豆包 TTS | 后端 API |

### 4.3 已确认的官方 API 能力

豆包端到端实时语音 API 支持 WebSocket、双向流式语音、低延迟语音到语音对话、会话续接、客户端打断和 TTS 发音人配置。客户端输入支持 16kHz 单声道 PCM，也支持 Opus；输出默认是 OGG/Opus，也可以配置 PCM。

豆包端到端模型 O2.0 和 SC2.0 支持“克隆音色2.0”，音色名称格式为 `saturn_` 或 `S_` 前缀。O2.0 适合通用实时对话，SC2.0 更适合角色表达和声音智能体。

### 4.4 必须验证的技术门槛

声音复刻 V3 创建返回的音色标识，是否能直接作为端到端实时语音 API 的 `saturn_` 或 `S_` 音色配置，尚未在当前文档证据中闭环。

在实现“克隆音色通话”前，必须完成最小验证：

1. 创建一条复刻 V3 音色。
2. 记录返回的音色标识。
3. 用该标识发起端到端实时语音 WebSocket 会话。
4. 验证服务端接受、输出音色正确、用户打断后通话可继续。

验证失败时应明确暴露失败原因，不允许静默改为普通音色。

### 4.5 Ark 模型列表入口

AI语音面板顶部增加“模型配置”区域，用于开发和联调时手动填写火山方舟 Ark API Key 并拉取账号可见模型。

前端行为：

1. Key 只保存在当前面板状态中，不写入源码、BuildConfig、提交文件或普通日志。
2. 点击“获取模型”后调用 `GET https://ark.cn-beijing.volces.com/api/v3/models`。
3. 返回模型按语音功能类型筛选候选项：
   - 实时对话、克隆音色通话：需要实时音频或 Speech-to-Speech 能力。
   - 克隆语音、AI语音消息：需要 TTS 或 Text-to-Speech 能力。
   - 声音智能体：可使用 LLM 或 TextGeneration 模型作为对话/角色模型。
4. 某类语音功能没有匹配模型时，前端显示“未匹配”缺口，不允许把不可用模型伪装为可用。

本次实测使用用户临时提供的 Ark Key 调用模型列表接口，接口返回 200，能获取 126 个可见模型；其中多数为 LLM/VLM/Embedding/图像/视频类模型，未在返回列表中发现可直接用于 TTS、声音复刻或端到端实时语音的专用模型。另一次用具体聊天模型 ID 调用 Chat Completions 返回 404，说明“能列出模型”不等于“全部模型都可直接调用”，最终调用仍受模型 ID、开通资源和账号权限限制。

## 5. 前端状态、事件与分层

新增独立包，避免继续向 `FloatingChatOverlayUi.kt` 堆叠业务逻辑：

```text
floatingchat/aivoice/
├── contract/
│   ├── AiVoiceEvent.kt
│   ├── AiVoiceState.kt
│   └── AiVoiceEffect.kt
├── presentation/
│   └── AiVoiceCoordinator.kt
├── ui/
│   ├── AiVoicePanel.kt
│   ├── VoiceClonePanel.kt
│   ├── VoiceMessageGenerator.kt
│   ├── RealtimeCallPanel.kt
│   └── VoiceAgentPanel.kt
├── domain/
│   ├── VoiceProfile.kt
│   ├── VoiceAgent.kt
│   └── VoiceUseCases.kt
├── data/
│   ├── VoiceApiClient.kt
│   └── VoiceRepository.kt
└── design/
    └── AiVoiceTokens.kt
```

界面只渲染状态和派发事件：

```text
Composable -> AiVoiceEvent -> AiVoiceCoordinator -> UseCase -> Repository/API
                                                               -> AiVoiceState -> Composable
```

新增：

```text
BottomPanelMode.AiVoice
MoreToolPanel 中的 AI语音入口
```

不修改现有普通语音输入的入口语义。

## 6. 样式约束

AI语音 UI 样式集中在 `AiVoiceTokens`，并继承现有 `OverlayTokens` 的颜色体系。组件内禁止散落字体、间距、圆角和颜色硬编码。

```kotlin
object AiVoiceTokens {
    val panelSpacing = 12.dp
    val itemSpacing = 10.dp
    val panelTitle = TextStyle(...)
    val itemTitle = TextStyle(...)
    val itemDescription = TextStyle(...)
}
```

验收：修改 `AiVoiceTokens` 中的字体或间距后，所有 AI语音继承组件同步生效。

## 7. 验收标准

1. 聊天功能栏存在“AI语音”按钮，点击后显示五项已确认能力。
2. 现有普通语音录制与发送功能不发生回归。
3. 实时对话支持连接、收音、AI 流式播放、用户打断、重连和结束状态。
4. 克隆音色创建全程在当前悬浮面板完成，成功后返回原始任务。
5. 克隆语音和 AI语音消息支持生成、试听和发送。
6. 声音智能体具备列表、创建、音色选择和访问权限配置。
7. 所有云端请求经自有后端转发或签发会话参数，客户端不保存长期云端密钥。
8. 所有 AI语音组件的字体、间距和颜色通过统一 Token 管理。
9. 克隆实时通话在未通过音色兼容验证前不得标记为已可用。

## 8. 实施顺序

1. 创建 AI语音入口、状态合同和空面板。
2. 接入普通 AI 实时对话 WebSocket API。
3. 接入声音复刻、TTS、生成与试听发送。
4. 完成复刻音色与实时 API 的兼容验证。
5. 接入克隆音色通话。
6. 接入声音智能体和权限配置。
7. 完成异常、断线、权限、音频焦点和回归测试。

## 9. 本地配置与待提供凭证

Android 调试包只读取网关地址与短期会话令牌；这两个值不进入源码。将下列项放入未提交的 `local.properties`，或以同名环境变量提供：

```properties
ai.voice.gatewayBaseUrl=https://your-gateway.example.com
ai.voice.sessionToken=short-lived-session-token
```

对应环境变量：

```text
AI_VOICE_GATEWAY_BASE_URL
AI_VOICE_SESSION_TOKEN
```

自有后端再保存云厂商凭证，Android 不保存它们。后端需要的火山引擎配置如下：

```text
VOLCENGINE_APP_ID
VOLCENGINE_API_KEY 或 VOLCENGINE_APP_KEY
VOLCENGINE_ACCESS_TOKEN
VOLCENGINE_REALTIME_RESOURCE_ID_O2
VOLCENGINE_REALTIME_RESOURCE_ID_SC2
VOLCENGINE_VOICE_CLONE_RESOURCE_ID
VOLCENGINE_TTS_RESOURCE_ID
```

其中资源 ID 的最终值必须以对应账号控制台开通的模型与计费资源为准。声音复刻 V3 返回的音色标识与端到端实时音色配置的兼容验证仍是“克隆音色通话”上线门槛。
