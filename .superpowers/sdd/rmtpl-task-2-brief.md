# Task 2 Brief: Kotlin + service 删除

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-remove-templates-section-plan.md` Task 2

## Global Constraints（本项目所有任务适用）

- 只删调试呈现，不删模板系统（`loadHyperFocusTemplates`、模板编辑器、发送渲染）
- `flutter analyze` 基线：8 个预存在 infos，不新增
- `flutter test` 基线：+716 ~3 全绿
- 不改变 `_debugStatus` 的其他任何字段与 UI 行为

## Files

- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
- Modify: `lib/services/miui_live_activities_service.dart`

## Step 1: 删除 Kotlin 字段

在 `MainActivity.kt` `buildHyperFocusDebugStatus` 中：
1. 删除 summary 里 `"templatesLoaded" to templates.isNotEmpty(),`（约 L2087）
2. 删除 `"templates" to linkedMapOf(...)` 块（约 L2100 附近，含 pre/active/post 三阶段模板为空的汇总）
3. 检查 `templates` 变量（`loadHyperFocusTemplates(context)` 的调用结果）在本函数内的其他使用：grep 确认，若仅用于上述两处则一并删除该变量声明；若还有其他用途则保留

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

## Step 2: 删除 service 字段

在 `lib/services/miui_live_activities_service.dart` 中删除：
1. 降级响应（约 L442）：`'templatesLoaded': false,`
2. 测试辅助响应（约 L826）：`'templatesLoaded': true,`

Run: `flutter analyze`
Expected: 8 个预存在 infos，无新增

## Step 3: Commit

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt lib/services/miui_live_activities_service.dart
git commit -m "refactor: drop templates fields from hyperfocus debug status"
```
