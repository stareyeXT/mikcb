import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:university_timetable/l10n/app_localizations.dart';
import 'package:university_timetable/l10n/enum_localizations.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';

import '../models/timetable_settings.dart';
import '../providers/timetable_provider.dart';
import '../services/miui_live_activities_service.dart';
import '../utils/hex_color.dart';
import '../utils/app_toast.dart';
import '../ui/hyperos/hyperos.dart';

const String _expandedIconDir = 'miui_expanded_icons';
const String _labelLogoDir = 'miui_label_logos';
const List<String> _labelColors = [
  '#FFFFFF',
  '#E2E8F0',
  '#BFDBFE',
  '#A7F3D0',
  '#FDE68A',
  '#F9A8D4',
];

String _formatLiveTimeCorrection(AppLocalizations l10n, int seconds) {
  if (seconds == 0) {
    return l10n.liveTimeCorrectionNone;
  }
  if (seconds > 0) {
    return l10n.liveTimeCorrectionDelay(seconds);
  }
  return l10n.liveTimeCorrectionAdvance(seconds.abs());
}

String _buildLiveClassReminderLeadSummary(
  AppLocalizations l10n,
  TimetableSettings settings,
) {
  if (settings.liveClassReminderStartMinutes == 0) {
    return l10n.liveClassReminderLeadSummaryImmediate(
      settings.liveEndSecondsCountdownThreshold,
    );
  }
  if (settings.liveEnableDuringClass &&
      settings.liveShowDuringClassNotification &&
      !settings.livePromoteDuringClass) {
    return l10n.liveClassReminderLeadSummaryKeepNormal(
      settings.liveClassReminderStartMinutes,
      settings.liveEndSecondsCountdownThreshold,
    );
  }
  if (settings.liveEnableDuringClass &&
      settings.liveShowDuringClassNotification) {
    return l10n.liveClassReminderLeadSummaryIsland(
      settings.liveClassReminderStartMinutes,
      settings.liveEndSecondsCountdownThreshold,
    );
  }
  return l10n.liveClassReminderLeadSummaryFocused(
    settings.liveClassReminderStartMinutes,
    settings.liveEndSecondsCountdownThreshold,
  );
}

class LiveReminderTimingScreen extends StatefulWidget {
  const LiveReminderTimingScreen({super.key});

  @override
  State<LiveReminderTimingScreen> createState() =>
      _LiveReminderTimingScreenState();
}

class _LiveReminderTimingScreenState extends State<LiveReminderTimingScreen> {
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
  late final TimetableProvider _provider;
  Timer? _autoSaveTimer;
  Future<void> _saveQueue = Future<void>.value();

