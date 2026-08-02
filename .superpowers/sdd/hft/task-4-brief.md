### Task 4: Kotlin 诊断接口（调度器访问器 + buildHyperFocusDebugStatus + 测试结果记录）

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt`（在 `suspendScheduleTriggers` 之后新增公开函数）
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - 方法通道：`"getHyperFocusDebugStatus"`（在 `"getLiveUpdateDebugStatus"` 分支之后）
  - `sendTestFocusNotification`（~1109）改名为私有 `sendTestFocusNotificationInner`，外层新 `sendTestFocusNotification` 统一记录测试结果；`testChannelId` 本地常量提升为 companion 常量
  - `LiveUpdateService` companion（~1898）：新增 `buildHyperFocusDebugStatus(context)`

**Interfaces:**
- Consumes: `LiveUpdateScheduler.findNextSelection`（私有，同类可访问）、`resolveNextTrigger`、`resolveStage`、`loadSnapshot`、`suspendedUntilMillis`、`hasActiveLiveSelection`；`LiveUpdateService.buildEnvironmentSnapshot`、`hasNotificationPermissionCompat`、`lastDebugSnapshot`（同类 companion 私有成员）
- Produces: MethodChannel `getHyperFocusDebugStatus` 返回 `Map`：`generatedAtMillis` / `summary`（`hasNotificationPermission`、`testChannelBlocked`、`templatesLoaded`、`schedulerReady`、`nextTriggerCourseName`、`nextTriggerStage`、`nextTriggerAtMillis`、`hasLastTestResult`、`lastTestStage`、`lastTestSucceeded`、`lastTestMessage`、`lastTestAtMillis`）/ `environment`（复用 buildEnvironmentSnapshot）/ `scheduling`（`nextCourseName`、`nextCourseStartAtMillis`、`nextCourseEndAtMillis`、`nextTriggerAtMillis`、`nextTriggerStage`、`hasActiveSelection`、`suspendedUntilMillis`）/ `templates`（pre/active/post × 7 字段布尔）/ `test`（`lastStage`、`lastSucceeded`、`lastMessage`、`lastAtMillis`）/ `recentDiagnostics`（`enabled`、`tail`）

- [ ] **Step 1: LiveUpdateScheduler 新增只读调试访问器**

在 `LiveUpdateScheduler` object 中 `suspendScheduleTriggers` 函数之后新增：

```kotlin
    /** Read-only scheduler state for the HyperFocus diagnostics screen. */
    fun buildNextTriggerDebugInfo(context: Context): Map<String, Any?> {
        val snapshot = loadSnapshot(context) ?: return emptyMap()
        val nowMillis = System.currentTimeMillis()
        val selection = findNextSelection(context, snapshot, nowMillis)
        val suspendedUntil = suspendedUntilMillis(context)
        return linkedMapOf(
            "nextCourseName" to selection?.currentCourse?.name,
            "nextCourseStartAtMillis" to selection?.startAtMillis,
            "nextCourseEndAtMillis" to selection?.endAtMillis,
            "nextTriggerAtMillis" to selection?.triggerAtMillis,
            "nextTriggerStage" to selection?.stage,
            "hasActiveSelection" to hasActiveLiveSelection(snapshot, context, nowMillis),
            "suspendedUntilMillis" to (suspendedUntil.takeIf { it > nowMillis }),
        )
    }
```

- [ ] **Step 2: 提升测试渠道常量为 companion 常量**

`MainActivity` companion（~79 行区域）新增：

```kotlin
        const val HYPERFOCUS_TEST_CHANNEL_ID = "hyperfocus_test_channel"
```

`sendTestFocusNotificationInner` 内两处 `val testChannelId = "hyperfocus_test_channel"`（~1281、~1291 区域）改为引用 `HYPERFOCUS_TEST_CHANNEL_ID`。

- [ ] **Step 3: sendTestFocusNotification 拆分为内层 + 记录**

将现有 `sendTestFocusNotification`（~1109，含全部阶段模板逻辑，即原方法体）重命名为 `sendTestFocusNotificationInner(args: Map<String, String>?): String?`（`private`），在其上方新增：

```kotlin
    private fun recordHyperFocusTestResult(stage: String, succeeded: Boolean, message: String) {
        getSharedPreferences("hyper_focus_test", Context.MODE_PRIVATE)
            .edit()
            .putString("last_stage", stage)
            .putBoolean("last_succeeded", succeeded)
            .putString("last_message", message)
            .putLong("last_at_millis", System.currentTimeMillis())
            .apply()
    }

    private fun sendTestFocusNotification(args: Map<String, String>?): String? {
        val stage = args?.get("stage") ?: "pre"
        val failure = try {
            sendTestFocusNotificationInner(args)
        } catch (e: Throwable) {
            Log.e("HyperFocusApi", "sendTestFocus failed", e)
            "发送失败：$e"
        }
        recordHyperFocusTestResult(stage, failure == null, failure ?: "")
        return failure
    }
