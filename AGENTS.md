# AGENTS.md

qíngyǔ (qingyu) — Flutter 课表/校园生活 app，小米澎湃超级岛（动态岛）深度集成，含实时上课状态、通知与前台服务渲染。Android 原生层为 Kotlin，UI 层为 Flutter。

## 常用命令

```bash
# Dart 分析 / 测试（根目录）
flutter analyze
flutter test

# Android Kotlin 单测（gradlew 在 android/ 下，不在仓库根；build 目录在仓库根 ../build，勿在 android/ 内找产物）
android/gradlew -p android :app:testProdDebugUnitTest -x compileFlutterBuildProdDebug

# 构建（flavor: dev / prod；两个 flavor 是不同 applicationId，可并存安装）
android/gradlew -p android :app:assembleProdDebug     # 正式包 debug（com.mutx163.qingyu）
android/gradlew -p android :app:assembleDevDebug      # 开发包 debug（com.mutx163.qingyu.debug）
android/gradlew -p android :app:assembleProdRelease   # 正式包 release
# APK 产物：build/app/outputs/flutter-apk/app-{dev,prod}-{debug,release}.apk
```

- dev 与 prod 是**两个独立应用**（不同 applicationId），系统通知、SharedPreferences 互不共享——设备上可同时存在，测试用 dev 包、正式服务可能仍在 prod 包跑（排查"两个岛"问题先分清包）
- `flutter analyze` 现有 17 个 `prefer_initializing_formals` info 提示，均为历史遗留，与新增代码无关，勿顺手"修复"造成大 diff

## 架构总览

数据流：Flutter 课表 → `TimetableProvider` → `LiveActivityController`（选中/当前课程）→ `MiuiLiveActivitiesService.startLiveUpdate`（方法通道）→ `LiveUpdateService`（前台服务）→ 每阶段构建通知 + `XiaomiSuperIslandNotificationRenderer`（focus.param）→ 通知 id=2001。

测试流：设置页「超级岛设置」测试按钮 → `sendTestFocusNotification`（方法通道）→ `MainActivity.sendTestFocusNotificationInner` → 通知 id=10001。

### 状态机与阶段（重要）

scheduler Wire 阶段值 ↔ 模板 key：

| Wire 值 | 模板 key | 含义 |
|---|---|---|
| `beforeClass` | `pre` | 课前窗口（默认提前 10 分钟） |
| `duringClass` | `active` | 课中 |
| `duringClassStatusBar` | `active` | 课中（仅状态栏，快速操作/勿扰后） |
| `beforeEnd` | `active` | 临下课（提前 N 分钟） |
| `afterClass` | `post` | 课后窗口 |

映射函数：`hyperFocusTemplateStage`（Kotlin）、`settings_hyper_focus.dart` 的 stage switch（注意 nextTriggerStage 是 **Wire 值**，不是 pre/active/post——映射写错会导致测试通知永远发 pre 模板）。

## 小米超级岛（Xiaomi Super Island）

### 引擎与模板规范
- 引擎：`com.xzakota.hyper.notification:focus-api:1.4`（第三方逆向库，`FocusNotification.buildV3` 生成模板 JSON 作为通知 extras 的 `miui.focus.param` 交给 HyperOS）
- 模板规范：`https://github.com/1812z/HyperIsland`「小米超级岛通知模板库」——**必读约束**：
  - `sameWidthDigitInfo` 是**等宽数字组件**：只接受数字内容或 `timerInfo`；塞非数字文字 → HyperOS 抛 `IslandParamsException: digit is empty`，展开态 view=null（岛空白）。文字必须用 `imageTextInfoRight` / `textInfo`
  - 官方结构 `{"digit":"06:23","content":"开场","timerInfo":{...}}`：digit=数字本体、content=单位小字、timerInfo=系统倒计时
  - `combinePicInfo`（小岛：图标+环形进度）、`imageTextInfoLeft`（A区：可挂 `progressInfo` 进度环）、B 区组件一次只能一个
- 渲染端要点：数字（倒计时）用 `sameWidthDigitInfo` + timerInfo；非数字文字一律 `imageTextInfoRight`；`progressInfo.progress` 0-100；`colorReach` 用 `settings.outEffectColor`（ARGB 格式如 `#FFFFFFFF`）

