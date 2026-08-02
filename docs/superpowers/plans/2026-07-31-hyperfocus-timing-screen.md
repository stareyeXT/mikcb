# 超级岛提醒时段页面对齐 Live Updates 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将超级岛"提醒时机"页重写为与 Live Updates"提醒时段"页同构的 UI，共用同一套 `live*` 设置，并移除 `hfEnable*` 死字段。

**Architecture:** Dart 纯 UI 改动。`HyperFocusTimingScreen`（`lib/screens/live_settings_subpages.dart:1195-1252`）重写为 `_LiveReminderTimingScreenState`（同文件 78-290 行）的镜像：同分区、同组件、同 l10n key、同保存链路（250ms 防抖 + 队列持久化）。Kotlin 调度零改动（本就读取 `live*` 设置）。设置模型移除 `hfEnableBeforeClass/hfEnableDuringClass/hfEnableBeforeEnd` 三个死字段。

**Tech Stack:** Flutter/Dart（widget 测试用 `flutter_test` + `provider` + `shared_preferences` mock）。

## Global Constraints

- 不写任何代码注释。
- 页面标题保留硬编码 `提醒时机`（与其他超级岛页面一致）；其余文案全部复用 Live 页 l10n key。
- 超级岛"显示设置"页（`hfShow*` 字段）本次不动。
- Kotlin 侧零改动。
- 工作目录：`C:\daima\zwg\mikcb\mikcb-ECJTU`（git 仓库，分支 master）。

---

### Task 1: 写失败测试（新页面共用 live 设置）

**Files:**
- Create: `test/widgets/hyperfocus_timing_screen_test.dart`

**Interfaces:**
- Consumes: `createInitializedTestProvider(tester)` 与 `TestApp`（`test/helpers_test_app.dart`）、`TimetableSettingsScreen`（`lib/screens/timetable_settings_screen.dart`）、`TimetableSettings`/`TimetableProfile`（`lib/models/`）、`StorageService().resetForTesting()`（`lib/services/storage_service.dart`）。
- Produces: 导航辅助函数 `openHyperFocusTimingScreen(tester) → Future<TimetableProvider>`，两个测试用例（Task 2 实现后必须通过）。

- [ ] **Step 1: 创建测试文件**

创建 `test/widgets/hyperfocus_timing_screen_test.dart`：

```dart
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:university_timetable/models/timetable_profile.dart';
import 'package:university_timetable/models/timetable_settings.dart';
import 'package:university_timetable/providers/timetable_provider.dart';
import 'package:university_timetable/screens/timetable_settings_screen.dart';
import 'package:university_timetable/services/storage_service.dart';
import '../helpers_test_app.dart';

Future<void> _pumpScreen(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
}

void _seedInitializedPrefs() {
  final now = DateTime(2026, 4, 12);
  final settings = TimetableSettings.defaults();
  final profile = TimetableProfile(
    id: 'profile-1',
    name: '默认课表',
    courses: const [],
    settings: settings,
    currentWeek: 1,
    createdAt: now,
    lastUsedAt: now,
  );
  SharedPreferences.setMockInitialValues({
    'did_migrate_app_logs_default': true,
    'did_migrate_live_hide_prefix_default': true,
    'timetable_profiles': jsonEncode([profile.toJson()]),
    'active_timetable_profile_id': profile.id,
    'time_schemes': '[]',
  });
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const homeWidgetChannel = MethodChannel('com.mutx163.qingyu/home_widget');
  const analyticsChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');
  const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');

  setUp(() {
    StorageService().resetForTesting();
    _seedInitializedPrefs();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(homeWidgetChannel, (call) async => null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(analyticsChannel, (call) async => null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, (call) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(homeWidgetChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(analyticsChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, null);
  });

  Future<TimetableProvider> openHyperFocusTimingScreen(WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final provider = await createInitializedTestProvider(tester);

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const TestApp(home: TimetableSettingsScreen()),
      ),
    );
    await _pumpScreen(tester);

    await tester.scrollUntilVisible(
      find.text('超级岛与通知'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('超级岛与通知'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('小米超级岛'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('小米超级岛'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('提醒时机'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('提醒时机'));
    await tester.pumpAndSettle();
    return provider;
  }

  testWidgets('hyper focus timing switches edit shared live settings', (
    tester,
  ) async {
    final provider = await openHyperFocusTimingScreen(tester);

    expect(find.text('上课前提醒'), findsOneWidget);
    expect(provider.settings.liveEnableBeforeClass, isTrue);

    await tester.tap(find.text('上课前提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableBeforeClass, isFalse);

    await tester.tap(find.text('课中与下课提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableDuringClass, isFalse);
    expect(provider.settings.liveEnableBeforeEnd, isFalse);
    expect(find.text('重点提醒切入时机'), findsNothing);

    await tester.tap(find.text('课中与下课提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableDuringClass, isTrue);
    expect(provider.settings.liveEnableBeforeEnd, isTrue);
    expect(find.text('重点提醒切入时机'), findsOneWidget);
  });

  testWidgets('hyper focus timing thresholds share live settings', (
    tester,
  ) async {
    final provider = await openHyperFocusTimingScreen(tester);

    await tester.scrollUntilVisible(
      find.text('时间阈值'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('20 分钟').first);
    await tester.pumpAndSettle();
    for (final minutes in [30, 40, 50, 60]) {
      expect(find.text('$minutes 分钟'), findsWidgets);
    }
    await tester.tap(find.text('30 分钟').first);
    await tester.pumpAndSettle();
    expect(provider.settings.liveShowBeforeClassMinutes, 30);
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test\widgets\hyperfocus_timing_screen_test.dart`
Expected: 失败 — 找不到 `上课前提醒`（当前页面只有 `课前提醒`），第一个用例在第一个 `expect` 处 FAIL。