  @override
  void initState() {
    super.initState();
    _provider = context.read<TimetableProvider>();
    _draft = _provider.settings;
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
    // HyperOS list style: section label + one list group of equal-height rows.
    // Avoid ControlCard wrappers and long footnotes so the page scans like
    // system settings (title / one-line subtitle / trailing control).
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: Text(l10n.liveReminderTimingTitle),
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
          HyperosSectionLabel(text: l10n.liveDisplayModeTitle),
          HyperosListGroup(
            children: [
              HyperosSwitchTile(
                title: l10n.duringClassStatusNotificationTitle,
                subtitle: _draft.liveClassReminderStartMinutes == 0
                    ? l10n.duringClassStatusNotificationImmediate
                    : _draft.livePromoteDuringClass
                    ? l10n.duringClassStatusNotificationBeforeEnd
                    : l10n.duringClassStatusNotificationPersistent,
                value: _draft.liveShowDuringClassNotification,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(liveShowDuringClassNotification: value),
                ),
              ),
              HyperosSwitchTile(
                title: l10n.enableIslandDisplayTitle,
                subtitle: l10n.enableIslandDisplaySubtitle,
                value: _draft.livePromoteDuringClass,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(livePromoteDuringClass: value),
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
              HyperosSelectTile<LiveDuringClassTimeDisplayMode>(
                label: l10n.duringEndTimeDisplayLabel,
                subtitle: l10n.duringEndTimeDisplayHelp,
                items: {
                  for (final value in LiveDuringClassTimeDisplayMode.values)
                    liveDuringClassTimeDisplayModeLabel(l10n, value): value,
                },
                value: _draft.liveDuringEndTimeDisplayMode,
                onChanged: (value) => _updateDraft(
                  _draft.copyWith(liveDuringEndTimeDisplayMode: value),
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
    final message = await _provider.updateTimetableSettings(next);
    if (!mounted) return;
    if (message != null) {
      showAppToast(context, message: message);
      setState(() => _draft = _provider.settings);
    }
  }
}

class LiveDisplaySettingsScreen extends StatefulWidget {
  final String title;
  final bool forDuringEnd;

  const LiveDisplaySettingsScreen({
    super.key,
    required this.title,
    required this.forDuringEnd,
  });

  @override
  State<LiveDisplaySettingsScreen> createState() =>
      _LiveDisplaySettingsScreenState();
}

class _LiveDisplaySettingsScreenState extends State<LiveDisplaySettingsScreen> {
  late TimetableSettings _draft;
  late final TimetableProvider _provider;
  Timer? _autoSaveTimer;
  Future<void> _saveQueue = Future<void>.value();

  @override
  void initState() {
    super.initState();
    _provider = context.read<TimetableProvider>();
    _draft = _provider.settings;
    unawaited(
      _provider.refreshLiveActivityNow(
        forceSnapshotSync: true,
      ),
    );
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

  LiveDisplaySettings get _display => widget.forDuringEnd
      ? _draft.duringEndDisplaySettings
      : _draft.beforeClassDisplaySettings;

  bool get _followBeforeClass =>
      widget.forDuringEnd && _draft.liveDuringEndFollowBeforeClass;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final display = _display;
    final sectionCards = <Widget>[
      HyperosSectionLabel(text: l10n.liveDisplayContentTitle),
      HyperosListGroup(
        children: [
          HyperosSwitchTile(
            title: l10n.showCourseNameTitle,
            value: display.showCourseName,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(showCourseName: value)),
          ),
          HyperosSwitchTile(
            title: l10n.preferShortNameTitle,
            subtitle: l10n.preferShortNameSubtitle,
            value: display.useShortName,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(useShortName: value)),
          ),
          HyperosSwitchTile(
            title: l10n.showLocationTitle,
            value: display.showLocation,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(showLocation: value)),
          ),
          HyperosSwitchTile(
            title: l10n.showCountdownTitle,
            value: display.showCountdown,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(showCountdown: value)),
          ),
          HyperosSwitchTile(
            title: l10n.showStageTextTitle,
            subtitle: l10n.showStageTextSubtitle,
            value: display.showStageText,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(showStageText: value)),
          ),
          HyperosSwitchTile(
            title: l10n.hidePrefixTextTitle,
            subtitle: l10n.hidePrefixTextSubtitle,
            value: display.hidePrefixText,
            onChanged: (value) =>
                _updateDisplay(display.copyWith(hidePrefixText: value)),
          ),
        ],
      ),
      if (display.showCountdown) ...[
        const HyperosSectionGap(),
        HyperosSectionLabel(text: l10n.countdownFormatLabel),
        HyperosListGroup(
          children: [
            HyperosSelectTile<LiveCountdownTextStyle>(
              label: l10n.countdownFormatLabel,
              items: {
                for (final value in LiveCountdownTextStyle.values)
                  liveCountdownTextStyleLabel(l10n, value): value,
              },
              value: display.countdownTextStyle,
              onChanged: (value) =>
                  _updateDisplay(display.copyWith(countdownTextStyle: value)),
            ),
          ],
        ),
      ],
      if (!widget.forDuringEnd) ...[
        const HyperosSectionGap(),
        HyperosSectionLabel(text: l10n.beforeClassQuickActionTitle),
        HyperosListGroup(
          children: [
            HyperosSelectTile<LiveBeforeClassQuickAction>(
              label: l10n.beforeClassQuickActionTitle,
              items: {
                for (final value in LiveBeforeClassQuickAction.values)
                  liveBeforeClassQuickActionLabel(l10n, value): value,
              },
              value: _draft.liveBeforeClassQuickAction,
              onChanged: (value) => _updateDraft(
                _draft.copyWith(liveBeforeClassQuickAction: value),
              ),
            ),
          ],
        ),
      ],
      const HyperosSectionGap(),
      HyperosSectionLabel(text: l10n.liveIslandVisualTitle),
      HyperosListGroup(
        children: [
          HyperosSwitchTile(
            title: l10n.liveMiuiLabelImageTitle,
            subtitle: l10n.liveMiuiLabelImageSubtitle,
            value: display.enableMiuiIslandLabelImage,
            onChanged: (value) => _updateDisplay(
              display.copyWith(enableMiuiIslandLabelImage: value),
            ),
          ),
        ],
      ),
      if (display.enableMiuiIslandLabelImage) ...[
        const HyperosSectionGap(),
        HyperosControlCard(
          edgeToEdge: true,
          child: HyperosControlCardRows(
            children: [
              HyperosSelectTile<MiuiIslandLabelContent>(
                label: l10n.liveMiuiLabelContentLabel,
                items: {
                  for (final value in MiuiIslandLabelContent.values)
                    miuiIslandLabelContentLabel(l10n, value): value,
                },
                value: display.miuiIslandLabelContent,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelContent: value),
                ),
              ),
              HyperosSelectTile<MiuiIslandLabelStyle>(
                label: l10n.liveMiuiLabelStyleLabel,
                items: {
                  for (final value in MiuiIslandLabelStyle.values)
                    miuiIslandLabelStyleLabel(l10n, value): value,
                },
                value: display.miuiIslandLabelStyle,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelStyle: value),
                ),
              ),
              if (display.miuiIslandLabelStyle ==
                  MiuiIslandLabelStyle.iconAndText)
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    HyperosControlCardScope.defaultHorizontalPadding,
                    4,
                    HyperosControlCardScope.defaultHorizontalPadding,
                    8,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        l10n.liveMiuiLabelLogoTitle,
                        style: HyperosTypography.listTitle(context),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.liveMiuiLabelLogoSubtitle,
                        style: HyperosTypography.listDetail(context),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: HyperosButton(
                              label: display.miuiIslandLabelLogoPath == null
                                  ? l10n.selectImageAction
                                  : l10n.replaceImageAction,
                              variant: HyperosButtonVariant.secondary,
                              expand: true,
                              onPressed: () => _pickLabelLogoImage(display),
                            ),
                          ),
                          if (display.miuiIslandLabelLogoPath != null) ...[
                            const SizedBox(width: 12),
                            Expanded(
                              child: HyperosButton(
                                label: l10n.deleteAction,
                                variant: HyperosButtonVariant.destructive,
                                expand: true,
                                onPressed: () async {
                                  await _deleteManagedImageArtifacts(
                                    directoryName: _labelLogoDir,
                                    filePrefix: widget.forDuringEnd
                                        ? 'during_end_label_logo'
                                        : 'before_class_label_logo',
                                  );
                                  _updateDisplay(
                                    display.copyWith(
                                      clearMiuiIslandLabelLogoPath: true,
                                    ),
                                    clearLabelLogoPath: true,
                                  );
                                },
                              ),
                            ),
                          ],
                        ],
                      ),
                      if (display.miuiIslandLabelLogoPath != null) ...[
                        const SizedBox(height: 12),
                        _ImagePreview(
                          path: display.miuiIslandLabelLogoPath!,
                          imageCornerRadius:
                              display.miuiIslandLabelLogoCornerRadius,
                        ),
                      ],
                    ],
                  ),
                ),
              if (display.miuiIslandLabelStyle ==
                      MiuiIslandLabelStyle.iconAndText &&
                  display.miuiIslandLabelLogoPath != null)
                HyperosSliderTile(
                  title: l10n.liveMiuiLabelLogoCornerRadiusLabel(
                    display.miuiIslandLabelLogoCornerRadius.toStringAsFixed(0),
                  ),
                  value: display.miuiIslandLabelLogoCornerRadius.clamp(
                    0.0,
                    12.0,
                  ),
                  min: 0,
                  max: 12,
                  divisions: 12,
                  onChanged: (value) => _updateDisplay(
                    display.copyWith(miuiIslandLabelLogoCornerRadius: value),
                    debounce: true,
                  ),
                ),
              HyperosSliderTile(
                title: l10n.liveMiuiLabelFontSizeLabel(
                  display.miuiIslandLabelFontSize.toStringAsFixed(0),
                ),
                value: display.miuiIslandLabelFontSize,
                min: 1,
                max: 32,
                divisions: 31,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelFontSize: value),
                  debounce: true,
                ),
              ),
              HyperosSliderTile(
                title: l10n.liveMiuiLabelOffsetXLabel(
                  display.miuiIslandLabelOffsetX.toStringAsFixed(1),
                ),
                value: display.miuiIslandLabelOffsetX.clamp(-2.0, 2.0),
                min: -2,
                max: 2,
                divisions: 40,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelOffsetX: value),
                  debounce: true,
                ),
              ),
              HyperosSliderTile(
                title: l10n.liveMiuiLabelOffsetYLabel(
                  display.miuiIslandLabelOffsetY.toStringAsFixed(1),
                ),
                value: display.miuiIslandLabelOffsetY.clamp(-2.0, 2.0),
                min: -2,
                max: 2,
                divisions: 40,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelOffsetY: value),
                  debounce: true,
                ),
              ),
              HyperosSelectTile<MiuiIslandLabelFontWeight>(
                label: l10n.liveMiuiLabelFontWeightLabel,
                items: {
                  for (final value in MiuiIslandLabelFontWeight.values)
                    miuiIslandLabelFontWeightLabel(l10n, value): value,
                },
                value: display.miuiIslandLabelFontWeight,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelFontWeight: value),
                ),
              ),
              HyperosSelectTile<MiuiIslandLabelRenderQuality>(
                label: l10n.liveMiuiLabelRenderQualityLabel,
                items: {
                  for (final value in MiuiIslandLabelRenderQuality.values)
                    miuiIslandLabelRenderQualityLabel(l10n, value): value,
                },
                value: display.miuiIslandLabelRenderQuality,
                onChanged: (value) => _updateDisplay(
                  display.copyWith(miuiIslandLabelRenderQuality: value),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HyperosControlCardScope.defaultHorizontalPadding,
                  4,
                  HyperosControlCardScope.defaultHorizontalPadding,
                  13,
                ),
                child: Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: _labelColors
                      .map(
                        (color) => _ColorDot(
                          colorHex: color,
                          selected: display.miuiIslandLabelFontColor == color,
                          onTap: () => _updateDisplay(
                            display.copyWith(miuiIslandLabelFontColor: color),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ),
            ],
          ),
        ),
      ],
      const HyperosSectionGap(),
      HyperosControlCard(
        edgeToEdge: true,
        child: HyperosControlCardRows(
          children: [
            HyperosSelectTile<MiuiIslandExpandedIconMode>(
              label: l10n.liveMiuiExpandedIconLabel,
              items: {
                for (final value in MiuiIslandExpandedIconMode.values)
                  miuiIslandExpandedIconModeLabel(l10n, value): value,
              },
              value: display.miuiIslandExpandedIconMode,
              onChanged: (value) {
                () async {
                  if (value != MiuiIslandExpandedIconMode.customImage) {
                    await _deleteManagedImageArtifacts(
                      directoryName: _expandedIconDir,
                      filePrefix: widget.forDuringEnd
                          ? 'during_end_expanded_icon'
                          : 'before_class_expanded_icon',
                    );
                  }
                  _updateDisplay(
                    display.copyWith(
                      miuiIslandExpandedIconMode: value,
                      clearMiuiIslandExpandedIconPath:
                          value != MiuiIslandExpandedIconMode.customImage,
                    ),
                    clearExpandedIconPath:
                        value != MiuiIslandExpandedIconMode.customImage,
                  );
                }();
              },
            ),
            if (display.miuiIslandExpandedIconMode ==
                MiuiIslandExpandedIconMode.customImage)
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HyperosControlCardScope.defaultHorizontalPadding,
                  4,
                  HyperosControlCardScope.defaultHorizontalPadding,
                  // Match last-row bottom of preference tiles (not card bleed).
                  13,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: HyperosButton(
                            label: display.miuiIslandExpandedIconPath == null
                                ? l10n.selectImageAction
                                : l10n.replaceImageAction,
                            variant: HyperosButtonVariant.secondary,
                            expand: true,
                            onPressed: () => _pickExpandedIconImage(display),
                          ),
                        ),
                        if (display.miuiIslandExpandedIconPath != null) ...[
                          const SizedBox(width: 12),
                          Expanded(
                            child: HyperosButton(
                              label: l10n.deleteAction,
                              variant: HyperosButtonVariant.destructive,
                              expand: true,
                              onPressed: () async {
                                await _deleteManagedImageArtifacts(
                                  directoryName: _expandedIconDir,
                                  filePrefix: widget.forDuringEnd
                                      ? 'during_end_expanded_icon'
                                      : 'before_class_expanded_icon',
                                );
                                _updateDisplay(
                                  display.copyWith(
                                    clearMiuiIslandExpandedIconPath: true,
                                  ),
                                  clearExpandedIconPath: true,
                                );
                              },
                            ),
                          ),
                        ],
                      ],
                    ),
                    if (display.miuiIslandExpandedIconPath != null) ...[
                      const SizedBox(height: 12),
                      _ImagePreview(path: display.miuiIslandExpandedIconPath!),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ),
    ];
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: Text(widget.title),
      child: HyperosListView(
        children: [
          if (widget.forDuringEnd) ...[
            HyperosSectionLabel(text: l10n.liveDisplayConfigModeTitle),
            HyperosListGroup(
              children: [
                HyperosSwitchTile(
                  title: l10n.followBeforeClassDisplayTitle,
                  value: _draft.liveDuringEndFollowBeforeClass,
                  onChanged: (value) => _updateDraft(
                    _draft.copyWith(liveDuringEndFollowBeforeClass: value),
                  ),
                ),
              ],
            ),
            const HyperosSectionGap(),
          ],
          if (_followBeforeClass)
            IgnorePointer(
              ignoring: true,
              child: AnimatedOpacity(
                duration: const Duration(milliseconds: 180),
                opacity: 0.5,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: sectionCards,
                ),
              ),
            )
          else
            ...sectionCards,
        ],
      ),
    );
  }

  void _updateDisplay(
    LiveDisplaySettings next, {
    bool debounce = false,
    bool clearExpandedIconPath = false,
    bool clearLabelLogoPath = false,
  }) {
    final nextSettings = widget.forDuringEnd
        ? _draft.copyWithDuringEndDisplaySettings(
            next,
            clearExpandedIconPath: clearExpandedIconPath,
            clearLabelLogoPath: clearLabelLogoPath,
          )
        : _draft.copyWithBeforeClassDisplaySettings(
            next,
            clearExpandedIconPath: clearExpandedIconPath,
            clearLabelLogoPath: clearLabelLogoPath,
          );
    _updateDraft(nextSettings, debounce: debounce);
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
    final message = await _provider.updateTimetableSettings(next);
    if (!mounted) return;
    if (message != null) {
      showAppToast(context, message: message);
      setState(() => _draft = _provider.settings);
    }
  }

  Future<void> _pickExpandedIconImage(LiveDisplaySettings display) async {
    final targetPath = await _pickAndStoreImage(
      directoryName: _expandedIconDir,
      filePrefix: widget.forDuringEnd
          ? 'during_end_expanded_icon'
          : 'before_class_expanded_icon',
    );
    if (!mounted || targetPath == null) return;
    _updateDisplay(
      display.copyWith(
        miuiIslandExpandedIconMode: MiuiIslandExpandedIconMode.customImage,
        miuiIslandExpandedIconPath: targetPath,
      ),
    );
  }

  Future<void> _pickLabelLogoImage(LiveDisplaySettings display) async {
    final targetPath = await _pickAndStoreImage(
      directoryName: _labelLogoDir,
      filePrefix: widget.forDuringEnd
          ? 'during_end_label_logo'
          : 'before_class_label_logo',
    );
    if (!mounted || targetPath == null) return;
    _updateDisplay(display.copyWith(miuiIslandLabelLogoPath: targetPath));
  }

  Future<String?> _pickAndStoreImage({
    required String directoryName,
    required String filePrefix,
  }) async {
    final result = await FilePicker.pickFiles(
      type: FileType.image,
      withData: true,
    );
    if (!mounted || result == null || result.files.isEmpty) return null;
    final file = result.files.single;
    final bytes =
        file.bytes ??
        (file.path == null ? null : await File(file.path!).readAsBytes());
    if (bytes == null || bytes.isEmpty) return null;
    final ext = (file.extension?.isNotEmpty ?? false)
        ? file.extension!.toLowerCase()
        : 'png';
    final dir = await getApplicationDocumentsDirectory();
    final targetDir = Directory(
      '${dir.path}${Platform.pathSeparator}$directoryName',
    );
    if (!await targetDir.exists()) {
      await targetDir.create(recursive: true);
    }
    final targetPath =
        '${targetDir.path}${Platform.pathSeparator}$filePrefix.$ext';
    await File(targetPath).writeAsBytes(bytes, flush: true);
    await _deleteManagedImageArtifacts(
      directoryName: directoryName,
      filePrefix: filePrefix,
      preservePath: targetPath,
    );
    return targetPath;
  }

  Future<void> _deleteManagedImageArtifacts({
    required String directoryName,
    required String filePrefix,
    String? preservePath,
  }) async {
    final dir = await getApplicationDocumentsDirectory();
    final targetDir = Directory(
      '${dir.path}${Platform.pathSeparator}$directoryName',
    );
    if (!await targetDir.exists()) {
      return;
    }
    final preservedAbsolutePath = preservePath == null
        ? null
        : File(preservePath).absolute.path;
    await for (final entity in targetDir.list()) {
      if (entity is! File) {
        continue;
      }
      final fileName = entity.uri.pathSegments.isEmpty
          ? ''
          : entity.uri.pathSegments.last;
      if (!fileName.startsWith('$filePrefix.')) {
        continue;
      }
      if (preservedAbsolutePath != null &&
          entity.absolute.path == preservedAbsolutePath) {
        continue;
      }
      try {
        if (await entity.exists()) {
          await entity.delete();
        }
      } catch (_) {}
    }
  }
}

