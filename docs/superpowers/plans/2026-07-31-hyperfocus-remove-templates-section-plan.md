# 删除超级岛测试页模板调试区段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除超级岛"测试与诊断"页的模板调试区段及相关字段（Dart UI、Kotlin `buildHyperFocusDebugStatus`、service 响应、l10n），模板系统本身与发送渲染保留。

**Architecture:** 纯删除改动，分三层：Dart UI + l10n（Task 1）、Kotlin + service（Task 2）、全量回归验证（Task 3）。

**Tech Stack:** Flutter（Dart）、Kotlin、flutter gen-l10n

## Global Constraints

- 只删调试呈现，不删模板系统（`loadHyperFocusTemplates`、模板编辑器、设置入口 L1812、发送渲染）
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不改变 `_debugStatus` 的其他任何字段与 UI 行为

---

### Task 1: Dart UI + l10n 删除

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart`
  - 删除：`_HyperFocusTestingSection.debugTemplates` 枚举成员（L2855）；`_sections()` 中 `_HyperFocusTestingSection.debugTemplates,` 行（L3080）；`_buildSection` 中 `debugTemplates` switch 分支（L3303-3312）；`_templateSummary` 方法（L3361-3368）
  - 删除参数链：L3029 `final templates = _debugSectionMap(_debugStatus?['templates']);`；L3058 `templates: templates,`；L3095 `required Map<String, dynamic> templates,`；L3037 `final templatesLoaded = summary['templatesLoaded'] == true;`；L3064 `templatesLoaded: templatesLoaded,`；L3101 `required bool templatesLoaded,`
- Modify: `lib/l10n/app_zh.arb`（删 4 个 key：hfTestingDebugTemplates、hfTestingTemplateStagePre、hfTestingTemplateStageActive、hfTestingTemplateStagePost）
- Modify: 同 4 个 key × `app_zh_TW.arb`、`app_zh_HK.arb`、`app_en.arb`、`app_ja.arb`、`app_ko.arb`
- 生成：`flutter gen-l10n` 重新生成 `lib/l10n/app_localizations*.dart`

**Interfaces:**
- Consumes: 现有 `_buildSection` 签名（其余参数不变）
- Produces: `_sections()` 不再包含 debugTemplates；`_buildSection` 签名去掉 `templates`/`templatesLoaded` 两个参数

- [ ] **Step 1: 删除 Dart UI 代码**

在 `lib/screens/timetable_settings_screen.dart` 中依次删除：
1. `_HyperFocusTestingSection.debugTemplates,`（枚举成员，约 L2855）
2. `_sections()` 中 `_HyperFocusTestingSection.debugTemplates,`（约 L3080）
3. `_buildSection` 中整个 debugTemplates 分支：
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
   - `final templates = _debugSectionMap(_debugStatus?['templates']);`（L3029）
   - `final templatesLoaded = summary['templatesLoaded'] == true;`（L3037）
   - `templates: templates,`（L3058）
   - `templatesLoaded: templatesLoaded,`（L3064）
   - `required Map<String, dynamic> templates,`（L3095）
   - `required bool templatesLoaded,`（L3101）

注意：`_buildSection` 调用处与声明处都删；`_debugSectionMap`/`_debugValueText` 等工具方法仍被其他区段使用，不能删。

- [ ] **Step 2: 删除 l10n keys 并重新生成**

在 6 个 arb 文件中删除 4 个 key（各文件行号不同，按 key 名精确删除）：

```json
"hfTestingDebugTemplates": "...",
"hfTestingTemplateStagePre": "...",
"hfTestingTemplateStageActive": "...",
"hfTestingTemplateStagePost": "...",
```

Run: `flutter gen-l10n`
Expected: 成功，`lib/l10n/app_localizations*.dart` 中相关 getter 已移除

- [ ] **Step 3: 运行 analyze 确认无遗漏引用**

Run: `flutter analyze`
Expected: 8 个预存在 infos 且**无** `hfTesting` 相关未定义 getter 报错（若报错说明有遗漏引用，补删）

- [ ] **Step 4: 运行测试**

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 全绿（测试 mock 中的 `templatesLoaded` 多余字段无害，不影响断言）

- [ ] **Step 5: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart lib/l10n/
git commit -m "refactor: drop templates debug section from hyperfocus testing screen"
```

---

### Task 2: Kotlin + service 删除

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - 删除 `buildHyperFocusDebugStatus` summary 中 `"templatesLoaded" to templates.isNotEmpty(),`（约 L2087）
  - 删除 `"templates" to linkedMapOf(...)` 整个块（约 L2100-2120，pre/active/post 阶段模板为空的汇总）——删除前先确认 `templates` 变量（`loadHyperFocusTemplates` 调用）是否还有其他用途：若无则连同其声明一起删，若有则保留
- Modify: `lib/services/miui_live_activities_service.dart`
  - 删除降级响应中的 `'templatesLoaded': false,`（约 L442）与测试辅助响应中的 `'templatesLoaded': true,`（约 L826）

**Interfaces:**
- Consumes: `buildHyperFocusDebugStatus`（其余字段不变）
- Produces: 状态 JSON 不再含 `templates`/`templatesLoaded` 字段

- [ ] **Step 1: 删除 Kotlin 字段**

在 `MainActivity.kt` `buildHyperFocusDebugStatus` 中：
1. 删除 summary 里 `"templatesLoaded" to templates.isNotEmpty(),`
2. 删除 `"templates" to linkedMapOf(...)` 块（含 pre/active/post 三阶段）
3. 检查 `val templates = loadHyperFocusTemplates(context)`（或类似声明）：grep 该变量在本函数内的其他使用，若仅用于上述两处则一并删除声明

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 删除 service 字段**

在 `miui_live_activities_service.dart` 中删除：
1. 降级响应（约 L442）：`'templatesLoaded': false,`
2. 测试辅助响应（约 L826）：`'templatesLoaded': true,`

Run: `flutter analyze`
Expected: 8 个预存在 infos，无新增

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt lib/services/miui_live_activities_service.dart
git commit -m "refactor: drop templates fields from hyperfocus debug status"
```

---

### Task 3: 全量回归验证

**Files:**
- 无代码改动（纯验证）

- [ ] **Step 1: analyze**

Run: `flutter analyze`
Expected: 8 个预存在 infos（0 error、0 warning、无新增）

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: +716 ~3 全绿

- [ ] **Step 3: 构建**

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL
