# 超级岛设置 5 项修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 5 个问题：测试界面 details 截断、模板编辑改列表式多选、删除超岛菜单岛视觉入口、测试通知真实时间+到时消失、消失时间改分钟数字输入。

**Architecture:** 5 项独立修复分 4 个 Dart/Kotlin 任务 + 1 个回归任务。

**Tech Stack:** Flutter（Dart）、Kotlin、MethodChannel

## Global Constraints

- `flutter analyze` 基线：8 个预存在 infos，0 error
- `flutter test` 基线：+716 ~3 全绿
- `gradlew assembleDebug` 必须 BUILD SUCCESSFUL
- 模板存储格式保持逗号分隔变量列表（`resolveTemplate` 兼容，Kotlin 解析不变）
- 删除"岛视觉"入口不触碰 Live 引擎的"课前/课中课后显示设置"入口（`_buildLiveUpdatesSettings`）与 `LiveDisplaySettingsScreen`

---

### Task 1: 测试界面 details 截断 + 删除岛视觉入口

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart`
  - `_buildDebugSection`（L3427-3456）加 details 截断
  - `_buildHyperFocusSettings` 删除"岛视觉"两个 tile（L1834-1869）

**Interfaces:**
- Produces: `_ellipsize(String? v, {int max = 24})` helper（State 内私有方法）

- [ ] **Step 1: 加 _ellipsize helper**

在 `_HyperFocusTestingSettingsScreenState`（含 `_buildDebugSection` 的 State 类）内新增：
```dart
  String _ellipsize(String? value, {int max = 24}) {
    final v = value?.trim() ?? '';
    if (v.length <= max) return v;
    return '${v.substring(0, max)}…';
  }
```

- [ ] **Step 2: _buildDebugSection 应用截断**

`_buildDebugSection`（L3439-3450）两处 details 改为 `_ellipsize(...)`：
```dart
            for (final entry in entries.entries)
              HyperosListTile(
                icon: Icons.label_outline,
                title: entry.key,
                details: _ellipsize(entry.value),
              ),
            if (trailingJson.isNotEmpty)
              HyperosListTile(
                icon: Icons.data_object,
                title: 'JSON',
                details: _ellipsize(trailingJson, max: 60),
              ),
```

- [ ] **Step 3: 删除岛视觉入口**

`_buildHyperFocusSettings` 中删除两个 `HyperosListTile`（"岛视觉" forDuringEnd:false 和 forDuringEnd:true 两个 tile，L1834-1869）。"显示自定义"分组保留"状态栏岛自定义"和"展开态自定义"两个 tile。

- [ ] **Step 4: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2/2 绿

- [ ] **Step 5: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart
git commit -m "fix: ellipsize debug details and drop island visual entries from hyperfocus menu"
```

---

### Task 2: 模板编辑改列表式多选

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart`
  - `_HyperFocusStatusIslandScreenState._variableChipField`（L1619-1664）
  - `_HyperFocusExpandedIslandScreenState._variableChipField`（L1902-1947）

**Interfaces:**
- Consumes: `_availableVariables`（8 项：课名/短课名/教室/教师/开始/结束/倒计时/正计时，两 State 各有一份）、`_controllers[key].text`（逗号分隔）
- Produces: 新的 `_variableSelectField(String key, String label)`（替换 `_variableChipField`），列表式多选交互，存储仍逗号分隔

- [ ] **Step 1: 确认弹层组件**

Run: `rg "class HyperosSheet" lib/ui/hyperos/`
Expected: 找到 HyperosSheet（hyperos_sheet.dart）的构造函数签名。若其签名不便承载"多选勾选列表"，改用 `showModalBottomSheet` 或 `HyperosControlCard` + 勾选行展开。**以最贴近项目风格且实现简单为准**。

- [ ] **Step 2: 实现列表式多选字段组件（在 _HyperFocusStatusIslandScreenState）**

将 `_variableChipField` 改为 `_variableSelectField`：
```dart
  Widget _variableSelectField(String key, String label) {
    final current = _controllers[key]?.text ?? '';
    final selected = current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: HyperosListTile(
        icon: Icons.tune,
        title: label,
        details: selected.isEmpty
            ? '未选择'
            : _ellipsizeList(selected.toList()),
        onTap: () => _openVariableMultiSelect(key, selected),
      ),
    );
  }

  String _ellipsizeList(List<String> items, {int max = 4}) {
    if (items.isEmpty) return '未选择';
    if (items.length <= max) return items.join(', ');
    return '${items.sublist(0, max).join(', ')}…';
  }
