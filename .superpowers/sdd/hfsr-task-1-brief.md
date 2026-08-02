# Task 1 Brief: TimetableSettings 模型扩展

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 1

## Global Constraints（本项目所有任务适用）

- 视觉默认值：`hfIconAEnabled=true`、`hfStatusTextColor=#FFFFFFFF`、`hfOutEffectStatusEnabled=true`、`hfOutEffectStatusColor=#FFFFFFFF`、`hfOutEffectExpandEnabled=true`、`hfOutEffectExpandColor=#FFFFFFFF`
- 消失时间默认：`hfIslandTimeoutPre=300`、`hfIslandTimeoutActive=600`、`hfIslandTimeoutPost=600`
- 死字段删除：`hfShowCourseName`/`hfShowLocation`/`hfShowCountdown`/`hfCustomTitle`/`hfCustomTitleColor`（保留 `hfTemplatesJson`）
- `flutter analyze` 基线：8 个预存在 infos

## Files

- Modify: `lib/models/timetable_settings.dart`
  - L1083-1088 附近：删除 5 个死字段声明，保留 `hfTemplatesJson`
  - 默认值（L1239-1245 附近）、toJson（L1551-1556 附近）、fromJson（L1873-1878 附近）、copyWith（L2090-2095 参数区、L2348-2353 赋值区）

## Step 1: 删除死字段声明

删除：
```dart
  final bool hfShowCourseName;
  final bool hfShowLocation;
  final bool hfShowCountdown;
  final String hfCustomTitle;
  final String hfCustomTitleColor;
```
保留 `final String hfTemplatesJson;`。

## Step 2: 新增字段声明

在 `hfTemplatesJson` 声明后新增：
```dart
  final int hfIslandTimeoutPre;
  final int hfIslandTimeoutActive;
  final int hfIslandTimeoutPost;
  final bool hfIconAEnabled;
  final String hfStatusTextColor;
  final bool hfOutEffectStatusEnabled;
  final String hfOutEffectStatusColor;
  final bool hfOutEffectExpandEnabled;
  final String hfOutEffectExpandColor;
```

## Step 3: 更新默认值

删除死字段默认值，新增：
```dart
    this.hfIslandTimeoutPre = 300,
    this.hfIslandTimeoutActive = 600,
    this.hfIslandTimeoutPost = 600,
    this.hfIconAEnabled = true,
    this.hfStatusTextColor = '#FFFFFFFF',
    this.hfOutEffectStatusEnabled = true,
    this.hfOutEffectStatusColor = '#FFFFFFFF',
    this.hfOutEffectExpandEnabled = true,
    this.hfOutEffectExpandColor = '#FFFFFFFF',
```

## Step 4: 更新 toJson

删除死字段 5 行，新增：
```dart
      'hfIslandTimeoutPre': hfIslandTimeoutPre,
      'hfIslandTimeoutActive': hfIslandTimeoutActive,
      'hfIslandTimeoutPost': hfIslandTimeoutPost,
      'hfIconAEnabled': hfIconAEnabled,
      'hfStatusTextColor': hfStatusTextColor,
      'hfOutEffectStatusEnabled': hfOutEffectStatusEnabled,
      'hfOutEffectStatusColor': hfOutEffectStatusColor,
      'hfOutEffectExpandEnabled': hfOutEffectExpandEnabled,
      'hfOutEffectExpandColor': hfOutEffectExpandColor,
```

## Step 5: 更新 fromJson

删除死字段 5 行，新增：
```dart
      hfIslandTimeoutPre: (json['hfIslandTimeoutPre'] as num?)?.toInt() ?? 300,
      hfIslandTimeoutActive: (json['hfIslandTimeoutActive'] as num?)?.toInt() ?? 600,
      hfIslandTimeoutPost: (json['hfIslandTimeoutPost'] as num?)?.toInt() ?? 600,
      hfIconAEnabled: json['hfIconAEnabled'] as bool? ?? true,
      hfStatusTextColor: json['hfStatusTextColor'] as String? ?? '#FFFFFFFF',
      hfOutEffectStatusEnabled: json['hfOutEffectStatusEnabled'] as bool? ?? true,
      hfOutEffectStatusColor: json['hfOutEffectStatusColor'] as String? ?? '#FFFFFFFF',
      hfOutEffectExpandEnabled: json['hfOutEffectExpandEnabled'] as bool? ?? true,
      hfOutEffectExpandColor: json['hfOutEffectExpandColor'] as String? ?? '#FFFFFFFF',
```

## Step 6: 更新 copyWith

参数区（删除 5 个死字段参数，新增 9 个）：
```dart
    int? hfIslandTimeoutPre,
    int? hfIslandTimeoutActive,
    int? hfIslandTimeoutPost,
    bool? hfIconAEnabled,
    String? hfStatusTextColor,
    bool? hfOutEffectStatusEnabled,
    String? hfOutEffectStatusColor,
    bool? hfOutEffectExpandEnabled,
    String? hfOutEffectExpandColor,
```
赋值区：
```dart
      hfIslandTimeoutPre: hfIslandTimeoutPre ?? this.hfIslandTimeoutPre,
      hfIslandTimeoutActive: hfIslandTimeoutActive ?? this.hfIslandTimeoutActive,
      hfIslandTimeoutPost: hfIslandTimeoutPost ?? this.hfIslandTimeoutPost,
      hfIconAEnabled: hfIconAEnabled ?? this.hfIconAEnabled,
      hfStatusTextColor: hfStatusTextColor ?? this.hfStatusTextColor,
      hfOutEffectStatusEnabled: hfOutEffectStatusEnabled ?? this.hfOutEffectStatusEnabled,
      hfOutEffectStatusColor: hfOutEffectStatusColor ?? this.hfOutEffectStatusColor,
      hfOutEffectExpandEnabled: hfOutEffectExpandEnabled ?? this.hfOutEffectExpandEnabled,
      hfOutEffectExpandColor: hfOutEffectExpandColor ?? this.hfOutEffectExpandColor,
```

## Step 7: 搜索死字段残留引用

Run: `rg "hfShowCourseName|hfShowLocation|hfShowCountdown|hfCustomTitle|hfCustomTitleColor" lib/`
Expected: 仅剩 `HyperFocusDisplayScreen`（live_settings_subpages.dart L1406/L1413/L1420）的引用——**Task 5 会删除该页面**，此处暂不处理；如还有其它 lib/ 引用（除该文件），先在本任务修正。

## Step 8: 验证

Run: `flutter analyze`
Expected: 除 `HyperFocusDisplayScreen` 死字段引用外无新增 error（该 3 处 use 属 Task 5 范围，可临时保留）

## Step 9: Commit

```bash
git add lib/models/timetable_settings.dart
git commit -m "feat: extend timetable settings with hyperfocus visual and timeout fields"
```
