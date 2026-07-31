# 设计：超级岛设置页重构（UI 对齐 Live + 参考 xiaoaiisland 选项）

**日期:** 2026-07-31
**状态:** 已批准
**关联:** 小米超级岛（HyperFocus）引擎设置页 `_buildHyperFocusSettings`（timetable_settings_screen.dart L1777-1842）及子页面

## 背景

当前超级岛设置菜单只有 4 项（提醒时机/显示设置/自定义模板/测试），"自定义模板"页（`HyperFocusStageTemplateScreen`）用 ChoiceChip 变量点选，UI 风格与 Live Updates 设置页（Hyperos 组件、分组卡片）不一致；显示设置只有 3 个无效果的开关（`hfShowCourseName` 等死字段）；缺少 xiaoaiisland（LSPosed 超岛模块）丰富的自定义选项（状态栏岛/展开态分页、消失时间、颜色/发光视觉选项）。

用户要求：超级岛设置页 UI 风格对齐 Live Updates 设置页，自定义选项参考 xiaoaiisland（状态栏岛自定义 + 展开态自定义 + 消失时间 + 视觉选项）。

## 决策记录

- 范围：整个超级岛设置页重排；功能项=状态栏岛自定义 + 展开态自定义 + 消失时间（仅岛按阶段）+ 视觉选项（颜色/发光/岛A图标）
- 模板编辑范式：保持"变量点选"（逗号分隔变量列表，`resolveTemplate` 兼容），不改为自由文本模板
- 存储：模板统一到 Flutter `TimetableSettings`（跟 Live 一样随 profile 备份/导入导出），保留 Kotlin prefs 双写供渲染
- 消失时间：仅岛消失时间按阶段配置（替换硬编码 300/600），通知消失时间本期不做
- UI 组件：全部用 Hyperos* 系列（HyperosSubpage/HyperosListGroup/HyperosControlCard/HyperosSwitchTile/HyperosSelectTile/HyperosNumberPickerTile/HyperosHexColorChipGroup/HyperosTabRow）

## 1. 页面结构

超级岛设置菜单（`_buildHyperFocusSettings`）重排为 Hyperos 分组导航（对齐 Live 设置页 + xiaoaiisland 组织）：

```
超级岛设置
└─ 提醒
   └─ 提醒时机          （保留现有 HyperFocusTimingScreen）
└─ 显示自定义
   ├─ 状态栏岛自定义     （新页：岛A/岛B/息屏 × 3 阶段 + 岛视觉）
   ├─ 展开态自定义       （新页：主要标题/次要文本1/2/前置文本1/2/主要小文本1/2 × 3 阶段 + 展开态发光）
   └─ 岛视觉             （复用 Live 岛视觉配置：label 图/Logo/字号/偏移/展开图标）
└─ 消失时间
   └─ 岛消失时间         （新页：按课前/课中/课后配置 islandTimeout）
└─ 工具
   └─ 测试               （保留现有测试页）
```

- **状态栏岛自定义** + **展开态自定义** 取代原"自定义模板"单页（`HyperFocusStageTemplateScreen` 删除或改造）
- **岛视觉** 复用 `LiveDisplaySettingsScreen` 的岛视觉编辑（label 图/Logo/字号/偏移/展开图标，超岛引擎经 smallIcon/largeIcon 已消费，只需加入口）
- 原 4 项菜单中"显示设置"（3 个假开关 `HyperFocusDisplayScreen`）删除，被新的"状态栏岛/展开态/岛视觉"替代

## 2. 模板数据模型

### 模板 key 扩展（21 → 30 key，10 字段 × 3 阶段）

| 分组 | 字段 | 现有关键 | 新增 key |
|---|---|---|---|
| 状态栏岛 | 岛A（左侧文字） | islandA_pre/active/post | — |
| | 岛B（右侧文字） | islandB_pre/active/post | — |
| | 息屏显示 | ticker_pre/active/post | — |
| 展开态 | 主要标题 | baseTitle_pre/active/post | — |
| | 次要文本1 | baseContent_pre/active/post | — |
| | 次要文本2 | baseSubcontent_pre/active/post | — |
| | 前置文本1 | — | hintContent_pre/active/post |
| | 前置文本2 | — | hintSubcontent_pre/active/post |
| | 主要小文本1 | hintTitle_pre/active/post | — |
| | 主要小文本2 | — | hintSubtitle_pre/active/post |

新增 9 个 key：`hintContent_*`（前置文本1）、`hintSubcontent_*`（前置文本2）、`hintSubtitle_*`（主要小文本2）。

### Kotlin 渲染接入（buildHyperFocusBundle 正式 L3238-3312 + 测试 L1243-1327 同步）

| 新模板 key | buildV3 接入位 |
|---|---|
| hintSubtitle（主要小文本2） | `hintInfo.subTitle` |
| hintContent（前置文本1） | `hintInfo.extraTitle` |
| hintSubcontent（前置文本2） | `hintInfo.specialTitle` |

注意 `hintInfo.content` 已被倒计时文本占用（L3262），新字段不得占用 content。

### 模板默认值

