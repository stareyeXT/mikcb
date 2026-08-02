# Task 5a Brief: 菜单重排 + 消失时间页 + Flutter islandConfig 参数传递

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 5（拆分为 Flutter 侧）

## Global Constraints（本项目所有任务适用）

- 视觉/超时默认值（TimetableSettings Task 1 已定义）：hfIslandTimeoutPre=300、hfIslandTimeoutActive=600、hfIslandTimeoutPost=600、hfIconAEnabled=true、hfStatusTextColor=#FFFFFFFF、hfOutEffectStatusEnabled=true、hfOutEffectStatusColor=#FFFFFFFF、hfOutEffectExpandEnabled=true、hfOutEffectExpandColor=#FFFFFFFF
- UI 全部用 Hyperos* 组件
- `flutter analyze` 基线：8 个预存在 infos，0 error；`flutter test` 基线：+716 ~3
- 岛消失时间 UI 限界 30~3600 秒

## Files

- Modify: `lib/screens/timetable_settings_screen.dart`
  - `_buildHyperFocusSettings`（L1777-1827）重排为分组导航
  - 删除"自定义模板"入口（旧类 `HyperFocusStageTemplateScreen` 一并删除，见 Step 2）
- Modify: `lib/screens/live_settings_subpages.dart`
  - 删除旧类 `HyperFocusStageTemplateScreen`（约 L1441-1640，含 State）
  - 新增 `HyperFocusIslandTimeoutScreen`
- Modify: `lib/services/miui_live_activities_service.dart`
  - `startLiveUpdate`（L281）加 9 个命名参数
  - `_buildData`（L449-548）加 9 参数 + islandConfig 加 9 字段
- Modify: `lib/providers/timetable/live_activity_controller.dart`
  - `_liveUpdateActivityBody`（L506-558）传 9 个新参数

## Step 1: 菜单重排（timetable_settings_screen.dart L1777-1827）

`_buildHyperFocusSettings` 返回从 `HyperosListGroup` 改为 `Column`（与 `_buildLiveSettingsSection` 结构一致），分组导航：

```dart
  Widget _buildHyperFocusSettings(BuildContext context, AppLocalizations l10n) {
    return Column(
      children: [
        HyperosSectionLabel(text: '提醒'),
        HyperosListGroup(
          children: [
            HyperosListTile(
              icon: Icons.alarm_outlined,
              title: '提醒时机',
              details: '课前: ${_draft.liveEnableBeforeClass ? "开" : "关"} 课中: ${_draft.liveEnableDuringClass || _draft.liveEnableBeforeEnd ? "开" : "关"}',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => const HyperFocusTimingScreen(),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
          ],
        ),
        const HyperosSectionGap(),
        HyperosSectionLabel(text: '显示自定义'),
        HyperosListGroup(
          children: [
            HyperosListTile(
              icon: Icons.brightness_4_outlined,
              title: '状态栏岛自定义',
              details: '岛A/岛B/息屏文字（课前/课中/课后）',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => const HyperFocusStatusIslandScreen(),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
            HyperosListTile(
              icon: Icons.space_dashboard_outlined,
              title: '展开态自定义',
              details: '主要标题/次要文本/前置文本/主要小文本',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => const HyperFocusExpandedIslandScreen(),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
            HyperosListTile(
              icon: Icons.image_outlined,
              title: '岛视觉',
              details: '岛标签图、Logo、字号、偏移、展开图标',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => LiveDisplaySettingsScreen(
                    title: '岛视觉（课前）',
                    forDuringEnd: false,
                  ),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
            HyperosListTile(
              icon: Icons.image_outlined,
              title: '岛视觉（课中/课后）',
              details: '岛标签图、Logo、字号、偏移、展开图标',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => LiveDisplaySettingsScreen(
                    title: '岛视觉（课中/课后）',
                    forDuringEnd: true,
                  ),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
          ],
        ),
        const HyperosSectionGap(),
        HyperosSectionLabel(text: '消失时间'),
        HyperosListGroup(
          children: [
            HyperosListTile(
              icon: Icons.timer_outlined,
              title: '岛消失时间',
              details: '按课前/课中/课后配置状态栏岛消失时间',
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => const HyperFocusIslandTimeoutScreen(),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
          ],
        ),
        const HyperosSectionGap(),
        HyperosSectionLabel(text: '工具'),
        HyperosListGroup(
          children: [
            HyperosListTile(
              icon: Icons.science_outlined,
              title: '测试',
              details: l10n.hfTestingEntryDetails,
              onTap: () async {
                await HyperosNavigation.push(
                  context,
                  builder: (_) => const _HyperFocusTestingSettingsScreen(),
                );
                if (!mounted) return;
                setState(() {
                  _draft = context.read<TimetableProvider>().settings;
                });
              },
            ),
          ],
        ),
      ],
    );
  }
```

注意：`_buildLiveSettingsSection`（L1651-1660）的 Column 已含 `_buildHyperFocusSettings` 返回的 Column，`HyperosSectionGap` 在 `_buildLiveSettingsSection` 已有（L1654）——分组内用 SectionLabel 分隔。确认最终渲染不冗余（若 `_buildLiveSettingsSection` 的 gap 造成视觉重复可接受，不阻塞）。

## Step 2: 删除旧类 HyperFocusStageTemplateScreen

在 `lib/screens/live_settings_subpages.dart` 删除 `HyperFocusStageTemplateScreen` 类及其 State（约 L1441-1640，具体以 grep `class HyperFocusStageTemplateScreen` 定位）。删除后菜单不再引用它（Step 1 已改）。

