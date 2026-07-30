# HyperFocusApi 真实课表数据集成设计

## 目标

在 mikcb 的 Live Updates 引擎中，当引擎切换为「小米超级岛（HyperFocusApi）」时，使用 `FocusNotifyApi + Template` 构建 MIUI Focus 通知，将真实课表数据送入超级岛，替代现有手动拼 JSON 的 `buildMiuiFocusParam()` 路径。

## 前置

- 已完成：`SuperIslandEngine` 枚举、引擎切换 UI、`FocusApi.sendFocus()` 测试通知
- HyperFocusApi 库已通过 JitPack 引入（`com.github.ghhccghk:HyperFocusApi:2.0`）
- API 可用：`FocusNotifyApi.build()` + `Template` 构建器模式，含 `BaseInfo`、`HintInfo`、`TimerInfo`、`HighlightInfo` 等类型

## 架构

```
Dart:
  live_activity_controller.dart
    _liveSyncScheduleSnapshot()
      → snapshot JSON 中新增 "superIslandEngine" 字段

Kotlin:
  LiveUpdateScheduler.syncSnapshot()
    → 解析并存储 engine 设置到 LiveUpdateService 字段

  LiveUpdateService.buildNotification()
    ├─ superIslandEngine == "builtIn"  (默认)
    │   └─ buildMiuiFocusParam()       → miui.focus.param JSON   ← 现有逻辑不改
    └─ superIslandEngine == "hyperFocusApi"
        └─ buildHyperFocusBundle()     → Bundle (via FocusNotifyApi + Template)
```

## 改动清单

### 1. Dart 侧：snapshot 传递 engine 设置

**文件:** `lib/providers/timetable/live_activity_controller.dart`（`_liveSyncScheduleSnapshot` 方法）

在 snapshot JSON 的 `settings` 对象中追加：

```dart
'superIslandEngine': host.settings.superIslandEngine.value,
```

### 2. Kotlin 侧：LiveUpdateScheduler 解析 engine

**文件:** `android/.../LiveUpdateScheduler.kt`（`ScheduledSelection` 类或 `snapshotJson` 处理）

从 snapshot JSON 的 `settings.superIslandEngine` 读取，存入 `ScheduledSelection` 或作为 `LiveUpdateService` 的 intent extra 传递。

### 3. Kotlin 侧：LiveUpdateService 接收 engine

**文件:** `android/.../MainActivity.kt`（`LiveUpdateService` 内部类）

- 在 `onStartCommand` 的 intent extra 解析中，新增 `EXTRA_SUPER_ISLAND_ENGINE`
- 存储为字段 `var superIslandEngine: String = "builtIn"`

### 4. Kotlin 侧：buildNotification() 分支

**文件:** `android/.../MainActivity.kt`（`LiveUpdateService.buildNotification` 方法，~3171 行）

在通知构建的最后一步（`miui.focus.param` 附着处），根据引擎选择分支：

```kotlin
if (superIslandEngine == "hyperFocusApi") {
    val focusBundle = buildHyperFocusBundle(
        courseName = courseName,
        startTime = startTimeText,
        endTime = endTimeText,
        location = location,
        stage = stage,
        startAtMillis = startAtMillis,
        endAtMillis = endAtMillis,
    )
    if (focusBundle != null) {
        extendedNotification.extras.putAll(focusBundle)
    }
} else {
    // 现有逻辑：buildMiuiFocusParam() → miui.focus.param
}
```

### 5. Kotlin 侧：buildHyperFocusBundle() 新方法

核心方法，使用 `FocusNotifyApi` 的 Builder API：

```kotlin
private fun buildHyperFocusBundle(
    courseName: String,
    startTime: String,
    endTime: String,
    location: String,
    stage: String?,
    startAtMillis: Long,
    endAtMillis: Long,
): Bundle? {
    if (!isXiaomiFamilyDevice()) return null

    val now = System.currentTimeMillis()
    val isBeforeClass = stage == "beforeClass"
    val timerWhen = if (isBeforeClass) startAtMillis else endAtMillis
    val hintTitle = if (isBeforeClass) {
        formatCountdownRemaining(startAtMillis - now, prefix = "距离上课还有 ")
    } else {
        formatCountdownRemaining(endAtMillis - now, prefix = "距离下课还有 ")
    }
    val timeRange = "$startTime - $endTime"

    val notifyApi = FocusNotifyApi()
    val builder = NotificationCompat.Builder(this, channelId)
    val template = notifyApi.build(builder)

    template.setBaseInfo(BaseInfo().apply {
        setTitle(courseName)
        setContent(timeRange)
        setSubContent(location)
        setType(2)
        setShowDivider(true)
    })

    template.setHintInfo(HintInfo().apply {
        setTitle(hintTitle)
        setTimerInfo(TimerInfo().apply {
            setTimerType(-1) // countdown
            setTimerWhen(timerWhen)
            setTimerSystemCurrent(now)
        })
        setType(1)
    })

    template.setTicker(courseName)
    template.setAodTitle(courseName)
    template.setUpdatable(true)
    template.setEnableFloat(true)
    template.setTimeout(3600)

    return template.create()
}
```

### 6. 阶段映射

| 内置引擎 stage | HyperFocusApi hint title | TimerInfo |
|---|---|---|
| `beforeClass` | "距离上课还有 XX:XX" | type=-1, when=startAtMillis |
| `duringClass` / `beforeEnd` | "距离下课还有 XX:XX" | type=-1, when=endAtMillis |

### 7. 格式辅助

新增 `formatCountdownRemaining(ms, prefix)` — 将毫秒格式化为 `XX:XX` 并拼接前缀。

## 不改动的部分

- 前台服务生命周期、ticker 更新频率、通知渠道
- 内置引擎的 `buildMiuiFocusParam()` / `buildIslandSummary()` JSON 构造
- Dart 侧的 UI、引擎切换、HyperFocusApi 设置项
- `sendTestFocusNotification()` 测试通知方法

## 测试验证

1. 引擎切为「小米超级岛」→ 有课表时前台服务启动，通知显示 Focus 通知
2. 上课前阶段：状态栏岛显示课程名 + 倒计时
3. 上课中阶段：岛自动更新为下课倒计时
4. 引擎切回「内置」→ 恢复现有行为
5. 发送测试通知（已有）不受影响
