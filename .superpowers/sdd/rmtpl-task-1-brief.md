# Task 1 Brief: Dart UI + l10n 删除

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-remove-templates-section-plan.md` Task 1

## Global Constraints（本项目所有任务适用）

- 只删调试呈现，不删模板系统（`loadHyperFocusTemplates`、模板编辑器、设置入口 L1812、发送渲染）
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不改变 `_debugStatus` 的其他任何字段与 UI 行为

## Files

- Modify: `lib/screens/timetable_settings_screen.dart`
- Modify: `lib/l10n/app_zh.arb`、`app_zh_TW.arb`、`app_zh_HK.arb`、`app_en.arb`、`app_ja.arb`、`app_ko.arb`
- 生成：`flutter gen-l10n` 重新生成 `lib/l10n/app_localizations*.dart`

## Step 1: 删除 Dart UI 代码

在 `lib/screens/timetable_settings_screen.dart` 中依次删除：
1. `_HyperFocusTestingSection.debugTemplates,`（枚举成员，约 L2855）
2. `_sections()` 中 `_HyperFocusTestingSection.debugTemplates,`（约 L3080）
3. `_buildSection` 中整个 debugTemplates 分支（约 L3303-3312）：
```dart
      _HyperFocusTestingSection.debugTemplates => _buildDebugSection(
        context: context,
        title: l10n.hfTestingDebugTemplates,
        entries: {
          l10n.hfTestingTemplateStagePre: _templateSummary(templates['pre'], l10n),
          l10n.hfTestingTemplateStageActive:
              _templateSummary(templates['active'], l10n),
          l10n.hfTestingTemplateStagePost: _templateSummary(templates['post'], l10n),
        },
      ),
```
4. `_templateSummary` 整个方法（约 L3361-3368）
5. 参数链 6 处：
   - `final templates = _debugSectionMap(_debugStatus?['templates']);`（约 L3029）
   - `final templatesLoaded = summary['templatesLoaded'] == true;`（约 L3037）
   - `templates: templates,`（约 L3058）
   - `templatesLoaded: templatesLoaded,`（约 L3064）
   - `required Map<String, dynamic> templates,`（约 L3095）
   - `required bool templatesLoaded,`（约 L3101）

注意：`_buildSection` 调用处与声明处都删；`_debugSectionMap`/`_debugValueText` 等工具方法仍被其他区段使用，不能删。

## Step 2: 删除 l10n keys 并重新生成

在 6 个 arb 文件中删除 4 个 key（各文件行号不同，按 key 名精确删除）：
`hfTestingDebugTemplates`、`hfTestingTemplateStagePre`、`hfTestingTemplateStageActive`、`hfTestingTemplateStagePost`

Run: `flutter gen-l10n`
Expected: 成功，`lib/l10n/app_localizations*.dart` 中相关 getter 已移除

## Step 3: 运行 analyze 确认无遗漏引用

Run: `flutter analyze`
Expected: 8 个预存在 infos 且**无** `hfTesting` 相关未定义 getter 报错（若报错说明有遗漏引用，补删）

## Step 4: 运行测试

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 全绿（测试 mock 中的 `templatesLoaded` 多余字段无害，不影响断言）

## Step 5: Commit

```bash
git add lib/screens/timetable_settings_screen.dart lib/l10n/
git commit -m "refactor: drop templates debug section from hyperfocus testing screen"
```
