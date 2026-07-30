# HyperFocus 可视化变量组装器 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 HyperFocus 自定义模板从裸模板语法改为可视化芯片选择——每字段从 8 个变量芯片中勾选要显示的内容，按顺序自动拼接。

**Architecture:** 
- Kotlin 侧存储从 `"即将上课：{课名}"` 改为逗号分隔变量名 `"课名"`，`resolveTemplate()` 改为解析变量名列表
- Dart 侧 `HyperFocusStageTemplateScreen` 从 `HyperosTextField` 改为 `ChoiceChip` 芯片选择器
- 存储格式仍是 SharedPreferences JSON，值语义从模板字符串变为变量名列表
- 旧格式检测：值包含 `{` → 视为旧格式，重置为新默认值

**Tech Stack:** Kotlin (MainActivity), Dart/Flutter (live_settings_subpages.dart), SharedPreferences

## Global Constraints

- YAGNI: 不支持自定义文字、不支持拖拽排序、不支持实时预览
- 变量名使用中文：`课名,短课名,教室,教师,开始,结束,倒计时,正计时`
- 所有 21 个字段统一的芯片顺序
- 默认值见 spec 文档

---

### Task 1: Kotlin `hfDefaultTemplates` 和 `resolveTemplate()` 改造

**Files:**
- Modify: `android/app/.../MainActivity.kt` (hfDefaultTemplates + resolveTemplate + loadHyperFocusTemplates)

**Interfaces:**
- Consumes: 现有 `hfDefaultTemplates` 结构、`resolveTemplate()` 签名
- Produces: 新格式默认值、新 `resolveTemplate()` 逻辑、迁移检测

- [ ] **Step 1: 更新 hfDefaultTemplates 默认值**

```kotlin
private val hfDefaultTemplates = mapOf(
    "ticker_pre" to "课名",
    "ticker_active" to "课名",
    "ticker_post" to "课名",
    "islandA_pre" to "教室",
    "islandA_active" to "短课名",
    "islandA_post" to "短课名",
    "islandB_pre" to "",
    "islandB_active" to "上课中",
    "islandB_post" to "已下课",
    "baseTitle_pre" to "课名",
    "baseTitle_active" to "课名",
    "baseTitle_post" to "课名",
    "baseContent_pre" to "开始,结束",
    "baseContent_active" to "开始,结束",
    "baseContent_post" to "开始,结束",
    "baseSubcontent_pre" to "教室",
    "baseSubcontent_active" to "教室",
    "baseSubcontent_post" to "教室",
    "hintTitle_pre" to "",
    "hintTitle_active" to "上课中",
    "hintTitle_post" to "已下课",
)
```

关键变更：
- `ticker_pre`: `"即将上课：{课名}"` → `"课名"`（去掉文字前缀）
- `islandA_pre`: `"{教室}"` → `"教室"`（去掉 `{}`）
- `islandB_pre`: `"上课"` → `""`（默认空，用户自己选）
- `baseContent_pre`: `"{开始} - {结束}"` → `"开始,结束"`（逗号分隔）
- `hintTitle_pre`: `"即将上课"` → `""`（默认空）
- `islandA_active/post`: `"{课名}"` → `"短课名"`
- `islandB_active`: `"下课"` → `"上课中"`
- `hintTitle_active/post`: 保持 `"上课中"/"已下课"`

- [ ] **Step 2: 重写 `resolveTemplate()` 支持新格式**

```kotlin
private fun resolveTemplate(
    tpl: String,
    courseName: String,
    shortName: String,
    location: String,
    teacher: String,
    startTime: String,
    endTime: String,
    countdownText: String,
    elapsedText: String,
): String {
    // Migration: old format with {variables}
    if (tpl.contains("{")) {
        var result = tpl
        result = result.replace("{课名}", courseName)
        result = result.replace("{短课名}", shortName.ifBlank { courseName })
        result = result.replace("{教室}", location.ifBlank { courseName })
        result = result.replace("{教师}", teacher)
        result = result.replace("{开始}", startTime)
        result = result.replace("{结束}", endTime)
        result = result.replace("{倒计时}", countdownText)
        result = result.replace("{正计时}", elapsedText)
        return result
    }
    // New format: comma-separated variable names
    val variableMap = mapOf(
        "课名" to courseName,
        "短课名" to shortName.ifBlank { courseName },
        "教室" to location.ifBlank { courseName },
        "教师" to teacher,
        "开始" to startTime,
        "结束" to endTime,
        "倒计时" to countdownText,
        "正计时" to elapsedText,
    )
    return tpl.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { variableMap[it] ?: "" }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
```

- [ ] **Step 3: 更新 `loadHyperFocusTemplates()` 添加迁移检测**

```kotlin
private fun loadHyperFocusTemplates(): Map<String, String> {
    val json = getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
        .getString("templates_json", null) ?: return hfDefaultTemplates
    return try {
        val obj = org.json.JSONObject(json)
        // Migration: if any value contains "{", the data is old format → reset to defaults
        for (key in obj.keys()) {
            val v = obj.optString(key, "")
            if (v.contains("{")) {
                return hfDefaultTemplates
            }
        }
        val merged = hfDefaultTemplates.toMutableMap()
        for (key in obj.keys()) {
            merged[key] = obj.optString(key, hfDefaultTemplates[key] ?: "")
        }
        merged
    } catch (_: Exception) {
        hfDefaultTemplates
    }
}
```