class LiveKeepAliveSettingsScreen extends StatefulWidget {
  const LiveKeepAliveSettingsScreen({super.key});

  @override
  State<LiveKeepAliveSettingsScreen> createState() =>
      _LiveKeepAliveSettingsScreenState();
}

class _LiveKeepAliveSettingsScreenState
    extends State<LiveKeepAliveSettingsScreen>
    with WidgetsBindingObserver {
  final MiuiLiveActivitiesService _liveService = MiuiLiveActivitiesService();
  late TimetableSettings _draft;
  bool _enabled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _draft = context.read<TimetableProvider>().settings;
    unawaited(_refresh(retryIfDisabled: true));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_refresh(retryIfDisabled: true));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: Text(l10n.liveKeepAliveTitle),
      child: HyperosListView(
        children: [
          HyperosSectionLabel(text: l10n.liveKeepAliveOptionsTitle),
          HyperosListGroup(
            children: [
              HyperosSwitchTile(
                title: l10n.hideFromRecentsTitle,
                subtitle: l10n.hideFromRecentsSubtitle,
                value: _draft.liveHideFromRecents,
                onChanged: (value) async {
                  final provider = context.read<TimetableProvider>();
                  final message = await provider.updateTimetableSettings(
                    _draft.copyWith(liveHideFromRecents: value),
                  );
                  if (!context.mounted) return;
                  if (message != null) {
                    showAppToast(context, message: message);
                  }
                  setState(() => _draft = provider.settings);
                },
              ),
              _LiveKeepAliveServiceTile(
                enabled: _enabled,
                onOpenSettings: _openSettings,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _openSettings() async {
    await _liveService.openAccessibilitySettings();
    await Future<void>.delayed(const Duration(milliseconds: 500));
    if (!mounted) return;
    await _refresh(retryIfDisabled: true);
  }

  Future<void> _refresh({bool retryIfDisabled = false}) async {
    var enabled = await _liveService.isKeepAliveAccessibilityEnabled();
    if (!enabled && retryIfDisabled) {
      for (var i = 0; i < 3 && !enabled; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 450));
        if (!mounted) return;
        enabled = await _liveService.isKeepAliveAccessibilityEnabled();
      }
    }
    if (!mounted) return;
    setState(() {
      _enabled = enabled;
    });
  }
}

class _LiveKeepAliveServiceTile extends StatelessWidget {
  const _LiveKeepAliveServiceTile({
    required this.enabled,
    required this.onOpenSettings,
  });

  final bool enabled;
  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cardColor = HyperosColors.card(context);
    final highlightColor = HyperosColors.rowHighlight(context);
    final iconAccent = enabled
        ? HyperosIconColors.green
        : HyperosIconColors.orange;

    final row = ConstrainedBox(
      constraints: const BoxConstraints(
        minHeight: HyperosTokens.listRowMinHeight,
      ),
      child: Padding(
        padding: HyperosTokens.rowPaddingUniform,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            HyperosIconBadge(
              icon: enabled
                  ? Icons.check_circle_rounded
                  : Icons.accessibility_new_rounded,
              accent: iconAccent,
            ),
            const SizedBox(width: HyperosTokens.rowContentGap),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    l10n.keepAliveServiceTitle,
                    style: HyperosTypography.listTitle(context),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    enabled
                        ? l10n.keepAliveServiceEnabledSubtitle
                        : l10n.keepAliveServiceDisabledSubtitle,
                    style: HyperosTypography.listDetail(context),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            HyperosButton(
              label: l10n.goEnableAction,
              variant: HyperosButtonVariant.secondary,
              onPressed: onOpenSettings,
            ),
          ],
        ),
      ),
    );

    return HyperosPressableRow(
      onTap: onOpenSettings,
      backgroundColor: cardColor,
      highlightColor: highlightColor,
      child: row,
    );
  }
}