```
> 若 StatusIsland State 无 `_ellipsizeList`，直接内联该逻辑。`_ellipsize` 是 Task 1 里另一个文件的方法，这里不要引用跨文件私有方法——自行内联截断。

- [ ] **Step 3: 实现多选弹层**

新增 `_openVariableMultiSelect(String key, Set<String> selected)`：弹出多选列表（HyperosSheet 或 showModalBottomSheet），列出 `_availableVariables` 各带 checkbox/勾选，确认后写回：
```dart
  Future<void> _openVariableMultiSelect(String key, Set<String> selected) async {
    final result = await showModalBottomSheet<List<String>>(
      context: context,
      builder: (ctx) => _VariableMultiSelectSheet(
        variables: _availableVariables,
        initial: selected.toList(),
      ),
    );
    if (result == null) return;
    setState(() {
      _controllers[key]?.text = result.join(',');
    });
  }
```
新增 `_VariableMultiSelectSheet`（StatefulWidget，StatelessWidget 亦可）：Column + ListView 列出 8 个变量，每个 `CheckboxListTile`（或 `HyperosCheckboxTile`，检查其签名），底部确认按钮（`HyperosButton`）返回选中列表并 `Navigator.pop`。**不立即保存——确认时统一通过现有"保存"按钮持久化**（沿用现有保存流程，弹层只改 controller 文本）。

- [ ] **Step 4: 展开态页同步**

对 `_HyperFocusExpandedIslandScreenState` 做同样改造：`_variableChipField`（L1902-1947）→ `_variableSelectField` + `_openVariableMultiSelect` + 共享 `_VariableMultiSelectSheet`（Sheet 组件定义在文件顶层，两 State 共用）。页面 build 里 `_variableChipField(...)` 调用点（L1698-1700 状态栏岛、L1981-1987 展开态）改为 `_variableSelectField(...)`。

- [ ] **Step 5: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test`
Expected: +716 ~3 全绿

- [ ] **Step 6: Commit**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: replace template chip picker with list-style multi-select sheet"
```

---

### Task 3: 测试通知真实时间 + 到时消失（Kotlin）

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `sendTestFocusNotificationInner` 时间计算（L1158-1187）
  - notify 后加到时消失（L1376 之后）

**Interfaces:**
- Consumes: `args["startTime"]`/`args["endTime"]`（"HH:mm" 字符串，Flutter `_sendTestNotification` 已传 L3031-3032）
- Produces: 测试通知用真实课表时间；pre/active 阶段到时自动取消

- [ ] **Step 1: 真实时间换算**

`sendTestFocusNotificationInner` 的 `when (templateStage)` 块（L1168-1187）改为：优先用真实 `startTime`/`endTime` 换算当日时间戳，解析失败回退模拟。用 helper 解析 "HH:mm"：

新增私有方法（放在 `sendTestFocusNotificationInner` 所在类）：
```kotlin
    private fun parseTodayClockToMillis(hhmm: String?): Long? {
        if (hhmm.isNullOrBlank()) return null
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
```
（MainActivity.kt 已有类似 `buildCourseTimeMillis` L4180-4195——检查是否可直接复用；若签名匹配 `"HH:mm"` → millis 则可直接调用，否则新增）

时间计算改为（L1168-1187）：
```kotlin
            val realStart = parseTodayClockToMillis(startTime)
            val realEnd = parseTodayClockToMillis(endTime)
            val hasRealTime = realStart != null && realEnd != null && realEnd > realStart
            val classStartAt: Long
            val classEndAt: Long
            val timerTarget: Long
            val hintText: String
            when (templateStage) {
                "active" -> {
                    classStartAt = now - 10 * 60_000L
                    classEndAt = now + 5 * 60_000L
                    timerTarget = classEndAt
                    hintText = "距下课还有 5 分钟"
                }
                "post" -> {
                    classStartAt = now - 20 * 60_000L
                    classEndAt = now - 60_000L
                    timerTarget = 0L
                    hintText = "已下课"
                }
                else -> {
                    if (hasRealTime && realStart!! > now) {
                        // 用真实上课时间
                        classStartAt = realStart
                        classEndAt = realEnd!!
                        timerTarget = classStartAt
                        hintText = "距离上课还有 ${((classStartAt - now) / 60_000L + 1)} 分钟"
                    } else {
                        classStartAt = now + 5 * 60_000L
                        classEndAt = classStartAt + 100 * 60_000L
                        timerTarget = classStartAt
                        hintText = "距离上课还有 5 分钟"
                    }
                }
            }
```
> pre 阶段：真实 `startTime` 若在今天稍后则用真实时间；若已过（realStart <= now）则回退模拟（说明此刻不该测"课前"）。active 阶段保持模拟（课中倒计时基于 endTime 更有意义，但 endTime 可能已过——保守起见 active/post 暂不改，仅 pre 用真实时间。**若用户期望 active 也用真实 endTime 倒计时，可留到真机反馈**）。

- [ ] **Step 2: 到时消失**

notify（L1376）之后、post-inspect 之前，对 `timerTarget > 0 && timerTarget > now` 的 pre 阶段 schedule 自动取消：
```kotlin
            if (timerTarget > 0L && timerTarget > now) {
                val delayMillis = timerTarget - now
                Handler(Looper.getMainLooper()).postDelayed({
                    notificationManager.cancel(10001)
                }, delayMillis)
            }
```
（post 阶段 timerTarget=0L 不会触发。active 阶段 timerTarget=classEndAt=now+5min，也会触发——课中模拟 5 分钟后取消，符合"到时消失"。需 import `android.os.Handler`/`Looper`，检查文件已有。）

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: use real course time and auto-dismiss for hyperfocus test notification"
```

---

### Task 4: 岛消失时间改分钟数字输入

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart`
  - `HyperFocusIslandTimeoutScreen`（L1374-1470）

**Interfaces:**
- Consumes: `hfIslandTimeoutPre/Active/Post`（秒 int，默认 300/600/600）
- Produces: 分钟数字输入，提交换算秒（×60），限界 1~60 分钟

- [ ] **Step 1: 改造 _buildTimeoutTile 为分钟数字输入**

`_buildTimeoutTile`（L1429-1443）从 `HyperosNumberPicker` 改为 `HyperosTextField`（数字键盘 + 分钟单位）：

```dart
  Widget _buildTimeoutTile(
    String label,
    int seconds,
    ValueChanged<int> onChanged,
  ) {
    final minutesController = TextEditingController(
      text: (seconds / 60).round().toString(),
    );
    return HyperosTextFieldTile(
      cardTitle: label,
      cardSubtitle: '分钟（1~60）',
      field: HyperosTextField(
        controller: minutesController,
        label: '分钟',
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9]'))],
        onChanged: (text) {
          final minutes = int.tryParse(text) ?? 0;
          final clamped = minutes.clamp(1, 60);
          onChanged(clamped * 60);
        },
      ),
    );
  }
