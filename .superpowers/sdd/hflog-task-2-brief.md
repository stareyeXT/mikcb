# Task 2 Brief: Dart 埋点（TDD）

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-test-logging-plan.md` Task 2

## Global Constraints（本项目所有任务适用）

- category 前缀 `send_test_focus_*`，全用 `record`（不用 `report`，避免 2 分钟 dedupe 节流吞记录）
- 不改变任何现有行为与返回文案
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不做 notify 后延迟复查（spec 范围外）

## Files

- Modify: `lib/screens/timetable_settings_screen.dart`（`_sendTestNotification`，L2945 后插入）
- Test: `test/widgets/hyper_focus_testing_screen_test.dart`（测试 2 加 umeng 通道 mock + 断言）

## Interfaces

- Consumes: `MiuiLiveActivitiesService.recordDiagnosticEvent(String category, String message, {Map<String, Object?> extras, DiagnosticLogLevel level})`（miui_live_activities_service.dart L202-214）——在测试界面 State 里通过 `_hyperFocusService` 访问
- Produces: 落盘 category `send_test_focus_requested`（Dart 侧 AppLogService + Kotlin 通道）

## Step 1: 写失败测试

在 `test/widgets/hyper_focus_testing_screen_test.dart`：

1. `main()` 顶部（`const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');` 之后）加：

```dart
const umengChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');
```

2. 测试 2 `'hyper focus testing screen sends test with scheduled stage'` 体内，`setMockMethodCallHandler(liveChannel, ...)` 调用之后加：

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

## Step 2: 运行测试确认失败

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart --plain-name "sends test with scheduled stage"`
Expected: FAIL（`recordedCategories` 为空，断言 `contains('send_test_focus_requested')` 失败）

## Step 3: 实现 Dart 埋点

`_sendTestNotification`（lib/screens/timetable_settings_screen.dart L2933-2958）中，`final course = selection?.currentCourse;` 与 `appDebugLog('MiuiLive', '测试课程：${course?.name}');` 之后、`final error = await _hyperFocusService.sendTestFocusNotification(...)` 之前插入：

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

## Step 4: 运行测试确认通过

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: PASS（2 个用例全绿；测试 2 断言 `recordedCategories` 含 `send_test_focus_requested`）

## Step 5: Commit

```bash
git add lib/screens/timetable_settings_screen.dart test/widgets/hyper_focus_testing_screen_test.dart
git commit -m "feat: record send-test-focus request from testing screen"
```
