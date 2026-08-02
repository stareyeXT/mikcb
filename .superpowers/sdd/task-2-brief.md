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

