part of '../timetable_settings_screen.dart';

enum _HyperFocusTestingSection {
  notification,
  islandStatus,
  debugScheduling,
  debugLastTest,
  debugEnvironment,
  debugRecentLogs,
  rawJson,
  localLogs,
}

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
    final scheduledStage = _debugStatus?['scheduling']?['nextTriggerStage'];
    final stage = switch (scheduledStage) {
      'pre' || 'active' || 'post' => scheduledStage as String,
      'beforeClass' => 'pre',
      'duringClass' || 'duringClassStatusBar' || 'beforeEnd' => 'active',
      'afterClass' => 'post',
      _ => 'pre',
    };
    if (!mounted) return;
    appDebugLog('MiuiLive', '测试阶段：$stage');
    final provider = context.read<TimetableProvider>();
    await provider.initialize();
    final selection = provider.getTestLiveActivityCourseSelection();
    final course = selection?.currentCourse;
    appDebugLog('MiuiLive', '测试课程：${course?.name}');
    final startAtMillis = selection?.currentStartAt?.millisecondsSinceEpoch;
    final endAtMillis = selection?.currentEndAt?.millisecondsSinceEpoch;
    final progressBreakOffsetsMillis = course == null
        ? const <int>[]
        : provider.buildLiveProgressBreakOffsetsMillis(
            course,
            startAtMillis: startAtMillis,
            endAtMillis: endAtMillis,
          );
    await _hyperFocusService.recordDiagnosticEvent(
      'send_test_focus_requested',
      '收到超级岛测试通知发送请求',
      extras: {
        'stage': stage,
        'courseName': course?.name ?? '',
        'atMillis': DateTime.now().millisecondsSinceEpoch.toString(),
      },
    );
    final settings = provider.settings;
    // 与正式路径（live_activity_controller）一致：课前用 beforeClass 展示设置，其余阶段用 duringEnd
    final displaySettings = stage == 'pre'
        ? settings.beforeClassDisplaySettings
        : settings.duringEndDisplaySettings;
    final error = await _hyperFocusService.sendTestFocusNotification(
      courseName: course?.name,
      shortName: course?.shortName,
      startTime: course?.startTime,
      endTime: course?.endTime,
      startAtMillis: startAtMillis,
      endAtMillis: endAtMillis,
      location: (course?.location.isNotEmpty ?? false) ? course!.location : null,
      teacher: (course?.teacher.isNotEmpty ?? false) ? course!.teacher : null,
      stage: stage,
      showCountdown: displaySettings.showCountdown,
      progressBreakOffsetsMillis: progressBreakOffsetsMillis,
      hfIslandTimeoutPre: settings.hfIslandTimeoutPre,
      hfIslandTimeoutActive: settings.hfIslandTimeoutActive,
      hfIslandTimeoutPost: settings.hfIslandTimeoutPost,
      hfIconAEnabled: settings.hfIconAEnabled,
      hfOutEffectStatusEnabled: settings.hfOutEffectStatusEnabled,
      hfOutEffectStatusColor: settings.hfOutEffectStatusColor,
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
    await SharePlus.instance.share(
      ShareParams(
        text: shareText,
        subject: shareSubject,
        files: [XFile(exportPath)],
      ),
    );
    if (!mounted) return;
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
    _debugL10nContext = context;
    final summary = _debugSectionMap(_debugStatus?['summary']);
    final scheduling = _debugSectionMap(_debugStatus?['scheduling']);
    final test = _debugSectionMap(_debugStatus?['test']);
    final recentDiagnostics = _debugSectionMap(
      _debugStatus?['recentDiagnostics'],
    );
    final hasPermission = summary['hasNotificationPermission'] == true;
    final channelBlocked = summary['testChannelBlocked'] == true;
    final schedulerReady = summary['schedulerReady'] == true;
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
          test: test,
          recentDiagnostics: recentDiagnostics,
          hasPermission: hasPermission,
          channelBlocked: channelBlocked,
          schedulerReady: schedulerReady,
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
      _HyperFocusTestingSection.debugLastTest,
      _HyperFocusTestingSection.debugEnvironment,
      _HyperFocusTestingSection.debugRecentLogs,
      _HyperFocusTestingSection.rawJson,
    ],
    _HyperFocusTestingSection.localLogs,
  ];

  Widget _buildSection(
    BuildContext context,
    _HyperFocusTestingSection section, {
    required AppLocalizations l10n,
    required Map<String, dynamic> summary,
    required Map<String, dynamic> scheduling,
    required Map<String, dynamic> test,
    required Map<String, dynamic> recentDiagnostics,
    required bool hasPermission,
    required bool channelBlocked,
    required bool schedulerReady,
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
      _HyperFocusTestingSection.debugLastTest => _buildDebugSection(
        context: context,
        title: l10n.hfTestingDebugLastTest,
        entries: {
          if (!hasLastTest) l10n.hfTestingLastTestNever: '',
          if (hasLastTest) l10n.hfTestingLastTestStage: lastTestStage,
          if (hasLastTest)
            l10n.hfTestingLastTestResult: lastTestSucceeded
                ? l10n.hfTestingLastTestSucceeded
                : l10n.hfTestingLastTestFailed,
          if (hasLastTest && lastTestMessage.isNotEmpty)
            l10n.hfTestingLastTestMessage: lastTestMessage,
          if (hasLastTest)
            l10n.hfTestingLastTestTime:
                _formatMillis(test['lastAtMillis'] as int?, l10n),
        },
      ),
      _HyperFocusTestingSection.debugEnvironment => _buildDebugSection(
        context: context,
        title: l10n.liveTestingSectionEnvironment,
        entries: {
          for (final entry in _debugSectionMap(_debugStatus?['environment'])
              .entries)
            entry.key: _debugValueText(entry.value),
        },
      ),
      _HyperFocusTestingSection.debugRecentLogs => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _DebugSectionCard(
            title: l10n.liveTestingSectionRecentLogs,
            data: recentDiagnostics,
          ),
          const HyperosSectionGap(),
        ],
      ),
      _HyperFocusTestingSection.rawJson => _buildDebugSection(
        context: context,
        title: l10n.liveTestingRawDataTitle,
        entries: const {},
        trailingJson: _debugStatus == null
            ? ''
            : const JsonEncoder.withIndent('  ').convert(_debugStatus),
      ),
      _HyperFocusTestingSection.localLogs => _buildLocalLogsSection(context),
    };
  }

  String _ellipsize(String? value, {int max = 24}) {
    final v = value?.trim() ?? '';
    if (v.length <= max) return v;
    return '${v.substring(0, max)}…';
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
              title: l10n.liveTestingClearLogsAction,
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
      if (!context.mounted) return;
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
}