class _ColorDot extends StatelessWidget {
  final String colorHex;
  final bool selected;
  final VoidCallback onTap;

  const _ColorDot({
    required this.colorHex,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: _parseColor(colorHex),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: selected
                ? Theme.of(context).colorScheme.primary
                : Theme.of(context).dividerColor,
            width: selected ? 2.5 : 1,
          ),
        ),
      ),
    );
  }
}

class _ImagePreview extends StatelessWidget {
  final String path;
  final double imageCornerRadius;

  const _ImagePreview({required this.path, this.imageCornerRadius = 12});

  @override
  Widget build(BuildContext context) {
    final file = File(path);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Theme.of(context).dividerColor),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(imageCornerRadius),
            child: file.existsSync()
                ? Image.file(
                    file,
                    width: 56,
                    height: 56,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      return Container(
                        width: 56,
                        height: 56,
                        color: Theme.of(
                          context,
                        ).colorScheme.surfaceContainerHighest,
                        alignment: Alignment.center,
                        child: const Icon(Icons.broken_image_outlined),
                      );
                    },
                  )
                : Container(
                    width: 56,
                    height: 56,
                    color: Theme.of(
                      context,
                    ).colorScheme.surfaceContainerHighest,
                    alignment: Alignment.center,
                    child: const Icon(Icons.broken_image_outlined),
                  ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              path,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: context.theme.typography.body.xs2,
            ),
          ),
        ],
      ),
    );
  }
}

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

class HyperFocusIslandTimeoutScreen extends StatefulWidget {
  const HyperFocusIslandTimeoutScreen({super.key});

