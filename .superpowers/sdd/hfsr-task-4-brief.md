# Task 4 Brief: 状态栏岛自定义页 + 展开态自定义页

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 4（按实际代码调整）

## Global Constraints（本项目所有任务适用）

- 模板编辑范式保持"变量点选"（`_variableChipField`，逗号分隔变量列表）
- 新增模板 key：`hintContent_*`（前置文本1）、`hintSubcontent_*`（前置文本2）、`hintSubtitle_*`（主要小文本2）
- UI 全部用 Hyperos* 组件（HyperosSubpage/HyperosListView/HyperosSectionLabel/HyperosListGroup/HyperosTabRow/FButton）
- `flutter analyze` 基线：8 个预存在 infos，0 error
- `flutter test` 基线：+716 ~3

## Files

- Modify: `lib/screens/live_settings_subpages.dart`
  - 将现有 `HyperFocusStageTemplateScreen`（约 L1441-1640）拆分为两个新类：
    - `HyperFocusStatusIslandScreen`（状态栏岛：ticker/islandA/islandB × 3 阶段）
    - `HyperFocusExpandedIslandScreen`（展开态：baseTitle/baseContent/baseSubcontent/hintTitle/hintContent/hintSubcontent/hintSubtitle × 3 阶段）
  - 删除原 `HyperFocusStageTemplateScreen`
  - 复用现有 `_variableChipField`（L1522-1567）、`_defaultTemplates` 结构、`_loadTemplates`/`_saveTemplates`/`_resetStage` 逻辑（Task 2 已改造为双写）

## 背景

Task 2（026ccae）已把 `HyperFocusStageTemplateScreen` 的 `_loadTemplates`（优先 TimetableSettings.hfTemplatesJson + Kotlin prefs 迁移）和 `_saveTemplates`（双写）改造完成。本任务把这些逻辑原样复制到两个新页面，只是 `_controllers` 的 key 列表和 UI 分区不同。

注意：删除 `HyperFocusStageTemplateScreen` 后，`timetable_settings_screen.dart` 的菜单入口（L1810-1824"自定义模板"tile）会悬空——**Task 5 会重排菜单**。本任务先把该入口改指向临时目标或保留原类名占位，最终由 Task 5 处理。**最简单做法**：本任务保留旧类名 `HyperFocusStageTemplateScreen` 作为壳，内部改为跳转到两个新页（或直接在本任务把菜单入口的两个 tile 加好，删掉旧 tile）。以 Task 5 为准：本任务仅创建两个新页面类，暂不删旧类（避免悬空），Task 5 统一处理入口。

## Step 1: 创建 HyperFocusStatusIslandScreen

复制 `HyperFocusStageTemplateScreen` 结构，改造：

- 类名：`HyperFocusStatusIslandScreen`，State `_HyperFocusStatusIslandScreenState`
- `_controllers` key 列表：`['ticker', 'islandA', 'islandB']`（× pre/active/post = 9 个）
- `_defaultTemplates` 只含这 9 个 key，值沿用原默认（ticker→课名、islandA→教室/短课名/短课名、islandB→空/上课中/已下课）
- `_availableVariables` 沿用原 8 个变量
- build：`HyperosSubpage(title: '状态栏岛自定义')` + `HyperosTabRow(['课前','课中','课后'])` + 提示文本（沿用"点击选择要在各区域显示的信息"）+ `HyperosSectionLabel('状态栏岛')` + 3 个 `_variableChipField`：
  - `'ticker_$_s'` → '状态栏/息屏文本'
  - `'islandA_$_s'` → '岛左侧文字'
  - `'islandB_$_s'` → '岛右侧后缀'
  - 保存/恢复默认 FButton 行（沿用）
- 继承 Task 2 的 `_loadTemplates`（优先 hfTemplatesJson）/`_saveTemplates`（双写）/`_persistTemplatesToSettings`/`_resetStage`/`_variableChipField`/dispose 逻辑（原样复制）

## Step 2: 创建 HyperFocusExpandedIslandScreen

同样复制改造：

- 类名：`HyperFocusExpandedIslandScreen`，State `_HyperFocusExpandedIslandScreenState`
- `_controllers` key 列表：`['baseTitle', 'baseContent', 'baseSubcontent', 'hintTitle', 'hintContent', 'hintSubcontent', 'hintSubtitle']`（× pre/active/post = 21 个）
- `_defaultTemplates` 含 21 个 key：
  - baseTitle/pre/active/post → 课名
  - baseContent/pre/active/post → 开始,结束
  - baseSubcontent/pre/active/post → 教室
  - hintTitle/pre → 空，active → 上课中，post → 已下课
  - hintContent/pre → 即将上课，active → 距离下课，post → 已经下课（对齐 Kotlin 默认）
  - hintSubcontent/pre/active/post → 空
  - hintSubtitle/pre/active/post → 空
- `_availableVariables` 沿用原 8 个
- build：`HyperosSubpage(title: '展开态自定义')` + `HyperosTabRow` + 提示文本 + `HyperosSectionLabel('展开态')` + 7 个 `_variableChipField`：
  - `'baseTitle_$_s'` → '主要标题'
  - `'baseContent_$_s'` → '次要文本1'
  - `'baseSubcontent_$_s'` → '次要文本2'
  - `'hintTitle_$_s'` → '主要小文本1'
  - `'hintContent_$_s'` → '前置文本1'
  - `'hintSubcontent_$_s'` → '前置文本2'
  - `'hintSubtitle_$_s'` → '主要小文本2'
  - 保存/恢复默认 FButton 行
- 继承同样的 `_loadTemplates`/`_saveTemplates`/`_persistTemplatesToSettings`/`_resetStage`/`_variableChipField`/dispose

## Step 3: 保留旧类壳（Task 5 处理入口）

`HyperFocusStageTemplateScreen` 暂**不删除**（Task 5 统一处理菜单入口）。可以不做任何改动。

## Step 4: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error（两个新类可能产生 unused warning？——类被定义但未引用会怎样：Dart 顶层类未使用不会有 warning。但原 `HyperFocusStageTemplateScreen` 仍被菜单引用，正常）

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 绿（不涉及模板页）

Run: `flutter test`（全量，确认无破坏）
Expected: +716 ~3 绿

## Step 5: Commit

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: split hyperfocus template editor into status bar and expanded island screens"
```
