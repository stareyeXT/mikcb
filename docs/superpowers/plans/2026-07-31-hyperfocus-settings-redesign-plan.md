# 超级岛设置页重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重做超级岛设置页：UI 对齐 Live 风格（Hyperos 组件分组导航），新增状态栏岛自定义/展开态自定义/岛消失时间/视觉选项（颜色/发光/岛A图标），模板存储统一到 TimetableSettings，Kotlin 渲染扩展新模板字段与配置化 islandTimeout。

**Architecture:** 分三层：① Flutter 模型/存储（TimetableSettings 扩展 + 模板双写迁移）→ ② Kotlin 渲染扩展（buildHyperFocusBundle 消费新字段）→ ③ UI 页面（新状态栏岛/展开态/消失时间页 + 菜单重排）。

**Tech Stack:** Flutter（Dart）、Kotlin、MethodChannel（com.mutx163.qingyu/miui_live）、flutter gen-l10n

## Global Constraints

- 模板编辑范式保持"变量点选"（逗号分隔变量列表，`resolveTemplate` 兼容），不改为自由文本
- 新增模板 key 固定为：`hintContent_*`（前置文本1）、`hintSubcontent_*`（前置文本2）、`hintSubtitle_*`（主要小文本2）
- 视觉默认值：`hfIconAEnabled=true`、`hfStatusTextColor=#FFFFFFFF`、`hfOutEffectStatusEnabled=true`、`hfOutEffectStatusColor=#FFFFFFFF`、`hfOutEffectExpandEnabled=true`、`hfOutEffectExpandColor=#FFFFFFFF`
- 消失时间默认：`hfIslandTimeoutPre=300`、`hfIslandTimeoutActive=600`、`hfIslandTimeoutPost=600`；UI 限界 30~3600s
- 死字段删除：`hfShowCourseName`/`hfShowLocation`/`hfShowCountdown`/`hfCustomTitle`/`hfCustomTitleColor`（无消费）
- `flutter analyze` 基线：8 个预存在 infos；`flutter test` 基线：+716 ~3
- 全部 UI 用 Hyperos* 组件，保持现有设置页风格

---

### Task 1: TimetableSettings 模型扩展

**Files:**
- Modify: `lib/models/timetable_settings.dart`
  - L1083-1088 附近：删除 `hfShowCourseName`/`hfShowLocation`/`hfShowCountdown`/`hfCustomTitle`/`hfCustomTitleColor`，保留 `hfTemplatesJson`
  - 新增字段（在 hf 字段区）：`hfIslandTimeoutPre`/`hfIslandTimeoutActive`/`hfIslandTimeoutPost`（int）、`hfIconAEnabled`（bool）、`hfStatusTextColor`（String）、`hfOutEffectStatusEnabled`（bool）、`hfOutEffectStatusColor`（String）、`hfOutEffectExpandEnabled`（bool）、`hfOutEffectExpandColor`（String）
- 同步修改：默认值（L1239-1245 附近）、toJson（L1551-1556 附近）、fromJson（L1873-1878 附近）、copyWith（L2090-2095 附近、L2348-2353 附近）

**Interfaces:**
- Produces: `TimetableSettings` 新增 getter：`hfIslandTimeoutPre`, `hfIslandTimeoutActive`, `hfIslandTimeoutPost` (int), `hfIconAEnabled` (bool), `hfStatusTextColor`, `hfOutEffectStatusColor`, `hfOutEffectExpandColor` (String), `hfOutEffectStatusEnabled`, `hfOutEffectExpandEnabled` (bool)；`copyWith` 支持同名参数

- [ ] **Step 1: 删除死字段声明**

在 `lib/models/timetable_settings.dart` 删除：
```dart
  final bool hfShowCourseName;
  final bool hfShowLocation;
  final bool hfShowCountdown;
  final String hfCustomTitle;
  final String hfCustomTitleColor;
```
保留 `final String hfTemplatesJson;`。

- [ ] **Step 2: 新增字段声明**

在保留的 `hfTemplatesJson` 声明后新增：
```dart
  final int hfIslandTimeoutPre;
  final int hfIslandTimeoutActive;
  final int hfIslandTimeoutPost;
  final bool hfIconAEnabled;
  final String hfStatusTextColor;
  final bool hfOutEffectStatusEnabled;
  final String hfOutEffectStatusColor;
  final bool hfOutEffectExpandEnabled;
  final String hfOutEffectExpandColor;
```

- [ ] **Step 3: 更新默认值**