  @override
  State<HyperFocusIslandTimeoutScreen> createState() =>
      _HyperFocusIslandTimeoutScreenState();
}

class _HyperFocusIslandTimeoutScreenState
    extends State<HyperFocusIslandTimeoutScreen> {
  late TimetableSettings _draft;
  late final TextEditingController _preMinutesCtrl;
  late final TextEditingController _activeMinutesCtrl;
  late final TextEditingController _postMinutesCtrl;
  Timer? _autoSaveTimer;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
    _preMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPre / 60).round().clamp(1, 60).toString(),
    );
    _activeMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutActive / 60).round().clamp(1, 60).toString(),
    );
    _postMinutesCtrl = TextEditingController(
      text: (_draft.hfIslandTimeoutPost / 60).round().clamp(1, 60).toString(),
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

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('岛消失时间'),
      child: HyperosListView(
        children: [
          HyperosSectionLabel(text: '状态栏岛消失时间（分钟）'),
          HyperosListGroup(
            children: [
              _buildTimeoutTile('课前', _preMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPre: v))),
              _buildTimeoutTile('课中', _activeMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutActive: v))),
              _buildTimeoutTile('课后', _postMinutesCtrl,
                  (v) => _updateDraft(_draft.copyWith(hfIslandTimeoutPost: v))),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTimeoutTile(
    String label,
    TextEditingController controller,
    ValueChanged<int> onChanged,
  ) {
    return HyperosTextFieldTile(
      cardTitle: label,
      cardSubtitle: '分钟（1~60）',
      field: HyperosTextField(
        controller: controller,
        keyboardType: TextInputType.number,
        onChanged: (text) {
          final minutes = int.tryParse(text) ?? 0;
          final clamped = minutes.clamp(1, 60);
          onChanged(clamped * 60);
          // 回写规范化值，避免界面显示与实际保存不一致（如输入 99 实际保存 60）
          final normalized = clamped.toString();
          if (controller.text != normalized) {
            controller.text = normalized;
            controller.selection = TextSelection.collapsed(offset: normalized.length);
          }
        },
      ),
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

class _VariableMultiSelectSheet extends StatefulWidget {
  const _VariableMultiSelectSheet({
    required this.variables,
    required this.phrases,
    required this.initial,
  });

  final List<String> variables;
  final List<String> phrases;
  final List<String> initial;

  @override
  State<_VariableMultiSelectSheet> createState() =>
      _VariableMultiSelectSheetState();
}

class _VariableMultiSelectSheetState extends State<_VariableMultiSelectSheet> {
  late final Set<String> _selected = widget.initial.toSet();

  @override
  Widget build(BuildContext context) {
    return HyperosSheet(
      title: '选择显示信息',
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.72,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '课程变量（自动填入真实数据）',
                style: HyperosTypography.listDetail(context),
              ),
              for (final v in widget.variables)
                HyperosCheckboxTile(
                  title: v,
                  value: _selected.contains(v),
                  onChanged: (on) => setState(() {
                    if (on) {
                      _selected.add(v);
                    } else {
                      _selected.remove(v);
                    }
                  }),
                ),
              const SizedBox(height: 8),
              Text(
                '常用短语（原样显示）',
                style: HyperosTypography.listDetail(context),
              ),
              for (final p in widget.phrases)
                HyperosCheckboxTile(
                  title: p,
                  value: _selected.contains(p),
                  onChanged: (on) => setState(() {
                    if (on) {
                      _selected.add(p);
                    } else {
                      _selected.remove(p);
                    }
                  }),
                ),
              const SizedBox(height: 12),
              HyperosButton(
                label: '确定',
                expand: true,
                onPressed: () => Navigator.pop(context, _selected.toList()),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class HyperFocusStatusIslandScreen extends StatefulWidget {
  const HyperFocusStatusIslandScreen({super.key});

  @override
  State<HyperFocusStatusIslandScreen> createState() => _HyperFocusStatusIslandScreenState();
}

class _HyperFocusStatusIslandScreenState extends State<HyperFocusStatusIslandScreen> {
  String _selectedStage = 'pre';

  static const _defaultTemplates = {
    'ticker_pre': '课名',
    'ticker_active': '课名',
    'ticker_post': '课名',
    'islandA_pre': '教室',
    'islandA_active': '短课名',
    'islandA_post': '短课名',
    'islandB_pre': '',
    'islandB_active': '倒计时',
    'islandB_post': '已下课',
  };

  static const _availableVariables = [
    '课名', '短课名', '教室', '教师', '开始', '结束', '倒计时', '正计时',
  ];

  static const _availablePhrases = [
    '即将上课', '正在上课', '上课中', '距下课', '距离下课', '已经下课', '已下课',
  ];

  late Map<String, TextEditingController> _controllers;

  String get _s => _selectedStage;

  static const _tabOrder = ['pre', 'active', 'post'];

  @override
  void initState() {
    super.initState();
    _controllers = {};
    for (final key in ['ticker', 'islandA', 'islandB']) {
      for (final stage in _tabOrder) {
        final k = '${key}_$stage';
        _controllers[k] = TextEditingController(text: _defaultTemplates[k]!);
      }
    }
    _loadTemplates();
  }

  Future<void> _loadTemplates() async {
    final provider = context.read<TimetableProvider>();
    final settingsJson = provider.settings.hfTemplatesJson;
    if (settingsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(settingsJson) as Map<String, dynamic>;
        if (!mounted) return;
        for (final key in _controllers.keys) {
          final v = decoded[key];
          // 空字符串也是有效保存值（用户清空的模板），必须回填，
          // 否则重新保存时默认值会覆盖用户的清空操作
          if (v is String) {
            _controllers[key]?.text = v;
          }
        }
        return;
      } catch (_) {
        // 解析失败则回退 Kotlin prefs 迁移
      }
    }
    final service = MiuiLiveActivitiesService();
    final saved = await service.loadHyperFocusTemplates();
    if (!mounted) return;
    var migrated = false;
    for (final key in _controllers.keys) {
      final v = saved[key];
      // 空字符串同样是有效保存值（用户清空的模板）
      if (v != null) {
        if (_controllers[key]?.text != v) {
          _controllers[key]?.text = v;
          migrated = true;
        }
      }
    }
    if (migrated) {
      await _persistTemplatesToSettings(provider, Map.from(saved));
    }
  }

  Future<void> _persistTemplatesToSettings(
    TimetableProvider provider,
    Map<String, String> map,
  ) async {
    await provider.updateTimetableSettings(
      provider.settings.copyWith(hfTemplatesJson: jsonEncode(map)),
    );
  }

  Future<void> _saveTemplates({bool silent = false}) async {
    final service = MiuiLiveActivitiesService();
    final provider = context.read<TimetableProvider>();

    final merged = <String, String>{};
    final settingsJson = provider.settings.hfTemplatesJson;
    if (settingsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(settingsJson) as Map<String, dynamic>;
        for (final entry in decoded.entries) {
          if (entry.value is String) {
            merged[entry.key] = entry.value as String;
          }
        }
      } catch (_) {
        // 解析失败则回退 Kotlin prefs 迁移
      }
    }
    if (merged.isEmpty) {
      merged.addAll(await service.loadHyperFocusTemplates());
    }

    for (final key in _defaultTemplates.keys) {
      merged[key] = _controllers[key]?.text ?? _defaultTemplates[key]!;
    }

    final ok = await service.saveHyperFocusTemplates(merged);
    await _persistTemplatesToSettings(provider, merged);
    if (!mounted) return;
    if (!silent) {
      showHyperosSnackBar(context, message: ok ? '模板已保存' : '保存失败');
    }
  }

  Future<void> _resetStage() async {
    for (final key in _controllers.keys) {
      if (key.endsWith('_$_s')) {
        _controllers[key]?.text = _defaultTemplates[key]!;
      }
    }
    await _saveTemplates();
  }

  Widget _variableSelectField(String key, String label) {
    final current = _controllers[key]?.text ?? '';
    final selected = current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
    return HyperosListTile(
      icon: Icons.tune,
      title: label,
      details: _selectedSummary(selected),
      onTap: () => _openVariableMultiSelect(key, selected),
    );
  }

  String _selectedSummary(List<String> selected) {
    if (selected.isEmpty) return '未选择';
    if (selected.length <= 4) return selected.join('、');
    return '${selected.sublist(0, 4).join('、')}…';
  }

  Future<void> _openVariableMultiSelect(String key, List<String> selected) async {
    final result = await showHyperosSheet<List<String>>(
      context: context,
      builder: (sheetContext) => _VariableMultiSelectSheet(
        variables: _availableVariables,
        phrases: _availablePhrases,
        initial: selected,
      ),
    );
    if (result == null) return;
    if (!mounted) return;
    setState(() {
      _controllers[key]?.text = result.join(',');
    });
  }

  @override
  void dispose() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('状态栏岛自定义'),
      child: Builder(
        builder: (childContext) {
          final headerInset = HyperosBlurredHeaderScope.insetOf(childContext);
          return Column(
            children: [
              Padding(
                padding: EdgeInsets.only(top: headerInset),
                child: HyperosTabRow(
                  tabs: ['课前', '课中', '课后'],
                  selectedIndex: _tabOrder.indexOf(_selectedStage),
                  onChanged: (i) =>
                      setState(() => _selectedStage = _tabOrder[i]),
                ),
              ),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        '点击选择要在各区域显示的信息',
                        style: HyperosTypography.listDetail(context),
                      ),
                      const SizedBox(height: 8),
                      HyperosSectionLabel(text: '状态栏岛'),
                      _variableSelectField('ticker_$_s', '状态栏/息屏文本'),
                      _variableSelectField('islandA_$_s', '岛左侧文字'),
                      _variableSelectField('islandB_$_s', '岛右侧后缀'),
                      const HyperosSectionGap(),
                      Row(
                        children: [
                          Expanded(
                            child: HyperosButton(
                              label: '保存',
                              expand: true,
                              onPressed: _saveTemplates,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: HyperosButton(
                              label: '恢复默认',
                              expand: true,
                              onPressed: _resetStage,
                            ),
                          ),
                        ],
                      ),
                  const HyperosSectionGap(),
                ],
              ),
            ),
          ),
            ],
          );
      },
    ),
  );
}
}