### 正式渲染路径（LiveUpdateService + renderer）
- `LiveUpdateService`（1684 行）：前台服务、`onStartCommand` 解析 payload → `startTicker` 每分钟 tick 重发通知（id=2001，`live_update_channel`，同 ID 更新）
- `resolveStage(now)`：按 `startAtMillis/endAtMillis` + 阶段窗口判定当前阶段
- `buildDuringClassProgress`：计算 `progressPercent`（整课进度）、`progressBreakOffsetsMillis`（小节断点）、`nextMilestoneAtMillis`（倒计时目标 = 下一小节下课点，无断点=整课结束）
- `XiaomiSuperIslandNotificationRenderer.render`：`buildHyperFocusBundle`（V3 模板）+ `buildMiuiFocusParam`（legacy 格式）+ 标签 bitmap（`buildLabelBitmap`，canvas 绘制）
- 大课拆小节：`progressBreakOffsetsMillis` 含 breakStart/breakEnd 两断点（45/55 分钟），`nextMilestoneAtMillis = startAt + 断点`（取第一个 > now 的）
- 通知本体 `AndroidLiveUpdateNotificationRenderer`：**hyperFocusApi 引擎不设 CATEGORY_PROGRESS/ProgressStyle**（否则 HyperOS 把通知当"live updates"实时活动渲染，与超级岛并存两条）

### 测试渲染路径（MainActivity）
- `sendTestFocusNotificationInner(args)`：stage 参数（Flutter 传，来自 nextTriggerStage Wire 映射）→ `templateStage`（pre/active/post）→ 按阶段模拟 classStartAt/classEndAt/timerTarget
- `nextMilestoneAtMillis`：由 Flutter 传入 `progressBreakOffsetsMillis`（CSV）计算（realStart + 偏移，过滤 > now，minOrNull）——与正式路径同断点
- 渲染代码在 MainActivity 内**与 renderer 保持同逻辑**（同步纪律，见下）

### 模板字段（hfTemplatesJson）
每个阶段一组（pre/active/post），key 后缀 `_stage`：
- `ticker`/`aodTitle` — 通知栏/紧凑文案
- `baseTitle`/`baseContent`/`baseSubcontent` — 通知栏主体（baseContent 用 `开始,结束` 变量）
- `hintTitle`/`hintContent`/`hintSubcontent`/`hintSubtitle` — hintInfo 区（hintTitle=倒计时变量）
- `islandA` — 大岛 A 区（左侧）文字
- `islandB` — 大岛 B 区（右侧）文字

变量（`resolveTemplate` 支持）：`{课名}` `{短课名}` `{教室}` `{教师}` `{开始}` `{结束}` `{倒计时}` `{正计时}`。islandB 含"倒计时"字面量或 showCountdown 开启 → 渲染倒计时数字。

### 关键文件
- `android/.../qingyu/XiaomiSuperIslandNotificationRenderer.kt` — 正式超级岛渲染（V3、进度环、combinePicInfo、标签 bitmap、isXiaomiFamilyDevice 判定）
- `android/.../qingyu/AndroidLiveUpdateNotificationRenderer.kt` — 通知本体（useProgressStyle 开关）
- `android/.../qingyu/LiveUpdateService.kt` — 前台服务、ticker、resolveStage、debug 状态（`buildHyperFocusDebugStatus`：scheduling/hyperFocus/environment 三块）
- `android/.../qingyu/LiveUpdateScheduler.kt` — 调度（`findNextSelection`/`buildNextTriggerDebugInfo`）、`NativeLiveSettings`（含 hf* 字段，跨阶段持久化）、`parseSnapshot`/`selectionToPayload`
- `android/.../qingyu/MainActivity.kt` — 方法通道（测试通知、UI 通道）、测试路径渲染
- `android/.../qingyu/HyperFocusTemplates.kt` — `loadHyperFocusTemplates`（Kotlin prefs 兜底）、`resolveTemplate`、`formatCountdownForTemplate`/`formatElapsedForTemplate`
- `lib/services/miui_live_activities_service.dart` — Flutter↔Kotlin 桥（startLiveUpdate/_buildData/sendTestFocusNotification + 测试 double）
- `lib/screens/settings/settings_hyper_focus.dart` — 超级岛设置页 + 测试按钮（stage 映射、断点传参）
- `lib/screens/live_settings_subpages.dart` — 「状态栏岛自定义」模板页（`_defaultTemplates` 默认值，`_HyperFocusStatusIslandScreenState`）
- `lib/providers/timetable/live_activity_logic.dart` — `buildLiveProgressBreakOffsetsMillis`
- `lib/providers/timetable/live_activity_controller.dart` — startLiveUpdate 调用、selection 状态（currentStartAt/currentEndAt）