删除死字段默认值，新增：
```dart
    this.hfIslandTimeoutPre = 300,
    this.hfIslandTimeoutActive = 600,
    this.hfIslandTimeoutPost = 600,
    this.hfIconAEnabled = true,
    this.hfStatusTextColor = '#FFFFFFFF',
    this.hfOutEffectStatusEnabled = true,
    this.hfOutEffectStatusColor = '#FFFFFFFF',
    this.hfOutEffectExpandEnabled = true,
    this.hfOutEffectExpandColor = '#FFFFFFFF',
```

- [ ] **Step 4: 更新 toJson**

删除死字段 5 行，新增：
```dart
      'hfIslandTimeoutPre': hfIslandTimeoutPre,
      'hfIslandTimeoutActive': hfIslandTimeoutActive,
      'hfIslandTimeoutPost': hfIslandTimeoutPost,
      'hfIconAEnabled': hfIconAEnabled,
      'hfStatusTextColor': hfStatusTextColor,
      'hfOutEffectStatusEnabled': hfOutEffectStatusEnabled,
      'hfOutEffectStatusColor': hfOutEffectStatusColor,
      'hfOutEffectExpandEnabled': hfOutEffectExpandEnabled,
      'hfOutEffectExpandColor': hfOutEffectExpandColor,
```

- [ ] **Step 5: 更新 fromJson**

删除死字段 5 行，新增：
```dart
      hfIslandTimeoutPre: (json['hfIslandTimeoutPre'] as num?)?.toInt() ?? 300,
      hfIslandTimeoutActive: (json['hfIslandTimeoutActive'] as num?)?.toInt() ?? 600,
      hfIslandTimeoutPost: (json['hfIslandTimeoutPost'] as num?)?.toInt() ?? 600,
      hfIconAEnabled: json['hfIconAEnabled'] as bool? ?? true,
      hfStatusTextColor: json['hfStatusTextColor'] as String? ?? '#FFFFFFFF',
      hfOutEffectStatusEnabled: json['hfOutEffectStatusEnabled'] as bool? ?? true,
      hfOutEffectStatusColor: json['hfOutEffectStatusColor'] as String? ?? '#FFFFFFFF',
      hfOutEffectExpandEnabled: json['hfOutEffectExpandEnabled'] as bool? ?? true,
      hfOutEffectExpandColor: json['hfOutEffectExpandColor'] as String? ?? '#FFFFFFFF',
```

- [ ] **Step 6: 更新 copyWith**

参数区（删除死字段 5 个参数，新增 9 个）：
```dart
    int? hfIslandTimeoutPre,
    int? hfIslandTimeoutActive,
    int? hfIslandTimeoutPost,
    bool? hfIconAEnabled,
    String? hfStatusTextColor,
    bool? hfOutEffectStatusEnabled,
    String? hfOutEffectStatusColor,
    bool? hfOutEffectExpandEnabled,
    String? hfOutEffectExpandColor,
```
赋值区：
```dart
      hfIslandTimeoutPre: hfIslandTimeoutPre ?? this.hfIslandTimeoutPre,
      hfIslandTimeoutActive: hfIslandTimeoutActive ?? this.hfIslandTimeoutActive,
      hfIslandTimeoutPost: hfIslandTimeoutPost ?? this.hfIslandTimeoutPost,
      hfIconAEnabled: hfIconAEnabled ?? this.hfIconAEnabled,
      hfStatusTextColor: hfStatusTextColor ?? this.hfStatusTextColor,
      hfOutEffectStatusEnabled: hfOutEffectStatusEnabled ?? this.hfOutEffectStatusEnabled,
      hfOutEffectStatusColor: hfOutEffectStatusColor ?? this.hfOutEffectStatusColor,
      hfOutEffectExpandEnabled: hfOutEffectExpandEnabled ?? this.hfOutEffectExpandEnabled,
      hfOutEffectExpandColor: hfOutEffectExpandColor ?? this.hfOutEffectExpandColor,
```

- [ ] **Step 7: 搜索死字段残留引用**

Run: `rg "hfShowCourseName|hfShowLocation|hfShowCountdown|hfCustomTitle|hfCustomTitleColor" lib/`
Expected: 仅剩 `HyperFocusDisplayScreen`（live_settings_subpages.dart L1406/L1413/L1420）的引用——**Task 5 会删除该页面**，此处暂不处理；如还有其它 lib/ 引用（除该文件），先在本任务修正

- [ ] **Step 8: 验证**

