# HyperFocusApi 超级岛引擎 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 mikcb 添加一个可选的小米超级岛引擎（HyperFocusApi），与现有 Live Updates 互斥，通过开关切换。毛坯版仅实现 UI + 测试 Demo。

**Architecture:** Flutter 侧新增 `superIslandEngine` 设置字段 + 引擎选择器 UI + 条件渲染设置项；Kotlin 侧添加 HyperFocusApi 依赖 + MethodChannel handler 发送测试焦点通知。

**Tech Stack:** Flutter, Kotlin, HyperFocusApi (JitPack), Provider

## Global Constraints

- 新增字段必须有 `copyWith`、`toJson`、`fromJson` 完整支持
- 所有 UI 遵循现有 `Hyperos*` 组件风格
- MethodChannel 名称复用已有的 `com.mutx163.qingyu/miui_live`
- Kotlin 代码遵循现有 `when (call.method)` 分支模式
- 引擎切换不影响另一方的配置数据

---

## 文件结构

| # | 文件 | 操作 | 职责 |
|---|------|------|------|
| 1 | `lib/models/timetable_settings.dart` | 修改 | 新增 `SuperIslandEngine` 枚举 + `superIslandEngine` + HyperFocusApi 字段 |
| 2 | `android/build.gradle` | 修改 | 添加 JitPack 仓库 |
| 3 | `android/app/build.gradle` | 修改 | 添加 HyperFocusApi 依赖 |
| 4 | `android/app/src/main/kotlin/.../MainActivity.kt` | 修改 | 新增 `sendTestFocus` handler |
| 5 | `lib/services/miui_live_activities_service.dart` | 修改 | 新增 `sendTestFocusNotification()` |
| 6 | `lib/screens/timetable_settings_screen.dart` | 修改 | `_LiveSettingsScreen` 顶部加引擎选择器 + 条件渲染 |
| 7 | `lib/screens/live_settings_subpages.dart` | 修改 | 新增 HyperFocusApi 专属设置页 + 测试页 |

---

### Task 1: 数据模型 — 新增 SuperIslandEngine 枚举和字段

**Files:**
- Modify: `lib/models/timetable_settings.dart` (lines 135, 1069, 1194, 1480, 1689, 1990, 2170)

**Interfaces:**
- Consumes: 现有 `TimetableSettings` 类的构造/序列化模式
- Produces: `SuperIslandEngine` 枚举 + 6 个新字段（含默认值） + `copyWith` 参数 + `toJson` 序列化 + `fromJson` 反序列化

- [ ] **Step 1: 在 line 135 后添加 SuperIslandEngine 枚举**

```dart
enum SuperIslandEngine { builtIn, hyperFocusApi }

extension SuperIslandEngineX on SuperIslandEngine {
  String get value => name;
  static SuperIslandEngine fromValue(String? value) {
    return SuperIslandEngine.values.firstWhere(
      (e) => e.name == value,
      orElse: () => SuperIslandEngine.builtIn,
    );
  }
}
```

- [ ] **Step 2: 在 line 1069 后添加 HyperFocusApi 设置字段**

```dart
  final SuperIslandEngine superIslandEngine;
  final bool hfEnableBeforeClass;
  final bool hfEnableDuringClass;
  final bool hfEnableBeforeEnd;
  final bool hfShowCourseName;
  final bool hfShowLocation;
  final bool hfShowCountdown;
  final String hfCustomTitle;
  final String hfCustomTitleColor;
```

- [ ] **Step 3: 在 line 1194 构造函数中添加默认值**

```dart
    this.superIslandEngine = SuperIslandEngine.builtIn,
    this.hfEnableBeforeClass = true,
    this.hfEnableDuringClass = true,
    this.hfEnableBeforeEnd = true,
    this.hfShowCourseName = true,
    this.hfShowLocation = true,
    this.hfShowCountdown = true,
    this.hfCustomTitle = '',
    this.hfCustomTitleColor = '#FFFFFF',
```

- [ ] **Step 4: 在 `toJson()` 中添加序列化（line 1480 附近）**

```dart
      'superIslandEngine': superIslandEngine.value,
      'hfEnableBeforeClass': hfEnableBeforeClass,
      'hfEnableDuringClass': hfEnableDuringClass,
      'hfEnableBeforeEnd': hfEnableBeforeEnd,
      'hfShowCourseName': hfShowCourseName,
      'hfShowLocation': hfShowLocation,
      'hfShowCountdown': hfShowCountdown,
      'hfCustomTitle': hfCustomTitle,
      'hfCustomTitleColor': hfCustomTitleColor,
```