```

注意：`sendTestFocusNotificationInner` 内部原有 catch 块捕获异常返回错误字符串（~1304 区域 `Log.e("HyperFocusApi", "sendTestFocus failed", e)` + return 错误串）需删除该内部 catch，让异常抛到外层统一记录——若保留内部 catch，则其 return 语句改为 `throw e`（保持行为一致：失败原因字符串与原来完全相同）。原方法体保持不变。

- [ ] **Step 4: 新增 buildHyperFocusDebugStatus**

在 `LiveUpdateService` companion（~1968 `buildDebugStatus` 之后）新增：

```kotlin
        fun buildHyperFocusDebugStatus(context: Context): Map<String, Any?> {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(MainActivity.HYPERFOCUS_TEST_CHANNEL_ID)
            val channelBlocked = channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE
            val testPrefs = context.getSharedPreferences("hyper_focus_test", Context.MODE_PRIVATE)
            val hasLastTest = testPrefs.contains("last_stage")
            val lastStage = testPrefs.getString("last_stage", null)
            val lastSucceeded = if (testPrefs.contains("last_succeeded")) testPrefs.getBoolean("last_succeeded", false) else null
            val lastMessage = testPrefs.getString("last_message", null)
            val lastAtMillis = if (testPrefs.contains("last_at_millis")) testPrefs.getLong("last_at_millis", 0L) else null
            val templates = loadHyperFocusTemplates(context)
            val scheduling = LiveUpdateScheduler.buildNextTriggerDebugInfo(context)

            fun templateFlags(stage: String): Map<String, Boolean> = linkedMapOf(
                "ticker" to (templates["ticker_$stage"]?.isNotBlank() == true),
                "islandA" to (templates["islandA_$stage"]?.isNotBlank() == true),
                "islandB" to (templates["islandB_$stage"]?.isNotBlank() == true),
                "baseTitle" to (templates["baseTitle_$stage"]?.isNotBlank() == true),
                "baseContent" to (templates["baseContent_$stage"]?.isNotBlank() == true),
                "baseSubcontent" to (templates["baseSubcontent_$stage"]?.isNotBlank() == true),
                "hintTitle" to (templates["hintTitle_$stage"]?.isNotBlank() == true),
            )

            val schedulingSummary = linkedMapOf<String, Any?>(
                "schedulerReady" to (scheduling["nextTriggerAtMillis"] != null),
                "nextTriggerCourseName" to scheduling["nextCourseName"],
                "nextTriggerStage" to scheduling["nextTriggerStage"],
                "nextTriggerAtMillis" to scheduling["nextTriggerAtMillis"],
            )

            return linkedMapOf(
                "generatedAtMillis" to System.currentTimeMillis(),
                "summary" to linkedMapOf(
                    "hasNotificationPermission" to hasNotificationPermissionCompat(context),
                    "testChannelBlocked" to channelBlocked,
                    "templatesLoaded" to templates.isNotEmpty(),
                    "schedulerReady" to schedulingSummary["schedulerReady"],
                    "nextTriggerCourseName" to schedulingSummary["nextTriggerCourseName"],
                    "nextTriggerStage" to schedulingSummary["nextTriggerStage"],
                    "nextTriggerAtMillis" to schedulingSummary["nextTriggerAtMillis"],
                    "hasLastTestResult" to hasLastTest,
                    "lastTestStage" to lastStage,
                    "lastTestSucceeded" to lastSucceeded,
                    "lastTestMessage" to lastMessage,
                    "lastTestAtMillis" to lastAtMillis,
                ),
                "environment" to buildEnvironmentSnapshot(context),
                "scheduling" to scheduling,
                "templates" to linkedMapOf(
                    "pre" to templateFlags("pre"),
                    "active" to templateFlags("active"),
                    "post" to templateFlags("post"),
                ),
                "test" to linkedMapOf(
                    "lastStage" to lastStage,
                    "lastSucceeded" to lastSucceeded,
                    "lastMessage" to lastMessage,
                    "lastAtMillis" to lastAtMillis,
                ),
                "recentDiagnostics" to linkedMapOf(
                    "enabled" to UmengDiagnosticReporter.isLiveDiagnosticsEnabled(context),
                    "tail" to UmengDiagnosticReporter.readLiveDiagnosticsTail(context),
                ),
            )
        }
```

注意：`hasNotificationPermissionCompat`、`buildEnvironmentSnapshot` 是 `LiveUpdateService` companion 的私有成员（MainActivity.kt:2018、1981），新函数放同一 companion 内可直接调用；`loadHyperFocusTemplates` 是顶层函数（MainActivity.kt:4342），可直接调用。若 `NotificationManager.IMPORTANCE_NONE` 有 deprecated lint，改用 `NotificationManagerCompat.IMPORTANCE_NONE`（androidx.core）或 `@Suppress("DEPRECATION")`——以 gradle 编译结果为准。

- [ ] **Step 5: 注册方法通道分支**

在 `"getLiveUpdateDebugStatus"` 分支（~381）之后新增：

```kotlin
                    "getHyperFocusDebugStatus" -> {
                        result.success(LiveUpdateService.buildHyperFocusDebugStatus(this))
                    }
```

- [ ] **Step 6: 编译验证**

Run: `.\gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: add HyperFocus debug status API with test result recording"
```

---


