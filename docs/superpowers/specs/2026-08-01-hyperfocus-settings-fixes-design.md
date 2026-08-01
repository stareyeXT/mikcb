# 设计：超级岛设置 5 项修复

**日期:** 2026-08-01
**状态:** 已批准
**关联:** 超级岛设置页（上一轮重构产物）+ 测试界面

## 背景

超级岛设置页重构后，用户实测发现 5 个问题，逐一确认方案后形成本设计。

## 决策记录

- 问题 1：调试行 details 超长时截断简写，保留 `HyperosListTile` 同排布局（不做上下布局改版）
- 问题 2：模板编辑从"chip 点选"改为"列表式多选"（保留逗号分隔存储格式）
- 问题 3：从超岛菜单删除两个"岛视觉"入口（超岛引擎不消费 LiveDisplaySettings 字段）
- 问题 4：测试通知用真实课表时间算阶段/倒计时，且倒计时归零后自动消失
- 问题 5：岛消失时间从秒级滚轮改为分钟数字输入（存储仍为秒）

## 1. 测试界面标题遮挡（details 截断简写）

**根因**：`_buildDebugSection`（timetable_settings_screen.dart L3427-3456）用 `HyperosListTile(title: entry.key, details: entry.value)`，details 超长时 `_hyperosTrailingDetails` 占满 216dp 把 title 挤没。

**方案**：保持同排布局，对传入 `details` 的值做长度截断——超长取前 N 字符（如 24）+ "…"（或"等"）。定义一个 helper（如 `_ellipsize(String? v, int max)`）在 `_buildDebugSection` 的 details 传参处应用；rawJson 的 "JSON" 行单独处理（仍截断，因该行本质不适合长文本，但保持最小改动为截断简写）。

截断阈值：24 字符（与现有 `_hyperosTrailingDetails` 216dp 配合，确保 title 至少有正常宽度）。

## 2. 模板编辑改列表式多选

**根因**：`_variableChipField`（live_settings_subpages.dart L1619-1664 / L1902-1947）用一排 `ChoiceChip` 点选，用户不想要这种交互。

**方案**：改为列表式多选交互——每个字段一个列表项，点击后弹层/展开列出 8 个可用变量（课名/短课名/教室/教师/开始/结束/倒计时/正计时）供勾选，勾选结果仍写入 `_controllers[key].text` 为逗号分隔列表（`resolveTemplate` 兼容，Kotlin 解析不变）。

交互形态：优先用 `HyperosSheet`（hyperos_sheet.dart）或项目现有弹层组件承载勾选列表；若不便，可用 `HyperosControlCard` + 勾选行展开。保持 3 阶段 tab 切换、保存/恢复默认逻辑不变。两个自定义页（状态栏岛 3 字段、展开态 7 字段）共用新组件。

## 3. 删除超岛菜单"岛视觉"入口

**根因**：`_buildHyperFocusSettings` 的"岛视觉（课前）"和"岛视觉（课中/课后）"（timetable_settings_screen.dart L1834-1869）跳 `LiveDisplaySettingsScreen`，编辑 beforeClass/duringEnd 字段——只有 Live Updates（builtIn）引擎消费；超岛引擎 `buildHyperFocusBundle` 不读这些字段，改了不生效。

**方案**：从 `_buildHyperFocusSettings` 删除这两个 tile。"显示自定义"分组只剩"状态栏岛自定义"和"展开态自定义"。`LiveDisplaySettingsScreen` 及其字段、Live 引擎的"课前/课中课后显示设置"入口（`_buildLiveUpdatesSettings`）保持不动（它们对 Live 引擎仍有效）。

## 4. 测试通知：真实课表时间 + 到时消失

**根因**：
- pre 阶段硬编码 `classStartAt = now + 5分钟`（MainActivity.kt L1182）+ `hintText = "距离上课还有 5 分钟"`，与真实课表无关
- 测试通知 `setOngoing(true)`（L1371）+ 无到时消失逻辑，倒计时归零仍挂着

**方案**（Kotlin `sendTestFocusNotificationInner`）：
- **真实时间**：优先用 Flutter 传入的真实 `startTime`/`endTime`（"HH:mm"）换算当日 `classStartAt`/`classEndAt`；若解析失败或值非法，回退当前模拟逻辑（pre=now+5min 等）
- **到时消失**：`timerTarget` 到达后自动消失——通过 `Handler.postDelayed` 在 `timerTarget - now` 延迟后 `notificationManager.cancel(10001)`（并可选先 `buildHyperFocusDismissBundle` 再 cancel）。需在 onStartCommand/测试路径管理该 Runnable，避免泄漏
- Flutter 端 `_sendTestNotification` 已传 `startTime`/`endTime`（L3031-3032），无需改 Flutter 侧参数传递

## 5. 岛消失时间：分钟数字输入

**根因**：`HyperFocusIslandTimeoutScreen`（live_settings_subpages.dart L1374-1470）用 `HyperosNumberPicker`（min30-max3600 秒，3571 个滚轮项）卡顿且不直观。

**方案**：
- 三个 tile（课前/课中/课后）从 `HyperosNumberPicker` 改为 `HyperosTextField` 数字输入（参考 `_HyperosSliderValueSheetBody` 的数字输入范式，hyperos_controls.dart L425-570）
- UI 输入**分钟**（默认 300 秒=5 分钟、600 秒=10 分钟），提交/失焦时换算存储秒（×60）
- 限界：1~60 分钟（即 60~3600 秒），提交时 clamp；非法输入回退默认
- 单位在字段 subtitle/helper 标注"分钟"

## 验证

- `flutter analyze`（8 预存在 infos）+ `flutter test`（+716 ~3）+ `gradlew assembleDebug` BUILD SUCCESSFUL
- 真机：调试行标题不再被挤；模板列表多选可保存；超岛菜单无岛视觉入口；测试通知用真实时间且到时消失；消失时间按分钟输入生效
