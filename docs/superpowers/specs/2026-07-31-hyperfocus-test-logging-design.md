# 设计：超级岛测试发送全链路日志埋点

**日期:** 2026-07-31
**状态:** 已批准
**关联:** 小米超级岛（HyperFocus）引擎的"测试与诊断"页（`_HyperFocusTestingSettingsScreen`）

## 背景

"测试与诊断"页（`lib/screens/timetable_settings_screen.dart` L2863 起）已有完整的日志基础设施：状态卡 tail（`recentDiagnostics`）、"本地日志"段（查看 `LiveDiagnosticsLogViewerScreen` + 清空）、"导出诊断"按钮（`_exportDiagnostics`，优先导出日志文件），全部复用 `UmengDiagnosticReporter` + `live_update_diagnostics.log`。

但 `sendTestFocus` 发送链路（Kotlin `sendTestFocusNotificationInner`，MainActivity.kt L1135-1353）**在成功、权限关闭、渠道关闭、提交后系统未显示等所有非异常路径都没有埋点**，只有异常时才 `report("send_test_focus_failed")`。导致用户在测试界面发送测试通知后，导出的日志里没有任何相关记录（实测 `mikcb-live-diagnostics-1785501038023.log` 10276 行中零条 `send_test_focus_*` 记录）——日志功能形同虚设，无法排查"提交成功但系统未显示"类问题。

目标：给发送链路补全埋点，使每次发送的完整决策过程（参数、权限、渠道、提交、系统侧接收结果）全部落盘，现有查看/导出功能立即可用。

## 决策记录

- 全部用 `record`（无 2 分钟 dedupe 节流，每次发送都落盘），不用 `report`（会按 category 节流吞掉同 category 记录）
- category 前缀 `send_test_focus_*`，与现有 `live_update_*` 体系并列
- 界面零改动：现有查看/清空/导出/状态卡 tail 直接消费新埋点
- Dart 端只记一条发起记录（`send_test_focus_requested`），服务层失败路径不重复记（Kotlin 已有结论行）
- 不做 notify 后延迟复查（方案 B 已讨论并否决：区分"从未显示"与"显示后被移除"属于后续专项，本次仅做全链路埋点）
- 现有 `send_test_focus_failed`（异常路径，`report` 带堆栈与上下文快照）保留不动
- 不改变任何现有行为与返回文案（含 post-inspect 失败文案 L1335）

## 1. Kotlin 埋点（`MainActivity.kt` `sendTestFocusNotificationInner`）

新增 4 个 category，全部 `UmengDiagnosticReporter.record`：

| category | 触发点 | 关键 extras |
|---|---|---|
| `send_test_focus_started` | 入口（参数解析后，权限检查前） | stage、courseName、shortName、startTime、endTime、location、templateStage、classStartAt、classEndAt、timerTarget、now |
| `send_test_focus_permission_blocked` | `areNotificationsEnabled()` 为 false（L1147） | stage |
| `send_test_focus_channel_blocked` | 渠道不存在或 importance == NONE（L1311） | stage、channelImportance |
| `send_test_focus_submitted` | `notify(10001)` 后立即同步检查（L1325 之后） | stage、activeIds、activeContainsTest（是否含 10001）、testChannelImportance、liveChannelImportance |

说明：

- `send_test_focus_started` 在模板渲染前记录（time 计算 L1152-1181 完成后、模板加载 L1183 之前），保证时间窗参数可对照
- `send_test_focus_submitted` 的 `activeContainsTest=false` 即"已提交但系统未显示"路径，对应现有返回文案 L1335，二者不冲突（一个落盘、一个回 UI）
- 成功路径（L1337 返回 null）由 `send_test_focus_submitted` 承担结论记录，不再单独加 `succeeded` 类别（避免冗余）
- `record` 在诊断开关或隐私未同意时内部静默跳过（UmengDiagnosticReporter.kt L51），与现有埋点一致

### 消息文案（`DiagnosticLogMessages.kt`）

新增 4 个常量，沿用现有中文文案风格：

```kotlin
const val SEND_TEST_FOCUS_STARTED = "收到超级岛测试通知发送请求"
const val SEND_TEST_FOCUS_PERMISSION_BLOCKED = "超级岛测试发送被拦截：系统通知权限未开启"
const val SEND_TEST_FOCUS_CHANNEL_BLOCKED = "超级岛测试发送被拦截：测试通知渠道已被关闭"
const val SEND_TEST_FOCUS_SUBMITTED = "超级岛测试通知已提交并检查系统接收结果"
```

## 2. Dart 埋点（`lib/screens/timetable_settings_screen.dart`）

`_sendTestNotification`（L2933-2958）开头（在 stage 推断之后）调 `recordDiagnosticEvent`：

```dart
await recordDiagnosticEvent('send_test_focus_requested', {
  'stage': stage,
  'courseName': courseName,
  'atMillis': DateTime.now().millisecondsSinceEpoch.toString(),
});
```

- `recordDiagnosticEvent` 已由 `miui_live_activities_service.dart` 封装（L202-214），走 `recordDiagnosticEvent` 通道方法（无节流）
- 若发送前 channel 调用抛错（service 层返回"发送失败：…"），不再额外埋点——Kotlin 端 `send_test_focus_failed`/`submitted` 已覆盖根因

## 3. 错误处理与不变行为

- `record` 自身不抛异常（UmengDiagnosticReporter 内部捕获），不改变发送链路行为
- 所有现有返回文案、`recordHyperFocusTestResult`（SharedPreferences 状态卡）、`send_test_focus_failed` report 均不变
- 日志体积：每次发送新增约 4 条记录，256KB 循环截断不受影响

## 4. 测试与验证

- 现有 2 个 widget 测试（`test/widgets/hyper_focus_testing_screen_test.dart`）保持绿色：`_sendTestNotification` 中新增的 `recordDiagnosticEvent` 调用走 mock channel，不影响断言
- 验证步骤：
  1. `flutter analyze` — 与基线（8 个预存在 infos）持平
  2. `flutter test` — +716 ~3 全绿
  3. `gradlew assembleDebug` — BUILD SUCCESSFUL
  4. 真机发送一次测试通知 → 测试页导出日志 → 确认含 `send_test_focus_started`、`send_test_focus_submitted`（及权限/渠道拦截时对应记录）

## 5. 范围外（本次不做）

- notify 后 5 秒延迟复查（区分"从未显示"与"显示后被移除"）
- 测试界面 UI 增强（结构化发送结果展示）
- Dart 端 `AppLogService`（app_runtime.log）埋点——native 日志文件已覆盖，避免双写
