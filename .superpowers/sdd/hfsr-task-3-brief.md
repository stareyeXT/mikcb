# Task 3 Brief: Kotlin 渲染扩展

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 3（按实际代码调整）

## Global Constraints（本项目所有任务适用）

- 视觉默认值：`iconAEnabled=true`、`statusTextColor=#FFFFFFFF`、`outEffectStatusEnabled=true`、`outEffectStatusColor=#FFFFFFFF`、`outEffectExpandEnabled=true`、`outEffectExpandColor=#FFFFFFFF`
- 消失时间默认：`islandTimeoutPre=300`、`islandTimeoutActive=600`、`islandTimeoutPost=600`
- 新增模板 key 固定：`hintContent_*`（前置文本1）、`hintSubcontent_*`（前置文本2）、`hintSubtitle_*`（主要小文本2）
- **模板值防注入**：`loadHyperFocusTemplates`（MainActivity.kt L4478-4483）检查任何 value 含 `{` 则整体回退 `hfDefaultTemplates`——**新增默认值不能含 `{`**

## Files

- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `hfDefaultTemplates`（L4449-4471）补 9 key
  - `buildHyperFocusBundle`（L3190-3321）模板读取 + hintInfo + islandTimeout + 视觉
  - `sendTestFocusNotificationInner`（L1218-1327）测试版同步
  - 类字段声明区（L2210-2259 附近）新增配置字段

## Step 1: hfDefaultTemplates 补 9 个新 key

在 `hfDefaultTemplates`（L4449-4471）末尾（`"hintTitle_post" to "已下课",` 之后）补：
```kotlin
    "hintContent_pre" to "即将上课",
    "hintContent_active" to "距离下课",
    "hintContent_post" to "已经下课",
    "hintSubcontent_pre" to "",
    "hintSubcontent_active" to "",
    "hintSubcontent_post" to "",
    "hintSubtitle_pre" to "",
    "hintSubtitle_active" to "",
    "hintSubtitle_post" to "",
```
（值均不含 `{`，满足防注入）

## Step 2: 新增类字段声明

在 `LiveUpdateService` 类字段声明区（L2216-2240 附近，`miuiIslandExpandedIconPath` 之后）新增：
```kotlin
    private var islandTimeoutPre = 300
    private var islandTimeoutActive = 600
    private var islandTimeoutPost = 600
    private var iconAEnabled = true
    private var statusTextColor = "#FFFFFFFF"
    private var outEffectStatusEnabled = true
    private var outEffectStatusColor = "#FFFFFFFF"
    private var outEffectExpandEnabled = true
    private var outEffectExpandColor = "#FFFFFFFF"
```

## Step 3: buildHyperFocusBundle 模板读取补 3 行

在 `buildHyperFocusBundle` 模板读取处（L3223-3229 附近，`val hintTitleText = ...` 之后）补：
```kotlin
            val hintContentText = r(templates["hintContent_$stageKey"] ?: "")
            val hintSubcontentText = r(templates["hintSubcontent_$stageKey"] ?: "")
            val hintSubtitleText = r(templates["hintSubtitle_$stageKey"] ?: "")
```

## Step 4: buildHyperFocusBundle hintInfo 接入新字段

`hintInfo { ... }` 块（L3259-3275）内，在 `content = remainingText.ifBlank { hintTitleText }` 之后补：
```kotlin
                    subTitle = hintSubtitleText
                    extraTitle = hintContentText
                    specialTitle = hintSubcontentText
```

## Step 5: islandTimeout 配置化

`islandTimeout = if (stageKey == "pre") 300 else 600`（L3279）改为：
```kotlin
                    islandTimeout = when (stageKey) {
                        "pre" -> islandTimeoutPre
                        "post" -> islandTimeoutPost
                        else -> islandTimeoutActive
                    }
```

## Step 6: 视觉选项接入 buildHyperFocusBundle

1. `outEffectSrc = "outer_glow"`（L3245）改为：
```kotlin
                outEffectSrc = if (outEffectStatusEnabled) "outer_glow" else ""
                outEffectColor = if (outEffectStatusEnabled) outEffectStatusColor else ""
```
（确认 FocusTemplateV3 是否支持 `outEffectColor` 属性；若库不认，仅条件化 outEffectSrc，outEffectColor 跳过并记录在报告）

2. 文本颜色：`island { bigIslandArea { imageTextInfoLeft { textInfo { ... } } } }`（L3282-3291）的 textInfo 内，若 `statusTextColor != "#FFFFFFFF"` 则设置 `colorTitle = statusTextColor`（TextAndColorInfo/TextInfo 支持 colorTitle），否则维持 `showHighlightColor = true`。用条件表达式：
```kotlin
                                title = islandAText
                                if (statusTextColor == "#FFFFFFFF") {
                                    showHighlightColor = true
                                } else {
                                    colorTitle = statusTextColor
                                }
```

3. `extras.putString("miui.bigIsland.effect.src", "outer_glow")` 和 `extras.putString("miui.effect.src", "outer_glow")`（L3314-3315）改为条件化：
```kotlin
            if (outEffectStatusEnabled) {
                extras.putString("miui.bigIsland.effect.src", "outer_glow")
                extras.putString("miui.effect.src", "outer_glow")
            }
```

4. 岛A图标开关：`picInfo { type = 1 }`（L3255-3257，通知小图标 picInfo）与岛区域 `picInfo { type = 1 }`（L3288-3290）——`iconAEnabled=false` 时不设置 `type = 1`（即 `if (iconAEnabled) { type = 1 }`）。若库对空 picInfo 报错则保留 `type = 1` 并在报告说明。

5. 展开态发光：本任务只在字段层支持（`outEffectExpandEnabled/Color` 声明 + 渲染读取），展开态发光的具体 buildV3 属性（如 island/hintInfo 是否支持）——若库无对应属性，记录下来留给后续，不强行实现。

## Step 7: 测试版同步接入 sendTestFocusNotificationInner

`sendTestFocusNotificationInner`（L1218-1327）同步：
1. 模板读取（L1202-1208 附近）补 3 行 hintContent/hintSubcontent/hintSubtitle
2. hintInfo（L1239-1257 附近）补 subTitle/extraTitle/specialTitle
3. `islandTimeout = 300`（L1286 附近）改为读 `args` 传值（缺省 300）：
```kotlin
            islandTimeout = (args?.get("islandTimeoutPre")?.toIntOrNull() ?: 300)
```
4. `outEffectSrc = "outer_glow"`（L1250 附近）条件化（从 args 读 `outEffectStatusEnabled`，缺省 true）
5. 文本颜色/picInfo 同正式版（从 args 读 `statusTextColor`/`iconAEnabled`，缺省白/true）
6. `extras.putString(...)`（L1329-1330 附近）条件化

测试版从 `args` map 读取配置（stage/courseName 等已从 args 读，模式一致）。args 键名：`islandTimeoutPre`、`islandTimeoutActive`、`islandTimeoutPost`、`iconAEnabled`、`statusTextColor`、`outEffectStatusEnabled`、`outEffectStatusColor`、`outEffectExpandEnabled`、`outEffectExpandColor`。

## Step 8: 编译验证

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

## Step 9: Commit

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: render hyperfocus new template fields, visual options and configurable island timeout"
```
