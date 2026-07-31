# 设计：超级岛"测试与诊断"页（完整仿照 Live Updates 测试页）

**日期:** 2026-07-31
**状态:** 已批准
**关联:** 小米超级岛（HyperFocus）引擎的测试功能

## 背景

Live Updates 引擎有完整的"测试与诊断"页（`_LiveTestingSettingsScreen`，位于 `lib/screens/timetable_settings_screen.dart`），包含通知测试卡、岛状态卡（1 秒自动刷新、上次刷新时间）、调试详情分区、最近诊断、原始 JSON、本地日志、导出。小米超级岛引擎目前只有一个"测试"入口 + 阶段底部菜单（课前/课中/课后），没有诊断能力。

目标：为超级岛引擎提供与 Live 测试页结构一致的"测试与诊断"页，含独立的 Kotlin 诊断接口。

## 决策记录

- 范围：完整仿照（状态卡 + 通知测试卡 + 调试详情区 + 自动刷新/导出/本地日志）
- 诊断数据来源：独立 Kotlin 接口 `getHyperFocusDebugStatus`（不复用/不扩展 `getLiveUpdateDebugStatus`，Live 侧零改动）
- 入口：替换现有"测试"tile 的底部菜单流程，改为跳转新页面；底部菜单逻辑内嵌到新页面的通知测试卡
- 下课即收起（无课后持续岛）等既有行为不变

## 1. Kotlin 诊断接口

### `buildHyperFocusDebugStatus(context: Context): Map<String, Any?>`

位于 `MainActivity.kt` companion object（与 `buildDebugStatus` 同级对称），MethodChannel `"getHyperFocusDebugStatus"` 调用返回：

```kotlin
linkedMapOf(
    "generatedAtMillis" to System.currentTimeMillis(),
    "summary" to linkedMapOf(
        "hasNotificationPermission" to hasNotificationPermissionCompat(context),
        "testChannelImportance" to 渠道重要性等级（NONE/LOW/DEFAULT/HIGH/UNSPECIFIED），
        "testChannelBlocked" to 渠道是否被屏蔽（或不存在视为 blocked 前提下的重要性判断），
        "templatesLoaded" to loadHyperFocusTemplates(context).isNotEmpty(),
        "nextTriggerCourseName" to 调度快照中下次课程名（可空），
        "nextTriggerStage" to 下次触发阶段（pre/active/beforeEnd/post，可空），
        "nextTriggerAtMillis" to 下次触发毫秒（可空），
        "hasLastTestResult" to 是否有上次测试记录，
        "lastTestStage" to 上次测试阶段（可空），
        "lastTestSucceeded" to 上次是否成功（可空），
        "lastTestMessage" to 上次失败原因（成功为空串，可空），
        "lastTestAtMillis" to 上次测试时间毫秒（可空），
    ),
    "environment" to buildEnvironmentSnapshot(context),  // 直接复用
    "scheduling" to linkedMapOf(  // 来自共享 LiveUpdateScheduler 快照
        "nextCourseName" to ...,
        "nextCourseStartAtMillis" to ...,
        "nextCourseEndAtMillis" to ...,
        "nextTriggerAtMillis" to ...,
        "beforeClassBlockedUntilMillis" to ...,
        "suspendedUntilMillis" to ...,
        "stage" to 当前阶段（由 resolveStage 逻辑推断，可空），
    ),
    "templates" to linkedMapOf(  // 按阶段
        "pre" / "active" / "post" 各含 ticker/islandA/islandB/baseTitle/baseContent/baseSubcontent/hintTitle 的 isNotBlank 汇总
        // 只上报每阶段模板是否为空，不上报原文（避免隐私与体积）
    ),
    "test" to linkedMapOf(
        "lastStage" / "lastSucceeded" / "lastMessage" / "lastAtMillis",
    ),
    "recentDiagnostics" to linkedMapOf(
        "enabled" to UmengDiagnosticReporter.isLiveDiagnosticsEnabled(context),
        "tail" to UmengDiagnosticReporter.readLiveDiagnosticsTail(context),
    ),
)
```

- `summary` 与 `test` 内容重叠，但 `summary` 是状态卡直接消费的摘要，`test` 是调试详情区的完整块，两者都保留（与 Live 页 summary + 分区的模式一致）。
- `scheduling` 读取 `LiveUpdateScheduler` 已维护的共享快照（无需新增调度器改动）；若快照不存在，相关字段为 null，Dart 侧显示"暂无课表数据"。
- 渠道查询：`notificationManager.getNotificationChannel(testChannelId)`，`testChannelId` 与 `sendTestFocusNotification` 一致（现有常量）。importance == IMPORTANCE_NONE 视为被屏蔽。

### 上次测试结果记录

`sendTestFocusNotification` 成功或失败时，写入 SharedPreferences（如 `hyper_focus_test`，字段 `last_stage`/`last_succeeded`/`last_message`/`last_at_millis`），供诊断接口读取。失败路径（权限未开、渠道被屏蔽、异常）与成功路径都记录。

## 2. Dart 页面

### `_HyperFocusTestingSettingsScreen`（私有，位于 `lib/screens/timetable_settings_screen.dart`）

与 `_LiveTestingSettingsScreen` 同文件，复用其全部私有助手（`_debugSectionMap`、`_debugValueText`、`_DebugStatusChip`、`_refreshDebugStatus` 模式、导出/查看器流程）。

页面结构（镜像 Live 测试页）：