Run: `flutter analyze`
Expected: 除 `HyperFocusDisplayScreen` 死字段引用外无新增 error（该 3 处 use 属于 Task 5 删除范围，可临时保留）
Run: `flutter test test/models/timetable_settings_test.dart 2>&1`（若文件存在）
Expected: 通过；若该测试文件不存在则跳过

- [ ] **Step 9: Commit**

```bash
git add lib/models/timetable_settings.dart
git commit -m "feat: extend timetable settings with hyperfocus visual and timeout fields"
```

---

### Task 2: 模板存储迁移与 service 层

**Files:**
- Modify: `lib/services/miui_live_activities_service.dart`
  - `saveHyperFocusTemplates`（L682-694）、`loadHyperFocusTemplates`（L696-699）改双写
- Modify: `lib/screens/live_settings_subpages.dart`
  - `HyperFocusStageTemplateScreen._loadTemplates`（L1491-1500）、`_saveTemplates`（L1502-1511）改读写 TimetableSettings + Kotlin 双写

**Interfaces:**
- Consumes: Task 1 的 `TimetableSettings.hfTemplatesJson`（String，JSON 编码的 `Map<String,String>`）
- Produces: `MiuiLiveActivitiesService.saveHyperFocusTemplates(map)` 现写 Kotlin prefs + TimetableSettings 双写；`loadHyperFocusTemplates()` 优先 TimetableSettings、缺失回 Kotlin 并回填

- [ ] **Step 1: service 层模板双写**

`lib/services/miui_live_activities_service.dart` 当前（L682-699）：
```dart
  Future<bool> saveHyperFocusTemplates(Map<String, String> templates) async {
    ...
    return await _channel.invokeMethod<bool>('saveHyperFocusTemplates', {'templatesJson': jsonEncode(templates)}) ?? false;
  }

  Future<Map<String, String>> loadHyperFocusTemplates() async {
    ...
  }
```
（以实际代码为准）改为：保存时先写 Kotlin prefs（现有逻辑），同时更新 `TimetableSettings.hfTemplatesJson = jsonEncode(templates)`（通过 TimetableProvider.updateTimetableSettings）。加载时优先读 TimetableSettings.hfTemplatesJson，为空则读 Kotlin prefs 并回填。

> 说明：service 层无 provider 上下文，模板双写的 TimetableSettings 持久化在 Task 2 的 Step 2 页面层完成；service 层只保证 Kotlin prefs 读写不变 + 提供从 Kotlin 读回的能力。若实现时发现 provider 可注入，优先在 service 内完成。

- [ ] **Step 2: 页面层模板持久化迁移**

`HyperFocusStageTemplateScreen`（live_settings_subpages.dart）：
- `_loadTemplates`：先读 `provider.settings.hfTemplatesJson`（非空则 `jsonDecode` 填充 `_controllers`）；为空则调 `loadHyperFocusTemplates()` 读 Kotlin prefs，若有内容则写回 TimetableSettings（迁移）
- `_saveTemplates`：调 `service.saveHyperFocusTemplates(map)`（写 Kotlin）+ 调 `provider.updateTimetableSettings(settings.copyWith(hfTemplatesJson: jsonEncode(map)))`（写 Flutter）

需要 `jsonDecode`/`jsonEncode`（dart:convert），页面 State 已有 `context.read<TimetableProvider>()` 可用。

- [ ] **Step 3: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos（HyperFocusDisplayScreen 死字段 3 处仍在 Task 5 范围）
Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 绿（该测试不涉及模板编辑器）

- [ ] **Step 4: Commit**

```bash
git add lib/services/miui_live_activities_service.dart lib/screens/live_settings_subpages.dart
git commit -m "feat: migrate hyperfocus template storage to timetable settings with kotlin mirror"
```

---

### Task 3: Kotlin 渲染扩展

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `hfDefaultTemplates`（L4449-4471）补 9 个新 key 默认值
  - `buildHyperFocusBundle`（L3238-3312）接入新模板字段 + 视觉选项 + islandTimeout 配置
  - 测试版 `sendTestFocusNotificationInner`（L1243-1327）同步接入
  - `parseColorHexOrDefault` 已存在（L3163-3174）
  - 从 `LiveUpdateService`/intent 读取新字段（islandConfig 扩展）

