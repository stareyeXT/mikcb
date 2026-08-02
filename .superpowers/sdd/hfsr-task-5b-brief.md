# Task 5b Brief: Kotlin 链路（LiveUpdatePayload → intent → onStartCommand）

来源：`docs/superpowers/plans/2026-07-31-hyperfocus-settings-redesign-plan.md` Task 5（Kotlin 侧）

## Global Constraints（本项目所有任务适用）

- 视觉/超时默认值：islandTimeoutPre=300、islandTimeoutActive=600、islandTimeoutPost=600、iconAEnabled=true、statusTextColor=#FFFFFFFF、outEffectStatusEnabled=true、outEffectStatusColor=#FFFFFFFF、outEffectExpandEnabled=true、outEffectExpandColor=#FFFFFFFF
- Kotlin 类字段（Task 3 已在 LiveUpdateService 声明，L2257-2265）：islandTimeoutPre/Active/Post、iconAEnabled、statusTextColor、outEffectStatusEnabled/Color、outEffectExpandEnabled/Color
- `gradlew assembleDebug` 必须 BUILD SUCCESSFUL

## Files

- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt`
  - `LiveUpdatePayload` data class（L590-633）加 9 字段（带默认值）
  - `buildServiceIntentFromMethodPayload`（L864-895 区域）从 islandConfig 读入
  - `buildServiceIntent`（L1359-1382 区域）putExtra
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `LiveUpdateService.onStartCommand` 字段读取块（L2361-2384 区域末尾）读 intent 赋给类字段

## 背景

Task 5a（1994c41）已在 Flutter 侧把 9 个字段经 `startLiveUpdate` → `_buildData` → `islandConfig` map 传入。islandConfig 的 key：`hfIslandTimeoutPre`/`hfIslandTimeoutActive`/`hfIslandTimeoutPost`/`hfIconAEnabled`/`hfStatusTextColor`/`hfOutEffectStatusEnabled`/`hfOutEffectStatusColor`/`hfOutEffectExpandEnabled`/`hfOutEffectExpandColor`。

Task 3（06307e1）已在 `LiveUpdateService` 类声明了 9 个字段（L2257-2265），并在 `buildHyperFocusBundle`/`sendTestFocusNotificationInner` 消费。本任务打通 intent 链路：把 islandConfig 的 9 个值从 Flutter 一路传到 Kotlin 类字段。

## Step 1: LiveUpdatePayload 加 9 字段

`LiveUpdatePayload`（L590-633）末尾（`superIslandEngine` 之前或之后均可，带默认值避免破坏其它构造）加：
```kotlin
    val islandTimeoutPre: Int = 300,
    val islandTimeoutActive: Int = 600,
    val islandTimeoutPost: Int = 600,
    val iconAEnabled: Boolean = true,
    val statusTextColor: String = "#FFFFFFFF",
    val outEffectStatusEnabled: Boolean = true,
    val outEffectStatusColor: String = "#FFFFFFFF",
    val outEffectExpandEnabled: Boolean = true,
    val outEffectExpandColor: String = "#FFFFFFFF",
```

## Step 2: buildServiceIntentFromMethodPayload 读入

在 `buildServiceIntentFromMethodPayload` 的 payload 构造（L842-909）末尾加：
```kotlin
            islandTimeoutPre = (islandConfig["hfIslandTimeoutPre"] as? Number)?.toInt() ?: 300,
            islandTimeoutActive = (islandConfig["hfIslandTimeoutActive"] as? Number)?.toInt() ?: 600,
            islandTimeoutPost = (islandConfig["hfIslandTimeoutPost"] as? Number)?.toInt() ?: 600,
            iconAEnabled = islandConfig["hfIconAEnabled"] as? Boolean ?: true,
            statusTextColor = islandConfig["hfStatusTextColor"] as? String ?: "#FFFFFFFF",
            outEffectStatusEnabled = islandConfig["hfOutEffectStatusEnabled"] as? Boolean ?: true,
            outEffectStatusColor = islandConfig["hfOutEffectStatusColor"] as? String ?: "#FFFFFFFF",
            outEffectExpandEnabled = islandConfig["hfOutEffectExpandEnabled"] as? Boolean ?: true,
            outEffectExpandColor = islandConfig["hfOutEffectExpandColor"] as? String ?: "#FFFFFFFF",
```

## Step 3: buildServiceIntent putExtra

`buildServiceIntent`（L1359-1382 区域，`putExtra("miuiIslandExpandedIconPath", ...)` 之后）加：
```kotlin
            putExtra("hfIslandTimeoutPre", payload.islandTimeoutPre)
            putExtra("hfIslandTimeoutActive", payload.islandTimeoutActive)
            putExtra("hfIslandTimeoutPost", payload.islandTimeoutPost)
            putExtra("hfIconAEnabled", payload.iconAEnabled)
            putExtra("hfStatusTextColor", payload.statusTextColor)
            putExtra("hfOutEffectStatusEnabled", payload.outEffectStatusEnabled)
            putExtra("hfOutEffectStatusColor", payload.outEffectStatusColor)
            putExtra("hfOutEffectExpandEnabled", payload.outEffectExpandEnabled)
            putExtra("hfOutEffectExpandColor", payload.outEffectExpandColor)
```

## Step 4: onStartCommand 读取

`MainActivity.kt` `LiveUpdateService.onStartCommand` 字段读取块（L2361-2384 区域，`miuiIslandExpandedIconPath = intent?.getStringExtra(...)` 之后）加：
```kotlin
            islandTimeoutPre = intent?.getIntExtra("hfIslandTimeoutPre", 300) ?: 300
            islandTimeoutActive = intent?.getIntExtra("hfIslandTimeoutActive", 600) ?: 600
            islandTimeoutPost = intent?.getIntExtra("hfIslandTimeoutPost", 600) ?: 600
            iconAEnabled = intent?.getBooleanExtra("hfIconAEnabled", true) ?: true
            statusTextColor = intent?.getStringExtra("hfStatusTextColor") ?: "#FFFFFFFF"
            outEffectStatusEnabled = intent?.getBooleanExtra("hfOutEffectStatusEnabled", true) ?: true
            outEffectStatusColor = intent?.getStringExtra("hfOutEffectStatusColor") ?: "#FFFFFFFF"
            outEffectExpandEnabled = intent?.getBooleanExtra("hfOutEffectExpandEnabled", true) ?: true
            outEffectExpandColor = intent?.getStringExtra("hfOutEffectExpandColor") ?: "#FFFFFFFF"
```

## Step 5: 编译验证

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

## Step 6: Commit

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: plumb hyperfocus island config through intent to live update service"
```
