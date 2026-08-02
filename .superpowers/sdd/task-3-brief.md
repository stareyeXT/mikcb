### Task 3: 更新入口摘要并移除 hfEnable* 死字段

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart:1783`（入口 tile details）
- Modify: `lib/models/timetable_settings.dart`（字段声明 1083-1085、构造默认 1243-1245、toJson 1557-1559、fromJson 1882-1884、copyWith 参数 2102-2104 与实现 2363-2365）

**Interfaces:**
- Consumes: Task 2 后页面已无 `hfEnable*` 引用；本 Task 移除字段后，代码库中不得再有任何 `hfEnable*` 引用。
- Produces: 干净的 `TimetableSettings` 模型（无 `hfEnable*`）；入口 tile 显示 live 状态摘要。

- [ ] **Step 1: 更新入口 tile 摘要**

把 `lib/screens/timetable_settings_screen.dart:1783` 的：

```dart
          details: '${_draft.hfEnableBeforeClass ? "课前 " : ""}${_draft.hfEnableDuringClass ? "课中 " : ""}${_draft.hfEnableBeforeEnd ? "课后" : ""}',
```

替换为：

```dart
          details: '课前: ${_draft.liveEnableBeforeClass ? "开" : "关"} 课中: ${_draft.liveEnableDuringClass || _draft.liveEnableBeforeEnd ? "开" : "关"}',
```

- [ ] **Step 2: 移除模型字段**

在 `lib/models/timetable_settings.dart` 中删除以下 6 处（每处一行/一段，精确匹配）：

1. 字段声明（约 1083-1085 行）：
```dart
  final bool hfEnableBeforeClass;
  final bool hfEnableDuringClass;
  final bool hfEnableBeforeEnd;
```
2. 构造函数默认值（约 1243-1245 行）：
```dart
    this.hfEnableBeforeClass = true,
    this.hfEnableDuringClass = true,
    this.hfEnableBeforeEnd = true,
```
3. toJson（约 1557-1559 行）：
```dart
      'hfEnableBeforeClass': hfEnableBeforeClass,
      'hfEnableDuringClass': hfEnableDuringClass,
      'hfEnableBeforeEnd': hfEnableBeforeEnd,
```
4. fromJson（约 1882-1884 行）：
```dart
      hfEnableBeforeClass: json['hfEnableBeforeClass'] as bool? ?? true,
      hfEnableDuringClass: json['hfEnableDuringClass'] as bool? ?? true,
      hfEnableBeforeEnd: json['hfEnableBeforeEnd'] as bool? ?? true,
```
5. copyWith 参数（约 2102-2104 行）：
```dart
    bool? hfEnableBeforeClass,
    bool? hfEnableDuringClass,
    bool? hfEnableBeforeEnd,
```
6. copyWith 实现（约 2363-2365 行）：
```dart
      hfEnableBeforeClass: hfEnableBeforeClass ?? this.hfEnableBeforeClass,
      hfEnableDuringClass: hfEnableDuringClass ?? this.hfEnableDuringClass,
      hfEnableBeforeEnd: hfEnableBeforeEnd ?? this.hfEnableBeforeEnd,
```

- [ ] **Step 3: 确认无残留引用**

Run: `rg -n "hfEnable" lib test`
Expected: 无输出。

- [ ] **Step 4: 跑测试确认无回归**

Run: `flutter test test\widgets\hyperfocus_timing_screen_test.dart && flutter test test\widgets\timetable_settings_screen_test.dart`
Expected: 全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add lib/screens/timetable_settings_screen.dart lib/models/timetable_settings.dart
git commit -m "refactor: remove dead hfEnable* settings fields"
```

---