## Step 3: 新增 HyperFocusIslandTimeoutScreen

在 `lib/screens/live_settings_subpages.dart` 新增（仿照 `HyperFocusTimingScreen` 的 auto-save 模式，L1195-1371）：

```dart
class HyperFocusIslandTimeoutScreen extends StatefulWidget {
  const HyperFocusIslandTimeoutScreen({super.key});

  @override
  State<HyperFocusIslandTimeoutScreen> createState() =>
      _HyperFocusIslandTimeoutScreenState();
}

class _HyperFocusIslandTimeoutScreenState
    extends State<HyperFocusIslandTimeoutScreen> {
  static const int _minSeconds = 30;
  static const int _maxSeconds = 3600;
  late TimetableSettings _draft;
  Timer? _autoSaveTimer;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  @override
  void dispose() {
    if (_autoSaveTimer?.isActive ?? false) {
      _autoSaveTimer?.cancel();
      _persistDraft(_draft);
    } else {
      _autoSaveTimer?.cancel();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('岛消失时间'),
      child: HyperosListView(
        children: [
          HyperosSectionLabel(text: '状态栏岛消失时间（秒）'),
          HyperosListGroup(
            children: [
              _buildTimeoutTile('课前', _draft.hfIslandTimeoutPre,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPre: v))),
              _buildTimeoutTile('课中', _draft.hfIslandTimeoutActive,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutActive: v))),
              _buildTimeoutTile('课后', _draft.hfIslandTimeoutPost,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPost: v))),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTimeoutTile(
    String label,
    int value,
    ValueChanged<int> onChanged,
  ) {
    return HyperosNumberPickerTile(
      label: label,
      value: value,
      min: _minSeconds,
      max: _maxSeconds,
      onChanged: onChanged,
    );
  }

  void _updateDraft(TimetableSettings next, {bool debounce = true}) {
    setState(() => _draft = next);
    _autoSaveTimer?.cancel();
    if (debounce) {
      _autoSaveTimer = Timer(
        const Duration(milliseconds: 250),
        () => _persistDraft(next),
      );
      return;
    }
    _persistDraft(next);
  }

  void _persistDraft(TimetableSettings next) {
    final provider = context.read<TimetableProvider>();
    unawaited(
      provider.updateTimetableSettings(next).then((message) {
        if (!mounted) return;
        if (message != null) {
          showAppToast(context, message: message);
          setState(() => _draft = provider.settings);
        }
      }).catchError((_) {}),
    );
  }
}
```

> 先 grep `HyperosNumberPickerTile` 确认其构造参数（label/value/min/max/onChanged）是否匹配；若签名不同（如需要 `int?` value 或 `ValueChanged<double>`），以实际签名调整。若 `HyperosNumberPickerTile` 不适合，改用 `HyperosSliderTile`（title/min/max/divisions/onChanged）并显示秒数。**关键约束：三个字段必须可编辑并保存到 hfIslandTimeoutPre/Active/Post。**

## Step 4: startLiveUpdate + _buildData 加参数（miui_live_activities_service.dart）

`startLiveUpdate`（L281 开始）参数区新增 9 个命名参数（默认值与 TimetableSettings 一致）：
```dart
    int hfIslandTimeoutPre = 300,
    int hfIslandTimeoutActive = 600,
    int hfIslandTimeoutPost = 600,
    bool hfIconAEnabled = true,
    String hfStatusTextColor = '#FFFFFFFF',
    bool hfOutEffectStatusEnabled = true,
    String hfOutEffectStatusColor = '#FFFFFFFF',
    bool hfOutEffectExpandEnabled = true,
    String hfOutEffectExpandColor = '#FFFFFFFF',
```

`_buildData` 签名加同样 9 个参数（默认值相同），并在 `islandConfig` map（L522-541）加：
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

`startLiveUpdate` 内调用 `_buildData` 处（L333）透传这 9 个参数。

## Step 5: _liveUpdateActivityBody 传参（live_activity_controller.dart）

`_liveUpdateActivityBody` 的 `startLiveUpdate` 调用（L506-558）末尾（`progressMilestoneTimeTexts` 之后）加：
```dart
      hfIslandTimeoutPre: settings.hfIslandTimeoutPre,
      hfIslandTimeoutActive: settings.hfIslandTimeoutActive,
      hfIslandTimeoutPost: settings.hfIslandTimeoutPost,
      hfIconAEnabled: settings.hfIconAEnabled,
      hfStatusTextColor: settings.hfStatusTextColor,
      hfOutEffectStatusEnabled: settings.hfOutEffectStatusEnabled,
      hfOutEffectStatusColor: settings.hfOutEffectStatusColor,
      hfOutEffectExpandEnabled: settings.hfOutEffectExpandEnabled,
      hfOutEffectExpandColor: settings.hfOutEffectExpandColor,
```

`live_testing_trigger.dart`（L106）的 `startLiveUpdate` 调用**不改**（用默认值）。

## Step 6: 验证

Run: `flutter analyze`
Expected: 8 预存在 infos，0 error（旧类删除后无悬空引用；新页面已接线）

Run: `flutter test`
Expected: +716 ~3 全绿

## Step 7: Commit

```bash
git add lib/screens/timetable_settings_screen.dart lib/screens/live_settings_subpages.dart lib/services/miui_live_activities_service.dart lib/providers/timetable/live_activity_controller.dart
git commit -m "feat: rework hyperfocus settings menu, add island timeout page, plumb visual config"
```
