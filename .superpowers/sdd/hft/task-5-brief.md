### Task 5: 超级岛测试与诊断页面实现（GREEN）

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart`
  - 现有"测试"tile（~1825-1900 区域，`_buildHyperFocusSettings` 内）的 onTap 改为跳转新页面，details 用 `l10n.hfTestingEntryDetails`
  - 新增私有状态类 `_HyperFocusTestingScreenState` + 页面类 `_HyperFocusTestingSettingsScreen`（镜像 `_LiveTestingSettingsScreen`，放在该文件 `_LiveTestingSettingsScreen` 类附近）

**Interfaces:**
- Consumes: Task 1 l10n key（`hfTesting*` + 复用的 `liveTesting*`/`liveDiagnostics*`）、Task 2 `getHyperFocusDebugStatus()`、`sendTestFocusNotification`、现有私有助手 `_debugSectionMap`/`_debugValueText`/`_DebugStatusChip`/`_refreshDebugStatus` 模式、`HyperosNavigation`、`showHyperosSnackBar`、`showAppToast`、`appDebugLog`、Umeng 崩溃/ANR 触发器（`_triggerUmengTestCrash`/`_triggerUmengTestAnr` 所在类——若为 Live 测试页私有方法，本页面重复实现同等逻辑：`kReleaseMode` 下隐藏）
- Produces: 通过入口跳转可达的"超级岛测试与诊断"页；测试：Task 3 的两个测试应通过

- [ ] **Step 1: 替换"测试"tile 的 onTap 为页面跳转**

在 `_buildHyperFocusSettings` 中把现有"测试"tile（icon `Icons.science_outlined`）替换为：

```dart
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
```

删除原 onTap 中的底部菜单 + 发送逻辑（~1830-1900 区域整体替换为上述代码）。

- [ ] **Step 2: 实现页面主体（镜像 Live 测试页）**

在文件中新增（位置：`_LiveTestingSettingsScreen` 状态类附近；页面类命名 `_HyperFocusTestingSettingsScreen`，状态类 `_HyperFocusTestingScreenState`）：

```dart
class _HyperFocusTestingSettingsScreen extends StatefulWidget {
  const _HyperFocusTestingSettingsScreen();

  @override
  State<_HyperFocusTestingSettingsScreen> createState() =>
      _HyperFocusTestingScreenState();
}

