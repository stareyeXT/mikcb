### Task 4: Flutter Service — 添加 sendTestFocusNotification 方法

**Files:**
- Modify: `lib/services/miui_live_activities_service.dart`

- [ ] **Step 1: 在 `MiuiLiveActivitiesService` 类末尾添加方法**

```dart
  Future<bool> sendTestFocusNotification() async {
    if (!Platform.isAndroid) return false;
    try {
      await _channel.invokeMethod('sendTestFocus');
      return true;
    } catch (e) {
      appDebugLog('MiuiLive', '发送测试焦点通知失败：$e');
      return false;
    }
  }
```

- [ ] **Step 2: 验证**

Run: `dart analyze lib/services/miui_live_activities_service.dart`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add lib/services/miui_live_activities_service.dart
git commit -m "feat: add sendTestFocusNotification to MiuiLiveActivitiesService"
```

---


