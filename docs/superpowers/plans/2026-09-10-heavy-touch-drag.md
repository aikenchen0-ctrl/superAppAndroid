# Heavy Touch Drag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让任意已声明的 Compose 或 View 组件获得“轻触点击、重触拖拽”能力，并支持显式拖放区和 UI 重叠事件。

**Architecture:** 触摸分类 SDK 继续作为独立 AAR，只输出带 `gestureId` 的分类结果。新增纯 Kotlin 核心模块管理手势状态和几何命中，Android/Compose 模块只负责事件、坐标与声明式 UI 适配，业务层只接收回调。

**Tech Stack:** Kotlin 2.0.21、Android View、Jetpack Compose、JUnit 4、Aispect Touch Android AAR。

---

### Task 1: 纯 Kotlin 手势状态机与目标命中

**Files:**
- Create: `univerge-heavy-drag-core/build.gradle.kts`
- Create: `univerge-heavy-drag-core/src/main/kotlin/com/paifa/univerge/heavydrag/core/HeavyDragGeometry.kt`
- Create: `univerge-heavy-drag-core/src/main/kotlin/com/paifa/univerge/heavydrag/core/HeavyDragContracts.kt`
- Create: `univerge-heavy-drag-core/src/main/kotlin/com/paifa/univerge/heavydrag/core/HeavyDragCoordinator.kt`
- Create: `univerge-heavy-drag-core/src/test/kotlin/com/paifa/univerge/heavydrag/core/HeavyDragCoordinatorTest.kt`
- Modify: `settings.gradle.kts`

- [x] 写失败测试：轻触只 click、重触才 drag、旧 `gestureId` 忽略、多指和 cancel 终止。
- [x] 写失败测试：Drop Zone 的 enter/over/exit/drop 顺序、Overlap 的阈值和滞后规则。
- [x] 运行 `:univerge-heavy-drag-core:test`，确认因类型尚不存在而失败。
- [x] 实现不可变几何、源/目标合同、注册表和单会话状态机。
- [x] 再次运行测试，确认全部通过。

### Task 2: Android 根事件运行时与 View 适配器

**Files:**
- Create: `univerge-heavy-drag-android/build.gradle.kts`
- Create: `univerge-heavy-drag-android/src/main/AndroidManifest.xml`
- Create: `univerge-heavy-drag-android/src/main/kotlin/com/paifa/univerge/heavydrag/android/HeavyTouchClassifierGateway.kt`
- Create: `univerge-heavy-drag-android/src/main/kotlin/com/paifa/univerge/heavydrag/android/HeavyDragRuntime.kt`
- Create: `univerge-heavy-drag-android/src/main/kotlin/com/paifa/univerge/heavydrag/android/HeavyDragHostView.kt`
- Create: `univerge-heavy-drag-android/src/test/kotlin/com/paifa/univerge/heavydrag/android/HeavyDragRuntimeTest.kt`
- Modify: `settings.gradle.kts`

- [x] 写失败测试：每笔 DOWN 只建立一个会话，未命中源不转发分类，UP/CANCEL 正确结束。
- [x] 定义与具体模型 SDK 无关的 classifier gateway。
- [x] 实现根级事件运行时及传统 View 容器适配器。
- [x] 运行 `:univerge-heavy-drag-android:testDebugUnitTest`。

### Task 3: Compose 声明式适配器

**Files:**
- Create: `univerge-heavy-drag-compose/build.gradle.kts`
- Create: `univerge-heavy-drag-compose/src/main/AndroidManifest.xml`
- Create: `univerge-heavy-drag-compose/src/main/kotlin/com/paifa/univerge/heavydrag/compose/HeavyDragHost.kt`
- Create: `univerge-heavy-drag-compose/src/main/kotlin/com/paifa/univerge/heavydrag/compose/HeavyDragModifiers.kt`
- Create: `univerge-heavy-drag-compose/src/main/kotlin/com/paifa/univerge/heavydrag/compose/HeavyDragState.kt`
- Modify: `settings.gradle.kts`