class _HyperFocusTestingScreenState extends State<_HyperFocusTestingSettingsScreen>
    with WidgetsBindingObserver {
  static const Duration _autoRefreshInterval = Duration(seconds: 1);

  final MiuiLiveActivitiesService _hyperFocusService = MiuiLiveActivitiesService();
  Map<String, dynamic>? _debugStatus;
  bool _loadingDebugStatus = true;
  bool _exportingDiagnostics = false;
  bool _clearingDiagnostics = false;
  bool _openingDiagnosticsViewer = false;
  Timer? _autoRefreshTimer;
  bool _refreshInFlight = false;
  bool _isAppResumed = true;
  bool _autoRefreshEnabled = true;
  DateTime? _lastDebugStatusUpdatedAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_refreshDebugStatus(showLoading: true));
    _autoRefreshTimer = Timer.periodic(_autoRefreshInterval, (_) {
      if (!mounted || !_isAppResumed) return;
      if (!_autoRefreshEnabled) return;
      unawaited(_refreshDebugStatus());
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isAppResumed = state == AppLifecycleState.resumed;
  }

  @override
  void dispose() {
    _autoRefreshTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _refreshDebugStatus({bool showLoading = false}) async {
    if (_refreshInFlight) return;
    _refreshInFlight = true;
    if (mounted && showLoading) {
      setState(() => _loadingDebugStatus = true);
    }
    try {
      final status = await _hyperFocusService.getHyperFocusDebugStatus();
      if (!mounted) return;
      setState(() {
        _debugStatus = status;
        _loadingDebugStatus = false;
        _lastDebugStatusUpdatedAt = DateTime.now();
      });
    } finally {
      _refreshInFlight = false;
      if (mounted && showLoading) {
        setState(() => _loadingDebugStatus = false);
      }
    }
  }

  Future<void> _sendTestNotification() async {
    final l10n = AppLocalizations.of(context)!;
    final stage = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                l10n.hfTestingStageSheetTitle,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.alarm),
              title: Text(l10n.hfTestingStagePreTitle),
              subtitle: Text(l10n.hfTestingStagePreSubtitle),
              onTap: () => Navigator.pop(sheetContext, 'pre'),
            ),
            ListTile(
              leading: const Icon(Icons.school),
              title: Text(l10n.hfTestingStageActiveTitle),
              subtitle: Text(l10n.hfTestingStageActiveSubtitle),
              onTap: () => Navigator.pop(sheetContext, 'active'),
            ),
            ListTile(
              leading: const Icon(Icons.flag),
              title: Text(l10n.hfTestingStagePostTitle),
              subtitle: Text(l10n.hfTestingStagePostSubtitle),
              onTap: () => Navigator.pop(sheetContext, 'post'),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (stage == null || !mounted) return;
    appDebugLog('MiuiLive', '测试阶段：$stage');
    final provider = context.read<TimetableProvider>();
    await provider.initialize();
    final selection = provider.getTestLiveActivityCourseSelection();
    final course = selection?.currentCourse;
    appDebugLog('MiuiLive', '测试课程：${course?.name}');
    final error = await _hyperFocusService.sendTestFocusNotification(
      courseName: course?.name,
      startTime: course?.startTime,
      endTime: course?.endTime,
      location: (course?.location.isNotEmpty ?? false) ? course!.location : null,
      teacher: (course?.teacher.isNotEmpty ?? false) ? course!.teacher : null,
      stage: stage,
    );
    appDebugLog('MiuiLive', '发送结果：${error ?? '成功'}');
    if (!mounted) return;
    showHyperosSnackBar(context, message: error ?? '测试焦点通知已发送');
    await _refreshDebugStatus(showLoading: true);
  }

  Future<void> _exportDiagnostics() async {
    final l10n = AppLocalizations.of(context)!;
    if (_exportingDiagnostics) return;
    setState(() => _exportingDiagnostics = true);
    final logPath = await _hyperFocusService.exportLiveDiagnosticsFile();
    if (!mounted) return;
    var exportPath = logPath;
    var shareText = l10n.liveDiagnosticsShareText;
    var shareSubject = l10n.liveDiagnosticsShareSubject;
    if ((exportPath == null || exportPath.isEmpty) && _debugStatus != null) {
      exportPath = await _exportCurrentDebugSnapshot();
      shareText = l10n.liveDiagnosticsSnapshotShareText;
      shareSubject = l10n.liveDiagnosticsSnapshotShareSubject;
    }
    if (!mounted) return;
    setState(() => _exportingDiagnostics = false);
    if (exportPath == null || exportPath.isEmpty) {
      showAppToast(
        context,
        message: AppLocalizations.of(context)!.liveDiagnosticsNothingToExport,
        kind: AppToastKind.warning,
      );
      return;
    }
    final result = await SharePlus.instance.share(
      ShareParams(
        text: shareText,
        subject: shareSubject,
        files: [XFile(exportPath)],
      ),
    );
    if (!mounted) return;
    if (result.status == ShareResultStatus.dismissed) {
      showAppToast(
        context,
        message: l10n.liveDiagnosticsShareCancelled,
        kind: AppToastKind.info,
      );
    }
  }

  Future<String?> _exportCurrentDebugSnapshot() async {
    final tempDir = await getTemporaryDirectory();
    final file = File(
      '${tempDir.path}/hyper_focus_debug_${DateTime.now().millisecondsSinceEpoch}.json',
    );
    await file.writeAsString(
      const JsonEncoder.withIndent('  ').convert(_debugStatus ?? const {}),
    );
    return file.path;
  }

  String _formatTimeOfDay(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';

  String _formatMillis(int? millis, AppLocalizations l10n) {
    if (millis == null) return l10n.hfTestingNone;
    final t = DateTime.fromMillisecondsSinceEpoch(millis);
    return _formatTimeOfDay(t);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final summary = _debugSectionMap(_debugStatus?['summary']);
    final scheduling = _debugSectionMap(_debugStatus?['scheduling']);
    final templates = _debugSectionMap(_debugStatus?['templates']);
    final test = _debugSectionMap(_debugStatus?['test']);
    final recentDiagnostics = _debugSectionMap(
      _debugStatus?['recentDiagnostics'],
    );
    final hasPermission = summary['hasNotificationPermission'] == true;
    final channelBlocked = summary['testChannelBlocked'] == true;
    final schedulerReady = summary['schedulerReady'] == true;
    final templatesLoaded = summary['templatesLoaded'] == true;
    final hasLastTest = summary['hasLastTestResult'] == true;
    final lastTestSucceeded = summary['lastTestSucceeded'] == true;
    final lastTestMessage = _debugValueText(summary['lastTestMessage']);
    final lastTestStage = _debugValueText(summary['lastTestStage']);
    final refreshedAt = _lastDebugStatusUpdatedAt;
    final refreshedAtText = refreshedAt == null
        ? l10n.liveTestingNotRefreshed
        : _formatTimeOfDay(refreshedAt);

    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: Text(l10n.hfTestingTitle),
      child: HyperosListView(
        itemCount: _sections().length,
        itemBuilder: (context, index) => _buildSection(
          context,
          _sections()[index],
          l10n: l10n,
          summary: summary,
          scheduling: scheduling,
          templates: templates,
          test: test,
          recentDiagnostics: recentDiagnostics,
          hasPermission: hasPermission,
          channelBlocked: channelBlocked,
          schedulerReady: schedulerReady,
          templatesLoaded: templatesLoaded,
          hasLastTest: hasLastTest,
          lastTestSucceeded: lastTestSucceeded,
          lastTestMessage: lastTestMessage,
          lastTestStage: lastTestStage,
          refreshedAtText: refreshedAtText,
        ),
      ),
    );
  }

  List<_HyperFocusTestingSection> _sections() => [
    _HyperFocusTestingSection.notification,
    _HyperFocusTestingSection.islandStatus,
    if (_debugStatus != null) ...[
      _HyperFocusTestingSection.debugScheduling,
      _HyperFocusTestingSection.debugTemplates,
      _HyperFocusTestingSection.debugLastTest,
      _HyperFocusTestingSection.debugEnvironment,
      _HyperFocusTestingSection.debugRecentLogs,
      _HyperFocusTestingSection.rawJson,
    ],
    _HyperFocusTestingSection.localLogs,
  ];
}
```

- [ ] **Step 3: 实现分区构建器与枚举**

继续在该文件中新增（与上一步同处）：

```dart
enum _HyperFocusTestingSection {
  notification,
  islandStatus,
  debugScheduling,
  debugTemplates,
  debugLastTest,
  debugEnvironment,
  debugRecentLogs,
  rawJson,
  localLogs,
}
```

```dart
  Widget _buildSection(
    BuildContext context,
    _HyperFocusTestingSection section, {
    required AppLocalizations l10n,
    required Map<String, dynamic> summary,
    required Map<String, dynamic> scheduling,
    required Map<String, dynamic> templates,
    required Map<String, dynamic> test,
    required Map<String, dynamic> recentDiagnostics,
    required bool hasPermission,
    required bool channelBlocked,
    required bool schedulerReady,
    required bool templatesLoaded,
    required bool hasLastTest,
    required bool lastTestSucceeded,
    required String lastTestMessage,
    required String lastTestStage,
    required String refreshedAtText,
  }) {
    return switch (section) {
      _HyperFocusTestingSection.notification => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          HyperosControlCard(
            title: l10n.hfTestingNotificationTitle,
            subtitle: l10n.hfTestingNotificationSubtitle,
            child: HyperosControlCardInset(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  HyperosButton(
                    label: l10n.liveTestingSendAction,
                    variant: HyperosButtonVariant.secondary,
                    onPressed: () => _sendTestNotification(),
                  ),
                  if (!kReleaseMode) ...[
                    const SizedBox(height: 12),
                    Text(
                      l10n.liveTestingUmengHint,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      children: [
                        HyperosButton(
                          label: l10n.liveTestingCrashAction,
                          variant: HyperosButtonVariant.secondary,
                          onPressed: () => _triggerUmengTestCrash(context),
                        ),
                        HyperosButton(
                          label: l10n.liveTestingAnrAction,
                          variant: HyperosButtonVariant.secondary,
                          onPressed: () => _triggerUmengTestAnr(context),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          const HyperosSectionGap(),
        ],
      ),
      _HyperFocusTestingSection.islandStatus => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          HyperosControlCard(
            title: l10n.hfTestingIslandStatusTitle,
            subtitle: l10n.hfTestingIslandStatusSubtitle,
            child: HyperosControlCardInset(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _DebugStatusChip(
                        icon: hasPermission
                            ? Icons.verified_outlined
                            : Icons.warning_amber_rounded,
                        label: hasPermission
                            ? l10n.hfTestingPermissionGranted
                            : l10n.hfTestingPermissionDenied,
                        color: hasPermission
                            ? Theme.of(context).colorScheme.primary
                            : Theme.of(context).colorScheme.error,
                      ),
                      _DebugStatusChip(
                        icon: channelBlocked
                            ? Icons.warning_amber_rounded
                            : Icons.verified_outlined,
                        label: channelBlocked
                            ? l10n.hfTestingChannelBlocked
                            : l10n.hfTestingChannelOk,
                        color: channelBlocked
                            ? Theme.of(context).colorScheme.error
                            : Colors.green,
                      ),
                      _DebugStatusChip(
                        icon: schedulerReady
                            ? Icons.schedule_outlined
                            : Icons.schedule,
                        label: schedulerReady
                            ? l10n.hfTestingSchedulerScheduled
                            : l10n.hfTestingSchedulerNoData,
                        color: schedulerReady
                            ? Theme.of(context).colorScheme.primary
                            : Theme.of(context).colorScheme.outline,
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (channelBlocked) ...[
                    Text(
                      l10n.hfTestingChannelBlockedHint,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
                  if (!schedulerReady) ...[
                    Text(
                      l10n.hfTestingNoScheduleHint,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.outline,
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          spacing: 12,
                          runSpacing: 12,
                          children: [
                            HyperosButton(
                              label: _loadingDebugStatus
                                  ? l10n.liveTestingRefreshing
                                  : l10n.liveTestingRefreshAction,
                              variant: HyperosButtonVariant.secondary,
                              loading: _loadingDebugStatus,
                              onPressed: _loadingDebugStatus
                                  ? null
                                  : () => _refreshDebugStatus(showLoading: true),
                            ),
                            HyperosButton(
                              label: _exportingDiagnostics
                                  ? l10n.liveTestingExporting
                                  : l10n.liveTestingExportAction,
                              variant: HyperosButtonVariant.secondary,
                              loading: _exportingDiagnostics,
                              onPressed: _exportingDiagnostics
                                  ? null
                                  : _exportDiagnostics,
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        HyperosSwitchTile(
                          value: _autoRefreshEnabled,
                          onChanged: (value) {
                            setState(() => _autoRefreshEnabled = value);
                          },
                          title: l10n.liveTestingAutoRefreshTitle,
                          subtitle: _autoRefreshEnabled
                              ? l10n.liveTestingAutoRefreshOn(
                                  _autoRefreshInterval.inSeconds,
                                )
                              : l10n.liveTestingAutoRefreshOff,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          l10n.liveTestingRefreshedAt(refreshedAtText),
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const HyperosSectionGap(),
        ],
      ),
      _HyperFocusTestingSection.debugScheduling => _buildDebugSection(
        context: context,
        title: l10n.hfTestingDebugScheduling,
        entries: {
          l10n.hfTestingNextCourse:
              _debugValueText(scheduling['nextCourseName']),
          l10n.hfTestingNextTrigger:
              _formatMillis(
                scheduling['nextTriggerAtMillis'] as int?,
                l10n,
              ),
          l10n.hfTestingCurrentStage:
              _debugValueText(scheduling['nextTriggerStage']),
          l10n.hfTestingBeforeClassBlockedUntil: l10n.hfTestingNone,
          l10n.hfTestingSuspendedUntil:
              _formatMillis(
                scheduling['suspendedUntilMillis'] as int?,
                l10n,
              ),
        },
      ),
      _HyperFocusTestingSection.debugTemplates => _buildDebugSection(
        context: context,
        title: l10n.hfTestingDebugTemplates,
        entries: {
          l10n.hfTestingTemplateStagePre: _templateSummary(templates['pre'], l10n),
          l10n.hfTestingTemplateStageActive:
              _templateSummary(templates['active'], l10n),
          l10n.hfTestingTemplateStagePost: _templateSummary(templates['post'], l10n),
        },
      ),
      _HyperFocusTestingSection.debugLastTest => _buildDebugSection(
        context: context,
        title: l10n.hfTestingDebugLastTest,
        entries: {
          if (!hasLastTest)
            l10n.hfTestingLastTestNever: ''
          else ...[
            l10n.hfTestingLastTestStage: lastTestStage,
            l10n.hfTestingLastTestResult: lastTestSucceeded
                ? l10n.hfTestingLastTestSucceeded
                : l10n.hfTestingLastTestFailed,
            if (lastTestMessage.isNotEmpty)
              l10n.hfTestingLastTestMessage: lastTestMessage,
            l10n.hfTestingLastTestTime:
                _formatMillis(test['lastAtMillis'] as int?, l10n),
          ],
        },
      ),
      _HyperFocusTestingSection.debugEnvironment => _buildDebugSection(
        context: context,
        title: l10n.liveTestingDebugEnvironmentTitle,
        entries: {
          for (final entry in _debugSectionMap(_debugStatus?['environment'])
              .entries)
            entry.key: _debugValueText(entry.value),
        },
      ),
      _HyperFocusTestingSection.debugRecentLogs => _buildDebugSection(
        context: context,
        title: l10n.liveTestingRecentLogsTitle,
        entries: {
          l10n.liveDiagnosticsEnabledTitle:
              recentDiagnostics['enabled'] == true ? '开' : '关',
          l10n.liveDiagnosticsTailTitle:
              _debugValueText(recentDiagnostics['tail']),
        },
      ),
      _HyperFocusTestingSection.rawJson => _buildDebugSection(
        context: context,
        title: l10n.liveTestingRawJsonTitle,
        entries: const {'': ''},
        trailingJson: _debugStatus == null
            ? ''
            : const JsonEncoder.withIndent('  ').convert(_debugStatus),
      ),
      _HyperFocusTestingSection.localLogs => _buildLocalLogsSection(context),
    };
  }

  String _templateSummary(dynamic stageTemplates, AppLocalizations l10n) {
    if (stageTemplates is! Map) return l10n.hfTestingNone;
    final flags = stageTemplates.entries
        .where((e) => e.value == true)
        .map((e) => e.key)
        .toList();
    return flags.isEmpty ? l10n.hfTestingNone : flags.join(' · ');
  }

  Widget _buildDebugSection({
    required BuildContext context,
    required String title,
    required Map<String, String> entries,
    String trailingJson = '',
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        HyperosSectionLabel(text: title),
        HyperosListGroup(
          children: [
            for (final entry in entries.entries)
              HyperosListTile(
                title: Text(entry.key),
                details: entry.value,
              ),
            if (trailingJson.isNotEmpty)
              HyperosListTile(
                title: const Text('JSON'),
                details: trailingJson,
              ),
          ],
        ),
        const HyperosSectionGap(),
      ],
    );
  }

  Widget _buildLocalLogsSection(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        HyperosSectionLabel(text: l10n.liveTestingLocalLogsTitle),
        HyperosListGroup(
          children: [
            HyperosListTile(
              icon: Icons.description_outlined,
              title: l10n.liveDiagnosticsViewerTitle,
              onTap: () => _openDiagnosticsViewer(context),
            ),
            HyperosListTile(
              icon: Icons.delete_outline,
              title: l10n.liveDiagnosticsClearAction,
              onTap: () => _clearDiagnostics(context),
            ),
          ],
        ),
        const HyperosSectionGap(),
      ],
    );
  }

  Future<void> _openDiagnosticsViewer(BuildContext context) async {
    final l10n = AppLocalizations.of(context)!;
    if (_openingDiagnosticsViewer) return;
    _openingDiagnosticsViewer = true;
    try {
      await HyperosNavigation.push(
        context,
        builder: (context) => LiveDiagnosticsLogViewerScreen(
          title: l10n.liveDiagnosticsViewerTitle,
          watchRawLog: () => _hyperFocusService.watchLiveDiagnosticsText(),
          onLoadEmpty: () {
            if (!context.mounted) return;
            showAppToast(
              context,
              message: l10n.liveDiagnosticsUnavailable,
              kind: AppToastKind.warning,
            );
            Navigator.of(context).pop();
          },
        ),
      );
    } finally {
      _openingDiagnosticsViewer = false;
    }
  }

  Future<void> _clearDiagnostics(BuildContext context) async {
    final l10n = AppLocalizations.of(context)!;
    if (_clearingDiagnostics) return;
    _clearingDiagnostics = true;
    try {
      await _hyperFocusService.clearLiveDiagnostics();
      if (!mounted) return;
      showAppToast(
        context,
        message: l10n.liveDiagnosticsCleared,
        kind: AppToastKind.success,
      );
      await _refreshDebugStatus(showLoading: true);
    } finally {
      _clearingDiagnostics = false;
    }
  }

  Future<void> _triggerUmengTestCrash(BuildContext context) async {
    appDebugLog('MiuiLive', '触发 Umeng 测试崩溃');
    const MethodChannel('com.mutx163.qingyu/umeng_analytics')
        .invokeMethod('triggerTestCrash');
  }

  Future<void> _triggerUmengTestAnr(BuildContext context) async {
    appDebugLog('MiuiLive', '触发 Umeng 测试 ANR');
    const MethodChannel('com.mutx163.qingyu/umeng_analytics')
        .invokeMethod('triggerTestAnr');
  }
```

（以上方法与枚举需插入到 `_HyperFocusTestingScreenState` 类体内；`_triggerUmengTestCrash`/`_triggerUmengTestAnr` 的 MethodChannel 方法名须与 Live 页现有实现一致——若 `_LiveTestingSettingsScreen` 已有同名私有方法且处于不同类，直接按上述定义；若 Live 实现调用的是别的辅助函数，以 Live 实现为准复制等价逻辑。）

- [ ] **Step 4: 核对所有引用项存在**

编译前确认（可用 grep）：
- `l10n.liveTestingAutoRefreshOn(int)` / `liveTestingAutoRefreshOff` / `liveTestingRefreshedAt(String)`（Live 页实际用法，timetable_settings_screen.dart:2391-2397）/ `liveTestingDebugEnvironmentTitle` / `liveTestingRecentLogsTitle` / `liveTestingLocalLogsTitle` / `liveTestingRawJsonTitle` / `liveDiagnosticsEnabledTitle` / `liveDiagnosticsTailTitle` / `liveDiagnosticsViewerTitle` / `liveDiagnosticsClearAction` / `liveDiagnosticsCleared` / `liveDiagnosticsUnavailable` / `liveTestingSendAction` / `liveTestingUmengHint` / `liveTestingCrashAction` / `liveTestingAnrAction` / `liveTestingRefreshAction` / `liveTestingRefreshing` / `liveTestingExportAction` / `liveTestingExporting` / `liveTestingAutoRefreshTitle` / `liveTestingNotRefreshed` / `liveTestingIslandStatusTitle` 等均存在（不在则从 `_LiveTestingSettingsScreen` 实际用法中替换）；Task 1 新增的 `hfTestingLastTestSucceeded`/`hfTestingLastTestFailed`/`hfTestingNone` 等 getter 已生成
- `HyperosControlCardInset`、`HyperosSwitchTile`、`HyperosSectionLabel`、`HyperosButton`、`SharePlus.instance.share(ShareParams(...))`、`XFile`、`getTemporaryDirectory`、`File`、`JsonEncoder`、`LiveDiagnosticsLogViewerScreen`、`AppToastKind` 的 import 均已存在于文件头部或与本文件现有用法一致
- `_debugSectionMap`/`_debugValueText`/`_DebugStatusChip` 是否在 `_LiveTestingSettingsScreen` 的 State 类或文件顶层——若在 State 类内私有，则本页面复制等价实现（同名即可，类内私有不冲突）

- [ ] **Step 5: 运行测试确认 GREEN**

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: 2 个测试全部 PASS（若步骤 4 发现 key 缺失等编译错误，修正后重跑）

- [ ] **Step 6: 运行 analyze**

Run: `flutter analyze lib/screens/timetable_settings_screen.dart test/widgets/hyper_focus_testing_screen_test.dart`
Expected: `No issues found!`

- [ ] **Step 7: 提交**

```bash
git add lib/screens/timetable_settings_screen.dart
git commit -m "feat: add HyperFocus testing and diagnostics screen"
```

---


