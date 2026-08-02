# Task 2 Brief: 模板存储迁移（页面层双写）

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 2（按实际代码调整）

## Global Constraints（本项目所有任务适用）

- 模板编辑范式保持"变量点选"（逗号分隔变量列表，`resolveTemplate` 兼容）
- TimetableSettings.hfTemplatesJson 成为模板权威源（随 profile 备份/导入导出）；Kotlin prefs 是渲染镜像
- `flutter analyze` 基线：8 个预存在 infos（现在 0 error）
- `flutter test` 基线：+716 ~3

## 设计决策（已确定）

service 层 `saveHyperFocusTemplates`/`loadHyperFocusTemplates`（miui_live_activities_service.dart L682-707）**保持不变**——仍写/读 Kotlin prefs，作为渲染镜像。**双写逻辑放在页面层**（`HyperFocusStageTemplateScreen`）：保存时既调 service（写 Kotlin）又写 `TimetableSettings.hfTemplatesJson`；加载时优先 TimetableSettings、缺失从 Kotlin prefs 迁移回填。

## Files

- Modify: `lib/screens/live_settings_subpages.dart`
  - `HyperFocusStageTemplateScreen._loadTemplates`（约 L1491-1500）
  - `_saveTemplates`（约 L1502-1511）

## Step 1: 改造 _loadTemplates（优先 TimetableSettings，Kotlin prefs 迁移）

当前（约 L1491-1500）：
```dart
  Future<void> _loadTemplates() async {
    final service = MiuiLiveActivitiesService();
    final saved = await service.loadHyperFocusTemplates();
    if (!mounted) return;
    for (final key in _controllers.keys) {
      if (saved.containsKey(key)) {
        _controllers[key]?.text = saved[key]!;
      }
    }
  }
```

改为：
```dart
  Future<void> _loadTemplates() async {
    final provider = context.read<TimetableProvider>();
    final settingsJson = provider.settings.hfTemplatesJson;
    if (settingsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(settingsJson) as Map<String, dynamic>;
        if (!mounted) return;
        for (final key in _controllers.keys) {
          final v = decoded[key];
          if (v is String && v.isNotEmpty) {
            _controllers[key]?.text = v;
          }
        }
        return;
      } catch (_) {
        // 解析失败则回退 Kotlin prefs 迁移
      }
    }
    final service = MiuiLiveActivitiesService();
    final saved = await service.loadHyperFocusTemplates();
    if (!mounted) return;
    var migrated = false;
    for (final key in _controllers.keys) {
      if (saved.containsKey(key) && saved[key]!.isNotEmpty) {
        _controllers[key]?.text = saved[key]!;
        migrated = true;
      }
    }
    if (migrated) {
      await _persistTemplatesToSettings(
        provider,
        Map.fromEntries(
          _controllers.entries.map(
            (e) => MapEntry(e.key, e.value.text),
          ),
        ),
      );
    }
  }
```

## Step 2: 新增 _persistTemplatesToSettings helper

在 State 类内新增：
```dart
  Future<void> _persistTemplatesToSettings(
    TimetableProvider provider,
    Map<String, String> map,
  ) async {
    await provider.updateTimetableSettings(
      provider.settings.copyWith(hfTemplatesJson: jsonEncode(map)),
    );
  }
```

## Step 3: 改造 _saveTemplates（双写）

当前（约 L1502-1511）：
```dart
  Future<void> _saveTemplates() async {
    final map = <String, String>{};
    for (final key in _defaultTemplates.keys) {
      map[key] = _controllers[key]?.text ?? _defaultTemplates[key]!;
    }
    final service = MiuiLiveActivitiesService();
    final ok = await service.saveHyperFocusTemplates(map);
    if (!mounted) return;
    showHyperosSnackBar(context, message: ok ? '模板已保存' : '保存失败');
  }
```

改为：
```dart
  Future<void> _saveTemplates() async {
    final map = <String, String>{};
    for (final key in _defaultTemplates.keys) {
      map[key] = _controllers[key]?.text ?? _defaultTemplates[key]!;
    }
    final service = MiuiLiveActivitiesService();
    final ok = await service.saveHyperFocusTemplates(map);
    final provider = context.read<TimetableProvider>();
    await _persistTemplatesToSettings(provider, map);
    if (!mounted) return;
    showHyperosSnackBar(context, message: ok ? '模板已保存' : '保存失败');
  }
```

## Step 4: 检查 imports

`HyperFocusStageTemplateScreen` 所在文件 `live_settings_subpages.dart` 需要：
- `dart:convert`（jsonDecode/jsonEncode）——检查文件顶部是否已有，没有则加
- `TimetableProvider`（lib/providers/timetable_provider.dart）——检查是否已 import，没有则加
- `context.read` 需要 package:provider ——检查

## Step 5: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 绿（该测试不涉及模板编辑器）

## Step 6: Commit

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: migrate hyperfocus template storage to timetable settings with kotlin mirror"
```
