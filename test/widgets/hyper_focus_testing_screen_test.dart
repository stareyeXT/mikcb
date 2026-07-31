import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:university_timetable/models/timetable_profile.dart';
import 'package:university_timetable/models/timetable_settings.dart';
import 'package:university_timetable/screens/timetable_settings_screen.dart';
import 'package:university_timetable/services/storage_service.dart';
import '../helpers_test_app.dart';

void _seedInitializedPrefs() {
  final now = DateTime(2026, 4, 12);
  final settings = TimetableSettings.defaults().copyWith(
    superIslandEngine: SuperIslandEngine.hyperFocusApi,
  );
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

void _testOnAndroid(
  String name,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(name, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

Future<void> _pumpToTestingEntry(WidgetTester tester) async {
  await tester.binding.setSurfaceSize(const Size(800, 1200));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  final provider = await createInitializedTestProvider(tester);

  await tester.pumpWidget(
    ChangeNotifierProvider.value(
      value: provider,
      child: const TestApp(home: TimetableSettingsScreen()),
    ),
  );
  await tester.pump(const Duration(milliseconds: 500));

  await tester.scrollUntilVisible(
    find.text('超级岛与通知'),
    200,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.tap(find.text('超级岛与通知'));
  await tester.pumpAndSettle();

  await tester.scrollUntilVisible(
    find.text('测试'),
    200,
    scrollable: find.byType(Scrollable).last,
  );
  await tester.tap(find.text('测试'));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');

  setUp(() {
    StorageService().resetForTesting();
    _seedInitializedPrefs();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, (call) async {
          switch (call.method) {
            case 'initialize':
              return null;
            case 'getHyperFocusDebugStatus':
              return {
                'generatedAtMillis': 0,
                'summary': {
                  'hasNotificationPermission': true,
                  'testChannelBlocked': false,
                  'templatesLoaded': true,
                  'schedulerReady': true,
                  'hasLastTestResult': false,
                },
                'scheduling': {
                  'nextCourseName': '高等数学',
                  'nextCourseStartAtMillis': 0,
                  'nextCourseEndAtMillis': 0,
                  'nextTriggerAtMillis': 0,
                  'nextTriggerStage': 'pre',
                  'hasActiveSelection': true,
                },
                'templates': {
                  'pre': {'ticker': true},
                  'active': {'ticker': true},
                  'post': {'ticker': true},
                },
                'test': {
                  'lastStage': null,
                  'lastSucceeded': null,
                  'lastMessage': null,
                  'lastAtMillis': null,
                },
                'recentDiagnostics': {'enabled': false, 'tail': ''},
              };
            default:
              return null;
          }
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, null);
  });

  _testOnAndroid(
    'hyper focus testing screen renders status chips and refresh switch',
    (tester) async {
      await _pumpToTestingEntry(tester);

      expect(find.text('超级岛测试与诊断'), findsOneWidget);
      expect(find.text('通知权限已开启'), findsOneWidget);
      expect(find.text('测试渠道正常'), findsOneWidget);
      expect(find.text('调度已就绪'), findsOneWidget);
      expect(find.text('自动刷新'), findsOneWidget);
    },
  );

  _testOnAndroid(
    'hyper focus testing screen sends test with scheduled stage',
    (tester) async {
      String? sentStage;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(liveChannel, (call) async {
            switch (call.method) {
              case 'initialize':
                return null;
              case 'getHyperFocusDebugStatus':
                return {
                  'summary': {
                    'hasNotificationPermission': true,
                    'testChannelBlocked': false,
                    'templatesLoaded': true,
                    'schedulerReady': true,
                    'hasLastTestResult': false,
                  },
                  'scheduling': {'nextTriggerStage': 'active'},
                };
              case 'sendTestFocus':
                sentStage = (call.arguments as Map)['stage'] as String?;
                return null;
              default:
                return null;
            }
          });

      await _pumpToTestingEntry(tester);

      await tester.tap(find.text('发送测试通知'));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await tester.pumpAndSettle();

      expect(sentStage, 'active');
      expect(find.text('测试焦点通知已发送'), findsOneWidget);
    },
  );
}
