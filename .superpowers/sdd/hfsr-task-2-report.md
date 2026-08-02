# Task 2 Report: 模板存储迁移（页面层双写）

## What I Changed

Only `lib/screens/live_settings_subpages.dart` was modified.

1. **Import** (line 2): added `import 'dart:convert';` for `jsonDecode`/`jsonEncode`. `package:provider` and `../providers/timetable_provider.dart` were already imported (lines 10, 13).

2. **`_HyperFocusStageTemplateScreenState._loadTemplates`** (lines 1431-1469): now reads `TimetableProvider` and prefers `settings.hfTemplatesJson`; if non-empty, decodes it and fills controllers (parse failure falls back). Otherwise loads from Kotlin prefs via `MiuiLiveActivitiesService.loadHyperFocusTemplates()`, fills controllers, and on any migrated value calls `_persistTemplatesToSettings` to backfill the settings JSON.

3. **New `_persistTemplatesToSettings` helper** (lines 1471-1478): awaits `provider.updateTimetableSettings(provider.settings.copyWith(hfTemplatesJson: jsonEncode(map)))`.

4. **`_saveTemplates`** (lines 1480-1491): dual-write — still writes Kotlin via `service.saveHyperFocusTemplates(map)`, then also writes `TimetableSettings.hfTemplatesJson` via the helper.

### Deviation from the brief (one line)
In `_saveTemplates` the brief places `final provider = context.read<TimetableProvider>();` after `await service.saveHyperFocusTemplates(map)`. That triggered a new `use_build_context_synchronously` info (breaking the 8-info baseline). I moved the `context.read` before the `await` — identical behavior, no lint.

## Test Results

- `flutter analyze`: **8 infos (all pre-existing), 0 errors** — matches baseline.
- `flutter test test/widgets/hyper_focus_testing_screen_test.dart`: **2/2 passed**.

## Files Changed

- `lib/screens/live_settings_subpages.dart` (+42 / -1)

## Self-Review Findings

- `_loadTemplates` prefers hfTemplatesJson, falls back to Kotlin prefs with one-time migration backfill — correct.
- `updateTimetableSettings` signature confirmed as `Future<String?>` (timetable_provider.dart:1932); awaiting is fine.
- `hfTemplatesJson` field confirmed on `TimetableSettings` (timetable_settings.dart:1083, in copyWith and toJson/fromJson).
- `_resetStage` also routes through the dual-writing `_saveTemplates`, consistent with the intent.
- Only the intended file modified.

## Concerns

- None blocking. The migration backfill writes full controller map (including defaults) as JSON once any non-empty Kotlin value exists; acceptable per brief.