`hfDefaultTemplates`（MainActivity.kt L4449-4471）补 9 个默认值（空串即可，或与 xiaoaiisland 默认对齐：前置文本1 默认"即将上课/距离下课/已经下课"等，以不破坏现有渲染为准，默认空串安全）。

## 3. 存储与数据流

- 模板 JSON 迁到 `TimetableSettings.hfTemplatesJson`（现有死字段，启用它）——随 profile 备份/导入导出
- **双写**：Flutter 保存模板时写 `TimetableSettings.hfTemplatesJson` + 经通道 `saveHyperFocusTemplates` 同步 Kotlin prefs `templates_json`（Kotlin 渲染逻辑不动）
- **读取**：Flutter `loadHyperFocusTemplates` 以 TimetableSettings 为准（缺失时从 Kotlin prefs 读并回填）
- **消失时间字段**（TimetableSettings 新增）：`hfIslandTimeoutPre`/`hfIslandTimeoutActive`/`hfIslandTimeoutPost`（秒 int，默认 300/600/600）
- **视觉字段**（TimetableSettings 新增）：
  - `hfIconAEnabled`（bool，默认 true）
  - `hfStatusTextColor`（hex 串，默认 `#FFFFFFFF`）
  - `hfOutEffectStatusEnabled`（bool，默认 true）
  - `hfOutEffectStatusColor`（hex 串，默认 `#FFFFFFFF`）
  - `hfOutEffectExpandEnabled`（bool，默认 true）
  - `hfOutEffectExpandColor`（hex 串，默认 `#FFFFFFFF`）
- **islandConfig 通道扩展**：`_buildData`（miui_live_activities_service.dart L449-540）把上述字段 + 模板传入 `islandConfig`；`LiveUpdateScheduler.buildServiceIntentFromMethodPayload`（L824-911）解析并过 intent；`LiveUpdateService.onStartCommand` 读取（L2337-2361）；`buildHyperFocusBundle` 消费
- **测试发送**：`sendTestFocusNotification` 通道读 Kotlin prefs 模板 + 新配置字段，测试版渲染与正式版一致

## 4. 视觉选项（超岛专属，对齐 xiaoaiisland）

### 状态栏岛视觉（状态栏岛自定义页底部）
| 选项 | 控件 | Kotlin 接入 |
|---|---|---|
| 岛A图标开关 | HyperosSwitchTile | `picInfo.type`（1=图标）条件化 |
| 文本颜色 | HyperosHexColorChipGroup | `colorTitle`/`colorContent`/`colorSubContent` + `IslandTemplate.highlightColor` |
| 发光效果开关 | HyperosSwitchTile | `outEffectSrc` 条件化（开=outer_glow，关=空） |
| 发光自定义颜色 | 开关 + 色板 | `outEffectColor` |

### 展开态视觉（展开态自定义页底部）
| 选项 | 控件 | Kotlin 接入 |
|---|---|---|
| 展开态发光开关 | HyperosSwitchTile | `outEffectSrc` 条件化 |
| 发光自定义颜色 | 开关 + 色板 | `outEffectColor` |

## 5. 消失时间（仅岛，按阶段）

新页"岛消失时间"：

| 阶段 | 控件 | 字段 | 默认 |
|---|---|---|---|
| 课前 | HyperosNumberPickerTile | hfIslandTimeoutPre | 300 |
| 课中 | HyperosNumberPickerTile | hfIslandTimeoutActive | 600 |
| 课后 | HyperosNumberPickerTile | hfIslandTimeoutPost | 600 |

Kotlin：`buildHyperFocusBundle` 的 `islandTimeout`（L3279 硬编码 `if (stageKey == "pre") 300 else 600`）改为读配置，按阶段映射取值；测试版（L1286）同步。

## 6. 错误处理

- 视觉字段非法 hex → 色板仅接受有效值，Kotlin `parseColorHexOrDefault`（L3163-3174）兜底
- islandTimeout 非法（≤0 或超大）→ UI 限界（30~3600s），Kotlin 读默认值兜底
- 模板存储双写失败 → 单边写入不阻塞 UI（通道调用已有 try/catch），下次保存自愈

## 7. 迁移

- 首次运行：`hfTemplatesJson` 为空且 Kotlin prefs 有存量模板 → 读取并写入 `hfTemplatesJson`（在 settings 加载时检测，一次性迁移）
- 存量 ChoiceChip 格式（逗号分隔变量列表）不变，`resolveTemplate` 兼容
- 死字段清理：`hfShowCourseName`/`hfShowLocation`/`hfShowCountdown`/`hfCustomTitle`/`hfCustomTitleColor` 删除（无消费），`hfTemplatesJson` 启用

## 8. 测试与验证

- 现有 2 个 HFT widget 测试保持绿
- 新增/更新 widget 测试：状态栏岛自定义页、展开态自定义页、岛消失时间页渲染 + 保存调用
- Kotlin 编译验证 `gradlew assembleDebug`
- 真机验证：模板/视觉/消失时间生效
- `flutter analyze`（8 基线）+ `flutter test`（+716 ~3）

## 9. 范围外（本次不做）

- 课前提醒分钟数、上课免打扰、自动叫醒、假期/调休
- 通知消失时间（仅岛）
- 导入/导出配置、全局恢复默认
- 点击跳转、上课静音快捷按钮
