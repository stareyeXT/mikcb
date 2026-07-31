# 超级岛测试发送全链路日志埋点 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `sendTestFocus` 发送链路补全 `record` 埋点（Kotlin 4 条 + Dart 1 条），使每次发送的决策过程全部落盘到 `live_update_diagnostics.log`，现有查看/导出功能立即可用。

**Architecture:** Kotlin 端在 `sendTestFocusNotificationInner`（MainActivity.kt L1135-1353）各决策点调用 `UmengDiagnosticReporter.record`（无节流，与现有 `live_update_*` 埋点同一文件同一体系）；Dart 端在 `_sendTestNotification` 发起时调用 `recordDiagnosticEvent`（走 `com.mutx163.qingyu/umeng_analytics` 通道）。界面零改动。

**Tech Stack:** Kotlin（UmengDiagnosticReporter.kt）、Flutter（MethodChannel）、widget 测试（flutter_test + mock channel）

## Global Constraints

- category 前缀 `send_test_focus_*`，全用 `record`（不用 `report`，避免 2 分钟 dedupe 节流吞记录）
- 不改变任何现有行为与返回文案（权限/渠道/post-inspect 文案、`recordHyperFocusTestResult`、`send_test_focus_failed` report 均不动）
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不做 notify 后延迟复查（spec 范围外）

---

### Task 1: Kotlin 埋点（文案常量 + 4 处 record）

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/DiagnosticLogMessages.kt`（末尾追加）
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `sendTestFocusNotificationInner`：L1147-1150（权限检查）、L1181-1183（时间窗计算后/模板加载前，新增 started 埋点）、L1311-1314（渠道检查）、L1327-1336（post-inspect）

**Interfaces:**
- Consumes: `UmengDiagnosticReporter.record(context: Context, category: String, message: String, level: ..., extras: Map<String, Any?>)`（UmengDiagnosticReporter.kt L44-72）
- Produces: 落盘 category `send_test_focus_started` / `send_test_focus_permission_blocked` / `send_test_focus_channel_blocked` / `send_test_focus_submitted`

- [ ] **Step 1: 追加文案常量**

在 `DiagnosticLogMessages.kt` 末尾（L73 后）追加：

```kotlin
const val SEND_TEST_FOCUS_STARTED = "收到超级岛测试通知发送请求"
const val SEND_TEST_FOCUS_PERMISSION_BLOCKED = "超级岛测试发送被拦截：系统通知权限未开启"
const val SEND_TEST_FOCUS_CHANNEL_BLOCKED = "超级岛测试发送被拦截：测试通知渠道已被关闭"
const val SEND_TEST_FOCUS_SUBMITTED = "超级岛测试通知已提交并检查系统接收结果"
```

- [ ] **Step 2: 权限拦截埋点（L1147-1150）**

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

- [ ] **Step 3: started 埋点（时间窗计算后、模板加载前）**

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

- [ ] **Step 4: 渠道拦截埋点（L1311-1314）**

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

- [ ] **Step 5: submitted 埋点（post-inspect，L1333 Log.d 之后）**

原代码（L1327-1337）：

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

- [ ] **Step 6: 编译验证**

Run: `gradlew assembleDebug`（workdir `android`）
Expected: `BUILD SUCCESSFUL`（无编译错误）

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/DiagnosticLogMessages.kt android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: record send-test-focus decision points in diagnostics log"
```

---

### Task 2: Dart 埋点（TDD）

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart`（`_sendTestNotification`，L2945 后插入）
- Test: `test/widgets/hyper_focus_testing_screen_test.dart`（测试 2 加 umeng 通道 mock + 断言）

**Interfaces:**
- Consumes: `MiuiLiveActivitiesService.recordDiagnosticEvent(String category, String message, {Map<String, Object?> extras, DiagnosticLogLevel level})`（miui_live_activities_service.dart L202-214）
- Produces: 落盘 category `send_test_focus_requested`（Dart 侧 AppLogService + Kotlin 通道）

- [ ] **Step 1: 写失败测试**

在 `test/widgets/hyper_focus_testing_screen_test.dart`：
1. `main()` 顶部（`const liveChannel = ...` 旁）加：

```dart
const umengChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');
```

2. 测试 2 `'hyper focus testing screen sends test with scheduled stage'` 体内，`setMockMethodCallHandler(liveChannel, ...)` 之后加：

```dart
final recordedCategories = <String>[];
TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
    .setMockMethodCallHandler(umengChannel, (call) async {
      if (call.method == 'recordDiagnosticEvent') {
        recordedCategories.add((call.arguments as Map)['category'] as String);
      }
      return null;
    });
addTearDown(() {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(umengChannel, null);
});
```

3. 断言（`expect(sentStage, 'active');` 之后）加：

```dart
expect(recordedCategories, contains('send_test_focus_requested'));
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart --plain-name "sends test with scheduled stage"`
Expected: FAIL（`recordedCategories` 为空，断言 `contains('send_test_focus_requested')` 失败）

- [ ] **Step 3: 实现 Dart 埋点**

`_sendTestNotification` 中 `final course = selection?.currentCourse;` 与 `appDebugLog('MiuiLive', '测试课程：${course?.name}');` 之后、`final error = await _hyperFocusService.sendTestFocusNotification(...)` 之前插入：

```dart
await _hyperFocusService.recordDiagnosticEvent(
  'send_test_focus_requested',
  '收到超级岛测试通知发送请求',
  extras: {
    'stage': stage,
    'courseName': course?.name ?? '',
    'atMillis': DateTime.now().millisecondsSinceEpoch.toString(),
  },
);
```

- [ ] **Step 4: 运行测试确认通过**

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: PASS（2 个用例全绿；测试 2 断言 `recordedCategories` 含 `send_test_focus_requested`）

- [ ] **Step 5: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart test/widgets/hyper_focus_testing_screen_test.dart
git commit -m "feat: record send-test-focus request from testing screen"
```

---

### Task 3: 全量回归验证

**Files:**
- 无代码改动（纯验证 + 文档）

- [ ] **Step 1: analyze**

Run: `flutter analyze`
Expected: 与基线持平——8 个预存在 infos（3 个 course_import_screen.dart + 5 个 miui_live_activities_service.dart use_null_aware_elements），0 error、0 warning、0 新增

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: +716 ~3 全绿（无失败、无跳过新增）

- [ ] **Step 3: 构建**

Run: `gradlew assembleDebug`（workdir `android`）
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 更新台账**

修改 `.superpowers\sdd\progress.md` 的 "HFT Plan" 区段（或新建 "HFLog Plan" 区段），记录：Task 1-3 完成、提交哈希、测试/分析基线结果。

```bash
git add .superpowers/sdd/progress.md
git commit -m "docs: track hyperfocus test logging plan completion"
```

- [ ] **Step 5: 真机验收（用户执行）**

1. 安装 `build\app\outputs\flutter-apk\app-dev-debug.apk`
2. 超级岛测试页点"发送测试通知"
3. 点"导出诊断"→ 分享保存 → 打开导出的 log
4. 确认包含 `send_test_focus_started` 与 `send_test_focus_submitted`（含 `activeContainsTest`/`activeIds`）；若权限/渠道拦截，对应 `*_blocked` 记录