- [ ] **Step 5: 在 `fromJson()` 中添加反序列化（line 1689 附近）**

```dart
      superIslandEngine: SuperIslandEngineX.fromValue(
        json['superIslandEngine'] as String?,
      ),
      hfEnableBeforeClass: json['hfEnableBeforeClass'] as bool? ?? true,
      hfEnableDuringClass: json['hfEnableDuringClass'] as bool? ?? true,
      hfEnableBeforeEnd: json['hfEnableBeforeEnd'] as bool? ?? true,
      hfShowCourseName: json['hfShowCourseName'] as bool? ?? true,
      hfShowLocation: json['hfShowLocation'] as bool? ?? true,
      hfShowCountdown: json['hfShowCountdown'] as bool? ?? true,
      hfCustomTitle: json['hfCustomTitle'] as String? ?? '',
      hfCustomTitleColor: json['hfCustomTitleColor'] as String? ?? '#FFFFFF',
```

- [ ] **Step 6: 在 `copyWith()` 中添加参数（line 1990 附近）**

```dart
    SuperIslandEngine? superIslandEngine,
    bool? hfEnableBeforeClass,
    bool? hfEnableDuringClass,
    bool? hfEnableBeforeEnd,
    bool? hfShowCourseName,
    bool? hfShowLocation,
    bool? hfShowCountdown,
    String? hfCustomTitle,
    String? hfCustomTitleColor,
```

 然后在方法体（line 2170 附近）添加：

```dart
      superIslandEngine: superIslandEngine ?? this.superIslandEngine,
      hfEnableBeforeClass: hfEnableBeforeClass ?? this.hfEnableBeforeClass,
      hfEnableDuringClass: hfEnableDuringClass ?? this.hfEnableDuringClass,
      hfEnableBeforeEnd: hfEnableBeforeEnd ?? this.hfEnableBeforeEnd,
      hfShowCourseName: hfShowCourseName ?? this.hfShowCourseName,
      hfShowLocation: hfShowLocation ?? this.hfShowLocation,
      hfShowCountdown: hfShowCountdown ?? this.hfShowCountdown,
      hfCustomTitle: hfCustomTitle ?? this.hfCustomTitle,
      hfCustomTitleColor: hfCustomTitleColor ?? this.hfCustomTitleColor,
```

- [ ] **Step 7: Build 验证**

Run: `cd lib && dart analyze models/timetable_settings.dart`
Expected: No errors

- [ ] **Step 8: Commit**

```bash
git add lib/models/timetable_settings.dart
git commit -m "feat: add SuperIslandEngine enum and HyperFocusApi settings fields"
```

---

### Task 2: Kotlin 依赖 — 添加 JitPack 和 HyperFocusApi

**Files:**
- Modify: `android/build.gradle` (line 10)
- Modify: `android/app/build.gradle` (line 142)

- [ ] **Step 1: 在 `android/build.gradle` 的 `allprojects.repositories` 中添加 JitPack**

```groovy
        maven { url 'https://jitpack.io' }
```

插入到 `maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }` 之后。

- [ ] **Step 2: 在 `android/app/build.gradle` 的 `dependencies` 中添加**

```groovy
    implementation 'com.github.ghhccghk:HyperFocusApi:2.0'
```

- [ ] **Step 3: 验证 Gradle 同步**

Run: `cd android && gradlew app:dependencies --configuration implementationClasspath`
Expected: 无错误，能看到 HyperFocusApi 依赖

- [ ] **Step 4: Commit**

```bash
git add android/build.gradle android/app/build.gradle
git commit -m "build: add JitPack repository and HyperFocusApi dependency"
```

---

### Task 3: Kotlin 侧 — MethodChannel 添加 sendTestFocus handler

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`

- [ ] **Step 1: 在 `MainActivity.kt` 顶部添加导入（import 区域）**

```kotlin
import com.hyperfocus.api.FocusApi
import android.graphics.drawable.Icon
```

- [ ] **Step 2: 在 `when (call.method)` 分支中添加新 case**

在 `"startLiveUpdate"` 分支之后（约 line 359）、`else` 分支之前添加：

```kotlin
                    "sendTestFocus" -> {
                        sendTestFocusNotification()
                        result.success(true)
                    }
