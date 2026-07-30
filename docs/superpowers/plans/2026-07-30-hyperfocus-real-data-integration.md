# HyperFocusApi 真实课表数据集成 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当引擎切换为 HyperFocusApi 时，前台服务使用 `FocusNotifyApi + Template` 构建 MIUI Focus 通知，将真实课表数据送入超级岛

**Architecture:** 在现有 `LiveUpdateScheduler → LiveUpdateService` 数据管道中传递 `superIslandEngine` 字段；`LiveUpdateService.buildNotification()` 根据引擎选择分支：内置 → 现有 `buildMiuiFocusParam()`，HyperFocusApi → 新增 `buildHyperFocusBundle()` (使用 FocusNotifyApi)

**Tech Stack:** Kotlin, Flutter MethodChannel, HyperFocusApi (`com.hyperfocus.api`), `FocusNotifyApi`, `Template`

## Global Constraints

- `NativeLiveSettings` data class default: `superIslandEngine = "builtIn"`
- JSON key from Dart: `"superIslandEngine"` — values: `"builtIn"` / `"hyperFocusApi"`
- `FocusNotifyApi` + `Template` builder API — not `FocusApi.sendFocus()` (后者是旧版 JSON 参数式 API)
- 不改动现有 `buildMiuiFocusParam()` / `buildIslandSummary()` 逻辑

---

### Task 1: Add superIslandEngine to NativeLiveSettings → LiveUpdatePayload → intent extras

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt` — 4 locations

**Interfaces:**
- Consumes: JSON key `"superIslandEngine"` from Dart snapshot (already present in `settings.toJson()`)
- Produces: Intent extra `"superIslandEngine"` in `LiveUpdateService` intent

- [ ] **Step 1: Add field to `NativeLiveSettings` data class**

Find `private data class NativeLiveSettings(` at line 499. Add after the last field (`liveBeforeClassQuickAction`):

```kotlin
    val liveBeforeClassQuickAction: String,
    val superIslandEngine: String = "builtIn",
)
```

- [ ] **Step 2: Parse from JSON in `NativeLiveSettings` constructor**

Find the JSON parsing block at line 1075. Add after the `liveBeforeClassQuickAction` line:

```kotlin
            liveBeforeClassQuickAction =
                settingsJson.optString("liveBeforeClassQuickAction", "none"),
            superIslandEngine =
                settingsJson.optString("superIslandEngine", "builtIn"),
```

- [ ] **Step 3: Add field to `LiveUpdatePayload` data class**

Find `private data class LiveUpdatePayload(` at line 589. Add after `validateAgainstSchedule`:

```kotlin
    val validateAgainstSchedule: Boolean = true,
    val superIslandEngine: String = "builtIn",
)
```

- [ ] **Step 4: Add mapping in `selectionToPayload()`**

Find the `return LiveUpdatePayload(` at line 1731. Add before `)`:

```kotlin
            validateAgainstSchedule = true,
            superIslandEngine = snapshot.settings.superIslandEngine,
        )
```

- [ ] **Step 5: Add intent extra in `buildServiceIntent()`**

Find the `putExtra("validateAgainstSchedule", payload.validateAgainstSchedule)` line. Add after it:

```kotlin
        putExtra("validateAgainstSchedule", payload.validateAgainstSchedule)
        putExtra("superIslandEngine", payload.superIslandEngine)
```

---

### Task 2: Read superIslandEngine in LiveUpdateService

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt` — LiveUpdateService inner class

**Interfaces:**
- Consumes: Intent extra `"superIslandEngine"` from `buildServiceIntent()`
- Produces: `superIslandEngine: String` field available in `buildNotification()`

- [ ] **Step 1: Add field to LiveUpdateService**

Find the field declarations in `LiveUpdateService` (around line 1740+ where `promoteDuringClass`, `showNotificationDuringClass` etc. are declared). Add:

```kotlin
    private var superIslandEngine: String = "builtIn"
```

- [ ] **Step 2: Read intent extra in `onStartCommand()`**

Find the block of intent extra reads (around line 2011-2090). Add after `validateAgainstSchedule` line:

```kotlin
    validateAgainstSchedule = intent?.getBooleanExtra("validateAgainstSchedule", true) ?: true
    superIslandEngine = intent?.getStringExtra("superIslandEngine") ?: "builtIn"
```

---

### Task 3: Implement buildHyperFocusBundle() and branch in buildNotification()

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt` — LiveUpdateService

**Interfaces:**
- Consumes: `superIslandEngine` field, course data fields, `stage`, `startAtMillis`, `endAtMillis`, `showCountdown`, `countdownTextStyle`
- Produces: `Bundle?` attached to notification extras

- [ ] **Step 1: Add imports at top of MainActivity.kt**

Find the import block (around line 1-50). Add:

```kotlin
import com.hyperfocus.api.FocusNotifyApi
import com.hyperfocus.api.info.BaseInfo
import com.hyperfocus.api.info.HintInfo
import com.hyperfocus.api.info.TimerInfo
```

- [ ] **Step 2: Add `formatCountdownForFocus()` helper method**

Add this as a private method in `LiveUpdateService`:

```kotlin
    private fun formatCountdownForFocus(millis: Long): String {
        if (millis <= 0) return "0:00"
        val totalSec = millis / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }
```

- [ ] **Step 3: Add `buildHyperFocusBundle()` method**

Add this as a private method in `LiveUpdateService`:

```kotlin
    private fun buildHyperFocusBundle(
        stage: String?,
        remainingText: String,
    ): Bundle? {
        if (!isXiaomiFamilyDevice()) return null
        return try {
            val now = System.currentTimeMillis()
            val isBeforeClass = stage == "beforeClass"
            val timerWhen = if (isBeforeClass) startAtMillis else endAtMillis
            val timeRange = "$startTimeText - $endTimeText"

            val titleText = if (isBeforeClass) {
                "距离上课还有 ${formatCountdownForFocus(timerWhen - now)}"
            } else {
                "距离下课还有 ${formatCountdownForFocus(timerWhen - now)}"
            }

            val notifyApi = FocusNotifyApi()
            val template = notifyApi.build(NotificationCompat.Builder(this, CHANNEL_ID))

            template.setBaseInfo(BaseInfo().apply {
                setTitle(courseName)
                setContent(timeRange)
                setSubContent(location)
                setType(2)
                setShowDivider(true)
                setColorTitle("#FFFFFF")
                setColorContent("#AAAAAA")
                setColorSubContent("#888888")
            })

            template.setHintInfo(HintInfo().apply {
                setTitle(titleText)
                setColorTitle("#FFFFFF")
                setTimerInfo(TimerInfo().apply {
                    setTimerType(-1)
                    setTimerWhen(timerWhen)
                    setTimerSystemCurrent(now)
                })
                setType(1)
            })

            template.setTicker(
                if (isBeforeClass) "即将上课：$courseName" else courseName
            )
            template.setAodTitle(courseName)
            template.setUpdatable(true)
            template.setEnableFloat(true)
            template.setTimeout(3600)

            template.create()
        } catch (e: Exception) {
            Log.w(TAG, "buildHyperFocusBundle failed", e)
            null
        }
    }
```

- [ ] **Step 4: Branch in `buildNotification()` — 3 locations**

**Location A — compute miuiFocusParam (line ~3352):**

Replace the existing `val miuiFocusParam = if (!shouldPromote ...` block with:

```kotlin
    val hyperFocusBundle = if (superIslandEngine == "hyperFocusApi" && (shouldPromote && !isDuringClassStatusBar)) {
        buildHyperFocusBundle(
            stage = stage,
            remainingText = miuiFocusHintText,
        )
    } else {
        null
    }
    val miuiFocusParam = if (superIslandEngine == "hyperFocusApi" || !shouldPromote || isDuringClassStatusBar) {
        null
    } else {
        buildMiuiFocusParam(
            title = title,
            remainingText = miuiFocusHintText,
            timeRangeText = timeRangeText,
            bodyContent = promotedContentText,
            visibleLocation = visibleLocation,
            stage = stage,
            classProgress = classProgress,
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis,
            islandName = nameToUse,
            progressBreakOffsetsMillis = progressBreakOffsetsMillis,
            progressMilestoneLabels = progressMilestoneLabels,
            progressMilestoneTimeTexts = progressMilestoneTimeTexts,
        )
    }
```

**Location B — apply to notification extras (line ~3510):**

Replace `miuiFocusParam?.let { notification.extras.putString("miui.focus.param", it) }` with:

```kotlin
    if (hyperFocusBundle != null) {
        notification.extras.putAll(hyperFocusBundle)
    } else {
        miuiFocusParam?.let { notification.extras.putString("miui.focus.param", it) }
    }
```

**Location C — `isMiuiFocusIslandReady` check (line ~3522):**

Replace `miuiFocusParam != null &&` with `(miuiFocusParam != null || hyperFocusBundle != null) &&`:

```kotlin
    val isMiuiFocusIslandReady =
        isXiaomiFamilyDevice() &&
            (miuiFocusParam != null || hyperFocusBundle != null) &&
            shouldPromote &&
            !isDuringClassStatusBar
```

Also at line ~3550, replace `miuiFocusParam == null` with `miuiFocusParam == null && hyperFocusBundle == null`:

```kotlin
    isXiaomiFamilyDevice() && miuiFocusParam == null && hyperFocusBundle == null ->
        getString(R.string.debug_miui_focus_param_missing)
```

- [ ] **Step 5: Build and check for compilation errors**

```bash
cd C:\daima\zwg\mikcb\mikcb-ECJTU && ./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Full build verification

- [ ] **Step 1: Clean build**

```bash
cd C:\daima\zwg\mikcb\mikcb-ECJTU && ./gradlew clean assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run Dart analyze**

```bash
cd C:\daima\zwg\mikcb\mikcb-ECJTU && dart analyze lib/ 2>&1 | tail -20
```

Expected: no errors (only pre-existing infos)

- [ ] **Step 3: Run Flutter build**

```bash
cd C:\daima\zwg\mikcb\mikcb-ECJTU && flutter build apk --debug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL
