# Task 2: Dart UI — 从 TextField 改为芯片选择器

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart` (仅 `_HyperFocusStageTemplateScreenState` 类)

## 目标

将 `HyperFocusStageTemplateScreen` 中每个字段的 `HyperosTextField` 替换为 8 个变量芯片（`ChoiceChip`），用户点击芯片切换选中/未选中。

## 具体变更

### 1. 添加变量列表常量

```dart
static const _availableVariables = [
  '课名', '短课名', '教室', '教师', '开始', '结束', '倒计时', '正计时',
];
```

### 2. 添加 `_variableChipField` 方法替换 `_textFieldTile`

```dart
Widget _variableChipField(String key, String label) {
  final current = _controllers[key]?.text ?? '';
  final selected = current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();
  return Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: HyperosTypography.listTitle(context)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: _availableVariables.map((v) {
            final isOn = selected.contains(v);
            return ChoiceChip(
              label: Text(v, style: TextStyle(
                fontSize: 13,
                color: isOn ? Colors.white : null,
              )),
              selected: isOn,
              onSelected: (on) {
                setState(() {
                  final list = current.isEmpty
                      ? <String>[]
                      : current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
                  if (on && !list.contains(v)) {
                    list.add(v);
                  } else if (!on) {
                    list.remove(v);
                  }
                  _controllers[key]?.text = list.join(',');
                });
              },
              selectedColor: Theme.of(context).colorScheme.primary,
              backgroundColor: Theme.of(context).colorScheme.surfaceContainerHighest,
              side: isOn ? BorderSide.none : BorderSide(color: Theme.of(context).dividerColor),
              visualDensity: VisualDensity.compact,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            );
          }).toList(),
        ),
      ],
    ),
  );
}
```

### 3. 替换 build() 中的 UI

将 build() 中的每个 `_textFieldTile` 及其 `HyperosListGroup` 包装替换为直接调用 `_variableChipField`。

当前：
```
HyperosSectionLabel('状态栏岛')
HyperosListGroup → _textFieldTile('ticker_$_s')

HyperosSectionLabel('岛内容')
HyperosListGroup → _textFieldTile('islandA_$_s') + 'islandB_$_s'

HyperosSectionLabel('展开态')
HyperosListGroup → _textFieldTile('baseTitle_$_s') + 'baseContent_$_s' + 'baseSubcontent_$_s'

HyperosSectionLabel('阶段标签')
HyperosListGroup → _textFieldTile('hintTitle_$_s')
```

新结构（删除 HyperosListGroup 和 _textFieldTile，改用 _variableChipField）：
```
HyperosSectionLabel('状态栏岛')
_variableChipField('ticker_$_s', '状态栏/息屏文本')

HyperosSectionLabel('岛内容')
_variableChipField('islandA_$_s', '岛左侧文字')
_variableChipField('islandB_$_s', '岛右侧后缀')

HyperosSectionLabel('展开态')
_variableChipField('baseTitle_$_s', '标题')
_variableChipField('baseContent_$_s', '内容')
_variableChipField('baseSubcontent_$_s', '副内容')

HyperosSectionLabel('阶段标签')
_variableChipField('hintTitle_$_s', '阶段标签文字')
```

### 4. 删除 `_textFieldTile` 方法

删除约第 1399-1410 行的 `_textFieldTile` 方法（不再需要）。

### 5. 更新提示文字

将 build() 中原来的
```dart
Text('可用变量：{课名} {短课名} {教室} {教师} {开始} {结束} {倒计时} {正计时}')
```
改为
```dart
Text('点击选择要在各区域显示的信息')
```

## 注意事项

- `_controllers` 结构不变，仍然是 `Map<String, TextEditingController>`
- 值格式保持逗号分隔字符串（如 `"课名,教室"`）
- `_loadTemplates`, `_saveTemplates`, `_resetStage` 方法不需要修改
- 导入可能需要添加 `import 'package:flutter/material.dart';`（如果不在文件顶部）

## 报告要求

写入 `.superpowers/sdd/task-2-report.md`：
1. 修改的文件及变更摘要
2. `flutter analyze` 结果
3. 任何问题或注意事项