### 同步纪律（重要）
- 正式渲染路径与测试路径（MainActivity）**必须保持同逻辑**——改动 renderer 后同步改测试路径，反之亦然（历史教训：测试路径缺 hf* 传参、stage 映射错误、倒计时分支不一致都造成过"测试和正式不一样"）
- `hfTemplatesJson` 保存在 FlutterSharedPreferences，**用户设备可能已写死默认值**——改 `_defaultTemplates` 只影响新值，旧设备靠渲染端兜底（如 showCountdown 逻辑、digit/文字分流）
- 改模板渲染逻辑后必须验证：数字路径（timerInfo）、文字路径（imageTextInfoRight）、post 阶段（无倒计时）

## 测试

Kotlin 单测（`android/app/src/test/kotlin/com/mutx163/qingyu/`）：
- `XiaomiSuperIslandNotificationRendererTest.kt`、`AndroidLiveUpdateNotificationRendererTest.kt` — 渲染断言（focus.param/通知字段）
- `LiveUpdateServiceLogicTest.kt`、`LiveUpdateSchedulerLogicTest.kt` — 状态机/调度/断点
- `LiveUpdateNotificationStateTest.kt`、`FairMemoryAdapterLogicTest.kt`、`WidgetHolidayLogicTest.kt`

Dart 测试（`test/`）：providers/services/screens 逻辑（当前 1130 个）。跑 `flutter test` 前可先 `flutter analyze`。

涉及 renderer 的改动必须：Kotlin 单测全过 + 相关 Dart 测试 + `flutter analyze` 无新增问题，再提交。

## 真机调试（adb + Xiaomi 16 / HyperOS）

```bash
adb shell dumpsys notification --noredact   # 完整 focus.param JSON（通知存在时）
# 解析 focus.param：python3 从 dumpsys 输出提取 json.loads，看 param_v2.hintInfo / param_island
adb shell "run-as com.mutx163.qingyu cat /data/data/com.mutx163.qingyu/shared_prefs/FlutterSharedPreferences.xml"
adb shell "run-as com.mutx163.qingyu.debug cat /data/data/com.mutx163.qingyu.debug/shared_prefs/FlutterSharedPreferences.xml"   # dev 包
adb shell "logcat -d -s flutter:* | grep MiuiLive"   # Flutter 侧测试日志（阶段/结果）
adb shell "logcat -d | grep -E 'IslandParamsException|digit is empty'"   # HyperOS 岛渲染崩溃
```

### 动态岛内部状态日志（SystemUI 3908 进程）
- `DynamicIslandEventCoordinator` — 岛数据（tickerData 完整 JSON、小岛态/大岛态、双岛 index、`current:dual`）
- `DynamicIslandWindowViewController` — `notif visible false`（通知被拒显示）、动画动作
- `FocusNotifPreHandler` — 预处理（`preHandleFocusNotification 0|pkg|id`）
- `FocusPlugin` — `isSameModule: true/false`（同 module focus 互斥）、`canShowFocus`、`handleTimeout`
- `IslandTemplateFactory` — base64 岛模板、`showView`
- `EmptyNotif`/`ModuleViewHolder` — 视图构建（focus_front_content 等）

### 常见症状 → 诊断
| 症状 | 排查 |
|---|---|
| 展开态空白 | logcat 搜 `digit is empty`（islandB 文字进了 sameWidthDigitInfo） |
| 通知栏有"live updates"样式 | 2001 是否带 ProgressStyle（hyperFocusApi 不应带） |
| 测试通知不显示 | `isSameModule: true`（同包 focus 被 2001 占用）；暂停服务再测 |
| 两个岛并存 | Xiaomi 16 多岛（`current:dual`）；dev/prod 双包各发一条 |
| 测试内容与正式不符 | 检查 stage 映射、hf* 传参、MainActivity 与 renderer 同步 |
| 倒计时目标不对 | 看 `timerWhen`（应为下一小节下课点/整课结束）与 `timerSystemCurrent` |

### 注意
- 动态岛同一时刻可能被其他 app（QQ/微信/mishare）占用，验证前先清理
- 识图不可用（vision 429）时用 `DynamicIslandEventCoordinator` 的 tickerData JSON 验证岛上内容
- `miui.focus.param` 关键词：`picInfo.type=1` 图标、`outEffectSrc=outer_glow` 外发光、`timerWhen` 倒计时目标、`islandTimeout` 岛时长、`param_island.bigIslandArea` 展开态、`smallIslandArea.combinePicInfo` 小岛

## 提交规范
- 分支：`codex/live-update-notification-renderers`（超级岛相关）
- 提交信息：英文、`fix:`/`feat:`/`chore:` 前缀，主体说明动机（参见 git log）
- 涉及 renderer 的改动必须跑 Kotlin 单测 + 相关 Dart 测试后再提交
- 一次提交一个逻辑改动，不夹带无关格式变更（CRLF/LF 警告正常）