- [x] 建立 `HeavyDragHost` 的唯一事件入口。
- [x] 实现 `heavyDraggable`、`heavyDropTarget`、`heavyOverlapTarget`。
- [x] 使用 `rememberUpdatedState` 保证回调不陈旧，使用 `DisposableEffect` 注册/注销。
- [x] 使用 `boundsInRoot()` 持续更新同 Root 几何边界。
- [x] 编译模块，确认不依赖 Aispect SDK 类。

### Task 4: SDK `gestureId` 与宿主适配器

**Files:**
- Modify: `C:/codeDev/cnn/sdk/aispect-touch-android/src/main/java/com/zhifaios/eyes/touch/AispectTouchResult.java`
- Modify: `C:/codeDev/cnn/sdk/aispect-touch-android/src/main/java/com/zhifaios/eyes/touch/AispectTouchError.java`
- Modify: `C:/codeDev/cnn/sdk/aispect-touch-android/src/main/java/com/zhifaios/eyes/touch/AispectTouchClassifier.java`
- Modify: `C:/codeDev/cnn/sdk/aispect-touch-android/src/main/java/com/zhifaios/eyes/touch/AispectTouchResultMapper.java`
- Modify: `C:/codeDev/cnn/sdk/aispect-touch-android/build.gradle`
- Create: `app/src/main/java/com/paifa/univerge/app/touch/AispectHeavyTouchGateway.kt`
- Modify: `app/build.gradle.kts`

- [x] 写 SDK 失败测试，证明结果和错误必须携带当前手势 ID，旧构造路径仍兼容。
- [x] 为 `handleMotionEvent` 增加可选宿主手势 ID，不引入 UI 业务概念。
- [x] 构建新版独立 AAR，并以新版本名复制到宿主 `app/libs`。
- [x] 实现宿主 gateway，将四分类映射为通用 light/heavy 分类结果。
- [x] 验证交互模块没有反向依赖 SDK。

### Task 5: 测试页迁移与端到端验证

**Files:**
- Modify: `app/src/main/java/com/paifa/univerge/app/TouchTestActivity.kt`
- Create: `app/src/test/java/com/paifa/univerge/app/HeavyTouchDragContractTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `docs/heavy-touch-drag-design.md`
- Modify: `C:/Users/血饮/Desktop/待完成任务/original/touch_eyes_dataAnalysis/FunctionAndDataStructureDescription.md`

- [x] 用 `HeavyDragHost` 替换页面级布尔状态。
- [x] 增加一个可拖动源、一个拖放区和一个重叠目标演示。
- [x] 轻触只产生点击视觉反馈，重触后源组件才随 MOVE 移动。
- [x] 验证 Drop Zone 与 Overlap 的回调结果在 UI 中可见。
- [x] 运行核心、Android、App 测试和 JDK 17 完整 APK 构建（核心/Android/Compose 与重触定向测试通过；App 全量 41 项中 38 项通过，3 项为既有悬浮聊天合同漂移；APK 构建通过）。
- [x] 核对 APK 使用独立 AAR，不再包含 `:univerge-touch` 源码依赖；宿主仅声明 `app/libs/aispect-touch-android-1.2.0.aar`。

### 验收门

- [ ] 未声明组件保持原生行为。
- [ ] 声明组件轻触只 click，绝不 drag。
- [x] 当前 `gestureId` 的 heavy 结果才能启动拖动（核心与 Android 定向测试覆盖）。
- [x] Drop Zone 和 OverlapTarget 事件互不混淆（核心测试覆盖）。
- [x] Compose、View、Overlay 可共享同一核心语义（核心合同、Compose 适配器和 View Host 已实现；Overlay 业务接入待后续）。
- [x] SDK、交互层和业务层不存在反向依赖（Aispect 仅在 App bridge 中出现）。
- [ ] 所有自动化验证及完整 APK 构建通过（APK 构建通过；App 全量测试仍有 3 个既有合同漂移失败，真机验收待 ADB 授权）。

> 当前验证记录：`app:assembleDebug` 使用 JDK 17 构建成功；`app:testDebugUnitTest` 的 3 个失败项是既有 `Ubiki*` 命名和 MainActivity 文本合同漂移，不涉及本计划新增模块。真机验收仍需设备恢复为 ADB `device` 状态后执行。
