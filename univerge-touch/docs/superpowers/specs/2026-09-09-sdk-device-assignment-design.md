# SDK 设备登记与多设备模型分配设计

## 目标

打通“SDK 设备登记 -> 管理后台选择设备 -> 设备精确模型分配 -> SDK 拉取并激活”的链路，同时保持设备标识为 App 私有随机 ID，不上传 IMEI、序列号或 Android 硬件标识。

## 现状与约束

- SDK 已生成并持久化 App 私有 `deviceId`，宿主可读取。
- 后端已有设备登记、设备列表和单设备 assignment API。
- 管理后台当前一次只能选择一个设备。
- SDK 启动时登记设备，但不会自动刷新 assignment。
- 公网管理地址当前为 HTTP，SDK 必须通过显式配置允许明文传输，不能静默降低安全策略。
- SDK 保持单个已选模型、上一版本回滚和触摸期间延迟激活边界。

## 设计

### SDK

1. `AispectTouchConfig` 增加可选 `remoteModelDeviceName`。为空时使用 `Build.MANUFACTURER + Build.MODEL`，只作为后台展示信息。
2. 登记请求增加 `deviceName`，保留旧 Java 方法签名的兼容重载。
3. `start()` 的后台登记任务完成后执行一次 `refreshRemoteModel()`；网络失败只记录失败结果，不影响本地识别。
4. 增加 `remoteModelDeviceName()` 和 `lastRemoteModelUpdateResult()` 公共只读接口。
5. 设备号继续来自 SharedPreferences UUID；宿主显式配置的 `remoteModelDeviceId` 优先。

### 后端与管理端

1. 保留既有单设备 `/api/admin/v1/assignments/device`。
2. 增加事务型 `/api/admin/v1/assignments/device/batch`，输入同一模型和一组 `{appId, deviceId}`，一次提交全部设备精确规则；任何一个目标或模型非法时整体回滚。
3. 管理页设备精确范围改为多选，提交批量接口；平台/App 默认逻辑不变。
4. 设备列表继续要求管理员登录；SDK 公共登记接口不返回设备列表。

## 失败与回退

- 未配置 endpoint 或 App ID：不登记、不刷新，继续本地模型。
- HTTP 且未显式允许：保持拒绝并返回配置错误。
- 登记失败但 assignment 可访问：仍允许刷新 assignment。
- assignment 下载、合同校验或激活失败：保留当前模型并记录最后一次结果。
- 批量分配任一目标失败：事务回滚，前端显示失败，不产生部分生效规则。

## 验收标准

- SDK 单测证明登记 JSON 包含设备名且旧接口仍兼容。
- SDK 单测证明最后一次刷新结果可读取，正式测试不触发主线程网络。
- 后端单测证明批量设备分配一次生成多条规则，并在一个非法目标时全部回滚。
- 管理端构建通过，多选设备提交批量 API。
- Android SDK Release AAR 构建通过；主项目结构文档同步记录改动。