**Interfaces:**
- Consumes: Task 1 新字段；islandConfig map（Task 1 后由 Flutter `_buildData` 传入，Task 5 会打通 Flutter→Kotlin；本任务先在 Kotlin 端支持字段读取）
- Produces: `buildHyperFocusBundle` 渲染：hintInfo.subTitle/extraTitle/specialTitle、outEffectSrc 条件化、outEffectColor、picInfo.type 条件化、islandTimeout 读配置

- [ ] **Step 1: hfDefaultTemplates 补 9 个新 key**

在 `hfDefaultTemplates`（MainActivity.kt L4449-4471）末尾补：
```kotlin
    "hintContent_pre" to "即将上课",
    "hintContent_active" to "距离下课",
    "hintContent_post" to "已经下课",
    "hintSubcontent_pre" to "",
    "hintSubcontent_active" to "",
    "hintSubcontent_post" to "",
    "hintSubtitle_pre" to "",
    "hintSubtitle_active" to "",
    "hintSubtitle_post" to "",
```

- [ ] **Step 2: buildHyperFocusBundle 模板渲染读取新字段**

在 `buildHyperFocusBundle` 的模板读取处（L3223-3229 附近）新增：
```kotlin
val hintContentText = r(templates["hintContent_$stageKey"] ?: "")
val hintSubcontentText = r(templates["hintSubcontent_$stageKey"] ?: "")
val hintSubtitleText = r(templates["hintSubtitle_$stageKey"] ?: "")
```

- [ ] **Step 3: buildHyperFocusBundle 接入新字段到 hintInfo**

在 `hintInfo { ... }`（L3259-3275）内补：
```kotlin
                    subTitle = hintSubtitleText
                    extraTitle = hintContentText
                    specialTitle = hintSubcontentText
```
（`subTitle`/`extraTitle`/`specialTitle` 为 TextAndColorInfo 库字段）

- [ ] **Step 4: islandTimeout 配置化**

`islandTimeout = if (stageKey == "pre") 300 else 600`（L3279）改为读取配置：
```kotlin
                    islandTimeout = when (stageKey) {
                        "pre" -> islandTimeoutPre
                        "post" -> islandTimeoutPost
                        else -> islandTimeoutActive
                    }
```
其中 `islandTimeoutPre/Active/Post` 为从 intent/字段读取的配置值（本任务先在函数签名/字段定义处声明，默认值与 Task 1 一致 300/600/600；Task 5 打通 intent 传入）。

- [ ] **Step 5: 视觉选项接入**

`buildHyperFocusBundle`：
1. `outEffectSrc = "outer_glow"`（L3245）改为条件化：
```kotlin
                outEffectSrc = if (outEffectStatusEnabled) "outer_glow" else ""
                outEffectColor = if (outEffectStatusEnabled) outEffectStatusColor else null
```
2. `picInfo { type = 1 }`（L3255-3257）改为：岛A图标开关 `iconAEnabled` 控制（false 时 type 用别的值或空 picInfo，以库行为为准——false 时设为空 `picInfo {}` 并去掉 `type = 1`）
3. `extras.putString("miui.bigIsland.effect.src", "outer_glow")`（L3314-3315）同样条件化（enabled 才 put）
4. `textInfo.showHighlightColor = true`（L3286）处：若 `hfStatusTextColor != #FFFFFFFF` 则设置 `colorTitle`（颜色值），否则维持 showHighlightColor

新增配置字段（本任务在类中声明，Task 5 打通 intent）：
```kotlin
private var islandTimeoutPre: Int = 300
private var islandTimeoutActive: Int = 600
private var islandTimeoutPost: Int = 600
private var iconAEnabled: Boolean = true
private var statusTextColor: String = "#FFFFFFFF"
private var outEffectStatusEnabled: Boolean = true
private var outEffectStatusColor: String = "#FFFFFFFF"
private var outEffectExpandEnabled: Boolean = true
private var outEffectExpandColor: String = "#FFFFFFFF"
```

- [ ] **Step 6: 测试版同步接入**

`sendTestFocusNotificationInner`（L1243-1327）：同样接入新模板字段（hintContent/hintSubcontent/hintSubtitle 到 hintInfo.subTitle/extraTitle/specialTitle）+ 视觉选项 + islandTimeout 配置（测试版从 `args` map 读 `islandTimeoutPre` 等，缺省 300/600/600）。

