### Task 5: UI — Live Settings 添加引擎选择器 + 条件渲染

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart` (`_LiveSettingsScreen`)

- [ ] **Step 1: 在 `_LiveSettingsScreenState` 中添加引擎选择方法**

```dart
  void _onEngineChanged(SuperIslandEngine engine) {
    final next = _draft.copyWith(superIslandEngine: engine);
    context.read<TimetableProvider>().updateTimetableSettings(next);
    setState(() => _draft = next);
  }
```

- [ ] **Step 2: 修改 `_buildLiveSettingsSection` 方法，顶部添加引擎选择卡片**

将 `_buildLiveSettingsSection` 改为先渲染引擎选择器，再根据引擎渲染不同内容：

```dart
  Widget _buildLiveSettingsSection(BuildContext context, int index) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        _buildEngineSelector(context, l10n),
        const HyperosSectionGap(),
        if (_draft.superIslandEngine == SuperIslandEngine.builtIn)
          _buildLiveUpdatesSettings(context, l10n)
        else
          _buildHyperFocusSettings(context, l10n),
      ],
    );
  }

  Widget _buildEngineSelector(BuildContext context, AppLocalizations l10n) {
    return HyperosSettingsBlock(
      title: '超级岛引擎',
      child: HyperosListGroup(
        children: [
          HyperosRadioTile<SuperIslandEngine>(
            title: 'Live Updates（内置）',
            subtitle: '当前默认引擎，支持三阶段提醒、自定义标签等全部功能',
            value: SuperIslandEngine.builtIn,
            groupValue: _draft.superIslandEngine,
            onChanged: (v) => _onEngineChanged(v),
          ),
          HyperosRadioTile<SuperIslandEngine>(
            title: '小米超级岛（HyperFocusApi）',
            subtitle: '基于 HyperFocusApi 的焦点通知方案，与 Live Updates 互斥',
            value: SuperIslandEngine.hyperFocusApi,
            groupValue: _draft.superIslandEngine,
            onChanged: (v) => _onEngineChanged(v),
          ),
        ],
      ),
    );
  }
```

- [ ] **Step 3: 提取现有设置到 `_buildLiveUpdatesSettings` 方法**

```dart
  Widget _buildLiveUpdatesSettings(BuildContext context, AppLocalizations l10n) {
    final beforeClassSummary = _liveDisplaySummary(
      context,
      _draft.beforeClassDisplaySettings,
    );
    final duringEndSummary = _draft.liveDuringEndFollowBeforeClass
        ? l10n.followBeforeClassSetting
        : _liveDisplaySummary(context, _draft.duringEndDisplaySettings);
    return HyperosListGroup(
      children: [
        // ... 现有全部 HyperosListTile（提醒时机、课前显示、课中/课后显示、保活、测试）
        // 原样从 _buildLiveSettingsSection 移过来
      ],
    );
  }
```

- [ ] **Step 4: 添加 `_buildHyperFocusSettings` 方法**

```dart
  Widget _buildHyperFocusSettings(BuildContext context, AppLocalizations l10n) {
    return HyperosListGroup(
      children: [
        HyperosListTile(
          icon: Icons.alarm_outlined,
          title: '提醒时机',
          details: '${_draft.hfEnableBeforeClass ? "课前 " : ""}${_draft.hfEnableDuringClass ? "课中 " : ""}${_draft.hfEnableBeforeEnd ? "课后" : ""}',
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
        HyperosListTile(
          icon: Icons.upcoming_outlined,
          title: '显示设置',
          details: '课名: ${_draft.hfShowCourseName ? "开" : "关"} 地点: ${_draft.hfShowLocation ? "开" : "关"} 倒计时: ${_draft.hfShowCountdown ? "开" : "关"}',
          onTap: () async {
            await HyperosNavigation.push(
              context,
              builder: (_) => const HyperFocusDisplayScreen(),
            );
            if (!mounted) return;
            setState(() {
              _draft = context.read<TimetableProvider>().settings;
            });
          },
        ),
        HyperosListTile(
          icon: Icons.dashboard_customize_outlined,
          title: '超级岛样式',
          details: '（占位）',
          // 占位，无 onTap
        ),
        HyperosListTile(
          icon: Icons.science_outlined,
          title: '测试',
          onTap: () async {
            await HyperosNavigation.push(
              context,
              builder: (_) => const HyperFocusTestScreen(),
            );
            if (!mounted) return;
            setState(() {
              _draft = context.read<TimetableProvider>().settings;
            });
          },
        ),
      ],
    );
  }
```

- [ ] **Step 5: 验证**

Run: `dart analyze lib/screens/timetable_settings_screen.dart`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart
git commit -m "feat: add engine selector and conditional settings rendering in Live Settings"
```

---


