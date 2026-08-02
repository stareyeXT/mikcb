# Task 4 Brief: 岛消失时间改分钟数字输入

来源：`docs/superpowers/plans/2026-08-01-hyperfocus-settings-fixes-plan.md` Task 4

## Global Constraints（本项目所有任务适用）

- `flutter analyze` 基线：8 个预存在 infos，0 error；`flutter test` 基线：+716 ~3
- 存储仍为秒（hfIslandTimeoutPre/Active/Post 秒 int），UI 显示/输入分钟，提交换算秒（×60）
- 限界：1~60 分钟（即 60~3600 秒），提交 clamp，非法回退

## Files

- Modify: `lib/screens/live_settings_subpages.dart`
  - `HyperFocusIslandTimeoutScreen`（L1374-1470）：`HyperosNumberPicker` → `HyperosTextField` 分钟输入

## 可用组件（已确认签名）

- `HyperosTextFieldTile({cardTitle, cardSubtitle, required field})`（hyperos_text_field.dart L233）
- `HyperosTextField`（hyperos_text_field.dart L9，参数含 controller/label/keyboardType/inputFormatters/onChanged——以实际签名为准）

## Step 1: State 持有分钟 TextEditingController

`_HyperFocusIslandTimeoutScreenState` 新增三个 controller（initState 初始化、dispose 释放），值从秒换算分钟：
```dart
  late final TextEditingController _preMinutesCtrl;
  late final TextEditingController _activeMinutesCtrl;
  late final TextEditingController _postMinutesCtrl;
```
initState（在 `_draft = ...` 后）：
```dart
    _preMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPre / 60).round().toString(),
    );
    _activeMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutActive / 60).round().toString(),
    );
    _postMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPost / 60).round().toString(),
    );
```
dispose（在 `_autoSaveTimer` 处理前）：
```dart
    _preMinutesCtrl.dispose();
    _activeMinutesCtrl.dispose();
    _postMinutesCtrl.dispose();
```

## Step 2: 改造 _buildTimeoutTile 为分钟输入

`_buildTimeoutTile`（L1429-1443）从 `(String label, int value, ValueChanged<int> onChanged)` + `HyperosNumberPicker` 改为 `(String label, TextEditingController controller, ValueChanged<int> onChanged)` + `HyperosTextFieldTile`：
```dart
  Widget _buildTimeoutTile(
    String label,
    TextEditingController controller,
    ValueChanged<int> onChanged,
  ) {
    return HyperosTextFieldTile(
      cardTitle: label,
      cardSubtitle: '分钟（1~60）',
      field: HyperosTextField(
        controller: controller,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9]'))],
        onChanged: (text) {
          final minutes = int.tryParse(text) ?? 0;
          final clamped = minutes.clamp(1, 60);
          onChanged(clamped * 60);
        },
      ),
    );
  }
```
> 以 `HyperosTextField` 实际参数名为准（可能没有 `controller`/`keyboardType`/`inputFormatters`/`onChanged` 同名参数——查 hyperos_text_field.dart L9-232）。若 `HyperosTextField` 不适合，改用项目里数字输入范式（如 `_HyperosSliderValueSheetBody` 的 HyperosTextField + FilteringTextInputFormatter，hyperos_controls.dart L425-570）。

## Step 3: 更新 build

section 标签改为"状态栏岛消失时间（分钟）"（L1413），三个 tile 调用（L1416-1421）改为：
```dart
              _buildTimeoutTile('课前', _preMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPre: v))),
              _buildTimeoutTile('课中', _activeMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutActive: v))),
              _buildTimeoutTile('课后', _postMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPost: v))),
```

## Step 4: 检查 imports

`FilteringTextInputFormatter`（package:flutter/services.dart）——检查文件是否已 import，无则加。

## Step 5: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test`
Expected: +716 ~3 全绿

## Step 6: Commit

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: island timeout in minutes via text input"
```
