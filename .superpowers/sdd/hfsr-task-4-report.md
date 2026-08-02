# Task 4 Report: 状态栏岛自定义页 + 展开态自定义页

## Status: DONE

## What was created

Modified `lib/screens/live_settings_subpages.dart` — added two new template editor screens that duplicate the Task-2-upgraded shared logic (`_loadTemplates` preferring `hfTemplatesJson` with Kotlin prefs migration, `_saveTemplates` dual-write, `_persistTemplatesToSettings`, `_resetStage`, `_variableChipField`, `dispose`) verbatim:

- `HyperFocusStatusIslandScreen` — `live_settings_subpages.dart:1622` (State `_HyperFocusStatusIslandScreenState`)
  - `_controllers` keys: ticker/islandA/islandB × pre/active/post = 9
  - `_defaultTemplates`: ticker→课名×3, islandA→教室/短课名/短课名, islandB→''/上课中/已下课
  - `_availableVariables`: 原 8 个变量 (课名/短课名/教室/教师/开始/结束/倒计时/正计时)
  - build: `HyperosSubpage(title: '状态栏岛自定义')` + `HyperosTabRow(['课前','课中','课后'])` + 提示文本 + `HyperosSectionLabel('状态栏岛')` + 3 个 chip 字段 (ticker_$_s→状态栏/息屏文本, islandA_$_s→岛左侧文字, islandB_$_s→岛右侧后缀) + 保存/恢复默认 FButton 行

- `HyperFocusExpandedIslandScreen` — `live_settings_subpages.dart:1848` (State `_HyperFocusExpandedIslandScreenState`)
  - `_controllers` keys: baseTitle/baseContent/baseSubcontent/hintTitle/hintContent/hintSubcontent/hintSubtitle × pre/active/post = 21
  - `_defaultTemplates`: baseTitle→课名×3, baseContent→开始,结束×3, baseSubcontent→教室×3, hintTitle→''/上课中/已下课, hintContent→即将上课/距离下课/已经下课 (对齐 Kotlin 默认), hintSubcontent→空×3, hintSubtitle→空×3
  - `_availableVariables`: 原 8 个变量
  - build: `HyperosSubpage(title: '展开态自定义')` + `HyperosTabRow` + 提示文本 + `HyperosSectionLabel('展开态')` + 7 个 chip 字段 (baseTitle_$_s→主要标题, baseContent_$_s→次要文本1, baseSubcontent_$_s→次要文本2, hintTitle_$_s→主要小文本1, hintContent_$_s→前置文本1, hintSubcontent_$_s→前置文本2, hintSubtitle_$_s→主要小文本2) + 保存/恢复默认 FButton 行

## Step 3: 保留旧类壳

`HyperFocusStageTemplateScreen` (`live_settings_subpages.dart:1374`) **未改动**（菜单入口仍引用它，Task 5 统一处理入口）。

## Test results

- `flutter analyze`: **8 infos, 0 errors** — 与基线一致；8 个 info 全部为预存在（course_import_screen.dart ×3、miui_live_activities_service.dart ×5），新代码零告警
- `flutter test test/widgets/hyper_focus_testing_screen_test.dart`: **2/2 passed**
- `flutter test`（全量）: **+716 ~3 All tests passed** — 与基线一致

## Commit

- `3d7b22b` feat: split hyperfocus template editor into status bar and expanded island screens
  - `git add lib/screens/live_settings_subpages.dart` → 468 insertions, 0 deletions（纯新增，旧类未触碰）

## Self-review findings

- 两个新类 key 列表、字段标签、默认值均符合 brief（尤其 hintContent 即将上课/距离下课/已经下课，hintSubcontent/hintSubtitle 为空）
- 所有共享逻辑（_loadTemplates/_saveTemplates/_persistTemplatesToSettings/_resetStage/_variableChipField/dispose/initState）逐字复制
- 两个新类互不引用，无循环依赖
- 旧类未改动（git diff 无删除行），菜单仍可编译
- analyze/test 均符合基线

## Concerns

- 无。两个新类暂未被任何入口引用（按 brief 预期，Task 5 处理菜单入口后接入）。
