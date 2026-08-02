# Task 4 Report: Kotlin 诊断接口（调度器访问器 + buildHyperFocusDebugStatus + 测试结果记录）

Status: **DONE**

## Changes

### 1. `LiveUpdateScheduler.kt` — read-only scheduler accessor
- `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt:774` — added `fun buildNextTriggerDebugInfo(context: Context): Map<String, Any?>` immediately after `suspendScheduleTriggers` (verbatim from brief Step 1). Returns `emptyMap()` when no snapshot; otherwise exposes `nextCourseName` / `nextCourseStartAtMillis` / `nextCourseEndAtMillis` / `nextTriggerAtMillis` / `nextTriggerStage` / `hasActiveSelection` / `suspendedUntilMillis` (null when in the past). Uses existing private members `loadSnapshot`, `findNextSelection`, `suspendedUntilMillis`, `hasActiveLiveSelection`.

### 2. `MainActivity.kt` — companion channel const
- `MainActivity.kt:88` — added `const val HYPERFOCUS_TEST_CHANNEL_ID = "hyperfocus_test_channel"` to the `MainActivity` companion (next to `CHANNEL_ID`).
- The two former `val testChannelId = "hyperfocus_test_channel"` usages inside `sendTestFocusNotificationInner` now reference the const: `MainActivity.kt:1308` (`createNotificationChannel`), `MainActivity.kt:1310` (`getNotificationChannel`), `MainActivity.kt:1316` (`Notification.Builder`). The local `testChannelId` variable was removed entirely.

### 3. `sendTestFocusNotification` split (record + wrapper + inner)
- `MainActivity.kt:1113` — new `private fun recordHyperFocusTestResult(stage, succeeded, message)` writes `last_stage` / `last_succeeded` / `last_message` / `last_at_millis` to SharedPreferences `"hyper_focus_test"` via `.apply()`.
- `MainActivity.kt:1123` — new outer `private fun sendTestFocusNotification(args)` wrapper: derives `stage` from args (default `"pre"`), calls `sendTestFocusNotificationInner`, catches `Throwable` → logs `Log.e("HyperFocusApi", "sendTestFocus failed", e)` and returns `"发送失败：$e"`, then records the result uniformly (`failure == null` ⇒ success).
- `MainActivity.kt:1135` — former body renamed to `private fun sendTestFocusNotificationInner(args: Map<String, String>?): String?`; body logic unchanged.
- Inner catch (`catch (e: Exception)`, ~`MainActivity.kt:1342`): the `Log.e` + `UmengDiagnosticReporter.report(...)` are retained, but the final `return "发送异常：${e.message ?: e.javaClass.simpleName}"` was replaced with `throw e`, so the exception propagates to the outer wrapper for uniform recording (per dispatcher instruction; intentional failure strings — "系统通知权限未开启…" and "测试通知渠道已被关闭…" — are plain returns and stay byte-identical to today's behavior).

### 4. `buildHyperFocusDebugStatus` — `LiveUpdateService` companion
- `MainActivity.kt:1995` — added `fun buildHyperFocusDebugStatus(context: Context): Map<String, Any?>` to the `LiveUpdateService` companion, right after `buildDebugStatus` (verbatim from brief Step 4). Reuses companion private members `hasNotificationPermissionCompat`, `buildEnvironmentSnapshot`, top-level `loadHyperFocusTemplates`, `LiveUpdateScheduler.buildNextTriggerDebugInfo`, and `UmengDiagnosticReporter` helpers. Produces `generatedAtMillis` / `summary` (12 fields incl. `testChannelBlocked` via `channel.importance == NotificationManager.IMPORTANCE_NONE`) / `environment` / `scheduling` / `templates` (pre/active/post × 7 flags) / `test` / `recentDiagnostics`.

### 5. MethodChannel branch
- `MainActivity.kt:385` — added `"getHyperFocusDebugStatus" -> result.success(LiveUpdateService.buildHyperFocusDebugStatus(this))` directly after the `"getLiveUpdateDebugStatus"` branch.

### 6. Build verification
Command: `.\gradlew assembleDebug` (in `C:\daima\zwg\mikcb\mikcb-ECJTU\android`)

Tail of output:
```
> Task :app:packageProdDebug
> Task :app:createProdDebugApkListingFileRedirect UP-TO-DATE
> Task :app:assembleProdDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 34s
482 actionable tasks: 51 executed, 431 up-to-date
```
No new compiler warnings from the changed files; `NotificationManager.IMPORTANCE_NONE` compiled without deprecation warnings (no `NotificationManagerCompat` fallback needed). `compileDevDebugKotlin` / `compilePerfDebugKotlin` / `compileProdDebugKotlin` all succeeded for the app module.

### 7. Commit
```
[master 29fbe48] feat: add HyperFocus debug status API with test result recording
 2 files changed, 113 insertions(+), 5 deletions(-)
```
Commit hash: `29fbe485d80e327316a63d2bac9783d7ef371d47`
Only the two Kotlin files were staged:
- `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateScheduler.kt`
- `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`

## Notes / concerns
- Per the brief's Step 3 note, the inner catch was kept (preserving the `UmengDiagnosticReporter.report("send_test_focus_failed")` diagnostic) with its return changed to `throw e`; the exception-path failure text reaching Flutter is now the wrapper's `"发送失败：$e"` instead of the old `"发送异常：…"` — the intentional, user-facing failure strings are unchanged.
- All other working-tree modifications (task briefs/reports, `docs/superpowers/plans/…`) were left uncommitted.

---

# Fix Report (review finding: exception text must stay identical to original)

## Finding
Commit `29fbe48`'s wrapper `sendTestFocusNotification` catch returned `"发送失败：$e"`. The pre-branch code returned `"发送异常：${e.message ?: e.javaClass.simpleName}"` from the inner exception catch. Two changes in one string: prefix (发送失败 vs 发送异常) and payload (full `toString` vs message-or-class-name).

## Fix diff
`android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`:
```diff
@@ -1126,7 +1126,7 @@ class MainActivity : FlutterActivity() {
             sendTestFocusNotificationInner(args)
         } catch (e: Throwable) {
             Log.e("HyperFocusApi", "sendTestFocus failed", e)
-            "发送失败：$e"
+            "发送异常：${e.message ?: e.javaClass.simpleName}"
         }
         recordHyperFocusTestResult(stage, failure == null, failure ?: "")
         return failure
```
Nothing else changed: the inner method, prefs recording, channel const, `buildHyperFocusDebugStatus`, scheduler accessor, and MethodChannel branch are untouched.

## Verification
Command: `.\gradlew assembleDebug` (in `C:\daima\zwg\mikcb\mikcb-ECJTU\android`)

Tail of output:
```
> Task :app:assemblePerfDebug
> Task :app:assembleDevDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 48s
482 actionable tasks: 45 executed, 437 up-to-date
```
All three app Kotlin variants (`compileDevDebugKotlin`, `compilePerfDebugKotlin`, `compileProdDebugKotlin`) succeeded; only pre-existing deprecation/unchecked-cast warnings.

## Commit
```
[master 7942e4a] fix: preserve original exception text in test focus send
 1 file changed, 1 insertion(+), 1 deletion(-)
```
Commit hash: `7942e4a` (only `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt` staged).
