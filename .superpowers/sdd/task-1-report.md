# Task 1 Report: 写失败测试（新页面共用 live 设置）

## What I implemented

Created `test/widgets/hyperfocus_timing_screen_test.dart` (152 lines) verbatim per the task brief:

- Navigation helper `openHyperFocusTimingScreen(tester) → Future<TimetableProvider>`: pumps `TimetableSettingsScreen` in `TestApp` with `createInitializedTestProvider`, navigates 超级岛与通知 → 小米超级岛 → 提醒时机.
- Test 1 `hyper focus timing switches edit shared live settings`: asserts the page shows `上课前提醒` (not the old `课前提醒`), and that toggling switches mutates `provider.settings.liveEnableBeforeClass`, `liveEnableDuringClass`, `liveEnableBeforeEnd`, and toggles the `重点提醒切入时机` section visibility.
- Test 2 `hyper focus timing thresholds share live settings`: asserts the `时间阈值` selector lists 30/40/50/60 分钟 options and picks 30 → `liveShowBeforeClassMinutes == 30`.
- Same scaffolding as `test/widgets/timetable_settings_screen_test.dart`: channel mocks (home_widget, umeng_analytics, miui_live), `_seedInitializedPrefs()` (with the two `did_migrate_*` keys), `StorageService().resetForTesting()`, 800x1200 surface.

No app code under `lib/` was modified.

## RED evidence

Command run (from `C:\daima\zwg\mikcb\mikcb-ECJTU`):

```
flutter test test\widgets\hyperfocus_timing_screen_test.dart
```

Result: `00:02 +0 -2: Some tests failed.` — both tests failed, as expected:

Test 1 (at the first expect, line 112 — exactly the predicted failure point):

```
The following TestFailure was thrown running a test:
Expected: exactly one matching candidate
  Actual: _TextWidgetFinder:<Found 0 widgets with text "上课前提醒": []>
   Which: means none were found but one was expected
   ...
file:///C:/daima/zwg/mikcb/mikcb-ECJTU/test/widgets/hyperfocus_timing_screen_test.dart line 112
```

This is expected: the current (pre-Task-2) 提醒时机 page only has `课前提醒`/`课中提醒` switches, not the new `上课前提醒`/`课中与下课提醒` naming, so the test fails on the first assertion.

Test 2:

```
The following StateError was thrown running a test:
Bad state: No element
  #1 WidgetController.element
  #2 WidgetController.dragUntilVisible ...
file:///C:/daima/zwg/mikcb/mikcb-ECJTU/test/widgets/hyperfocus_timing_screen_test.dart line 137
```

Also expected: the old timing page contains no `Scrollable` at all, so the first `scrollUntilVisible(find.text('时间阈值'), ..., scrollable: find.byType(Scrollable).last)` throws. Navigation to the old 提醒时机 page itself succeeded (the old page is titled 提醒时机), so the helper works; the failure is caused by the old page's structure.

## Files changed

- Created: `test/widgets/hyperfocus_timing_screen_test.dart` (152 insertions)
- Committed: `7f31b1c test: hyper focus timing page shares live settings`
- `git status` after commit: only pre-existing unrelated local changes remain (`.superpowers/sdd/task-1-brief.md`, `android/.../MainActivity.kt`, `lib/screens/timetable_settings_screen.dart`, `lib/services/miui_live_activities_service.dart` modified; two untracked docs). None were touched by this task.

## Self-review findings

- File matches the brief's code verbatim; no comments added.
- Channel mocks and prefs seeding mirror the existing settings screen test; `createInitializedTestProvider`/`TestApp` signatures verified against `test/helpers_test_app.dart`.
- Test 1 fails at exactly the predicted line 112 → the primary RED signal is correct.
- Test 2's failure mode (StateError on missing scrollable) differs slightly from test 1's, but is the same root cause: old page lacks the new UI.

## Concerns

1. Test 2 requires the post-Task-2 page to contain a `Scrollable` — if Task 2's new layout fits without scrolling (e.g. a plain Column), `find.byType(Scrollable).last` will keep throwing "No element" even after the new texts exist. Task 2 should ensure the page content is in a scrollable view (ListView/SingleChildScrollView) or the helper/test needs adjustment.
2. The live channel mock returns `null` for all calls (simpler than the existing test's `getLiveUpdateDebugStatus` mock). If the new page calls that method and null-checks the result, Task 2 may need the fuller mock; if the test then fails on a null-check rather than the widget assertions, the helper may need the same richer mock as `timetable_settings_screen_test.dart`.
3. The working tree was NOT clean at start (pre-existing local modifications to `lib/screens/timetable_settings_screen.dart` etc.). I committed only the test file as instructed, but those dirty files may affect the app code Task 2 builds on — worth flagging to the main agent.
