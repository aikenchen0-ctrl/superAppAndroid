# AA收款实现计划

## 当前状态与目标

- 当前右侧目录已有“AA收款”，但没有点击路由；旧版 `SplitBillPanel` 是底部面板，且不会写入消息。
- 目标是在既有悬浮根视图内实现全屏 Material 3 工作区，并严格对齐 iOS 的群聊本地消息行为。

## 实现步骤

- [x] 复核 iOS 页面、消息字段与接口边界。
- [x] 先写契约测试并确认失败。
- [x] 新增全屏 AA 收款工作区，使用 30dp 状态区、TopAppBar、PrimaryTabRow、HorizontalPager 和 LazyColumn。
- [x] 接入右侧目录与 Overlay，避免创建 Dialog、Activity 或额外 Window。
- [x] 提交时仅在群聊插入本地 `FloatingChatMessageType.SplitBill` 消息，不虚构远端接口。
- [x] 执行定向测试、模块编译、应用构建和差异检查。

## iOS 对齐字段

- 正文：`群收款 {人数} 人`
- 详情：总金额、收款群、已收人数、收款成员、收款成员 ID、未支付成员。
- 空金额默认 `320.00`。
- 私聊不可发起；至少选择一名群成员。

## 验证记录

- AA 定向契约、消息生成、成员过滤和支付卡解析测试通过。
- `:app:assembleDebug` 构建通过。
- 模块全量测试共 881 项，862 项通过；19 项为并行开发中的既有契约失败，与 AA 定向测试无关。
- `git diff --check` 通过，仅输出工作树既有的 CRLF 转换提示。
