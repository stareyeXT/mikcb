# 超级岛提醒时段页面对齐 Live Updates 设计文档

日期：2026-07-31

## 背景

小米超级岛（HyperFocusApi 引擎）的阶段调度在 Kotlin 侧已经**完全复用** Live Updates 的 `live*` 设置（`liveEnableBeforeClass`、`liveEnableDuringClass`、`liveEnableBeforeEnd`、`liveShowBeforeClassMinutes`、`liveClassReminderStartMinutes`、`liveTimeCorrectionSeconds` 等，见 `LiveUpdateScheduler.kt:140-197`、`MainActivity.kt:2783-2799`）。

但当前的超级岛"提醒时机"页（`HyperFocusTimingScreen`，`lib/screens/live_settings_subpages.dart:1195`）只有三个 `hfEnable*` 开关，且这些字段 **Kotlin 零引用**（纯摆设）。用户要求：超级岛的提醒时段与 Live Updates 的提醒时段**共用同一套设置、同样的逻辑、同样的 UI**。

已确认的决策（用户批准）：

- **方案 A**：重写 `HyperFocusTimingScreen` 为 `LiveReminderTimingScreen` 同构页面（同组件、同分区、同 l10n 文案），字段全部读写 `live*` 设置。
- **共用一套设置**：改一处，两个引擎的调度都生效；Kotlin 调度零改动。
- **下课即收起**：课后不显示"已下课"岛（符合 MIUI 超级岛行为），"课后"仅存在于模板编辑页（预览/测试用）。

## 目标

1. 超级岛"提醒时机"页与 Live"提醒时段"页 UI 同构、逻辑同源（共用 `live*` 设置）。
2. 移除三个死开关字段 `hfEnableBeforeClass`、`hfEnableDuringClass`、`hfEnableBeforeEnd`。

## 非目标（本次不做）

- 超级岛"显示设置"页的 `hfShowCourseName/hfShowLocation/hfShowCountdown` 死字段清理（后续单独处理）。
- 课后短暂显示"已下课"岛的调度支持。
- Live 引擎行为改动（Live 页 UI 保持不变）。

## 设计

### 1. 重写 `HyperFocusTimingScreen`（`lib/screens/live_settings_subpages.dart`）

结构完全镜像 `_LiveReminderTimingScreenState`（第 78-290 行），复刻其：分区、组件类型、副标题、条件显示逻辑、l10n key、保存方式（`_updateDraft`/`_enqueuePersist`/`_persistDraft`、250ms 防抖、dispose 时补存）。

页面结构（从上到下）：

| 分区 | 组件 | 设置字段 |
|---|---|---|
| 提醒开关 | `HyperosSwitchTile` 课前提醒（副标题 `beforeClassReminderSubtitle(liveShowBeforeClassMinutes)`） | `liveEnableBeforeClass` |
| 提醒开关 | `HyperosSwitchTile` 课中提醒（合并开关，同时写入两个字段；副标题同 Live 页） | `liveEnableDuringClass` + `liveEnableBeforeEnd` |
| 提醒开关（条件显示） | `HyperosSelectTile` 提前提醒时机（立即/5/10/15/20/30，仅课中提醒开启时显示） | `liveClassReminderStartMinutes` |
| 时间阈值 | `HyperosSelectTile` 课前弹出时间（1/5/10/15/20/30/40/50/60） | `liveShowBeforeClassMinutes` |
| 时间阈值 | `HyperosSelectTile` 结束前秒数（15/30/45/60/90） | `liveEndSecondsCountdownThreshold` |
| 时间校正 | `HyperosSliderTile`（-30~30 分钟，防抖保存） | `liveTimeCorrectionSeconds` |

说明：

- 不包含 Live 页的"显示模式"分区（`liveShowDuringClassNotification`/`livePromoteDuringClass` 是 Live 引擎专属：通知栏常驻/岛显示开关；超级岛显示内容由模板系统控制）。
- 结束前秒数仅影响通知栏文本（岛上倒计时是系统计时器），为保持 UI 一致照放。
- 页面标题保留 `提醒时机`（与入口一致），l10n 文案全部复用 Live 页 key（`liveReminderSwitchesTitle`、`beforeClassReminderTitle` 等）。
- 保存与 Live 页完全一致：无副作用、逐项持久化、失败 toast 回滚。

### 2. 移除死字段（`lib/models/timetable_settings.dart`）

删除 `hfEnableBeforeClass`、`hfEnableDuringClass`、`hfEnableBeforeEnd` 三个字段，涉及：

- 字段声明（第 1083-1085 行）
- 构造函数默认值（第 1243-1245 行）
- `toJson`（第 1557-1559 行）
- `fromJson`（第 1882-1884 行）
- `copyWith` 参数与实现（第 2102-2104、2363-2365 行）

兼容性：`fromJson` 对缺失 key 有 `?? true` 默认，旧存储 JSON 残留字段无影响。

### 3. 更新入口 tile（`lib/screens/timetable_settings_screen.dart:1780-1793`）

`_buildHyperFocusSettings` 中"提醒时机"tile 的 `details` 不再读 `hfEnable*`，改为显示 live 状态，如：

```
课前: ${_draft.liveEnableBeforeClass ? "开" : "关"} 课中: ${_draft.liveEnableDuringClass || _draft.liveEnableBeforeEnd ? "开" : "关"}
```

### 4. 测试

- 新增 `test/widgets/hyperfocus_timing_screen_test.dart`（参考 `timetable_settings_screen_test.dart:150-210` 的 Live 页测试）：进入"提醒时机"页 → 切换课前/课中开关 → 断言 `liveEnableBeforeClass`、`liveEnableDuringClass`、`liveEnableBeforeEnd` 被正确持久化；课中开关关闭时"提前提醒时机"选项不显示，开启后显示。
- 运行 `flutter test` 与 `flutter analyze`；Kotlin 侧无改动，无需重建，但跑一次 `.\gradlew assembleDebug` 确认整包可编译。

## 风险

- 无：Kotlin 调度零改动；Dart 改动为纯 UI 对齐 + 死字段清理；Live 页不动。
