import 'dart:async';
import 'dart:convert';
import 'dart:ui';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:university_timetable/l10n/app_localizations.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import 'providers/timetable_provider.dart';
import 'screens/course_import_screen.dart';
import 'screens/startup_flow_screens.dart';
import 'screens/user_guide_screen.dart';
import 'screens/timetable_screen.dart';
import 'services/app_log_service.dart';
import 'services/app_log_observers.dart';
import 'services/app_migration_service.dart';
import 'services/storage_service.dart';
import 'services/background_html_refresh_service.dart';
import 'services/umeng_analytics_service.dart';
import 'theme/app_theme.dart';
import 'package:workmanager/workmanager.dart';

Future<void> main() async {
  Workmanager().initialize(backgroundHtmlRefreshCallback);

  runZonedGuarded(() {
    WidgetsFlutterBinding.ensureInitialized();
    unawaited(AppLogService.instance.initialize());
    WidgetsBinding.instance.addObserver(AppLifecycleLogObserver());

    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      final stackTrace = details.stack ?? StackTrace.current;
      unawaited(
        AppLogService.instance.error(
          'flutter_framework_error',
          details.exceptionAsString(),
          error: details.exception,
          stackTrace: stackTrace,
        ),
      );
      unawaited(
        UmengAnalyticsService.reportUnhandledError(
          details.exception,
          stackTrace,
          category: 'flutter_framework_error',
        ),
      );
    };

    PlatformDispatcher.instance.onError = (error, stackTrace) {
      unawaited(
        AppLogService.instance.error(
          'flutter_platform_error',
          error.toString(),
          error: error,
          stackTrace: stackTrace,
        ),
      );
      unawaited(
        UmengAnalyticsService.reportUnhandledError(
          error,
          stackTrace,
          category: 'flutter_platform_error',
        ),
      );
      return false;
    };

    runApp(const MyApp());
  }, (error, stackTrace) {
    unawaited(
      AppLogService.instance.error(
        'flutter_zone_error',
        error.toString(),
        error: error,
        stackTrace: stackTrace,
      ),
    );
    unawaited(
      UmengAnalyticsService.reportUnhandledError(
        error,
        stackTrace,
        category: 'flutter_zone_error',
      ),
    );
  });
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => TimetableProvider(autoInitialize: false),
        ),
      ],
      child: Consumer<TimetableProvider>(
        builder: (context, provider, child) {
          final seedColor = colorFromHex(provider.settings.themeSeedColor);
          final fontFamily =
              fontFamilyFromSettings(provider.settings.appFontMode);

          return MaterialApp(
            debugShowCheckedModeBanner: false,
            onGenerateTitle: (context) => kReleaseMode
                ? AppLocalizations.of(context)!.appTitle
                : AppLocalizations.of(context)!.appTitleDebug,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            locale: localeFromSettings(provider.settings.appLocaleTag),
            themeMode: themeModeFromSettings(provider.settings.appThemeMode),
            theme: buildAppTheme(
              seedColor,
              Brightness.light,
              fontFamily: fontFamily,
            ),
            darkTheme: buildAppTheme(
              seedColor,
              Brightness.dark,
              fontFamily: fontFamily,
            ),
            navigatorObservers: <NavigatorObserver>[
              AppRouteLogObserver(),
            ],
            home: const AppEntryScreen(),
          );
        },
      ),
    );
  }
}

class AppEntryScreen extends StatefulWidget {
  const AppEntryScreen({super.key});

  @override
  State<AppEntryScreen> createState() => _AppEntryScreenState();
}

class _AppEntryScreenState extends State<AppEntryScreen> {
  final StorageService _storageService = StorageService();
  final AppMigrationService _migrationService = AppMigrationService();
  bool _startupHandled = false;
  bool _isBootstrapping = true;

  @override
  void initState() {
    super.initState();
    unawaited(_handleStartupFlows());
  }

