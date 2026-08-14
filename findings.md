# 群邀请卡发现

## 用户要求
- 将右侧功能“群邀请卡”按 iOS 行为和接口实现到 Android。
- 承载形式为全屏悬浮窗：顶部 30dp 状态区，工具栏左侧返回按钮，不使用底部取消对话框。
- 所有 Android UI 使用 Material 3 风格。
- 处理 `BadTokenException` 等窗口附加和移除异常。

## 已定位资料
- iOS 群邀请创建器：`ios-float/ios-float/ChatWindowViewController.swift`。
- iOS 群邀请审批/接收与 OpenAPI 选取器：`ios-float/ios-float/Features/Groups/GroupsFeature.swift`、`Features/Chat/ChatWindow+CollectionDataSource.swift`、`Features/Chat/ChatWindow+AudioDelegate.swift`。
- 接口文档：`接口文档.md`、`接口文档.json`。
- Android SCRM 客户端与模型：`ubiki-accessibility/.../scrm/ScrmApiClient.kt`、`ScrmModels.kt`。

## 已知接口
- `GET /openapi/v1/group-invitations`
- `POST /openapi/v1/chatrooms/invites/pull`
- `POST /openapi/v1/chatrooms/invites/agree`
- `POST /openapi/v1/chatrooms/invites/approve`

## 并发边界
Android 浮窗、右侧工具、SCRM 客户端已有未提交修改，必须在读取最新内容后做最小增量编辑。

## 实现结果
- 群邀请右侧工具在既有浮窗根视图内显示为全屏 Material 3 页面。
- 页面使用 30dp 状态区、返回图标和刷新图标，不使用 Activity、Dialog 或底部取消按钮。
- 实际调用 get、pull、agree、approve 四个既有 SCRM 接口。
- 复用 TYPE_ACCESSIBILITY_OVERLAY 控制器，避免额外窗口附着导致 BadToken 风险。