- [ ] **Step 7: 编译验证**

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: render hyperfocus new template fields, visual options and configurable island timeout"
```

---

### Task 4: 状态栏岛自定义页 + 展开态自定义页

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart`
  - 将 `HyperFocusStageTemplateScreen`（L1434-1640）拆为 `HyperFocusStatusIslandScreen`（状态栏岛：ticker/islandA/islandB × 3 阶段 + 岛视觉）与 `HyperFocusExpandedIslandScreen`（展开态：baseTitle/baseContent/baseSubcontent/hintTitle/hintContent/hintSubcontent/hintSubtitle × 3 阶段 + 展开态发光）
  - 复用现有 `_variableChipField`（L1522-1567，保留原实现）
  - 顶部 HyperosTabRow 三阶段切换（保留）
  - 保存/恢复默认逻辑保留（FButton → `_saveTemplates`/`_resetStage`）

**Interfaces:**
- Consumes: Task 2 的模板读写（service + TimetableSettings）；Task 3 的新模板 key
- Produces: 两个新页面类 `HyperFocusStatusIslandScreen`、`HyperFocusExpandedIslandScreen`（Task 5 菜单引用）

- [ ] **Step 1: 拆分为状态栏岛页**

新建 `HyperFocusStatusIslandScreen`（从 `HyperFocusStageTemplateScreen` 复制改造）：
- `_controllers` key 列表：`['ticker', 'islandA', 'islandB']`
- `_defaultTemplates` 只含 9 个 key（ticker/islandA/islandB × pre/active/post），默认值沿用原 L1445-1452
- build：顶部提示文本（沿用"点击选择要在各区域显示的信息"）+ `HyperosSectionLabel('状态栏岛')` + 3 个 `_variableChipField`（状态栏/息屏文本→ticker、岛左侧文字→islandA、岛右侧后缀→islandB）+ 保存/恢复默认按钮
- 底部加"岛视觉"入口区（`HyperosNavTile` → 复用 Live 岛视觉配置页，Task 5 处理跳转；本任务先留 `HyperosListTile(title: '岛视觉', trailing: 箭头)` 占位）

- [ ] **Step 2: 拆分为展开态页**

新建 `HyperFocusExpandedIslandScreen`：
- `_controllers` key 列表：`['baseTitle', 'baseContent', 'baseSubcontent', 'hintTitle', 'hintContent', 'hintSubcontent', 'hintSubtitle']`
- `_defaultTemplates` 含 21 个 key（7 字段 × pre/active/post），新字段 hintContent/hintSubcontent/hintSubtitle 默认值空串（hintContent 用"即将上课/距离下课/已经下课"对齐 Task 3 Step 1）
- build：`HyperosSectionLabel('展开态')` + 7 个 `_variableChipField`：
  - 主要标题→baseTitle、次要文本1→baseContent、次要文本2→baseSubcontent、前置文本1→hintContent、前置文本2→hintSubcontent、主要小文本1→hintTitle、主要小文本2→hintSubtitle
- 底部"展开态发光"开关 + 发光颜色（Task 5 接入实际字段，本任务先 UI）

- [ ] **Step 3: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos + 可能有新页面未用 warning（Task 5 接线后消失）
Run: `flutter test`（若影响，全量跑）
Expected: +716 ~3 绿

- [ ] **Step 4: Commit**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: split hyperfocus template editor into status bar and expanded island screens"
```

---

### Task 5: 岛消失时间页 + 岛视觉入口 + 菜单重排

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart`
  - `_buildHyperFocusSettings`（L1777-1842）重排菜单
  - 删除 `_HyperFocusDisplayScreen`（live_settings_subpages.dart L1373-1430）入口引用
- Modify: `lib/screens/live_settings_subpages.dart`
  - 删除 `HyperFocusDisplayScreen`（L1373-1430）
  - 新增 `HyperFocusIslandTimeoutScreen`（岛消失时间页）
  - 状态栏岛页的"岛视觉"入口跳转 `LiveDisplaySettingsScreen`
- Modify: `lib/services/miui_live_activities_service.dart` `_buildData`（L449-548）把新字段传 `islandConfig`
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt`（L824-911）解析 islandConfig 新字段过 intent
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt` `LiveUpdateService.onStartCommand`（L2337-2361）读取新字段 → 喂给 Task 3 的字段

**Interfaces:**
- Consumes: Task 1 字段、Task 3 的 Kotlin 字段、Task 4 页面
- Produces: 重排后的超级岛设置菜单、`HyperFocusIslandTimeoutScreen`

- [ ] **Step 1: 菜单重排**