- [ ] **Step 3: 提交测试**

```bash
git add test/widgets/hyperfocus_timing_screen_test.dart
git commit -m "test: hyper focus timing page shares live settings"
```

---

### Task 2: 重写 HyperFocusTimingScreen

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart:1195-1252`（`HyperFocusTimingScreen` 类及其 State 整体替换）

**Interfaces:**
- Consumes: 本文件已有顶层函数 `_formatLiveTimeCorrection(l10n, seconds)` 与 `_buildLiveClassReminderLeadSummary(l10n, settings)`（Live 页在用，勿改动）；同文件 Live 页的 `_beforeClassMinutesOptions/_endSecondsOptions/_timeCorrectionMin/_timeCorrectionMax` 常量；`HyperosListView/HyperosSectionLabel/HyperosListGroup/HyperosSwitchTile/HyperosSelectTile/HyperosSliderTile/HyperosSectionGap/HyperosSubpage`（已在文件顶部 import）；`TimetableSettings`、`Timer`、`showAppToast`（均已在文件作用域可用）。
- Produces: 新 `_HyperFocusTimingScreenState`，读写字段：`liveEnableBeforeClass`、`liveEnableDuringClass`、`liveEnableBeforeEnd`、`liveClassReminderStartMinutes`、`liveShowBeforeClassMinutes`、`liveEndSecondsCountdownThreshold`、`liveTimeCorrectionSeconds`。Task 3 依赖：本页不再引用 `hfEnable*`。

- [ ] **Step 1: 替换 `HyperFocusTimingScreen` 类**

把 `lib/screens/live_settings_subpages.dart` 中 `class HyperFocusTimingScreen`（1195 行起）到 `_HyperFocusTimingScreenState` 结束（1252 行 `}`）的整段，替换为：

```dart
class HyperFocusTimingScreen extends StatefulWidget {
  const HyperFocusTimingScreen({super.key});

  @override
  State<HyperFocusTimingScreen> createState() => _HyperFocusTimingScreenState();
}

class _HyperFocusTimingScreenState extends State<HyperFocusTimingScreen> {
  static const List<int> _beforeClassMinutesOptions = [
    1,
    5,
    10,
    15,
    20,
    30,
    40,
    50,
    60,
  ];
  static const List<int> _endSecondsOptions = [15, 30, 45, 60, 90];
  static const double _timeCorrectionMin = -30;
  static const double _timeCorrectionMax = 30;

