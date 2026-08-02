# Task 2 Brief: 模板编辑改列表式多选

来源：`docs/superpowers/plans/2026-08-01-hyperfocus-settings-fixes-plan.md` Task 2

## Global Constraints（本项目所有任务适用）

- `flutter analyze` 基线：8 个预存在 infos，0 error；`flutter test` 基线：+716 ~3
- 模板存储格式保持逗号分隔变量列表（`resolveTemplate` 兼容，Kotlin 解析不变）
- UI 用 Hyperos* 组件

## Files

- Modify: `lib/screens/live_settings_subpages.dart`
  - `_HyperFocusStatusIslandScreenState._variableChipField`（L1619-1664）→ 改为 `_variableSelectField`
  - `_HyperFocusExpandedIslandScreenState._variableChipField`（L1902-1947）→ 改为 `_variableSelectField`
  - 页面 build 里的 `_variableChipField(...)` 调用点改为 `_variableSelectField(...)`（状态栏岛 L1698-1700 的 3 处、展开态 L1981-1987 的 7 处）
  - 顶层新增 `_VariableMultiSelectSheet`（两 State 共用）

## 可用组件（已确认签名）

- `showHyperosSheet<T>({required BuildContext context, required WidgetBuilder builder})` → `showModalBottomSheet` 包装（lib/ui/hyperos/hyperos_sheet.dart L254）
- `HyperosSheet({title, required child})`（L206）
- `HyperosCheckboxTile({required title, subtitle, required value, required onChanged})`（lib/ui/hyperos/hyperos_checkbox.dart L144）
- `HyperosButton`（确认按钮，hyperos_controls.dart L732）

## 设计

每个模板字段从"一排 ChoiceChip"改为"一个列表项（点击弹层多选）"：
1. 字段行：`HyperosListTile(icon: Icons.tune, title: label, details: 已选变量或'未选择', onTap: 弹层)`
2. 弹层：`showHyperosSheet` 内 `HyperosSheet`，列出 8 个 `HyperosCheckboxTile`（勾选多选）+ 底部确认按钮，确认后返回选中列表并 `Navigator.pop`
3. 写回：确认后 `_controllers[key].text = result.join(',')`（逗号分隔，格式不变）

**注意**：弹层只改 controller 文本，不立即持久化——沿用现有"保存"按钮统一保存（与现在 chip 行为一致）。

## Step 1: 确认 `_availableVariables` 与 `_selectedStage`

两个 State 各有 `_availableVariables`（8 项：课名/短课名/教室/教师/开始/结束/倒计时/正计时）和 `_tabOrder`/`_selectedStage`。`_variableSelectField(key, label)` 直接复用 `_controllers[key]`（key 形如 `'ticker_$_s'`）。

## Step 2: 在 _HyperFocusStatusIslandScreenState 改造 _variableChipField → _variableSelectField

删除 `_variableChipField`（L1619-1664），新增：
```dart
  Widget _variableSelectField(String key, String label) {
    final current = _controllers[key]?.text ?? '';
    final selected = current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
    return HyperosListTile(
      icon: Icons.tune,
      title: label,
      details: _selectedSummary(selected),
      onTap: () => _openVariableMultiSelect(key, selected),
    );
  }

  String _selectedSummary(List<String> selected) {
    if (selected.isEmpty) return '未选择';
    if (selected.length <= 4) return selected.join('、');
    return '${selected.sublist(0, 4).join('、')}…';
  }
```

## Step 3: 新增 _openVariableMultiSelect + _VariableMultiSelectSheet

`_openVariableMultiSelect`（在 State 内）：
```dart
  Future<void> _openVariableMultiSelect(String key, List<String> selected) async {
    final result = await showHyperosSheet<List<String>>(
      context: context,
      builder: (sheetContext) => _VariableMultiSelectSheet(
        variables: _availableVariables,
        initial: selected,
      ),
    );
    if (result == null) return;
    setState(() {
      _controllers[key]?.text = result.join(',');
    });
  }
```

顶层新增 `_VariableMultiSelectSheet`（两 State 共用，放在文件顶层，靠近两个 Screen 类附近）：
```dart
class _VariableMultiSelectSheet extends StatefulWidget {
  const _VariableMultiSelectSheet({
    required this.variables,
    required this.initial,
  });

  final List<String> variables;
  final List<String> initial;

  @override
  State<_VariableMultiSelectSheet> createState() =>
      _VariableMultiSelectSheetState();
}

class _VariableMultiSelectSheetState extends State<_VariableMultiSelectSheet> {
  late final Set<String> _selected = widget.initial.toSet();

  @override
  Widget build(BuildContext context) {
    return HyperosSheet(
      title: '选择显示信息',
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final v in widget.variables)
            HyperosCheckboxTile(
              title: v,
              value: _selected.contains(v),
              onChanged: (on) => setState(() {
                if (on) {
                  _selected.add(v);
                } else {
                  _selected.remove(v);
                }
              }),
            ),
          const SizedBox(height: 12),
          HyperosButton(
            label: '确定',
            expand: true,
            onPressed: () => Navigator.pop(context, _selected.toList()),
          ),
        ],
      ),
    );
  }
}
```

## Step 4: _HyperFocusExpandedIslandScreenState 同步

同样的改造：`_variableChipField`（L1902-1947）→ `_variableSelectField` + `_selectedSummary` + `_openVariableMultiSelect`（复用顶层 `_VariableMultiSelectSheet`）。页面 build 里 7 处 `_variableChipField(...)` 改为 `_variableSelectField(...)`。

## Step 5: 更新两页 build 调用点

状态栏岛页 build（L1698-1700）：`_variableChipField('ticker_$_s', '状态栏/息屏文本')` 等 3 处 → `_variableSelectField(...)`。展开态页 build（L1981-1987）：7 处 → `_variableSelectField(...)`。

## Step 6: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test`
Expected: +716 ~3 全绿

## Step 7: Commit

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: replace template chip picker with list-style multi-select sheet"
```