`_buildHyperFocusSettings`（timetable_settings_screen.dart L1777-1842）重排为（参考 spec §1）：
```
分区「提醒」：提醒时机（→ HyperFocusTimingScreen，保留）
分区「显示自定义」：状态栏岛自定义（→ HyperFocusStatusIslandScreen）、展开态自定义（→ HyperFocusExpandedIslandScreen）、岛视觉（→ LiveDisplaySettingsScreen，with forDuringEnd 等参数）
分区「消失时间」：岛消失时间（→ HyperFocusIslandTimeoutScreen）
分区「工具」：测试（→ _HyperFocusTestingSettingsScreen，保留）
```
删除原"显示设置"（→ HyperFocusDisplayScreen）入口。用 `HyperosSectionLabel` + `HyperosListGroup` 分组。

- [ ] **Step 2: 删除 HyperFocusDisplayScreen**

删除 live_settings_subpages.dart L1373-1430 的 `HyperFocusDisplayScreen` 整个类。同步处理 TimetableSettings 死字段引用（Task 1 遗留的 3 处 use 随之消失）。

- [ ] **Step 3: 新增 HyperFocusIslandTimeoutScreen**

在 live_settings_subpages.dart 新增（仿照 HyperFocusTimingScreen 结构，编辑 `TimetableSettings` 字段，自动保存）：
```dart
class HyperFocusIslandTimeoutScreen extends StatefulWidget { ... }

class _HyperFocusIslandTimeoutScreenState extends State<HyperFocusIslandTimeoutScreen> {
  late TimetableSettings _draft;
  // initState/dispose 同 HyperFocusTimingScreen（_autoSaveTimer 模式）
  // 三个 HyperosNumberPickerTile 或 HyperosSliderTile，分别绑定
  // _draft.hfIslandTimeoutPre / hfIslandTimeoutActive / hfIslandTimeoutPost
  // min=30, max=3600, divisions=可约，onChanged 走 _updateDraft(debounce: true)
}
```
标题「岛消失时间」，三个列表项：课前/课中/课后（单位秒）。

- [ ] **Step 4: islandConfig 通道扩展（Flutter→Kotlin）**

`miui_live_activities_service.dart` `_buildData`（L522-541）的 `islandConfig` map 新增：
```dart
        'hfIslandTimeoutPre': hfIslandTimeoutPre,
        'hfIslandTimeoutActive': hfIslandTimeoutActive,
        'hfIslandTimeoutPost': hfIslandTimeoutPost,
        'hfIconAEnabled': hfIconAEnabled,
        'hfStatusTextColor': hfStatusTextColor,
        'hfOutEffectStatusEnabled': hfOutEffectStatusEnabled,
        'hfOutEffectStatusColor': hfOutEffectStatusColor,
        'hfOutEffectExpandEnabled': hfOutEffectExpandEnabled,
        'hfOutEffectExpandColor': hfOutEffectExpandColor,
```
`_buildData` 参数区新增对应参数（默认值与 Task 1 一致）。

- [ ] **Step 5: Kotlin intent 链路**

`LiveUpdateScheduler.buildServiceIntentFromMethodPayload`（L824-911）：islandConfig 映射处把新字段 `putExtra`。`LiveUpdateService.onStartCommand`（L2337-2361）：读取新字段赋值给 Task 3 声明的类字段（`islandTimeoutPre` 等），供 `buildHyperFocusBundle` 使用。

- [ ] **Step 6: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos，无新增（Task 1 遗留的死字段引用已随 HyperFocusDisplayScreen 删除而消失）
Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL
Run: `flutter test`
Expected: +716 ~3 绿

- [ ] **Step 7: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart lib/screens/live_settings_subpages.dart lib/services/miui_live_activities_service.dart android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: rework hyperfocus settings menu, island timeout page and visual config plumbing"
```

---

### Task 6: 测试与全量回归

**Files:**
- Modify: `test/widgets/hyper_focus_testing_screen_test.dart`（若有菜单结构断言需更新）
- 可能新增: `test/widgets/hyper_focus_settings_test.dart`

- [ ] **Step 1: analyze**

Run: `flutter analyze`
Expected: 8 个预存在 infos（0 error、0 warning 新增）

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: +716 ~3 全绿

- [ ] **Step 3: 构建**

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 真机验证（用户执行）**

1. 安装 APK，进入设置→超级岛与通知→超级岛设置
2. 验证新菜单分组（提醒/显示自定义/消失时间/工具）
3. 状态栏岛/展开态页编辑保存生效
4. 岛消失时间按阶段配置生效
5. 视觉选项（岛A图标/文本颜色/发光开关/发光颜色）生效
6. 测试页发送测试通知，验证新模板字段/视觉/超时
