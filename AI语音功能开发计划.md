# AI语音功能开发计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在悬浮聊天功能栏提供 AI语音入口、五项已确认的语音工作流和无密钥的 API 配置边界。

**Architecture:** 新功能置于 `floatingchat/aivoice`，UI 仅派发事件并渲染状态；协调器依赖可替换的 API 网关。现有 `FloatingChatOverlayUi.kt` 只负责将 `MoreToolPanel` 的入口映射到 `BottomPanelMode.AiVoice` 并展示新面板。

**Tech Stack:** Kotlin、Jetpack Compose、kotlinx.serialization、OkHttp、JUnit 4、Robolectric。

---

### Task 1: 建立 AI语音合同与状态机

**Files:**
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceContract.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceContractTest.kt`

- [ ] 写入失败测试，断言五项功能、克隆音色缺失时的创建流程和实时通话状态可表达。
- [ ] 运行 `./gradlew.bat :ubiki-accessibility:testDebugUnitTest --tests '*AiVoiceContractTest' --no-daemon`，确认因类型不存在失败。
- [ ] 创建密封事件、状态、效果和功能枚举。
- [ ] 重新运行同一测试，确认通过。

### Task 2: 建立 API 配置与网关边界

**Files:**
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceApi.kt`
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceApiConfig.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceApiConfigTest.kt`

- [ ] 写入失败测试，断言缺少服务地址或凭证时返回明确配置错误。
- [ ] 运行目标测试，确认失败。
- [ ] 实现 API 接口和配置校验；禁止默认密钥、模拟响应和静默降级。
- [ ] 重新运行目标测试，确认通过。

### Task 3: 实现状态协调器与生成工作流

**Files:**
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceCoordinator.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceCoordinatorTest.kt`

- [ ] 写入失败测试，断言功能选择、音色创建入口、生成前配置检查和克隆通话失败状态。
- [ ] 运行目标测试，确认失败。
- [ ] 实现最小协调器和依赖注入接口。
- [ ] 重新运行目标测试，确认通过。

### Task 4: 实现统一样式与 Compose 面板

**Files:**
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceTokens.kt`
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoicePanel.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceTokensTest.kt`

- [ ] 写入失败测试，断言五项功能使用同一组间距和文字样式 Token。
- [ ] 运行目标测试，确认失败。
- [ ] 实现菜单、生成器、音色创建、实时通话和智能体面板。
- [ ] 重新运行目标测试，确认通过。

### Task 5: 接入现有功能栏

**Files:**
- Modify: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatAiVoiceEntryTest.kt`

- [ ] 写入失败测试，断言功能栏存在 AI语音动作且映射到 AI语音面板模式。
- [ ] 运行目标测试，确认失败。
- [ ] 以最小修改增加 `BottomPanelMode.AiVoice`、按钮事件和面板分支。
- [ ] 重新运行目标测试，确认通过。

### Task 6: 全量验证

**Files:**
- Modify: `D:/xuanfu_APP/AI语音功能设计.md`

- [ ] 在设计文档中列出所需环境变量和未配置时的行为。
- [ ] 运行 `./gradlew.bat :ubiki-accessibility:testDebugUnitTest lintDebug assembleDebug --no-daemon`。
- [ ] 记录实际通过或失败输出；不提交其他终端产生的文件。

### Task 7: 追加 Ark Key 模型列表与模型选择入口

**Files:**
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceModelConfig.kt`
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceArkModelsApi.kt`
- Modify: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoicePanel.kt`
- Modify: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceModelConfigTest.kt`
- Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceArkModelsApiTest.kt`
- Modify Test: `ubiki-accessibility/src/test/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoicePanelContractTest.kt`

- [x] 实测用户临时提供的 Ark Key 可调用 `GET /api/v3/models` 并返回 126 个可见模型。
- [x] 确认该列表未证明 TTS、声音复刻、端到端实时语音全部可用，具体调用仍受模型 ID 和账号权限限制。
- [x] 写入失败测试，覆盖模型能力匹配、缺口提示、模型列表 API 和面板入口。
- [x] 运行目标测试，确认新类型缺失导致失败。
- [x] 实现 Ark 模型列表客户端、面板 API Key 输入、获取模型按钮和每类语音模型选择。
- [x] 扫描确认用户临时 Ark Key 未写入项目或文档文件。
- [x] 运行 AI语音定向测试和可行构建验证。
