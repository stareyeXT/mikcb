# Task 3 Report: 超级岛测试页 widget 测试（RED）

**Status:** DONE (RED as intended)

**Commit:** `ea528ab` (`test: hyper focus testing screen status and stage sheet`) — only
`test/widgets/hyper_focus_testing_screen_test.dart` staged (172 insertions).

## Files
- Created: `test/widgets/hyper_focus_testing_screen_test.dart` (brief's test logic and all finder
  strings/assertions verbatim; setup/pump adapted, see "Deviations").
- `lib/` and `test/widgets/timetable_settings_screen_test.dart`: untouched.

## Commands run
1. `flutter test test/widgets/hyper_focus_testing_screen_test.dart` — first run with brief's code **verbatim**.
2. Same command — after adapting only the setup/pump scaffolding.
3. `git add test/widgets/hyper_focus_testing_screen_test.dart && git commit -m "test: hyper focus testing screen status and stage sheet"`

## Run 1 (verbatim brief code) — RED, wrong failure mode
The brief's verbatim file could not reach the page: `TimetableSettingsScreen` uses
`Consumer<TimetableProvider>` (lib/screens/timetable_settings_screen.dart:64) and the TestApp does
not supply a provider, so every pump threw:

```
The following ProviderNotFoundException was thrown building Consumer<TimetableProvider>(dirty):
Error: Could not find the correct Provider<TimetableProvider> above this Consumer<TimetableProvider> Widget
#1021   main.<anonymous closure> (file:///C:/daima/zwg/mikcb/mikcb-ECJTU/test/widgets/hyper_focus_testing_screen_test.dart:70:18)
```
followed by `Found 0 widgets with text "超级岛与通知"` at the first `tap()` (line 73). Result: `00:00 +0 -2: Some tests failed.`

This is exactly the "environment needs additional setup" case the brief's Step 1 note and the
global constraints allow adapting ("you may adapt the setup portion (mocks, pumps) but keep the
test logic and finder strings from the brief intact").

## Deviation (setup/pump only — allowed by brief + global constraints)
Adapted, mirroring `test/widgets/timetable_settings_screen_test.dart` conventions:
- Added `_seedInitializedPrefs()` (SharedPreferences mock values + a default profile with
  `superIslandEngine: SuperIslandEngine.hyperFocusApi`, so the HyperFocus section with the
  '测试' tile is shown) — same pattern as `_seedInitializedPrefs` in the existing test.
- Added `_pumpToTestingEntry()` helper: surface size 800x1200, provider via
  `ChangeNotifierProvider.value` + `createInitializedTestProvider` (helpers_test_app.dart), then
  navigation with `scrollUntilVisible` for '超级岛与通知' (main list, `Scrollable.first`) and
  '测试' (sub list, `Scrollable.last`) — same scroll approach as
  timetable_settings_screen_test.dart:101-114.
- Test bodies, finder strings (`超级岛测试与诊断`, `通知权限已开启`, `测试渠道正常`, `调度已就绪`,
  `自动刷新`, `发送测试通知`, `课中`, `测试焦点通知已发送`), channel mocks, and assertions
  (`sentStage == 'active'`, `findsOneWidget`) unchanged. No assertion weakened.

## Run 2 (adapted setup) — RED, intended failure mode ✅
```
00:00 +0: hyper focus testing screen renders status chips and refresh switch
...
══╡ EXCEPTION CAUGHT BY FLUTTER TEST FRAMEWORK ╞═
The following TestFailure was thrown running a test:
Expected: exactly one matching candidate
  Actual: _TextWidgetFinder:<Found 0 widgets with text "超级岛测试与诊断": []>
   Which: means none were found but one was expected
#4      main.<anonymous closure> (file:///C:/daima/zwg/mikcb/mikcb-ECJTU/test/widgets/hyper_focus_testing_screen_test.dart:128:5)
The test description was: hyper focus testing screen renders status chips and refresh switch
```
and
```
00:01 +0 -1: hyper focus testing screen opens stage sheet and sends test
...
The finder "Found 0 widgets with text "发送测试通知": []" (used in a call to "tap()") could not find any matching widgets.
#3      main.<anonymous closure> (file:///C:/daima/zwg/mikcb/mikcb-ECJTU/test/widgets/hyper_focus_testing_screen_test.dart:164:18)
The test description was: hyper focus testing screen opens stage sheet and sends test
```
Final: `00:02 +0 -2: Some tests failed.` — both tests fail exactly because the
`HyperFocusTestingScreen` / `_HyperFocusTestingSettingsScreen` page does not exist yet (the '测试'
tile currently opens the old modal bottom sheet, which contains none of the new page's texts).
Navigation scaffolding (scroll + taps) succeeds; failure is purely the missing page. No compile
error referencing the missing class occurs because the page is reached via the settings entry
tile, not a direct import.

## Notes / concerns
- Test 2 currently fails at the `发送测试通知` tap (line 164), before any `sendTestFocus` call;
  when the entry is re-pointed at `HyperFocusTestingScreen`, the same assertions should pass
  green — the channel mock already handles `getHyperFocusDebugStatus` / `sendTestFocus`.
- `createInitializedTestProvider` triggers HolidayService HTTP calls in the test log (status 400
  under TestWidgetsFlutterBinding); harmless, same as the existing settings tests.
- Repo `master`, HEAD now `ea528ab`. Working tree still has pre-existing uncommitted sdd docs
  (untouched by this task).
