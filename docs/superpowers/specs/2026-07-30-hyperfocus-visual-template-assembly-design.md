# HyperFocus 可视化变量组装器设计 Spec

## 背景

当前自定义模板界面暴露裸模板语法（如 `{课名}` `{教室}`），普通用户难以理解。需要改为可视化方式：用户只需选择每字段显示哪些变量，无需接触模板语法。

## 设计

### 核心变更

去掉模板字符串（如 `"即将上课：{课名}"`），每字段改为从 8 个变量中选择要显示的变量及顺序。引擎按选中顺序空格拼接。

### 数据模型

每字段存储为逗号分隔的变量名列表（String->String，只是值格式变了）。

**hfDefaultTemplates 新默认值：**

| 字段 | 新默认值 |
|------|---------|
| ticker_before | `"课名"` |
| islandA_before | `"教室"` |
| islandB_before | `""` |
| baseTitle_before | `"课名"` |
| baseContent_before | `"开始,结束"` |
| baseSubcontent_before | `"教室"` |
| hintTitle_before | `""` |
| ticker_during | `"课名"` |
| islandA_during | `"短课名"` |
| islandB_during | `"上课中"` |
| baseTitle_during | `"课名"` |
| baseContent_during | `"开始,结束"` |
| baseSubcontent_during | `"教室"` |
| hintTitle_during | `""` |
| ticker_after | `"课名"` |
| islandA_after | `"短课名"` |
| islandB_after | `"已下课"` |
| baseTitle_after | `"课名"` |
| baseContent_after | `"开始,结束"` |
| baseSubcontent_after | `"教室"` |
| hintTitle_after | `""` |

注意：`islandB_during` 原为 `"上课"` 改为 `"上课中"`，`islandB_after` 原为 `"已下课"`，其他去掉了自定义文字前缀。

### 模板引擎

`resolveTemplate()` 改为：
1. 读取字段值 = 逗号分隔变量名列表
2. 分割为 List<String>
3. 映射每个变量名到实际值（课名、短课名、教室、教师、开始时间、结束时间、倒计时、正计时）
4. 用空格 join 过滤空值

### UI

每个字段不再显示 `HyperosTextField`，改为一行 8 个可选芯片（ChoiceChip 或自定义 Chip）。

布局：
- 字段标签（如 "状态栏/息屏文本"）
- 下一行：8 个紧凑 Chip 水平排列，Wrap 换行
- 选中态：实心底色，未选中态：描边

交互：
- Tap 切换选中/未选中
- 多选（每个字段可勾选 0~8 个变量）
- 按从左到右顺序拼接

#### Widget 结构

```
HyperFocusStageTemplateScreen
├── HyperosTabRow (课前/课中/课后)
├── Expanded > SingleChildScrollView
│   ├── 提示文字 "选择要显示的信息："
│   ├── HyperosSectionLabel("状态栏岛")
│   │   └── _variableChipField("ticker", "状态栏/息屏文本")
│   ├── HyperosSectionLabel("岛内容")
│   │   ├── _variableChipField("islandA", "岛左侧文字")
│   │   └── _variableChipField("islandB", "岛右侧后缀")
│   ├── HyperosSectionLabel("展开态")
│   │   ├── _variableChipField("baseTitle", "标题")
│   │   ├── _variableChipField("baseContent", "内容")
│   │   └── _variableChipField("baseSubcontent", "副内容")
│   ├── HyperosSectionLabel("阶段标签")
│   │   └── _variableChipField("hintTitle", "阶段标签文字")
│   └── Row(保存 + 恢复默认按钮)
```

`_variableChipField(key, label)` 方法：
- 读取 `_controllers[key]`（存逗号分隔字符串）
- 渲染 8 个 Chip，根据当前值判断选中态
- 点击 Chip → 更新 `_controllers[key]` 的逗号分隔字符串

### 迁移策略

在 `loadHyperFocusTemplates()` 中检测值格式：
- 如果值包含 `{` → 旧格式 → 使用新默认值
- 否则 → 新格式，正常解析

## 不做的

- 不支持自定义文字/前后缀
- 不支持拖拽排序（按 Chip 固定顺序）
- 不支持实时预览（本次不做）

## 文件改动

- `android/app/.../MainActivity.kt`：`hfDefaultTemplates` 默认值更新、`resolveTemplate()` 逻辑重写
- `lib/screens/live_settings_subpages.dart`：`HyperFocusStageTemplateScreen` 重写为 Chip 选择器
- `lib/models/timetable_settings.dart`：无需改字段，`hfTemplatesJson` 仍是 String

## 存储示意

SharedPreferences JSON 片段：
```json
{
  "ticker_before": "课名",
  "islandA_before": "教室",
  "baseContent_before": "开始,结束",
  ...
}
```