```
HyperosSubpage(title: l10n.hfTestingTitle)
└─ HyperosListView
   ├─ [通知测试卡]  HyperosControlCard("通知测试")
   │    ├─ HyperosButton("发送测试通知") → 阶段底部菜单（课前/课中/课后，现有三选项）
   │    │      → MiuiLiveActivitiesService.sendTestFocusNotification(stage)
   │    │      → snackbar 成功/失败 + appDebugLog
   │    └─ dev 模式：Umeng 崩溃/ANR 测试按钮（复用现有处理）
   ├─ [超级岛状态卡] HyperosControlCard("超级岛状态")
   │    ├─ semesterUnset 警告（复用）
   │    ├─ Wrap chips:
   │    │    通知权限（开/关）、测试渠道（正常/被屏蔽/未创建）、调度器（已调度/无数据）
   │    ├─ 状态说明文字（如"暂无课表数据"、渠道被屏蔽提示）
   │    ├─ 刷新按钮 + 导出按钮（复用流程）
   │    ├─ 自动刷新开关（默认开，1 秒间隔）+ 上次刷新时间（复用文案）
   ├─ [调试详情] 仅 _debugStatus != null 时显示：
   │    ├─ 环境（复用分区渲染，key 与 Live 相同结构）
   │    ├─ 调度（nextCourse/nextTrigger/blockedUntil/suspended/stage）
   │    ├─ 模板（pre/active/post 六字段加载状态）
   │    ├─ 上次测试（阶段/结果/时间/失败原因）
   │    └─ 最近诊断（Umeng tail）
   ├─ [本地日志]（复用 Live 的日志查看/清空/开关入口）
   └─ [原始 JSON]（复用渲染）
```

### 状态机

镜像 Live：`initState` 立即刷新一次 + `Timer.periodic(1s)` 自动刷新（`_isAppResumed` 暂停、`_refreshInFlight` 防重入、`_autoRefreshEnabled` 开关）。

### 入口改动

`_buildHyperFocusSettings` 的"测试"tile（当前 ~1825 行起）：
- onTap 改为 `HyperosNavigation.push → HyperFocusTestingSettingsScreen`
- details 更新为"发送课前/课中/课后测试通知、查看超级岛诊断状态"（或类似）
- 现有底部菜单 + 发送逻辑迁移到新页面通知测试卡内（保留现有错误透出与 appDebugLog）

## 3. l10n

- 复用 Live key：`liveTestingSendAction`、`liveTestingRefreshAction`、`liveTestingRefreshing`、`liveTestingExportAction`/`Exporting`、`liveTestingAutoRefreshTitle`、`liveTestingNoIslandReasonTitle`、`liveTestingUmengHint`、`liveTestingCrashAction`、`liveTestingAnrAction`、`liveTestingNotRefreshed`、`liveDiagnostics*` 系列、`liveTestingRawJsonTitle` 等（语义一致即复用）
- 新增 key（zh/en/ja/ko）：
  - `hfTestingTitle`：超级岛测试与诊断（页面标题）
  - `hfTestingNotificationTitle`/`Subtitle`：通知测试卡标题/副标题
  - `hfTestingIslandStatusTitle`/`Subtitle`：超级岛状态卡标题/副标题
  - `hfTestingPermissionOn`/`Off`：通知权限 chip
  - `hfTestingChannelOk`/`Blocked`/`Missing`：测试渠道 chip
  - `hfTestingSchedulerScheduled`/`NoData`：调度器 chip
  - `hfTestingNoScheduleHint`：无课表数据提示
  - `hfTestingChannelBlockedHint`：渠道被屏蔽提示
  - `hfTestingDebugScheduling`/`Templates`/`LastTest`：调试分区标题
  - `hfTestingLastTestNever`、`hfTestingLastTestSucceeded`、`hfTestingLastTestFailed`：上次测试文案
  - `hfTestingTemplateStage*` 如需按阶段显示模板状态
  - `hfTestingSendAction`：如不复用 liveTestingSendAction 则新增

## 4. 错误处理

| 场景 | 行为 |
|------|------|
| 通知权限未开 | 权限 chip 红色，状态文字提示，发送测试仍可点（Kotlin 侧返回失败原因透出） |
| 测试渠道被屏蔽/未创建 | 渠道 chip 红色 + 提示文字 |
| 无课表快照 | 调度器 chip 灰色 + "暂无课表数据" |
| 发送失败（权限/渠道/异常） | snackbar 显示 Kotlin 返回的具体失败原因；记录到"上次测试"并刷新状态 |
| 页面退出 | 取消定时器（dispose） |

## 5. 测试

新增 `test/widgets/hyper_focus_testing_screen_test.dart`（或并入现有 hyperfocus 测试文件，视实现而定）：
- mock MethodChannel `getHyperFocusDebugStatus` 返回固定 JSON
- 断言：状态卡 chips 渲染（权限开/渠道正常/已调度）、自动刷新开关默认开且触发刷新、点发送测试通知弹出阶段菜单、选阶段后调用 `sendTestFocusNotification` 并展示结果
- 断言：无快照时显示"暂无课表数据"
- 入口 tile 测试更新：进入新页面而非底部菜单

## 6. 明确不做（YAGNI）

- 不做 HyperFocus 专用前台服务/常驻诊断（复用现有调度器）
- 不做快速修复（quick fixtures，Live 专属）
- 不扩展 `getLiveUpdateDebugStatus`
- 不上报模板原文（只上报非空状态）
- 不做课后持续岛诊断
