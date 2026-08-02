### Task 2: Dart 服务层新增 getHyperFocusDebugStatus

**Files:**
- Modify: `lib/services/miui_live_activities_service.dart:402`（在 `getLiveUpdateDebugStatus` 方法后插入新方法）
- Modify: `lib/services/miui_live_activities_service.dart:686`（`TestMiuiLiveActivitiesService` 内）

**Interfaces:**
- Consumes: 现有 `_channel`（MethodChannel `com.mutx163.qingyu/miui_live`）、`initialize()`
- Produces: `Future<Map<String, dynamic>> getHyperFocusDebugStatus()`（真实实现 + `TestMiuiLiveActivitiesService` 覆写，供 Task 4/5 使用）

- [ ] **Step 1: 在真实服务类中新增方法**

在 `lib/services/miui_live_activities_service.dart` 的 `getLiveUpdateDebugStatus` 方法（结束于 ~423 行 `}`）之后插入：

```dart
  Future<Map<String, dynamic>> getHyperFocusDebugStatus() async {
    await initialize();
    try {
      final result = await _channel.invokeMethod('getHyperFocusDebugStatus');
      return Map<String, dynamic>.from(result as Map);
    } catch (e, stackTrace) {
      await UmengAnalyticsService.reportDiagnostic(
        'hyper_focus_debug_status_failed',
        AppLogMessages.liveUpdateDebugStatusFailed,
        error: e,
        stackTrace: stackTrace,
      );
      appDebugLog('MiuiLive', '获取超级岛调试状态失败：$e');
      return {
        'summary': {
          'hasNotificationPermission': false,
          'testChannelBlocked': true,
          'templatesLoaded': false,
          'schedulerReady': false,
          'hasLastTestResult': false,
        },
      };
    }
  }
```

（`AppLogMessages.liveUpdateDebugStatusFailed` 复用现有常量；若 lint 提示非 const，改用 `AppLogMessages.liveUpdateDebugStatusFailed` 字符串字面量同样可行——以 analyze 结果为准。）

- [ ] **Step 2: 在 TestMiuiLiveActivitiesService 中覆写**

在 `TestMiuiLiveActivitiesService` 类内（`getLiveUpdateDebugStatus` 覆写附近）新增：

```dart
  @override
  Future<Map<String, dynamic>> getHyperFocusDebugStatus() async {
    return const {
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
        'pre': {'ticker': true, 'islandA': true, 'islandB': true, 'baseTitle': true, 'baseContent': true, 'baseSubcontent': true, 'hintTitle': true},
        'active': {'ticker': true, 'islandA': true, 'islandB': true, 'baseTitle': true, 'baseContent': true, 'baseSubcontent': true, 'hintTitle': true},
        'post': {'ticker': true, 'islandA': true, 'islandB': true, 'baseTitle': true, 'baseContent': true, 'baseSubcontent': true, 'hintTitle': true},
      },
      'test': {'lastStage': null, 'lastSucceeded': null, 'lastMessage': null, 'lastAtMillis': null},
      'recentDiagnostics': {'enabled': false, 'tail': ''},
    };
  }
```

- [ ] **Step 3: 运行 analyze**

Run: `flutter analyze lib/services/miui_live_activities_service.dart`
Expected: `No issues found!`

- [ ] **Step 4: 提交**

```bash
git add lib/services/miui_live_activities_service.dart
git commit -m "feat: add getHyperFocusDebugStatus to live activities service"
```

---


