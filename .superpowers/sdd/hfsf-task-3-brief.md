# Task 3 Brief: 测试通知真实时间 + 到时消失（Kotlin）

来源：`docs/superpowers/plans/2026-08-01-hyperfocus-settings-fixes-plan.md` Task 3

## Global Constraints（本项目所有任务适用）

- `gradlew assembleDebug` 必须 BUILD SUCCESSFUL
- 不改变现有返回文案、埋点、post-inspect 逻辑（只改时间计算与新增到时消失）

## Files

- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`
  - `sendTestFocusNotificationInner` 时间计算（L1158-1187）
  - notify（L1376）之后加到时消失

## 背景

测试通知的 pre 阶段硬编码 `classStartAt = now + 5分钟`、`hintText = "距离上课还有 5 分钟"`（L1181-1186），与真实课表无关；且测试通知 `setOngoing(true)`（L1371）无到时消失，倒计时归零仍挂着。

**可直接复用**：`buildCourseTimeMillis(timeText: String): Long?`（MainActivity.kt L4180-4195）——解析 "HH:mm" 返回当日毫秒，解析失败返回 null。用它替代计划中的 `parseTodayClockToMillis`。

Flutter `_sendTestNotification` 已传 `startTime`/`endTime`（"HH:mm"，timetable_settings_screen.dart L3031-3032）进 `args`（MainActivity.kt L1139-1140 已读取 `startTime`/`endTime`）。

## Step 1: 时间计算改用真实时间（L1158-1187）

`when (templateStage)` 块（L1168-1187）改为：

```kotlin
            val realStart = buildCourseTimeMillis(startTime)
            val realEnd = buildCourseTimeMillis(endTime)
            val hasRealTime = realStart != null && realEnd != null && realEnd > realStart
            val classStartAt: Long
            val classEndAt: Long
            val timerTarget: Long
            val hintText: String
            when (templateStage) {
                "active" -> {
                    classStartAt = now - 10 * 60_000L
                    classEndAt = now + 5 * 60_000L
                    timerTarget = classEndAt
                    hintText = "距下课还有 5 分钟"
                }
                "post" -> {
                    classStartAt = now - 20 * 60_000L
                    classEndAt = now - 60_000L
                    timerTarget = 0L
                    hintText = "已下课"
                }
                else -> {
                    if (hasRealTime && realStart!! > now) {
                        classStartAt = realStart
                        classEndAt = realEnd!!
                        timerTarget = classStartAt
                        hintText = "距离上课还有 ${((classStartAt - now) / 60_000L + 1)} 分钟"
                    } else {
                        classStartAt = now + 5 * 60_000L
                        classEndAt = classStartAt + 100 * 60_000L
                        timerTarget = classStartAt
                        hintText = "距离上课还有 5 分钟"
                    }
                }
            }
```

> pre 阶段：真实 `startTime` 在今天稍后时用真实时间；若已过（realStart <= now）或解析失败回退模拟。active/post 保持模拟（避免 endTime 已过导致倒计时为负）。

## Step 2: 到时消失（notify L1376 之后）

在 `notificationManager.notify(10001, notification)`（L1376）之后、`val activeIds = ...`（L1378）之前插入：

```kotlin
            if (timerTarget > 0L && timerTarget > now) {
                Handler(Looper.getMainLooper()).postDelayed({
                    notificationManager.cancel(10001)
                }, timerTarget - now)
            }
```

检查 `android.os.Handler`/`Looper` 是否已 import（MainActivity.kt 大概率已有，若无则加）。post 阶段 timerTarget=0L 不触发；active 阶段 timerTarget=classEndAt=now+5min 会触发（5 分钟后取消）。

## Step 3: 编译验证

Run: `.\gradlew.bat assembleDebug`（workdir `android`）
Expected: BUILD SUCCESSFUL

## Step 4: Commit

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: use real course time and auto-dismiss for hyperfocus test notification"
```
