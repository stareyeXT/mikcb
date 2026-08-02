### Task 3: 新增超级岛测试页 widget 测试（RED）

**Files:**
- Create: `test/widgets/hyper_focus_testing_screen_test.dart`
- Modify: `test/widgets/timetable_settings_screen_test.dart`（入口行为断言暂不改，本任务只新增文件）

**Interfaces:**
- Consumes: `MiuiLiveActivitiesService.getHyperFocusDebugStatus()`、`sendTestFocusNotification({String? courseName, String? startTime, String? endTime, String? location, String? teacher, required String stage})`（返回 `String?`）、`HyperFocusTestingScreen` 计划私有类 `_HyperFocusTestingSettingsScreen`（通过入口进入，本任务仅验证编译失败即可）

- [ ] **Step 1: 编写失败测试**

新建 `test/widgets/hyper_focus_testing_screen_test.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:university_timetable/screens/timetable_settings_screen.dart';
import 'package:university_timetable/services/storage_service.dart';
import '../helpers_test_app.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');

  setUp(() {
    StorageService().resetForTesting();
    SharedPreferences.setMockInitialValues({
      'did_migrate_app_logs_default': true,
      'did_migrate_live_hide_prefix_default': true,
    });
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

  testWidgets('hyper focus testing screen renders status chips and refresh switch', (
    tester,
  ) async {
    await tester.pumpWidget(const TestApp(home: TimetableSettingsScreen()));
    await tester.pump(const Duration(milliseconds: 500));

    await tester.tap(find.text('超级岛与通知'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('测试'));
    await tester.pumpAndSettle();

    expect(find.text('超级岛测试与诊断'), findsOneWidget);
    expect(find.text('通知权限已开启'), findsOneWidget);
    expect(find.text('测试渠道正常'), findsOneWidget);
    expect(find.text('调度已就绪'), findsOneWidget);
    expect(find.text('自动刷新'), findsOneWidget);
  });

  testWidgets('hyper focus testing screen opens stage sheet and sends test', (
    tester,
  ) async {
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
              };
            case 'sendTestFocus':
              sentStage = (call.arguments as Map)['stage'] as String?;
              return null;
            default:
              return null;
          }
        });

    await tester.pumpWidget(const TestApp(home: TimetableSettingsScreen()));
    await tester.pump(const Duration(milliseconds: 500));

    await tester.tap(find.text('超级岛与通知'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('测试'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('发送测试通知'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('课中'));
    await tester.pumpAndSettle();

    expect(sentStage, 'active');
    expect(find.text('测试焦点通知已发送'), findsOneWidget);
  });
}
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `flutter test test/widgets/hyper_focus_testing_screen_test.dart`
Expected: FAIL（编译错误：`_HyperFocusTestingSettingsScreen`/`HyperFocusTestingScreen` 不存在，或页面无对应文本）——记录确切失败信息

- [ ] **Step 3: 提交**

```bash
git add test/widgets/hyper_focus_testing_screen_test.dart
git commit -m "test: hyper focus testing screen status and stage sheet"
```

---