class HyperFocusExpandedIslandScreen extends StatefulWidget {
  const HyperFocusExpandedIslandScreen({super.key});

  @override
  State<HyperFocusExpandedIslandScreen> createState() => _HyperFocusExpandedIslandScreenState();
}

class _HyperFocusExpandedIslandScreenState extends State<HyperFocusExpandedIslandScreen> {
  String _selectedStage = 'pre';

  static const _defaultTemplates = {
    'baseTitle_pre': '课名',
    'baseTitle_active': '课名',
    'baseTitle_post': '课名',
    'baseContent_pre': '开始,结束',
    'baseContent_active': '开始,结束',
    'baseContent_post': '开始,结束',
    'baseSubcontent_pre': '教室',
    'baseSubcontent_active': '教室',
    'baseSubcontent_post': '教室',
    'hintTitle_pre': '',
    'hintTitle_active': '上课中',
    'hintTitle_post': '已下课',
    'hintContent_pre': '即将上课',
    'hintContent_active': '距离下课',
    'hintContent_post': '已经下课',
    'hintSubcontent_pre': '',
    'hintSubcontent_active': '',
    'hintSubcontent_post': '',
    'hintSubtitle_pre': '',
    'hintSubtitle_active': '',
    'hintSubtitle_post': '',
  };

  static const _availableVariables = [
    '课名', '短课名', '教室', '教师', '开始', '结束', '倒计时', '正计时',
  ];

  static const _availablePhrases = [
    '即将上课', '正在上课', '上课中', '距下课', '距离下课', '已经下课', '已下课',
  ];

  late Map<String, TextEditingController> _controllers;

  String get _s => _selectedStage;

  static const _tabOrder = ['pre', 'active', 'post'];

  Future<void> _sendTestNotification() async {
    final provider = context.read<TimetableProvider>();
    final displaySettings = _s == 'pre'
        ? provider.settings.beforeClassDisplaySettings
        : provider.settings.duringEndDisplaySettings;
    final showCountdown = displaySettings.showCountdown;
    await _saveTemplates(silent: true);
    final service = MiuiLiveActivitiesService();
    final error = await service.sendTestFocusNotification(
      courseName: '高等数学',
      startTime: '08:00',
      endTime: '09:40',
      location: '教科A-101',
      teacher: '张老师',
      stage: _s,
      showCountdown: showCountdown,
    );
    if (!mounted) return;
    showHyperosSnackBar(
      context,
      message: error ?? '测试通知已发送，请下拉通知栏查看效果',
    );
  }

  @override
  void initState() {
    super.initState();
    _controllers = {};
    for (final key in ['baseTitle', 'baseContent', 'baseSubcontent', 'hintTitle', 'hintContent', 'hintSubcontent', 'hintSubtitle']) {
      for (final stage in _tabOrder) {
        final k = '${key}_$stage';
        _controllers[k] = TextEditingController(text: _defaultTemplates[k]!);
      }
    }
    _loadTemplates();
  }

