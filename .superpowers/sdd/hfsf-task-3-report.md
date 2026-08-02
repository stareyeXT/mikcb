# Task 3 Report: 测试通知真实时间 + 到时消失（Kotlin）

## What I changed (MainActivity.kt, file:line after edit)

1. **Step 1 — pre stage uses real course time** (`sendTestFocusNotificationInner` time calc, L1164-1196):
   - Added `realStart = buildCourseTimeMillis(startTime)`, `realEnd = buildCourseTimeMillis(endTime)`, `hasRealTime = realStart != null && realEnd != null && realEnd > realStart`.
   - `else` (pre) branch now uses real `startTime`/`endTime` when `hasRealTime && realStart > now` (realStart at L1186, realEnd at L1187, `timerTarget = classStartAt` L1188, dynamic hint text L1189); otherwise falls back to simulated `now + 5min` / `+100min` (L1191-1195).
   - active/post branches unchanged (still simulated, timerTarget=classEndAt / 0).

2. **Step 2 — auto-dismiss after notify** (L1385-1391):
   - After `notificationManager.notify(10001, notification)` (L1384), added:
     ```kotlin
     if (timerTarget > 0L && timerTarget > now) {
         Handler(Looper.getMainLooper()).postDelayed({
             notificationManager.cancel(10001)
         }, timerTarget - now)
     }
     ```
   - post stage (timerTarget=0L) does not trigger; active (timerTarget=now+5min) cancels after 5 min; pre (timerTarget=realStart) cancels at class start.

3. **Helper**: `buildCourseTimeMillis` was NOT accessible from `MainActivity` — the existing one (L4294) is a `private fun` of `LiveUpdateService`. Added a private copy of the identical implementation inside `MainActivity` (L2003, after `resolveDefaultVibrator`). `android.os.Handler`/`Looper` imports already present (L41, L43), no import change needed.

## Test results

- `.\gradlew.bat assembleDebug` (workdir `android`): **BUILD SUCCESSFUL in 36s**
- First attempt failed with `Unresolved reference 'buildCourseTimeMillis'` because the helper was private to `LiveUpdateService`; resolved by adding the private copy in `MainActivity`, then build passed.

## Files changed

- `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`

## Self-review findings

- Real-time calc for pre correct: uses `buildCourseTimeMillis`, falls back to simulated when real time in past (`realStart > now` false) or unparseable (`null`); `hasRealTime` also requires `realEnd > realStart`.
- Active/post unchanged (still simulated per brief to avoid negative countdown).
- Auto-dismiss added after notify, guarded by `timerTarget > now`, before `val activeIds`/post-inspect.
- Existing return texts, UmengDiagnosticReporter records (send_test_focus_started/submitted), post-inspect logic untouched.
- Only MainActivity.kt modified; build green.

## Concerns

- Added a duplicate private `buildCourseTimeMillis` in `MainActivity` rather than reusing the `LiveUpdateService` one (not accessible from `MainActivity`). Keeping it private-local is minimal-scope; if code-sharing is preferred later, the helper could be promoted to a top-level/internal function, but that would touch `LiveUpdateService` call sites — out of this task's scope.
