# 设计：删除超级岛测试与诊断页"模板"调试区段

**日期:** 2026-07-31
**状态:** 已批准
**关联:** 超级岛"测试与诊断"页（`_HyperFocusTestingSettingsScreen`）的 `debugTemplates` 区段

## 背景

"测试与诊断"页的"模板"调试区段（`debugTemplates`）只显示课前/课中/课后各阶段模板字段是否为空，对排查发送问题没有价值（发送结果已由 `send_test_focus_*` 埋点覆盖）。`templatesLoaded` 状态字段也未被任何 UI 使用。用户要求删除该调试区段。

## 决策记录

- 只删测试与诊断页的模板**调试呈现**，不删模板系统本身（`loadHyperFocusTemplates`、自定义模板编辑器 `live_settings_subpages.dart` L1444-1619、设置入口 `timetable_settings_screen.dart` L1812）——发送测试通知仍用模板渲染内容（ticker/island 文本）
- `rawJson` 区段显示完整 `_debugStatus`：Kotlin 端删除 `templates` 块后，rawJson 自然不再包含模板数据，无需额外处理
- l10n 删除后运行 `flutter gen-l10n` 重新生成 `app_localizations*.dart`（生成文件不手动编辑）
- 现有 widget 测试无模板文本断言，不受影响

## 删除清单

### 1. Dart UI（`lib/screens/timetable_settings_screen.dart`）

- `_HyperFocusTestingSection.debugTemplates` 枚举成员（L2855）
- `_sections()` 中的 `_HyperFocusTestingSection.debugTemplates` 条目（L3080）
- `_buildSection` 中 `debugTemplates` 分支（L3303-3312）
- `_templateSummary` 方法（L3361-3368）
- `templates` map 参数链：L3029（取值）、L3058（传参）、L3095（参数声明）——仅被 debugTemplates 使用
- `templatesLoaded` 参数链：L3037（取值）、L3064（传参）、L3101（参数声明）——未被任何 UI 使用

### 2. Kotlin（`android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`）

- `buildHyperFocusDebugStatus` summary 中 `templatesLoaded` 字段（L2087）
- `buildHyperFocusDebugStatus` 中 `templates` 块（L2100 附近，按阶段 pre/active/post 上报模板是否为空的 linkedMapOf）

### 3. Dart service（`lib/services/miui_live_activities_service.dart`）

- 降级响应（L442）与测试辅助响应（L826）中的 `templatesLoaded` 字段

### 4. l10n

- 6 个 arb 文件（app_zh/app_zh_TW/app_zh_HK/app_en/app_ja/app_ko）中的 4 个 key：`hfTestingDebugTemplates`、`hfTestingTemplateStagePre`、`hfTestingTemplateStageActive`、`hfTestingTemplateStagePost`
- 运行 `flutter gen-l10n` 重新生成 `lib/l10n/app_localizations*.dart`

## 验证

1. `flutter analyze` — 与基线持平（8 个预存在 infos）
2. `flutter test` — +716 ~3 全绿
3. `gradlew assembleDebug` — BUILD SUCCESSFUL
4. 真机：测试与诊断页不再显示"模板"区段；发送测试通知正常（内容仍来自模板）