```

- [ ] **Step 3: 在类中添加 `sendTestFocusNotification()` 方法**

在类中任意合适位置（如 `openNotificationSettings()` 方法附近）添加：

```kotlin
    private fun sendTestFocusNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "hyperfocus_test_channel"
            val channel = NotificationChannel(
                channelId,
                "HyperFocusApi Test",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)

            val sendNotification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("测试课程")
                .setContentText("高等数学")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setAutoCancel(false)

            val intent = Intent()
            intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            intent.data = Uri.fromParts("package", packageName, null)

            val baseInfo = FocusApi.baseinfo(
                title = "测试课程",
                colorTitle = "#FFFFFF",
                basetype = 1,
                content = "高等数学",
                colorContent = "#FFFFFF",
                subContent = "教科A-101",
                colorSubContent = "#CCCCCC",
                extraTitle = "",
                colorExtraTitle = "#FFFFFF",
                subTitle = "08:00 - 09:40",
                colorsubTitle = "#AAAAAA",
                specialTitle = "即将上课",
                colorSpecialTitle = "#FFFFFF",
            )

            val hintInfo = FocusApi.hintInfo(
                type = 1,
                titleLineCount = 2,
                title = "高等数学",
                colortitle = "#FFFFFF",
                content = "距离上课还有 5 分钟",
                colorContent = "#AAAAAA",
                actionInfo = FocusApi.actionInfo(
                    actionsIntent = intent.toUri(Intent.URI_INTENT_SCHEME),
                    actionsTitle = "查看课表",
                ),
            )

            val api = FocusApi.sendFocus(
                title = "测试课程",
                baseInfo = baseInfo,
                hintInfo = hintInfo,
                picbg = Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
                picmarkv2 = Icon.createWithResource(this, android.R.drawable.ic_menu_myplaces),
                picbgtype = 2,
                picmarkv2type = 2,
                builder = sendNotification,
                ticker = "即将上课：高等数学",
                picticker = Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
            )

            sendNotification.addExtras(api)
            notificationManager.notify(10001, sendNotification.build())
        } catch (e: Exception) {
            Log.e("HyperFocusApi", "sendTestFocus failed", e)
        }
    }
```

- [ ] **Step 4: Build 验证**

Run: `cd android && gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: add sendTestFocus notification via HyperFocusApi"
```

---

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

### Task 5: UI — Live Settings 添加引擎选择器 + 条件渲染

**Files:**
- Modify: `lib/screens/timetable_settings_screen.dart` (`_LiveSettingsScreen`)

- [ ] **Step 1: 在 `_LiveSettingsScreenState` 中添加引擎选择方法**

```dart
  void _onEngineChanged(SuperIslandEngine engine) {
    final next = _draft.copyWith(superIslandEngine: engine);
    context.read<TimetableProvider>().updateTimetableSettings(next);
    setState(() => _draft = next);
  }
```

- [ ] **Step 2: 修改 `_buildLiveSettingsSection` 方法，顶部添加引擎选择卡片**

将 `_buildLiveSettingsSection` 改为先渲染引擎选择器，再根据引擎渲染不同内容：

```dart
  Widget _buildLiveSettingsSection(BuildContext context, int index) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        _buildEngineSelector(context, l10n),
        const HyperosSectionGap(),
        if (_draft.superIslandEngine == SuperIslandEngine.builtIn)
          _buildLiveUpdatesSettings(context, l10n)
        else
          _buildHyperFocusSettings(context, l10n),
      ],
    );
  }

  Widget _buildEngineSelector(BuildContext context, AppLocalizations l10n) {
    return HyperosSettingsBlock(
      title: '超级岛引擎',
      child: HyperosListGroup(
        children: [
          HyperosRadioTile<SuperIslandEngine>(
            title: 'Live Updates（内置）',
            subtitle: '当前默认引擎，支持三阶段提醒、自定义标签等全部功能',
            value: SuperIslandEngine.builtIn,
            groupValue: _draft.superIslandEngine,
            onChanged: (v) => _onEngineChanged(v),
          ),
          HyperosRadioTile<SuperIslandEngine>(
            title: '小米超级岛（HyperFocusApi）',
            subtitle: '基于 HyperFocusApi 的焦点通知方案，与 Live Updates 互斥',
            value: SuperIslandEngine.hyperFocusApi,
            groupValue: _draft.superIslandEngine,
            onChanged: (v) => _onEngineChanged(v),
          ),
        ],
      ),
    );
  }