  Future<void> _loadTemplates() async {
    final provider = context.read<TimetableProvider>();
    final settingsJson = provider.settings.hfTemplatesJson;
    if (settingsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(settingsJson) as Map<String, dynamic>;
        if (!mounted) return;
        for (final key in _controllers.keys) {
          final v = decoded[key];
          // 空字符串也是有效保存值（用户清空的模板），必须回填，
          // 否则重新保存时默认值会覆盖用户的清空操作
          if (v is String) {
            _controllers[key]?.text = v;
          }
        }
        return;
      } catch (_) {
        // 解析失败则回退 Kotlin prefs 迁移
      }
    }
    final service = MiuiLiveActivitiesService();
    final saved = await service.loadHyperFocusTemplates();
    if (!mounted) return;
    var migrated = false;
    for (final key in _controllers.keys) {
      final v = saved[key];
      // 空字符串同样是有效保存值（用户清空的模板）
      if (v != null) {
        if (_controllers[key]?.text != v) {
          _controllers[key]?.text = v;
          migrated = true;
        }
      }
    }
    if (migrated) {
      await _persistTemplatesToSettings(provider, Map.from(saved));
    }
  }

  Future<void> _persistTemplatesToSettings(
    TimetableProvider provider,
    Map<String, String> map,
  ) async {
    await provider.updateTimetableSettings(
      provider.settings.copyWith(hfTemplatesJson: jsonEncode(map)),
    );
  }

  Future<void> _saveTemplates({bool silent = false}) async {
    final service = MiuiLiveActivitiesService();
    final provider = context.read<TimetableProvider>();

    final merged = <String, String>{};
    final settingsJson = provider.settings.hfTemplatesJson;
    if (settingsJson.isNotEmpty) {
      try {
        final decoded = jsonDecode(settingsJson) as Map<String, dynamic>;
        for (final entry in decoded.entries) {
          if (entry.value is String) {
            merged[entry.key] = entry.value as String;
          }
        }
      } catch (_) {
        // 解析失败则回退 Kotlin prefs 迁移
      }
    }
    if (merged.isEmpty) {
      merged.addAll(await service.loadHyperFocusTemplates());
    }

    for (final key in _defaultTemplates.keys) {
      merged[key] = _controllers[key]?.text ?? _defaultTemplates[key]!;
    }

    final ok = await service.saveHyperFocusTemplates(merged);
    await _persistTemplatesToSettings(provider, merged);
    if (!mounted) return;
    if (!silent) {
      showHyperosSnackBar(context, message: ok ? '模板已保存' : '保存失败');
    }
  }

  Future<void> _resetStage() async {
    for (final key in _controllers.keys) {
      if (key.endsWith('_$_s')) {
        _controllers[key]?.text = _defaultTemplates[key]!;
      }
    }
    await _saveTemplates();
  }

  Widget _variableSelectField(String key, String label) {
    final current = _controllers[key]?.text ?? '';
    final selected = current.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
    return HyperosListTile(
      icon: Icons.tune,
      title: label,
      details: _selectedSummary(selected),
      onTap: () => _openVariableMultiSelect(key, selected),
    );
  }

  String _selectedSummary(List<String> selected) {
    if (selected.isEmpty) return '未选择';
    if (selected.length <= 4) return selected.join('、');
    return '${selected.sublist(0, 4).join('、')}…';
  }

  Future<void> _openVariableMultiSelect(String key, List<String> selected) async {
    final result = await showHyperosSheet<List<String>>(
      context: context,
      builder: (sheetContext) => _VariableMultiSelectSheet(
        variables: _availableVariables,
        phrases: _availablePhrases,
        initial: selected,
      ),
    );
    if (result == null) return;
    if (!mounted) return;
    setState(() {
      _controllers[key]?.text = result.join(',');
    });
  }

  bool _previewShowCountdown() {
    final provider = context.read<TimetableProvider>();
    final displaySettings = _s == 'pre'
        ? provider.settings.beforeClassDisplaySettings
        : provider.settings.duringEndDisplaySettings;
    return displaySettings.showCountdown;
  }

  @override
  void dispose() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('展开态自定义'),
      child: Builder(
        builder: (childContext) {
          final headerInset = HyperosBlurredHeaderScope.insetOf(childContext);
          return Column(
            children: [
              Padding(
                padding: EdgeInsets.only(top: headerInset),
                child: HyperosTabRow(
              tabs: ['课前', '课中', '课后'],
              selectedIndex: _tabOrder.indexOf(_selectedStage),
              onChanged: (i) => setState(() => _selectedStage = _tabOrder[i]),
            ),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    '点击选择要在各区域显示的信息',
                    style: HyperosTypography.listDetail(context),
                  ),
                  const SizedBox(height: 12),
                  HyperosButton(
                    label: '发送测试通知',
                    expand: true,
                    onPressed: _sendTestNotification,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    // 展开卡文本为发送瞬间的快照：hintInfo 不支持系统走秒计时，
                    // 正式上课时服务每分钟重发通知，倒计时按分钟刷新。
                    '测试通知为单次快照，倒计时定格在发送瞬间；正式上课时每分钟自动刷新，按分钟走动',
                    style: HyperosTypography.listDetail(context).copyWith(
                      fontSize: 11,
                    ),
                  ),
                  const SizedBox(height: 12),
                  HyperosSectionLabel(text: '展开态'),
                  _variableSelectField('baseTitle_$_s', '主要标题'),
                  _variableSelectField('baseContent_$_s', '次要文本1'),
                  _variableSelectField('baseSubcontent_$_s', '次要文本2'),
                  _variableSelectField('hintTitle_$_s', '主要小文本1'),
                  _variableSelectField('hintContent_$_s', '前置文本1'),
                  _variableSelectField('hintSubcontent_$_s', '前置文本2'),
                  _variableSelectField('hintSubtitle_$_s', '主要小文本2'),
                  const HyperosSectionGap(),
                  Row(
                    children: [
                      Expanded(
                        child: HyperosButton(
                          label: '保存',
                          expand: true,
                          onPressed: _saveTemplates,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: HyperosButton(
                          label: '恢复默认',
                          expand: true,
                          onPressed: _resetStage,
                        ),
                      ),
                    ],
                  ),
                  const HyperosSectionGap(),
                  Text(
                    '参考样式',
                    style: HyperosTypography.listDetail(context),
                  ),
                  const SizedBox(height: 8),
                  _ExpandedIslandPreview(
                    // 按 stage 重建：模拟时间轴锚点随阶段切换重置
                    key: ValueKey('expanded_preview_$_s'),
                    stage: _s,
                    templates: {
                      for (final key in const [
                        'baseTitle',
                        'baseContent',
                        'baseSubcontent',
                        'hintTitle',
                        'hintContent',
                        'hintSubcontent',
                        'hintSubtitle',
                      ])
                        key: _controllers['${key}_$_s']?.text ?? '',
                    },
                    showCountdown: _previewShowCountdown(),
                  ),
                ],
              ),
            ),
          ),
            ],
          );
        },
      ),
    );
  }
}

Color _parseColor(String hexColor) {
  return parseHexColorOrFallback(hexColor, fallback: const Color(0xFF2563EB));
}

/// 与 HyperFocusTemplates.resolveTemplate 同逻辑：含花括号走逐 token 替换，
/// 否则按逗号列表解析（剔除空 token 与解析为空的项，空格连接）。
/// 展开态预览与 Kotlin 渲染端必须保持一致，改动需同步两侧并补单测。
String resolveHyperFocusPreviewTemplate(
  String tpl,
  Map<String, String> variables,
) {
  if (tpl.contains('{')) {
    var result = tpl;
    variables.forEach((token, value) {
      result = result.replaceAll('{$token}', value);
    });
    return result;
  }
  return tpl
      .split(',')
      .map((token) => token.trim())
      .where((token) => token.isNotEmpty)
      .map((token) => variables[token] ?? token)
      .where((resolved) => resolved.isNotEmpty)
      .join(' ');
}

