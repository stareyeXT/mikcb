# Task 1 Brief: 测试界面 details 截断 + 删除岛视觉入口

来源：`docs/superpowers/plans/2026-08-01-hyperfocus-settings-fixes-plan.md` Task 1

## Global Constraints（本项目所有任务适用）

- `flutter analyze` 基线：8 个预存在 infos，0 error
- `flutter test` 基线：+716 ~3
- 删除"岛视觉"入口不触碰 Live 引擎的"课前/课中课后显示设置"入口（`_buildLiveUpdatesSettings`）与 `LiveDisplaySettingsScreen`

## Files

- Modify: `lib/screens/timetable_settings_screen.dart`
  - `_buildDebugSection`（L3427-3456）加 details 截断
  - `_buildHyperFocusSettings` 删除"岛视觉"两个 tile（L1834-1869）

## Step 1: 加 _ellipsize helper

在 `_HyperFocusTestingSettingsScreenState`（含 `_buildDebugSection` 的 State 类）内新增：
```dart
  String _ellipsize(String? value, {int max = 24}) {
    final v = value?.trim() ?? '';
    if (v.length <= max) return v;
    return '${v.substring(0, max)}…';
  }
```

## Step 2: _buildDebugSection 应用截断

`_buildDebugSection`（L3439-3450）两处 details 改为 `_ellipsize(...)`：
```dart
            for (final entry in entries.entries)
              HyperosListTile(
                icon: Icons.label_outline,
                title: entry.key,
                details: _ellipsize(entry.value),
              ),
            if (trailingJson.isNotEmpty)
              HyperosListTile(
                icon: Icons.data_object,
                title: 'JSON',
                details: _ellipsize(trailingJson, max: 60),
              ),
```

## Step 3: 删除岛视觉入口

`_buildHyperFocusSettings` 中删除两个 `HyperosListTile`（"岛视觉" forDuringEnd:false 和 forDuringEnd:true 两个 tile）。"显示自定义"分组保留"状态栏岛自定义"和"展开态自定义"两个 tile。

## Step 4: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 绿

## Step 5: Commit

```bash
git add lib/screens/timetable_settings_screen.dart
git commit -m "fix: ellipsize debug details and drop island visual entries from hyperfocus menu"
```