  late TimetableSettings _draft;
  Timer? _autoSaveTimer;
  Future<void> _saveQueue = Future<void>.value();

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  @override
  void dispose() {
    if (_autoSaveTimer?.isActive ?? false) {
      _autoSaveTimer?.cancel();
      _enqueuePersist(_draft);
    } else {
      _autoSaveTimer?.cancel();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final duringClassEnabled =
        _draft.liveEnableDuringClass || _draft.liveEnableBeforeEnd;
    final timeCorrectionText = _formatLiveTimeCorrection(
      l10n,
      _draft.liveTimeCorrectionSeconds,
    );
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('提醒时机'),
      child: HyperosListView(
        children: [
          HyperosSectionLabel(text: l10n.liveReminderSwitchesTitle),
          HyperosListGroup(
            children: [
              HyperosSwitchTile(
                title: l10n.beforeClassReminderTitle,
                subtitle: l10n.beforeClassReminderSubtitle(
                  _draft.liveShowBeforeClassMinutes,
                ),
                value: _draft.liveEnableBeforeClass,
                onChanged: (value) =>
                    _updateDraft(_draft.copyWith(liveEnableBeforeClass: value)),
              ),
              HyperosSwitchTile(
                title: l10n.duringClassReminderTitle,
                subtitle: l10n.duringClassReminderSubtitle,
                value: duringClassEnabled,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(
                    liveEnableDuringClass: value,
                    liveEnableBeforeEnd: value,
                  ),
                ),
              ),
              if (duringClassEnabled)
                HyperosSelectTile<int>(
                  label: l10n.liveClassReminderLeadTitle,
                  subtitle: _buildLiveClassReminderLeadSummary(l10n, _draft),
                  items: {
                    l10n.liveClassReminderLeadOptionImmediate: 0,
                    l10n.liveClassReminderLeadOptionMinutes(5): 5,
                    l10n.liveClassReminderLeadOptionMinutes(10): 10,
                    l10n.liveClassReminderLeadOptionMinutes(15): 15,
                    l10n.liveClassReminderLeadOptionMinutes(20): 20,
                    l10n.liveClassReminderLeadOptionMinutes(30): 30,
                  },
                  value: _draft.liveClassReminderStartMinutes,
                  onChanged: (value) => _updateDraft(
                    _draft.copyWith(liveClassReminderStartMinutes: value),
                  ),
                ),
            ],
          ),
          const HyperosSectionGap(),
          HyperosSectionLabel(text: l10n.liveTimeThresholdTitle),
          HyperosListGroup(
            children: [
              HyperosSelectTile<int>(
                label: l10n.beforeClassPopupLabel,
                items: {
                  for (final value in _beforeClassMinutesOptions)
                    l10n.beforeClassMinutesOption(value): value,
                },
                value: _draft.liveShowBeforeClassMinutes,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(liveShowBeforeClassMinutes: value),
                ),
              ),
              HyperosSelectTile<int>(
                label: l10n.beforeEndSecondsLabel,
                items: {
                  for (final value in _endSecondsOptions)
                    l10n.beforeEndSecondsOption(value): value,
                },
                value: _draft.liveEndSecondsCountdownThreshold,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(liveEndSecondsCountdownThreshold: value),
                ),
              ),
            ],
          ),
          const HyperosSectionGap(),
          HyperosSectionLabel(text: l10n.timeCorrectionTitle),
          HyperosListGroup(
            children: [
              HyperosSliderTile(
                title: l10n.timeCorrectionLabel(timeCorrectionText),
                dialogTitle: l10n.timeCorrectionTitle,
                dialogHelper: l10n.timeCorrectionHelp,
                value: _draft.liveTimeCorrectionSeconds.toDouble(),
                min: _timeCorrectionMin,
                max: _timeCorrectionMax,
                divisions: (_timeCorrectionMax - _timeCorrectionMin).round(),
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(liveTimeCorrectionSeconds: value.round()),
                  debounce: true,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _updateDraft(TimetableSettings next, {bool debounce = false}) {
    setState(() => _draft = next);
    _autoSaveTimer?.cancel();
    if (debounce) {
      _autoSaveTimer = Timer(
        const Duration(milliseconds: 250),
        () => _enqueuePersist(next),
      );
      return;
    }
    _enqueuePersist(next);
  }

  void _enqueuePersist(TimetableSettings next) {
    _saveQueue = _saveQueue.catchError((_) {}).then((_) => _persistDraft(next));
  }

  Future<void> _persistDraft(TimetableSettings next) async {
    final provider = context.read<TimetableProvider>();
    final message = await provider.updateTimetableSettings(next);
    if (!mounted) return;
    if (message != null) {
      showAppToast(context, message: message);
      setState(() => _draft = provider.settings);
    }
  }
}
```

- [ ] **Step 2: 运行测试确认通过**

Run: `flutter test test\widgets\hyperfocus_timing_screen_test.dart`
Expected: 2 个用例全部 PASS。

- [ ] **Step 3: 跑一遍相关既有测试防回归**

Run: `flutter test test\widgets\timetable_settings_screen_test.dart`
Expected: 全部 PASS（该文件不触碰超级岛页）。

- [ ] **Step 4: 提交**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: align HyperFocus timing screen with Live Updates settings"
```

---

### Task 3: 更新入口摘要并移除 hfEnable* 死字段

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart:1783`（入口 tile details）
- Modify: `lib/models/timetable_settings.dart`（字段声明 1083-1085、构造默认 1243-1245、toJson 1557-1559、fromJson 1882-1884、copyWith 参数 2102-2104 与实现 2363-2365）

**Interfaces:**
- Consumes: Task 2 后页面已无 `hfEnable*` 引用；本 Task 移除字段后，代码库中不得再有任何 `hfEnable*` 引用。
- Produces: 干净的 `TimetableSettings` 模型（无 `hfEnable*`）；入口 tile 显示 live 状态摘要。

- [ ] **Step 1: 更新入口 tile 摘要**

把 `lib/screens/timetable_settings_screen.dart:1783` 的：

```dart
          details: '${_draft.hfEnableBeforeClass ? "课前 " : ""}${_draft.hfEnableDuringClass ? "课中 " : ""}${_draft.hfEnableBeforeEnd ? "课后" : ""}',
```

替换为：

```dart
          details: '课前: ${_draft.liveEnableBeforeClass ? "开" : "关"} 课中: ${_draft.liveEnableDuringClass || _draft.liveEnableBeforeEnd ? "开" : "关"}',
```

- [ ] **Step 2: 移除模型字段**

在 `lib/models/timetable_settings.dart` 中删除以下 6 处（每处一行/一段，精确匹配）：

1. 字段声明（约 1083-1085 行）：
```dart
  final bool hfEnableBeforeClass;
  final bool hfEnableDuringClass;
  final bool hfEnableBeforeEnd;
```
2. 构造函数默认值（约 1243-1245 行）：
```dart
    this.hfEnableBeforeClass = true,
    this.hfEnableDuringClass = true,
    this.hfEnableBeforeEnd = true,
```
3. toJson（约 1557-1559 行）：
```dart
      'hfEnableBeforeClass': hfEnableBeforeClass,
      'hfEnableDuringClass': hfEnableDuringClass,
      'hfEnableBeforeEnd': hfEnableBeforeEnd,
```
4. fromJson（约 1882-1884 行）：
```dart
      hfEnableBeforeClass: json['hfEnableBeforeClass'] as bool? ?? true,
      hfEnableDuringClass: json['hfEnableDuringClass'] as bool? ?? true,
      hfEnableBeforeEnd: json['hfEnableBeforeEnd'] as bool? ?? true,
```
5. copyWith 参数（约 2102-2104 行）：
```dart
    bool? hfEnableBeforeClass,
    bool? hfEnableDuringClass,
    bool? hfEnableBeforeEnd,
```
6. copyWith 实现（约 2363-2365 行）：
```dart
      hfEnableBeforeClass: hfEnableBeforeClass ?? this.hfEnableBeforeClass,
      hfEnableDuringClass: hfEnableDuringClass ?? this.hfEnableDuringClass,
      hfEnableBeforeEnd: hfEnableBeforeEnd ?? this.hfEnableBeforeEnd,
```

- [ ] **Step 3: 确认无残留引用**

Run: `rg -n "hfEnable" lib test`
Expected: 无输出。

- [ ] **Step 4: 跑测试确认无回归**

Run: `flutter test test\widgets\hyperfocus_timing_screen_test.dart && flutter test test\widgets\timetable_settings_screen_test.dart`
Expected: 全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add lib/screens/timetable_settings_screen.dart lib/models/timetable_settings.dart
git commit -m "refactor: remove dead hfEnable* settings fields"
```

---

### Task 4: 全量验证

**Files:** 无改动。

- [ ] **Step 1: 静态分析**

Run: `flutter analyze lib\models\timetable_settings.dart lib\screens\live_settings_subpages.dart lib\screens\timetable_settings_screen.dart test\widgets\hyperfocus_timing_screen_test.dart`
Expected: `No issues found!`（或仅有改动前已存在的 info 级提示，无 error/warning）。

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: 全部 PASS。

- [ ] **Step 3: Android 构建确认**

Run: `.\gradlew assembleDebug`（工作目录 `C:\daima\zwg\mikcb\mikcb-ECJTU\android`）
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交收尾（如 Step 1-3 有格式修正则一并提交）**

```bash
git status
```

Expected: 工作区干净（除未跟踪文件外）。如有改动，提交并说明。