```

- [ ] **Step 3: 提取现有设置到 `_buildLiveUpdatesSettings` 方法**

```dart
  Widget _buildLiveUpdatesSettings(BuildContext context, AppLocalizations l10n) {
    final beforeClassSummary = _liveDisplaySummary(
      context,
      _draft.beforeClassDisplaySettings,
    );
    final duringEndSummary = _draft.liveDuringEndFollowBeforeClass
        ? l10n.followBeforeClassSetting
        : _liveDisplaySummary(context, _draft.duringEndDisplaySettings);
    return HyperosListGroup(
      children: [
        // ... 现有全部 HyperosListTile（提醒时机、课前显示、课中/课后显示、保活、测试）
        // 原样从 _buildLiveSettingsSection 移过来
      ],
    );
  }
```

- [ ] **Step 4: 添加 `_buildHyperFocusSettings` 方法**

```dart
  Widget _buildHyperFocusSettings(BuildContext context, AppLocalizations l10n) {
    return HyperosListGroup(
      children: [
        HyperosListTile(
          icon: Icons.alarm_outlined,
          title: '提醒时机',
          details: '${_draft.hfEnableBeforeClass ? "课前 " : ""}${_draft.hfEnableDuringClass ? "课中 " : ""}${_draft.hfEnableBeforeEnd ? "课后" : ""}',
          onTap: () async {
            await HyperosNavigation.push(
              context,
              builder: (_) => const HyperFocusTimingScreen(),
            );
            if (!mounted) return;
            setState(() {
              _draft = context.read<TimetableProvider>().settings;
            });
          },
        ),
        HyperosListTile(
          icon: Icons.upcoming_outlined,
          title: '显示设置',
          details: '课名: ${_draft.hfShowCourseName ? "开" : "关"} 地点: ${_draft.hfShowLocation ? "开" : "关"} 倒计时: ${_draft.hfShowCountdown ? "开" : "关"}',
          onTap: () async {
            await HyperosNavigation.push(
              context,
              builder: (_) => const HyperFocusDisplayScreen(),
            );
            if (!mounted) return;
            setState(() {
              _draft = context.read<TimetableProvider>().settings;
            });
          },
        ),
        HyperosListTile(
          icon: Icons.dashboard_customize_outlined,
          title: '超级岛样式',
          details: '（占位）',
          // 占位，无 onTap
        ),
        HyperosListTile(
          icon: Icons.science_outlined,
          title: '测试',
          onTap: () async {
            await HyperosNavigation.push(
              context,
              builder: (_) => const HyperFocusTestScreen(),
            );
            if (!mounted) return;
            setState(() {
              _draft = context.read<TimetableProvider>().settings;
            });
          },
        ),
      ],
    );
  }
```

- [ ] **Step 5: 验证**

Run: `dart analyze lib/screens/timetable_settings_screen.dart`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add lib/screens/timetable_settings_screen.dart
git commit -m "feat: add engine selector and conditional settings rendering in Live Settings"
```

---

### Task 6: UI — HyperFocusApi 专属设置页 + 测试页

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart`

- [ ] **Step 1: 在文件末尾添加 `HyperFocusTimingScreen`**

```dart
class HyperFocusTimingScreen extends StatefulWidget {
  const HyperFocusTimingScreen({super.key});

  @override
  State<HyperFocusTimingScreen> createState() => _HyperFocusTimingScreenState();
}

