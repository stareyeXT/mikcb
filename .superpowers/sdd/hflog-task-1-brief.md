# Task 1 Brief: Kotlin 埋点（文案常量 + 4 处 record）

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-test-logging-plan.md` Task 1

## Global Constraints（本项目所有任务适用）

- category 前缀 `send_test_focus_*`，全用 `record`（不用 `report`，避免 2 分钟 dedupe 节流吞记录）
- 不改变任何现有行为与返回文案（权限/渠道/post-inspect 文案、`recordHyperFocusTestResult`、`send_test_focus_failed` report 均不动）
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不做 notify 后延迟复查（spec 范围外）

## Files

- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/DiagnosticLogMessages.kt`（末尾追加）
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `sendTestFocusNotificationInner`：L1147-1150（权限检查）、L1181-1183（时间窗计算后/模板加载前，新增 started 埋点）、L1311-1314（渠道检查）、L1327-1336（post-inspect）

## Interfaces

- Consumes: `UmengDiagnosticReporter.record(context: Context, category: String, message: String, level: ..., extras: Map<String, Any?>)`（UmengDiagnosticReporter.kt L44-72）
- Produces: 落盘 category `send_test_focus_started` / `send_test_focus_permission_blocked` / `send_test_focus_channel_blocked` / `send_test_focus_submitted`

注意：post-inspect 代码块（`activeIds`/`testChannelState`/`liveChannelState` 变量与"已提交但系统未显示"返回）已存在于当前工作区（commit `0e7e4f6` 已提交），Step 5 直接在其上加 record 即可。

## Step 1: 追加文案常量

在 `DiagnosticLogMessages.kt` 末尾（L73 后）追加：

```kotlin
const val SEND_TEST_FOCUS_STARTED = "收到超级岛测试通知发送请求"
const val SEND_TEST_FOCUS_PERMISSION_BLOCKED = "超级岛测试发送被拦截：系统通知权限未开启"
const val SEND_TEST_FOCUS_CHANNEL_BLOCKED = "超级岛测试发送被拦截：测试通知渠道已被关闭"
const val SEND_TEST_FOCUS_SUBMITTED = "超级岛测试通知已提交并检查系统接收结果"
```

## Step 2: 权限拦截埋点（L1147-1150）

原代码：

```kotlin
if (!notificationManager.areNotificationsEnabled()) {
    Log.e("HyperFocusApi", "notifications disabled")
    return "系统通知权限未开启，请先在设置中开启通知权限"
}
```

改为：

```kotlin
if (!notificationManager.areNotificationsEnabled()) {
    Log.e("HyperFocusApi", "notifications disabled")
    UmengDiagnosticReporter.record(
        context = applicationContext,
        category = "send_test_focus_permission_blocked",
        message = DiagnosticLogMessages.SEND_TEST_FOCUS_PERMISSION_BLOCKED,
        extras = mapOf("stage" to stage)
    )
    return "系统通知权限未开启，请先在设置中开启通知权限"
}
```

## Step 3: started 埋点（时间窗计算后、模板加载前）

在 `sendTestFocusNotificationInner` 的 `when (templateStage) { ... }` 结束（`hintText` 赋值完毕）之后、`val templates = loadHyperFocusTemplates(this)` 之前插入：

```kotlin
UmengDiagnosticReporter.record(
    context = applicationContext,
    category = "send_test_focus_started",
    message = DiagnosticLogMessages.SEND_TEST_FOCUS_STARTED,
    extras = mapOf(
        "stage" to stage,
        "courseName" to courseName,
        "shortName" to shortName,
        "startTime" to startTime,
        "endTime" to endTime,
        "location" to location,
        "templateStage" to templateStage,
        "classStartAt" to classStartAt,
        "classEndAt" to classEndAt,
        "timerTarget" to timerTarget,
        "now" to now,
    )
)
```

> 说明：spec 中 started 触发点写"入口（权限检查前）"，但 extras 含时间窗参数（在权限检查后才计算），故实现时置于时间窗计算完成后；权限拦截时只有 `send_test_focus_permission_blocked` 一条记录（信息足够）。

## Step 4: 渠道拦截埋点（L1311-1314）

原代码：

```kotlin
if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
    Log.e("HyperFocusApi", "test channel blocked, importance=${channel?.importance}")
    return "测试通知渠道已被关闭，请在系统通知设置中恢复该渠道"
}
```

改为：

```kotlin
if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
    Log.e("HyperFocusApi", "test channel blocked, importance=${channel?.importance}")
    UmengDiagnosticReporter.record(
        context = applicationContext,
        category = "send_test_focus_channel_blocked",
        message = DiagnosticLogMessages.SEND_TEST_FOCUS_CHANNEL_BLOCKED,
        extras = mapOf(
            "stage" to stage,
            "channelImportance" to (channel?.importance ?: -1),
        )
    )
    return "测试通知渠道已被关闭，请在系统通知设置中恢复该渠道"
}
```

## Step 5: submitted 埋点（post-inspect）

当前代码（commit 0e7e4f6 后的状态）：

```kotlin
val activeIds = notificationManager.activeNotifications.map { it.id }
val testChannelState = notificationManager.getNotificationChannel(HYPERFOCUS_TEST_CHANNEL_ID)
val liveChannelState = notificationManager.getNotificationChannel("live_update_channel")
Log.d(
    "HyperFocusApi",
    "post-inspect: activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}",
)
if (!activeIds.contains(10001)) {
    return "已提交但系统未显示（activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}）"
}
```

改为：

```kotlin
val activeIds = notificationManager.activeNotifications.map { it.id }
val testChannelState = notificationManager.getNotificationChannel(HYPERFOCUS_TEST_CHANNEL_ID)
val liveChannelState = notificationManager.getNotificationChannel("live_update_channel")
Log.d(
    "HyperFocusApi",
    "post-inspect: activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}",
)
val activeContainsTest = activeIds.contains(10001)
UmengDiagnosticReporter.record(
    context = applicationContext,
    category = "send_test_focus_submitted",
    message = DiagnosticLogMessages.SEND_TEST_FOCUS_SUBMITTED,
    extras = mapOf(
        "stage" to stage,
        "activeIds" to activeIds,
        "activeContainsTest" to activeContainsTest,
        "testChannelImportance" to (testChannelState?.importance ?: -1),
        "liveChannelImportance" to (liveChannelState?.importance ?: -1),
    )
)
if (!activeContainsTest) {
    return "已提交但系统未显示（activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}）"
}
```

## Step 6: 编译验证

Run: `gradlew assembleDebug`（workdir `android`）
Expected: `BUILD SUCCESSFUL`（无编译错误）

## Step 7: Commit

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/DiagnosticLogMessages.kt android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: record send-test-focus decision points in diagnostics log"
```