  Future<void> _handleStartupFlows() async {
    if (_startupHandled) {
      return;
    }
    _startupHandled = true;
    unawaited(
      AppLogService.instance.info(
        'startup_flow_started',
        'Startup flow handling started',
      ),
    );

    await _storageService.init();
    final isDataEmpty = await _storageService.isAppDataEffectivelyEmpty();
    final hasCompletedOnboarding =
        await _storageService.hasCompletedOnboarding();
    final hasHandledPackageMigration =
        await _storageService.hasHandledPackageMigration();
    final hasAcceptedPrivacy = await _storageService.hasAcceptedPrivacyPolicy();
    final hasSeenGuide = await _storageService.hasSeenUserGuide();

    final provider = context.read<TimetableProvider>();
    final legacyPackageFuture = _migrationService.findInstalledLegacyPackage();
    await provider.initialize();
    final legacyPackage = await legacyPackageFuture;
    final shouldShowMigrationGuide =
        !hasHandledPackageMigration && isDataEmpty && legacyPackage != null;

    if (!mounted) {
      return;
    }

    if (shouldShowMigrationGuide) {
      final action = await Navigator.of(context).push<MigrationFlowAction>(
        MaterialPageRoute(
          builder: (_) => PackageMigrationGuideScreen(
            legacyPackageName: legacyPackage,
          ),
          fullscreenDialog: true,
        ),
      );
      if (!mounted) {
        return;
      }
      if (action == MigrationFlowAction.restoreBackup) {
        final imported = await _runBackupImportFlow(
          forcedMode: _BackupImportMode.replaceCurrent,
        );
        if (imported) {
          await _storageService.setHandledPackageMigration(true);
          await _storageService.setCompletedOnboarding(true);
        }
      } else if (action == MigrationFlowAction.skip) {
        await _storageService.setHandledPackageMigration(true);
        await _storageService.setCompletedOnboarding(true);
      }
    } else if (!hasCompletedOnboarding) {
      final action = await Navigator.of(context).push<WelcomeFlowAction>(
        MaterialPageRoute(
          builder: (_) => const StartupWelcomeScreen(),
          fullscreenDialog: true,
        ),
      );
      if (!mounted) {
        return;
      }
      if (action != null) {
        var completedOnboarding = false;
        switch (action) {
          case WelcomeFlowAction.importCourses:
            completedOnboarding = await _runCourseImportFlow();
            break;
          case WelcomeFlowAction.restoreBackup:
            completedOnboarding = await _runBackupImportFlow(
              forcedMode: _BackupImportMode.replaceCurrent,
            );
            break;
          case WelcomeFlowAction.viewGuide:
          case WelcomeFlowAction.startUsing:
            completedOnboarding = true;
            break;
        }
        if (completedOnboarding) {
          await _storageService.setCompletedOnboarding(true);
        }
      }
    }

    if (hasAcceptedPrivacy && hasSeenGuide) {
      await AppLogService.instance.updatePrivacyAccepted(true);
      await UmengAnalyticsService.initializeIfNeeded();
      if (!mounted) {
        return;
      }
      unawaited(
        AppLogService.instance.info(
          'startup_flow_completed',
          'Startup flow completed without onboarding screens',
        ),
      );
      if (provider.hasHtmlImportSource) {
        await provider.refreshHtmlImportForWeek(provider.currentWeek);
      }
      setState(() {
        _isBootstrapping = false;
      });
      return;
    }

    if (!mounted) {
      return;
    }

    await WidgetsBinding.instance.endOfFrame;
    if (!mounted) {
      return;
    }

    final guideCompleted = await _openGuide(
      requirePrivacyConsent: !hasAcceptedPrivacy,
      initialPrivacyChecked: hasAcceptedPrivacy,
      markGuideSeenAfterExit: !hasSeenGuide,
    );
    if (!mounted || !guideCompleted) {
      return;
    }

    if (await _storageService.hasAcceptedPrivacyPolicy()) {
      await UmengAnalyticsService.initializeIfNeeded();
    }
    if (!mounted) {
      return;
    }

    unawaited(
      AppLogService.instance.info(
        'startup_flow_completed',
        'Startup flow completed after guide/onboarding',
      ),
    );
    if (provider.hasHtmlImportSource) {
      await provider.refreshHtmlImportForWeek(provider.currentWeek);
    }
    setState(() {
      _isBootstrapping = false;
    });
  }

  Future<bool> _openGuide({
    required bool requirePrivacyConsent,
    required bool initialPrivacyChecked,
    required bool markGuideSeenAfterExit,
  }) async {
    final accepted = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        settings: const RouteSettings(name: '/user-guide'),
        builder: (_) => UserGuideScreen(
          requirePrivacyConsent: requirePrivacyConsent,
          initialPrivacyChecked: initialPrivacyChecked,
        ),
        fullscreenDialog: true,
      ),
    );

    if (!mounted) {
      return false;
    }

    if (requirePrivacyConsent) {
      if (accepted == true) {
        await _storageService.setAcceptedPrivacyPolicy(true);
        await AppLogService.instance.updatePrivacyAccepted(true);
        await UmengAnalyticsService.initializeIfNeeded();
      } else {
        return false;
      }
    }

    if (markGuideSeenAfterExit) {
      await _storageService.setHasSeenUserGuide(true);
    }
    return true;
  }

  Future<bool> _runBackupImportFlow({
    _BackupImportMode? forcedMode,
  }) async {
    if (!mounted) {
      return false;
    }
    final l10n = AppLocalizations.of(context)!;
    final provider = context.read<TimetableProvider>();
    final importMode = forcedMode ??
        await showDialog<_BackupImportMode>(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text(l10n.selectImportModeTitle),
              content: Text(l10n.selectImportModeMessage),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text(l10n.cancelAction),
                ),
                FilledButton(
                  onPressed: () =>
                      Navigator.pop(context, _BackupImportMode.replaceCurrent),
                  child: Text(l10n.replaceCurrentTimetable),
                ),
                FilledButton.tonal(
                  onPressed: () =>
                      Navigator.pop(context, _BackupImportMode.importAsNew),
                  child: Text(l10n.importAsNewTimetable),
                ),
              ],
            );
          },
        );

    if (importMode == null || !mounted) {
      return false;
    }

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        withData: true,
        allowedExtensions: const ['json', 'mikcb'],
      );
      final file = result?.files.single;
      if (file == null) {
        return false;
      }
      final bytes = file.bytes;
      final content = bytes == null ? '' : utf8.decode(bytes);
      if (content.isEmpty) {
        throw FormatException(l10n.importFileReadFailed);
      }

      final message = switch (importMode) {
        _BackupImportMode.replaceCurrent =>
          await provider.importAppDataBackup(content),
        _BackupImportMode.importAsNew =>
          await provider.importAppDataBackupAsNewProfile(content),
      };

      if (!mounted) {
        return false;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            message ??
                (importMode == _BackupImportMode.importAsNew
                    ? l10n.createdNewTimetableAfterImport
                    : l10n.backupRestoredSuccess),
          ),
        ),
      );
      return true;
    } on FormatException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.message)),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.importFailedInvalidFile)),
        );
      }
    }
    return false;
  }

  Future<bool> _runCourseImportFlow() async {
    final imported = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        settings: const RouteSettings(name: '/courses/import'),
        builder: (_) => const CourseImportScreen(),
      ),
    );
    return imported == true;
  }

  @override
  Widget build(BuildContext context) {
    if (_isBootstrapping) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    return const TimetableScreen();
  }
}

enum _BackupImportMode {
  replaceCurrent,
  importAsNew,
}


