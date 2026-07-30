# Task 1 Report: Kotlin 侧默认值 + resolveTemplate + 迁移 + Dart 默认值

## Files Changed

### 1. `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`

**变更摘要：**

- **`hfDefaultTemplates`** (line 2960): 所有 21 个键的值从模板字符串（如 `"即将上课：{课名}"`、`"{教室}"`、`"{开始} - {结束}"`）更新为逗号分隔的变量名（如 `"课名"`、`"教室"`、`"开始,结束"`）或纯文本（如 `"上课中"`、`"已下课"`）
- **`resolveTemplate()`** (line 3005): 新增向后兼容逻辑：
  - 若值包含 `{` → 走旧格式的 `{变量}` 替换
  - 否则 → 新格式：按逗号分割 → 通过 `variableMap` 映射到实际值 → 过滤空值 → 空格拼接
- **`loadHyperFocusTemplates()`** (line 2984): 新增迁移检测：
  - 遍历已保存 JSON 的所有值，若任一值包含 `{` 则判定为旧格式，直接返回 `hfDefaultTemplates`
  - 否则正常合并保存的值到默认值之上

### 2. `lib/screens/live_settings_subpages.dart`

**变更摘要：**

- **`_defaultTemplates`** (line 1325): 与 Kotlin 侧完全同步，同上 21 个键值全部更新

## 编译检查结果

- **`flutter analyze`**: No issues found
- **`./gradlew assembleDebug`**: BUILD SUCCESSFUL (482 tasks, 51 executed)
  - 无新增 warning，所有 warning 均为已有（deprecated API、unchecked cast 等）

## 自审发现

- 新格式与旧格式的兼容检测通过 `tpl.contains("{")` 来分叉，逻辑直观可靠
- 迁移检测在 `loadHyperFocusTemplates()` 中逐值检查 `{`，检测到任何旧格式即全部丢弃，确保干净迁移
- 值中的逗号分割后 trim + filter empty，避免了空变量名或空值导致的空白问题
- 变量映射中 `短课名` 和 `教室` 在值为空时 fallback 到 `课名`，保持与旧格式的 `ifBlank` 行为一致

## 注意事项

- 用户已存储的旧格式模板将在首次加载时被自动丢弃并替换为新默认值
- Dart 侧的 UI 仍然是 TextEditingController + TextField，Task 2 会将其改为 ChoiceChip 选择器
- Kotlin 侧无新的依赖引入
