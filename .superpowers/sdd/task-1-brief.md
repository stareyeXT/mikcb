# Task 1: Kotlin 侧默认值 + resolveTemplate + 迁移 + Dart 默认值

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
- Modify: `lib/screens/live_settings_subpages.dart`

## Step 1: 更新 hfDefaultTemplates 默认值

将 `hfDefaultTemplates` map 的值从模板字符串改为逗号分隔的变量名列表。

```kotlin
private val hfDefaultTemplates = mapOf(
    "ticker_pre" to "课名",
    "ticker_active" to "课名",
    "ticker_post" to "课名",
    "islandA_pre" to "教室",
    "islandA_active" to "短课名",
    "islandA_post" to "短课名",
    "islandB_pre" to "",
    "islandB_active" to "上课中",
    "islandB_post" to "已下课",
    "baseTitle_pre" to "课名",
    "baseTitle_active" to "课名",
    "baseTitle_post" to "课名",
    "baseContent_pre" to "开始,结束",
    "baseContent_active" to "开始,结束",
    "baseContent_post" to "开始,结束",
    "baseSubcontent_pre" to "教室",
    "baseSubcontent_active" to "教室",
    "baseSubcontent_post" to "教室",
    "hintTitle_pre" to "",
    "hintTitle_active" to "上课中",
    "hintTitle_post" to "已下课",
)
```

关键变更：
- `ticker_pre`: `"即将上课：{课名}"` → `"课名"`
- `islandA_pre`: `"{教室}"` → `"教室"`
- `islandB_pre`: `"上课"` → `""`
- `baseContent_pre/active/post`: `"{开始} - {结束}"` → `"开始,结束"`
- `hintTitle_pre`: `"即将上课"` → `""`
- `islandA_active/post`: `"{课名}"` → `"短课名"`
- `islandB_active`: `"下课"` → `"上课中"`
- `hintTitle_active/post`: 保持不变

## Step 2: 重写 resolveTemplate() 支持新格式

原来的 `resolveTemplate` 做 `{变量}` 查找替换。新逻辑：
1. 如果值包含 `{` → 旧格式，走原来的替换逻辑（兼容现有用户）
2. 否则 → 新格式：按逗号分割，映射变量名到实际值，空格拼接

```kotlin
private fun resolveTemplate(
    tpl: String,
    courseName: String,
    shortName: String,
    location: String,
    teacher: String,
    startTime: String,
    endTime: String,
    countdownText: String,
    elapsedText: String,
): String {
    if (tpl.contains("{")) {
        var result = tpl
        result = result.replace("{课名}", courseName)
        result = result.replace("{短课名}", shortName.ifBlank { courseName })
        result = result.replace("{教室}", location.ifBlank { courseName })
        result = result.replace("{教师}", teacher)
        result = result.replace("{开始}", startTime)
        result = result.replace("{结束}", endTime)
        result = result.replace("{倒计时}", countdownText)
        result = result.replace("{正计时}", elapsedText)
        return result
    }
    val variableMap = mapOf(
        "课名" to courseName,
        "短课名" to shortName.ifBlank { courseName },
        "教室" to location.ifBlank { courseName },
        "教师" to teacher,
        "开始" to startTime,
        "结束" to endTime,
        "倒计时" to countdownText,
        "正计时" to elapsedText,
    )
    return tpl.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { variableMap[it] ?: "" }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
```

## Step 3: 更新 loadHyperFocusTemplates() 添加迁移

检测已保存的 JSON 中是否包含旧格式（值含 `{`），如果检测到就丢弃并返回 `hfDefaultTemplates`：

```kotlin
private fun loadHyperFocusTemplates(): Map<String, String> {
    val json = getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
        .getString("templates_json", null) ?: return hfDefaultTemplates
    return try {
        val obj = org.json.JSONObject(json)
        for (key in obj.keys()) {
            val v = obj.optString(key, "")
            if (v.contains("{")) {
                return hfDefaultTemplates
            }
        }
        val merged = hfDefaultTemplates.toMutableMap()
        for (key in obj.keys()) {
            merged[key] = obj.optString(key, hfDefaultTemplates[key] ?: "")
        }
        merged
    } catch (_: Exception) {
        hfDefaultTemplates
    }
}
```

## Step 4: 更新 Dart 侧 _defaultTemplates 常量

在 `live_settings_subpages.dart` 中更新 `_defaultTemplates`：

```dart
static const _defaultTemplates = {
  'ticker_pre': '课名',
  'ticker_active': '课名',
  'ticker_post': '课名',
  'islandA_pre': '教室',
  'islandA_active': '短课名',
  'islandA_post': '短课名',
  'islandB_pre': '',
  'islandB_active': '上课中',
  'islandB_post': '已下课',
  'baseTitle_pre': '课名',
  'baseTitle_active': '课名',
  'baseTitle_post': '课名',
  'baseContent_pre': '开始,结束',
  'baseContent_active': '开始,结束',
  'baseContent_post': '开始,结束',
  'baseSubcontent_pre': '教室',
  'baseSubcontent_active': '教室',
  'baseSubcontent_post': '教室',
  'hintTitle_pre': '',
  'hintTitle_active': '上课中',
  'hintTitle_post': '已下课',
};
```

## 报告要求

执行完毕后写入 `docs/superpowers/reports/task-1-report.md`，包含：
1. 修改的文件列表及变更摘要
2. 编译检查结果（`./gradlew assembleDebug` 和 `flutter analyze`）
3. 任何疑问或注意事项
