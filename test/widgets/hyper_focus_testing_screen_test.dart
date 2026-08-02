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
    find.text('自检'),
    200,
    scrollable: find.byType(Scrollable).last,
  );
  await tester.tap(find.text('自检'));
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 50)),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');
  const umengChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');
  const packageInfoChannel = MethodChannel('dev.fluttercommunity.plus/package_info');

  setUp(() {
    StorageService().resetForTesting();
    _seedInitializedPrefs();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, (call) async {
          switch (call.method) {
            case 'initialize':
              return null;
            case 'getLiveUpdateDebugStatus':
              return {
                'generatedAtMillis': 0,
                'summary': {
                  'serviceRunning': true,
                  'isActuallyPromotable': true,
                  'statusText': '运行正常',
                  'hasNotificationPermission': true,
                  'testChannelBlocked': false,
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

      expect(find.text('自检'), findsOneWidget);
      expect(find.text('服务运行中'), findsOneWidget);
      expect(find.text('运行正常'), findsOneWidget);
      expect(find.text('自动刷新'), findsOneWidget);
      expect(find.text('刷新诊断'), findsOneWidget);
    },
  );

  _testOnAndroid(
    'hyper focus testing screen send triggers production refresh diagnostics',
    (tester) async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(liveChannel, (call) async {
            switch (call.method) {
              case 'initialize':
                return null;
              case 'getLiveUpdateDebugStatus':
                return {
                  'summary': {
                    'serviceRunning': true,
                    'isActuallyPromotable': true,
                    'statusText': '运行正常',
                  },
                  'scheduling': {'nextTriggerStage': 'active'},
                };
              case 'suspendScheduleTriggers':
                return null;
              default:
                return null;
            }
          });
      final recordedCategories = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(umengChannel, (call) async {
            if (call.method == 'recordDiagnosticEvent') {
              recordedCategories.add((call.arguments as Map)['category'] as String);
            }
            return null;
          });
      addTearDown(() {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(umengChannel, null);
      });
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(packageInfoChannel, (call) async {
            if (call.method == 'getAll') {
              return {
                'appName': 'mikcb',
                'packageName': 'com.mutx163.qingyu',
                'version': '1.0.0',
                'buildNumber': '1',
                'buildSignature': '',
                'installerStore': null,
              };
            }
            return null;
          });
      addTearDown(() {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(packageInfoChannel, null);
      });

      await _pumpToTestingEntry(tester);

      await tester.tap(find.text('发送测试通知'));
      for (int i = 0; i < 20; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 20)),
        );
        await tester.pump(const Duration(milliseconds: 100));
        if (find.text('当前没有可测试的课程').evaluate().isNotEmpty) {
          break;
        }
      }

      expect(recordedCategories, contains('live_update_test_requested'));
      expect(find.text('当前没有可测试的课程'), findsOneWidget);
      await tester.pump(const Duration(seconds: 3));
    },
  );
}
