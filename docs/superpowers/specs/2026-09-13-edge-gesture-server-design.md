# Edge Gesture Server Design

状态：已批准，进入实现。

目标工程：`C:\codeDev\zhifa\superAppAndroid`

参考实现：`C:\codeDev\zhifa\UbikiTouch_1.17.8_jadx`

## 1. 目标

把左右侧和底部手势收敛为一个独立、可恢复、低延迟的 Gesture Server 运行时，同时保留现有 SuperApp 的动作能力。目标行为：

- 左右两侧支持最多四段可配置热区，每段独立控制启用、位置、长度和厚度。
- 底部使用独立矮条，宽度和手势映射与侧边分离。
- 侧边支持短拉、长拉、斜上/斜下短长、停住、上滑和下滑。
- 移动过程中只产生候选和预览；只有合法 `UP` 才执行一次动作。
- 回缩、`CANCEL`、多指、失焦、服务销毁和窗口失去输入所有权都取消当前事务。
- 热区外和中央屏幕不创建可触摸的全屏窗口，普通触摸继续交给前台 App。
- 在 Android 支持范围内设置真实触发 View 的 `systemGestureExclusionRects`；同侧合并后的排除长度超过系统预算时，报告“尽力接管”，不宣称保证。
- 服务重启后恢复最后一次完整配置；配置改变不打断正在进行的手势。

## 2. 已确认的参考事实

UbikiTouch 的性能优势来自结构，而不是反编译语言：

1. 无障碍服务运行在独立 `:service` 进程。
2. 每个触发器是窄的 `TYPE_ACCESSIBILITY_OVERLAY` View，默认不绘制。
3. 菜单只在首次 `DOWN` 懒加载，后续复用已有 View。
4. MOVE 热路径主要更新字段和做简单几何判断，配置和菜单项使用缓存。
5. 底部只是独立 gravity 的触发器，不再叠加另一套输入路径。

JADX 导出树包含反编译缺陷，不能直接作为构建输入；SuperApp 继续使用自己的 Kotlin 模块和测试。

## 3. 方案选择

### 方案 A：继续在现有 AccessibilityService 进程内重构

优点：改动最少，Binder 不需要新增，迁移风险较低。

缺点：聊天、视频、Compose 和媒体任务仍与手势共享进程；服务启动会继续携带非必要初始化；无法达到参考实现的进程隔离。

### 方案 B：独立 `:gesture_server` 无障碍服务进程（采用）

新增一个只负责手势的服务进程。它持有配置快照、输入所有权、识别状态机、预览调度和动作执行适配器；主进程只负责设置页、配置持久化和业务 UI，通过版本化 Binder 快照同步。

优点：高内聚，手势生命周期与业务 UI 解耦；主进程 GC、媒体解码和 Compose 重组不会直接进入手势热路径；便于独立恢复和性能测量。

代价：需要 Binder 合同、服务重启恢复、跨进程动作请求和生命周期处理；不能把主进程的 Activity、Compose controller 或 `View` 引用传入 Server。

### 方案 C：只保留 Native 接管，Overlay 作为全局回退

优点：窗口数量少，系统导航区域内可以减少 Overlay。

缺点：Android 版本和 OEM 差异大；Native 不可用或不覆盖的设备无法稳定提供完整热区；若同时保留 Overlay，仍会产生双输入所有权问题。

方案 B 是主架构，方案 C 只是 Server 内部的输入后端选择。Native 成功接管某一物理区域时，不创建该区域的可触摸 Overlay；Native 失败时，才创建窄 Overlay。底部同样遵守这一规则。

## 4. 分层与职责

```text
主进程
  Settings UI / Preferences
        | versioned Binder ConfigSnapshot
        v
:gesture_server
  GestureAccessibilityService
    -> GestureServerRuntime
       -> InputBackendSelector
          -> NativeInputBackend OR OverlayInputBackend
       -> HotZoneGeometry
       -> SideGestureRecognizer
       -> BottomGestureRecognizer
       -> PreviewScheduler
       -> GestureActionExecutor
       -> RecoveryStore
```

### 4.1 `univerge-core`

