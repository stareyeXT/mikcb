# Task 2 Report: Dart 埋点（TDD）

## Status: DONE_WITH_CONCERNS

## What I implemented

1. **Test (TDD red, Step 1)** — `test/widgets/hyper_focus_testing_screen_test.dart`:
   - Added `const umengChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');` next to `liveChannel`.
   - In test 2 (`'hyper focus testing screen sends test with scheduled stage'`), added the umeng mock that records `category` of `recordDiagnosticEvent` calls into `recordedCategories`, with `addTearDown` cleanup.
   - Added assertion `expect(recordedCategories, contains('send_test_focus_requested'));` after `expect(sentStage, 'active');`.
   - **Necessary addition beyond the brief (see Concerns):** also mocked the `dev.fluttercommunity.plus/package_info` channel (`getAll` → version map) in test 2, with tearDown cleanup.

2. **Implementation (Step 3)** — `lib/screens/timetable_settings_screen.dart`, `_sendTestNotification` (L2945-2946):
   - Inserted verbatim per brief, after `appDebugLog('MiuiLive', '测试课程：${course?.name}');` and before `final error = await _hyperFocusService.sendTestFocusNotification(...)`:
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

3. **Commit (Step 5):** `c1caede` — `feat: record send-test-focus request from testing screen` (2 files, +42).

## TDD Evidence

### RED
Command: `flutter test test/widgets/hyper_focus_testing_screen_test.dart --plain-name "sends test with scheduled stage"`

Relevant failing output (expected — no record call existed yet, so `recordedCategories` stayed empty):
```
[MiuiLive] 测试阶段：active
[MiuiLive] 测试课程：null
[MiuiLive] 发送结果：成功
The following TestFailure was thrown running a test:
Expected: contains 'send_test_focus_requested'
  Actual: []
   Which: does not contain 'send_test_focus_requested'
...
test/widgets/hyper_focus_testing_screen_test.dart line 200
00:02 +0 -1: Some tests failed.
```

### GREEN
Command: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
```
00:02 +2: All tests passed!
```

Full suite: `flutter test` → `00:28 +716 ~3: All tests passed!` (matches baseline).

`flutter analyze` → 8 infos, all pre-existing (course_import_screen.dart ×3, miui_live_activities_service.dart ×5); no new infos. Baseline satisfied.

## Files changed

- `lib/screens/timetable_settings_screen.dart` (+9): the record call only.
- `test/widgets/hyper_focus_testing_screen_test.dart` (+33): umeng mock + assertion per brief, plus the package_info mock (below).

No other files touched. `git status` before commit confirmed only these two files staged; all temporary bisect instrumentation in `lib/services/app_log_service.dart` and `lib/services/umeng_analytics_service.dart` was fully reverted (verified with `git diff`).

## Self-review findings

- Completeness: Steps 1-5 done verbatim, except the one justified test addition.
- Discipline: no changes to app behavior beyond the record call; no changes to return text/snackbar; no notify-delay recheck.
- Test output: no new warnings; suite matches baseline `+716 ~3`.

## Issues / concerns

**1. The GREEN attempt failed the FIRST time (sentStage null).** The record call makes `_sendTestNotification` cross `AppLogService.instance.log` → `AppLogService.initialize()` → `PackageInfo.fromPlatform()`. In `testWidgets` (FakeAsync), unmocked platform channels cannot complete — engine responses are only delivered on the real event loop, which `pumpAndSettle` blocks. So `PackageInfo.fromPlatform()` hangs forever in fake time and the chain never reaches `sendTestFocus` → `sentStage` stayed null and the test failed at the pre-existing assertion.

**2. Fix (test-only, beyond the brief):** mocked the `dev.fluttercommunity.plus/package_info` channel (`getAll` → `{'appName','packageName','version','buildNumber','buildSignature','installerStore'}`) in test 2 with tearDown cleanup. Verified by bisection: with the mock → 2/2 green (with the brief's exact `runAsync(50ms)` + `pumpAndSettle` flow); without it → `sentStage` null again. The mock completes via microtask in fake time, so no test-flow restructuring was needed. This is the only deviation from the brief's Step 1; it does not alter production code.

**3. During debugging I temporarily instrumented `app_log_service.dart` / `umeng_analytics_service.dart` (prints), and one of those edits accidentally removed the `if (!_shouldRecord(...)) return;` guard in `log()`; both files are now byte-identical to HEAD (verified via `git diff`).**

**4. The plan's Step 2 expectation** (red run failing only at the `contains` assertion) was met — the red failure was exactly `Actual: []` at the new assertion; the hang issue only surfaces when the implementation exists, so it could not have been caught in Step 2.