```
> 需确认 `HyperosTextField`（hyperos_text_field.dart L9-232）实际参数名（label/keyboardType/inputFormatters/onChanged/controller）——以实际签名为准。`minutesController` 在 tile 内创建无 dispose 问题吗？`_buildTimeoutTile` 每次 build 创建新 controller——会导致输入焦点丢失。**改为在 State 持有三个 TextEditingController**（`_preMinutesCtrl`/`_activeMinutesCtrl`/`_postMinutesCtrl`，initState 初始化、dispose 释放），tile 复用。

- [ ] **Step 2: State 持有分钟 controllers**

在 `_HyperFocusIslandTimeoutScreenState` 新增：
```dart
  late final TextEditingController _preMinutesCtrl;
  late final TextEditingController _activeMinutesCtrl;
  late final TextEditingController _postMinutesCtrl;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
    _preMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPre / 60).round().toString(),
    );
    _activeMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutActive / 60).round().toString(),
    );
    _postMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPost / 60).round().toString(),
    );
  }

  @override
  void dispose() {
    _preMinutesCtrl.dispose();
    _activeMinutesCtrl.dispose();
    _postMinutesCtrl.dispose();
    if (_autoSaveTimer?.isActive ?? false) {
      _autoSaveTimer?.cancel();
      _persistDraft(_draft);
    } else {
      _autoSaveTimer?.cancel();
    }
    super.dispose();
  }
```

- [ ] **Step 3: build 使用分钟 controller**

build（L1416-1421）改为：
```dart
              _buildTimeoutTile('课前', _preMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPre: v))),
              _buildTimeoutTile('课中', _activeMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutActive: v))),
              _buildTimeoutTile('课后', _postMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPost: v))),
```
`_buildTimeoutTile` 签名改为 `(String label, TextEditingController controller, ValueChanged<int> onChanged)`，内部 `HyperosTextField(controller: controller, ...)`，section 标签改为"状态栏岛消失时间（分钟）"。

- [ ] **Step 4: 验证**

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error
Run: `flutter test`
Expected: +716 ~3 全绿

- [ ] **Step 5: Commit**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: island timeout in minutes via text input"
```

---

### Task 5: 全量回归验证

**Files:**
- 无代码改动（纯验证）

- [ ] **Step 1: analyze**

Run: `flutter analyze`
Expected: 8 预存在 infos（0 error、0 warning 新增）

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: +716 ~3 全绿

- [ ] **Step 3: 构建**

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 真机验收（用户执行）**

1. 测试界面调试行：标题不再被长 details 挤没（超长显示"…"）
2. 状态栏岛/展开态：列表式多选可编辑保存
3. 超岛菜单：无"岛视觉"入口
4. 测试通知：用真实课表时间，倒计时到 0 自动消失
5. 岛消失时间：按分钟输入生效
