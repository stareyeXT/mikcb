# 小米超级岛（HyperFocusApi）引擎 — 设计文档

## 背景

mikcb 现有的 Live Updates（内置引擎）通过 MIUI 焦点通知实现超级岛效果。现需引入 HyperFocusApi 作为可选引擎，两者互斥，通过开关切换。

## 目标

1. 在 Live Settings 顶部添加引擎选择器（Live Updates / 小米超级岛）
2. 切换后显示对应引擎的定制设置项
3. 实现 HyperFocusApi 引擎的测试 Demo（发送硬编码焦点通知）
4. 毛坯版：UI 完整，功能仅含测试通知

## 架构

```
┌─────────────────────┐
│   Flutter UI         │
│  ┌─────────────────┐ │
│  │ Engine Selector  │ │  ← HyperosChoiceTile (builtIn / hyperFocusApi)
│  └─────────────────┘ │
│  ┌─────────────────┐ │
│  │ Live Updates     │ │  ← 现有设置（引擎=builtIn 时显示）
│  │ Settings         │ │
│  └─────────────────┘ │
│  ┌─────────────────┐ │
│  │ HyperFocusApi    │ │  ← 新设置（引擎=hyperFocusApi 时显示）
│  │ Settings         │ │
│  └─────────────────┘ │
└─────────┬───────────┘
          │ MethodChannel: "sendTestFocus"
          │
┌─────────▼───────────┐
│   Kotlin (Android)   │
│  ┌─────────────────┐ │
│  │ HyperFocusApi    │ │  ← JitPack 依赖
│  │ FocusApi         │ │
│  └─────────────────┘ │
│  ┌─────────────────┐ │
│  │ sendTestFocus()  │ │  ← MainActivity.kt handler
│  └─────────────────┘ │
└─────────────────────┘
```

## 数据模型

### TimetableSettings 新增字段

```dart
enum SuperIslandEngine { builtIn, hyperFocusApi }

class TimetableSettings {
  // ... 现有字段
  final SuperIslandEngine superIslandEngine;

  // HyperFocusApi 专属设置（毛坯版暂为占位）
  final bool hfEnableBeforeClass;
  final bool hfEnableDuringClass;
  final bool hfEnableBeforeEnd;
  final bool hfShowCourseName;
  final bool hfShowLocation;
  final bool hfShowCountdown;
  final String hfCustomTitle;
  final String hfCustomTitleColor;
}
```

## UI 结构

### Live Settings 页（修改 `_LiveSettingsScreen`）

```
┌─────────────────────────────┐
│ 超级岛引擎                   │
│                             │
│ ○ Live Updates（内置） [推荐] │
│ ● 小米超级岛（HyperFocusApi） │
│                             │
│ 切换引擎后下方设置项随之切换   │
└─────────────────────────────┘

--- 引擎 = builtIn 时 ---
  [现有全部设置项]

--- 引擎 = hyperFocusApi 时 ---
  ┌─ 提醒时机 ─────────────────┐
  │ 课前提醒          [开关]   │
  │ 课中提醒          [开关]   │
  │ 课后提醒          [开关]   │
  │ 提前时间          [选择]   │
  └────────────────────────────┘
  ┌─ 显示设置 ─────────────────┐
  │ 显示课名          [开关]   │
  │ 显示地点          [开关]   │
  │ 显示倒计时        [开关]   │
  │ 自定义标题文字    [输入]   │
  │ 标题颜色          [取色]   │
  └────────────────────────────┘
  ┌─ 超级岛样式（占位）──────────┐
  │ 展开态布局       [占位]     │
  │ 小岛图标         [占位]     │
  └────────────────────────────┘
  ┌─ 测试 ─────────────────────┐
  │ 📨 发送测试通知  [按钮]     │
  │ 🧹 清除测试通知  [按钮]     │
  │ [调试日志区域]              │
  └────────────────────────────┘
```

### 测试页面

- 显示引擎状态（就绪/未就绪）
- 焦点权限状态
- 超级岛支持状态
- 「发送测试通知」按钮 → 调用 MethodChannel `sendTestFocus`
- 「清除测试通知」按钮
- 操作日志

## Kotlin 侧实现

### build.gradle 依赖

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.ghhccghk:HyperFocusApi:2.0")
}
```

### MethodChannel handler（MainActivity.kt）

```kotlin
// 新增分支:
"sendTestFocus" -> {
    // 使用 HyperFocusApi.FocusApi.sendFocus() 发送硬编码通知
    // 参数: title="测试课程", content="高等数学", ticker="即将上课"
    // 包含 baseInfo, hintInfo 等
}
```

### 测试通知内容（硬编码）

- 标题：`测试课程`
- 内容：`高等数学`
- 地点：`教科A-101`
- 时间：`08:00 - 09:40`
- 倒计时：距离上课还有 5 分钟

## 文件变更清单

| 文件 | 变更 |
|------|------|
| `lib/models/timetable_settings.dart` | 新增 `SuperIslandEngine` 枚举 + `superIslandEngine` 字段 + HyperFocusApi 设置字段 |
| `lib/screens/timetable_settings_screen.dart` | `_LiveSettingsScreen` 顶部添加引擎选择器，条件渲染设置项 |
| `lib/screens/live_settings_subpages.dart` | 新增 `HyperFocusSettingsScreen` / `HyperFocusTestScreen` |
| `lib/services/miui_live_activities_service.dart` | 新增 `sendTestFocusNotification()` MethodChannel 调用 |
| `android/app/build.gradle` | 添加 JitPack 仓库 + HyperFocusApi 依赖 |
| `android/app/src/main/kotlin/.../MainActivity.kt` | 新增 `sendTestFocus` handler |
| `pubspec.yaml` | 无需改动 |

## 排除范围（毛坯版不实现）

- 实际课程时间同步和阶段切换逻辑
- HyperFocusApi 方案的实际三阶段提醒
- 预览功能
- 课表数据注入到通知

## 测试验证

1. 切换引擎到「小米超级岛」，确认 Live Updates 设置隐藏
2. 切换回「Live Updates」，确认设置恢复
3. 点击「发送测试通知」，确认手机弹出超级岛通知
4. 确认通知内容与硬编码数据一致