纯 Kotlin 领域合同，不依赖 Android：

- `HotZoneConfig`、`HotZoneGeometry`、`ExclusionReport`。
- 不可变 `ConfigSnapshot`，包含版本号、左右区域、底部区域、手势阈值和动作映射。
- `SideGestureRecognizer` 和 `BottomGestureRecognizer` 状态机。
- `GestureAction` 纯数据对象；不持有 `AccessibilityService` 或 `Context`。
- `GesturePreview` 和 `GestureCommit` 领域事件。

核心层只输出事件，不创建窗口、不读取 SharedPreferences、不查询 PackageManager、不执行系统动作。

### 4.2 `univerge-overlay`

只负责窄触摸 View 的 Android 适配：

- 将 `MotionEvent` 转为领域输入。
- 真实触发 View 设置排除矩形。
- 只维护一个输入回调和一个状态机引用。
- 预览通过可复用 View 或调度器绘制，不在每个 MOVE 增删窗口。

### 4.3 `univerge-accessibility`

只负责无障碍服务宿主和平台适配：

- `TouchInteractionController` Native 后端。
- `WindowManager` Overlay 后端。
- Binder 服务端和服务恢复。
- 将 `GestureAction` 适配为 `performGlobalAction`、启动 App、截图、锁屏、音量等平台调用。

聊天、视频、Compose UI 和业务 controller 不属于 Server 的启动依赖；它们通过无状态动作回调或 Binder 命令参与。

## 5. 配置快照与一致性

配置通过版本化不可变快照传递：

```kotlin
data class ConfigSnapshot(
    val version: Long,
    val leftZones: List<HotZoneConfig>,
    val rightZones: List<HotZoneConfig>,
    val bottomBar: BottomBarConfig,
    val actions: Map<GestureBinding, GestureAction>
)
```

规则：

1. 主进程持久化成功后再发送快照。
2. Server 只接受版本号更高且结构校验通过的快照。
3. `DOWN` 时捕获当前快照引用；本次手势直到 `UP/CANCEL` 都使用该引用。
4. 快照更新只更新空闲输入后端；活动手势结束后再切换几何和动作表。
5. Server 重启先从本地恢复存储读取快照，再等待主进程推送新版本。

## 6. 输入所有权

输入所有权是硬不变量：

- 一个物理区域只有一个输入所有者。
- Native 后端成功注册并覆盖某区域时，不创建该区域 Overlay。
- Native 注册失败或能力不足时，仅为未覆盖区域创建 Overlay。
- 底部 Native 和底部 Overlay 不能同时处理同一条底部区域。
- 不创建可触摸的全屏排除辅助 View；中央区域必须没有 Server 输入所有者。
- 预览窗口可以复用，但必须是不可触摸的；它不能改变输入路由。

输入后端应提供调试快照，列出每个区域的 owner、窗口 token、覆盖矩形和降级原因，便于发现重叠。

## 7. 侧边状态机

```text
IDLE
  -> PRESSED on DOWN inside one enabled zone
PRESSED
  -> TRACKING on first valid MOVE
  -> CANCELLED on multi-pointer, focus loss or invalid ownership
TRACKING
  -> TRACKING on MOVE; emit preview only
  -> COMMITTED on UP when final gesture is valid
  -> CANCELLED on inward rollback, outward escape, CANCEL or invalid ownership
COMMITTED/CANCELLED
  -> IDLE after exactly one terminal callback
```

判定规则：

- 方向使用平方距离和固定阈值，MOVE 不执行开方或重复命中布局。
- 斜上/斜下先判断内收方向，再按斜率区分；短长只由距离阈值决定。
- 停住/长按只改变候选状态，定时器不得直接执行动作。
- `UP` 时重新验证当前轨迹仍在同一个有效手势，动作表从 DOWN 快照读取。
- 每个会话拥有 `gestureId`、`activePointerId`、`zoneId` 和 `snapshotVersion`。

## 8. 底部状态机

底部使用独立的 `BottomBarConfig` 和识别器，支持上滑、上滑停住、水平滑和长按等底栏手势。底栏输入矩形只覆盖配置宽度和矮条高度；中央其余区域继续归前台 App。