/// 展开态参考样式的实时渲染：字段角色与 XiaomiSuperIslandNotificationRenderer
/// 的 buildHyperFocusBundle 一致，变量解析与 HyperFocusTemplates.resolveTemplate
/// 逐条对齐（含花括号模式与逗号列表模式），倒计时逐秒走字模拟系统 timerInfo。
class _ExpandedIslandPreview extends StatefulWidget {
  const _ExpandedIslandPreview({
    super.key,
    required this.stage,
    required this.templates,
    required this.showCountdown,
  });

  final String stage;
  final Map<String, String> templates;
  final bool showCountdown;

  @override
  State<_ExpandedIslandPreview> createState() => _ExpandedIslandPreviewState();
}

class _ExpandedIslandPreviewState extends State<_ExpandedIslandPreview> {
  static const _courseName = '高等数学';
  static const _shortName = '高数';
  static const _location = '教科A-101';
  static const _teacher = '张老师';
  static const _startTime = '08:00';
  static const _endTime = '09:40';

  late final DateTime _anchor = DateTime.now();
  // 模拟时间轴：pre=5 分钟后上课；active=已上 12 分钟、还剩 33 分钟；post=刚下课。
  late final DateTime _simStart = switch (widget.stage) {
    'pre' => _anchor.add(const Duration(minutes: 5)),
    'active' => _anchor.subtract(const Duration(minutes: 12)),
    _ => _anchor.subtract(const Duration(minutes: 1)),
  };
  late final DateTime _simEnd = switch (widget.stage) {
    'pre' => _anchor.add(const Duration(minutes: 105)),
    'active' => _anchor.add(const Duration(minutes: 33)),
    _ => _anchor,
  };

  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  String get _countdownText {
    if (widget.stage == 'post') return '';
    return _formatCountdown(_simEnd.difference(DateTime.now()));
  }

  String get _elapsedText {
    if (widget.stage != 'active') return '';
    return _formatCountdown(DateTime.now().difference(_simStart));
  }

  /// 与 HyperFocusTemplates.resolveTemplate 的变量表一致（空值兜底同 Kotlin）。
  Map<String, String> get _templateVariables => <String, String>{
        '课名': _courseName,
        '短课名': _shortName.isEmpty ? _courseName : _shortName,
        '教室': _location.isEmpty ? _courseName : _location,
        '教师': _teacher,
        '开始': _startTime,
        '结束': _endTime,
        '倒计时': _countdownText,
        '正计时': _elapsedText,
      };

  String _resolve(String tpl) =>
      resolveHyperFocusPreviewTemplate(tpl, _templateVariables);

  String get _mainTitle => _resolve(widget.templates['baseTitle'] ?? '');

  // 文本组件2：次要文本1 / 次要文本2 两个槽位（渲染端以 " · " 连接为 content）。
  String get _secondaryFirst => _resolve(widget.templates['baseContent'] ?? '');

  String get _secondarySecond =>
      _resolve(widget.templates['baseSubcontent'] ?? '');

  bool get _showTimer => widget.showCountdown && widget.stage != 'post';

  // 按钮组件2：前置文本1 = 运行时剩余时间（timerInfo 驱动，空时回退主要小文本1）。
  String get _prefixFirst {
    if (_showTimer) return _countdownText;
    return _resolve(widget.templates['hintTitle'] ?? '');
  }

  // 前置文本2 = hintContent（payload 中 extraTitle 承载）。
  String get _prefixSecond => _resolve(widget.templates['hintContent'] ?? '');

  // 主要小文本1 = hintTitle；主要小文本2 = hintSubtitle。
  String get _hintMainFirst => _resolve(widget.templates['hintTitle'] ?? '');

  String get _hintMainSecond =>
      _resolve(widget.templates['hintSubtitle'] ?? '');

  /// 参考图槽位框：有值显示解析后的值，无值灰显槽位名（与标注线框一致）。
  Widget _slotBox(
    String slotName,
    String value, {
    double fontSize = 14,
    FontWeight fontWeight = FontWeight.w500,
  }) {
    final filled = value.isNotEmpty;
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        decoration: BoxDecoration(
          color: filled
              ? Colors.white.withValues(alpha: 0.10)
              : Colors.white.withValues(alpha: 0.04),
          border: Border.all(
            color: Colors.white.withValues(alpha: filled ? 0.25 : 0.12),
          ),
          borderRadius: BorderRadius.circular(7),
        ),
        alignment: Alignment.centerLeft,
        child: Text(
          filled ? value : slotName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: Colors.white.withValues(alpha: filled ? 0.95 : 0.30),
            fontSize: filled ? fontSize : 10,
            fontWeight: fontWeight,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF101010),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        children: [
          // ── 文本组件2 ──
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(
                child: Column(
                  children: [
                    Row(
                      children: [
                        _slotBox('主要文本1', _mainTitle,
                            fontSize: 15, fontWeight: FontWeight.w700),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        _slotBox('次要文本1', _secondaryFirst, fontSize: 12),
                        if (_secondarySecond.isNotEmpty)
                          const SizedBox(width: 6),
                        _slotBox('次要文本2', _secondarySecond, fontSize: 12),
                        const SizedBox(width: 6),
                        _slotBox('功能图标', '', fontSize: 10),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(9),
                child: Image.asset(
                  'assets/branding/launcher_icon.png',
                  width: 40,
                  height: 40,
                  fit: BoxFit.cover,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // ── 按钮组件2 ──
          Container(
            padding: const EdgeInsets.only(top: 10),
            decoration: BoxDecoration(
              border: Border(
                top: BorderSide(color: Colors.white.withValues(alpha: 0.12)),
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Column(
                    children: [
                      Row(
                        children: [
                          _slotBox('前置文本1', _prefixFirst, fontSize: 11),
                          const SizedBox(width: 6),
                          _slotBox('前置文本2', _prefixSecond, fontSize: 11),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          _slotBox('主要小文本1', _hintMainFirst, fontSize: 13),
                          const SizedBox(width: 6),
                          _slotBox('主要小文本2', _hintMainSecond, fontSize: 13),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(3),
                        child: Image.asset(
                          'assets/branding/launcher_icon.png',
                          width: 13,
                          height: 13,
                          fit: BoxFit.cover,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '查看课表',
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.85),
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

String _formatCountdown(Duration duration) {
  if (duration.inMilliseconds <= 0) return '00:00';
  final totalSeconds = duration.inSeconds;
  final hours = totalSeconds ~/ 3600;
  final minutes = (totalSeconds % 3600) ~/ 60;
  final seconds = totalSeconds % 60;
  final mm = minutes.toString().padLeft(2, '0');
  final ss = seconds.toString().padLeft(2, '0');
  return hours > 0 ? '$hours:$mm:$ss' : '$mm:$ss';
}