class _HyperFocusTimingScreenState extends State<HyperFocusTimingScreen> {
  late TimetableSettings _draft;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  void _updateDraft(TimetableSettings next) {
    final provider = context.read<TimetableProvider>();
    provider.updateTimetableSettings(next);
    setState(() => _draft = next);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('提醒时机'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => HyperosListGroup(
          children: [
            HyperosSwitchTile(
              title: '课前提醒',
              value: _draft.hfEnableBeforeClass,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableBeforeClass: v),
              ),
            ),
            HyperosSwitchTile(
              title: '课中提醒',
              value: _draft.hfEnableDuringClass,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableDuringClass: v),
              ),
            ),
            HyperosSwitchTile(
              title: '课后提醒',
              value: _draft.hfEnableBeforeEnd,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableBeforeEnd: v),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 添加 `HyperFocusDisplayScreen`**

```dart
class HyperFocusDisplayScreen extends StatefulWidget {
  const HyperFocusDisplayScreen({super.key});

  @override
  State<HyperFocusDisplayScreen> createState() => _HyperFocusDisplayScreenState();
}

class _HyperFocusDisplayScreenState extends State<HyperFocusDisplayScreen> {
  late TimetableSettings _draft;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  void _updateDraft(TimetableSettings next) {
    final provider = context.read<TimetableProvider>();
    provider.updateTimetableSettings(next);
    setState(() => _draft = next);
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('显示设置'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => HyperosListGroup(
          children: [
            HyperosSwitchTile(
              title: '显示课名',
              value: _draft.hfShowCourseName,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowCourseName: v),
              ),
            ),
            HyperosSwitchTile(
              title: '显示地点',
              value: _draft.hfShowLocation,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowLocation: v),
              ),
            ),
            HyperosSwitchTile(
              title: '显示倒计时',
              value: _draft.hfShowCountdown,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowCountdown: v),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 3: 添加 `HyperFocusTestScreen`**

```dart
class HyperFocusTestScreen extends StatefulWidget {
  const HyperFocusTestScreen({super.key});

  @override
  State<HyperFocusTestScreen> createState() => _HyperFocusTestScreenState();
}

class _HyperFocusTestScreenState extends State<HyperFocusTestScreen> {
  final List<String> _logs = [];
  bool _isSending = false;

  void _addLog(String msg) {
    final time = TimeOfDay.now().format(context);
    setState(() => _logs.insert(0, '[$time] $msg'));
  }

  Future<void> _sendTestNotification() async {
    setState(() => _isSending = true);
    _addLog('正在发送测试通知...');
    final service = MiuiLiveActivitiesService();
    try {
      final success = await service.sendTestFocusNotification();
      if (success) {
        _addLog('测试通知已发送 ✓');
      } else {
        _addLog('发送失败 ✗');
      }
    } catch (e) {
      _addLog('发送异常：$e');
    }
    setState(() => _isSending = false);
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('测试'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => Column(
          children: [
            HyperosListGroup(
              children: [
                HyperosListTile(
                  icon: Icons.send_rounded,
                  title: '发送测试通知',
                  subtitle: '发送一条硬编码的焦点通知到超级岛',
                  onTap: _isSending ? null : _sendTestNotification,
                ),
              ],
            ),
            const HyperosSectionGap(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: HyperosSectionLabel('操作日志'),
            ),
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 16),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: _logs.isEmpty
                    ? const Center(
                        child: Text(
                          '暂无日志，点击上方按钮发送测试通知',
                          style: TextStyle(color: Colors.grey, fontSize: 13),
                        ),
                      )
                    : ListView.separated(
                        itemCount: _logs.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, index) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Text(
                            _logs[index],
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 12,
                            ),
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 4: 验证**

Run: `dart analyze lib/screens/live_settings_subpages.dart`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: add HyperFocusApi timing/display/test settings screens"
```

---

### Task 7: 构建验证

- [ ] **Step 1: Dart 静态分析**

Run: `cd lib && dart analyze .`
Expected: No errors

- [ ] **Step 2: 完整 APK 构建（Debug）**

Run: `cd android && gradlew assembleDebug`
Expected: BUILD SUCCESSFUL in `mikcb-ECJTU\build\app\outputs\flutter-apk\`

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: finalize HyperFocusApi engine integration (scaffold)"
```

---

## 验证清单

1. [ ] 打开应用 → 设置 → Live Settings → 看到引擎选择器
2. [ ] 默认选中「Live Updates（内置）」→ 显示完整的现有设置
3. [ ] 切换到「小米超级岛（HyperFocusApi）」→ 隐藏内置设置，显示简化版设置（提醒时机、显示设置、超级岛样式、测试）
4. [ ] 点击「测试」→ 进入测试页
5. [ ] 点击「发送测试通知」→ 手机弹出超级岛通知，显示"测试课程 / 高等数学"
6. [ ] 切回「Live Updates」→ 原有设置完好无损
7. [ ] 杀掉应用重开 → 引擎选择记住上次的选择