底部同样遵守：

- `DOWN` 建立事务但不执行。
- MOVE 只更新候选预览。
- `UP` 执行最多一次。
- `CANCEL`、多指、失焦和回缩取消。
- Native 与 Overlay 不能重叠处理。

## 9. 排除矩形和 200dp 能力报告

对每侧启用区域先转换为屏幕像素，再按垂直区间排序并合并重叠/相邻区间。报告至少包含：

- 合并后的区间列表。
- 合并总长度（以 dp 和 px 表示）。
- 是否在平台预算内。
- 当前输入后端：`DETERMINISTIC_EXCLUSION`、`BEST_EFFORT_NATIVE` 或 `OVERLAY_FALLBACK`。
- 超限原因和用户可见状态。

排除矩形的策略：

- 预算内：在真实窄触摸 View 上设置 `systemGestureExclusionRects`。
- 超预算、无 Root：保留可用 Native/Overlay 路径并标记尽力接管；不能伪造多个独立 `200dp` 配额。
- 有 Root：通过独立、可恢复的 Root adapter 修改系统参数；保存原值、记录写入版本，停止服务或失败时恢复。
- OEM 拒绝或系统状态变化：降级到系统导航优先，保持中央触摸不受影响。

## 10. 性能合同

MOVE 热路径禁止：

- `SharedPreferences`、磁盘、网络、PackageManager 查询。
- 每次 MOVE 创建 `GestureData`、列表、布局框或 `copy` 对象。
- 每次 MOVE `mainHandler.post` 一次再处理。
- 每次 MOVE 增删 Window 或创建动画 Surface。

允许：

- 更新固定的 primitive 会话字段。
- 使用预计算的命中几何和平方距离。
- 把最新预览状态交给单一 `Choreographer`/`postOnAnimation` 消费。
- 在 DOWN 预加载动作标签、图标和候选布局。

## 11. 错误与恢复

- Binder 断开：Server 继续使用最后有效快照；主进程恢复后重新推送。
- 快照版本倒退或校验失败：拒绝并记录原因，不影响当前活动手势。
- Native 回调注册失败：释放 Native 资源，再为对应区域创建 Overlay；禁止两者同时存在。
- Overlay 添加失败：保留系统导航和前台 App 输入，报告该区域不可用。
- 动作执行失败：记录一次带 `gestureId` 的失败结果，不自动重复执行。
- 服务被杀：重启后恢复配置和能力报告；活动手势不恢复、不补执行。
- Root 写入失败或进程异常：立即尝试恢复原值，并把系统参数状态标记为未知，等待下一次显式校验。

## 12. 测试策略

### 纯 JVM 单元测试

- 多段区间归一化、排序、合并和 200dp 报告。
- 左右内收、外滑、上下滑、斜上/斜下短长判定。
- DOWN/MOVE/UP、回缩、CANCEL、多指、失焦、重复 UP 的状态机。
- MOVE 只产生预览，UP 才产生唯一提交。
- 快照更新在活动手势结束前不生效。
- 底部独立手势集合和宽度命中。

### Android 单元/适配测试

- Native 成功时 Overlay 不创建；Native 失败时只创建缺失区域。
- 同一矩形没有两个 owner。
- 中央触摸不被 Server 可触摸窗口覆盖。
- `systemGestureExclusionRects` 只设置在真实触发 View。
- Binder 断开、服务重启和 Root 恢复。

### 真机验收

固定设备、系统、密度、构建类型和配置，记录：

- `DOWN→首个预览`、`UP→动作提交` 延迟。
- MOVE 每事件分配数和主线程排队数。
- 漏触、误触、重复动作比例。
- 中央点击穿透率。
- 服务 CPU、内存和窗口数量。
- 系统导航与尽力接管状态。

## 13. 非目标

- 不尝试绕过 Android 或 OEM 的系统安全边界。
- 不承诺无 Root 超过每侧系统排除预算后的确定性接管。
- 不把聊天、视频、相机、模型推理迁入手势服务。
- 不直接复制 JADX 反编译源码或其缺失实现。
- 不在本轮重写所有历史业务 UI。