- [ ] **Step 4: 更新 Dart 侧默认模板常量**

在 `live_settings_subpages.dart` 的 `_HyperFocusStageTemplateScreenState` 中更新 `_defaultTemplates`（与 Kotlin 侧一致）：

```dart
static const _defaultTemplates = {
  'ticker_pre': '课名',
  'ticker_active': '课名',
  'ticker_post': '课名',
  'islandA_pre': '教室',
  'islandA_active': '短课名',
  'islandA_post': '短课名',
  'islandB_pre': '',
  'islandB_active': '上课中',
  'islandB_post': '已下课',
  'baseTitle_pre': '课名',
  'baseTitle_active': '课名',
  'baseTitle_post': '课名',
  'baseContent_pre': '开始,结束',
  'baseContent_active': '开始,结束',
  'baseContent_post': '开始,结束',
  'baseSubcontent_pre': '教室',
  'baseSubcontent_active': '教室',
  'baseSubcontent_post': '教室',
  'hintTitle_pre': '',
  'hintTitle_active': '上课中',
  'hintTitle_post': '已下课',
};
```

---

### Task 2: Dart UI — 从 TextField 改为芯片选择器

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart:1315-1499`

**Interfaces:**
- Consumes: `_selectedStage`, `_controllers` (or their replacements)
- Produces: 新的 `_variableChipField` 组件

- [ ] **Step 1: 定义变量列表常量**

在 `_HyperFocusStageTemplateScreenState` 中添加：

```dart
static const _availableVariables = [
  '课名', '短课名', '教室', '教师', '开始', '结束', '倒计时', '正计时',
];
```

- [ ] **Step 2: 添加 `_variableChipField` 方法替换 `_textFieldTile`**

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

- [ ] **Step 3: 替换 build() 中的 UI**

将 build() 中每个 `_textFieldTile` 及其外层的 `HyperosListGroup` 替换为直接调用 `_variableChipField`。

当前结构（每个 stage）：
```
HyperosSectionLabel('状态栏岛')
HyperosListGroup → _textFieldTile('ticker_$_s')

HyperosSectionLabel('岛内容')
HyperosListGroup → _textFieldTile('islandA_$_s') + 'islandB_$_s'

...

HyperosSectionLabel('阶段标签')
HyperosListGroup → _textFieldTile('hintTitle_$_s')
```

新结构（每个 stage）：
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

注意：删掉原来的 `Text('可用变量：...')` 提示文字，因为芯片已经展示可用变量。

完整 build() 方法：

```dart
@override
Widget build(BuildContext context) {
  return HyperosSubpage(
    onBack: () => Navigator.pop(context),
    title: const Text('自定义模板'),
    child: Column(
      children: [
        HyperosTabRow(
          tabs: ['课前', '课中', '课后'],
          selectedIndex: _tabOrder.indexOf(_selectedStage),
          onChanged: (i) => setState(() => _selectedStage = _tabOrder[i]),
        ),
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '点击选择要在各区域显示的信息',
                  style: HyperosTypography.listDetail(context),
                ),
                const SizedBox(height: 8),
                HyperosSectionLabel(text: '状态栏岛'),
                _variableChipField('ticker_$_s', '状态栏/息屏文本'),
                const HyperosSectionGap(),
                HyperosSectionLabel(text: '岛内容'),
                _variableChipField('islandA_$_s', '岛左侧文字'),
                _variableChipField('islandB_$_s', '岛右侧后缀'),
                const HyperosSectionGap(),
                HyperosSectionLabel(text: '展开态'),
                _variableChipField('baseTitle_$_s', '标题'),
                _variableChipField('baseContent_$_s', '内容'),
                _variableChipField('baseSubcontent_$_s', '副内容'),
                const HyperosSectionGap(),
                HyperosSectionLabel(text: '阶段标签'),
                _variableChipField('hintTitle_$_s', '阶段标签文字'),
                const HyperosSectionGap(),
                Row(
                  children: [
                    Expanded(
                      child: FButton(
                        onPress: _saveTemplates,
                        child: const Text('保存'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FButton(
                        onPress: _resetStage,
                        child: const Text('恢复默认'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}
```

- [ ] **Step 4: 移除 `_textFieldTile` 和 `_availableVariables` 内联**

删除旧的 `_textFieldTile` 方法（约第 1399-1410 行），确认 `_textFieldTile` 不再被任何代码引用。

---

### Task 3: 构建验证

- [ ] **Step 1: 检查 Kotlin 代码编译**

```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查 Flutter 代码编译**

```bash
flutter analyze lib/screens/live_settings_subpages.dart
```
Expected: No issues found

- [ ] **Step 3: 检查 CI 完整性**

```bash
cd .. && flutter build apk --debug 2>&1 | tail -20
```
Expected: Built build/app/outputs/flutter-apk/app-debug.apk

---

### Ticket List

- Task 1: Kotlin 侧默认值 + resolveTemplate + 迁移
- Task 2: Dart 侧 UI 芯片选择器
- Task 3: 构建验证
