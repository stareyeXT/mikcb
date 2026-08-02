### Task 1: 写失败测试（新页面共用 live 设置）

**Files:**
- Create: `test/widgets/hyperfocus_timing_screen_test.dart`

**Interfaces:**
- Consumes: `createInitializedTestProvider(tester)` 与 `TestApp`（`test/helpers_test_app.dart`）、`TimetableSettingsScreen`（`lib/screens/timetable_settings_screen.dart`）、`TimetableSettings`/`TimetableProfile`（`lib/models/`）、`StorageService().resetForTesting()`（`lib/services/storage_service.dart`）。
- Produces: 导航辅助函数 `openHyperFocusTimingScreen(tester) → Future<TimetableProvider>`，两个测试用例（Task 2 实现后必须通过）。

- [ ] **Step 1: 创建测试文件**

创建 `test/widgets/hyperfocus_timing_screen_test.dart`：

```dart
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:university_timetable/models/timetable_profile.dart';
import 'package:university_timetable/models/timetable_settings.dart';
import 'package:university_timetable/providers/timetable_provider.dart';
import 'package:university_timetable/screens/timetable_settings_screen.dart';
import 'package:university_timetable/services/storage_service.dart';
import '../helpers_test_app.dart';

Future<void> _pumpScreen(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
}

void _seedInitializedPrefs() {
  final now = DateTime(2026, 4, 12);
  final settings = TimetableSettings.defaults();
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const homeWidgetChannel = MethodChannel('com.mutx163.qingyu/home_widget');
  const analyticsChannel = MethodChannel('com.mutx163.qingyu/umeng_analytics');
  const liveChannel = MethodChannel('com.mutx163.qingyu/miui_live');

  setUp(() {
    StorageService().resetForTesting();
    _seedInitializedPrefs();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(homeWidgetChannel, (call) async => null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(analyticsChannel, (call) async => null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, (call) async => null);
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(homeWidgetChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(analyticsChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(liveChannel, null);
  });

  Future<TimetableProvider> openHyperFocusTimingScreen(WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final provider = await createInitializedTestProvider(tester);

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const TestApp(home: TimetableSettingsScreen()),
      ),
    );
    await _pumpScreen(tester);

    await tester.scrollUntilVisible(
      find.text('超级岛与通知'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('超级岛与通知'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('小米超级岛'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('小米超级岛'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('提醒时机'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.text('提醒时机'));
    await tester.pumpAndSettle();
    return provider;
  }

  testWidgets('hyper focus timing switches edit shared live settings', (
    tester,
  ) async {
    final provider = await openHyperFocusTimingScreen(tester);

    expect(find.text('上课前提醒'), findsOneWidget);
    expect(provider.settings.liveEnableBeforeClass, isTrue);

    await tester.tap(find.text('上课前提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableBeforeClass, isFalse);

    await tester.tap(find.text('课中与下课提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableDuringClass, isFalse);
    expect(provider.settings.liveEnableBeforeEnd, isFalse);
    expect(find.text('重点提醒切入时机'), findsNothing);

    await tester.tap(find.text('课中与下课提醒'));
    await tester.pumpAndSettle();
    expect(provider.settings.liveEnableDuringClass, isTrue);
    expect(provider.settings.liveEnableBeforeEnd, isTrue);
    expect(find.text('重点提醒切入时机'), findsOneWidget);
  });

  testWidgets('hyper focus timing thresholds share live settings', (
    tester,
  ) async {
    final provider = await openHyperFocusTimingScreen(tester);

    await tester.scrollUntilVisible(
      find.text('时间阈值'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('20 分钟').first);
    await tester.pumpAndSettle();
    for (final minutes in [30, 40, 50, 60]) {
      expect(find.text('$minutes 分钟'), findsWidgets);
    }
    await tester.tap(find.text('30 分钟').first);
    await tester.pumpAndSettle();
    expect(provider.settings.liveShowBeforeClassMinutes, 30);
  });
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `flutter test test\widgets\hyperfocus_timing_screen_test.dart`
Expected: 失败 — 找不到 `上课前提醒`（当前页面只有 `课前提醒`），第一个用例在第一个 `expect` 处 FAIL。

- [ ] **Step 3: 提交测试**

```bash
git add test/widgets/hyperfocus_timing_screen_test.dart
git commit -m "test: hyper focus timing page shares live settings"
```

---

