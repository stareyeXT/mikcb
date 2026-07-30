### Task 3: Kotlin 侧 — MethodChannel 添加 sendTestFocus handler

**Files:**
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt`

- [ ] **Step 1: 在 `MainActivity.kt` 顶部添加导入（import 区域）**

```kotlin
import com.hyperfocus.api.FocusApi
import android.graphics.drawable.Icon
```

- [ ] **Step 2: 在 `when (call.method)` 分支中添加新 case**

在 `"startLiveUpdate"` 分支之后（约 line 359）、`else` 分支之前添加：

```kotlin
                    "sendTestFocus" -> {
                        sendTestFocusNotification()
                        result.success(true)
                    }
```

- [ ] **Step 3: 在类中添加 `sendTestFocusNotification()` 方法**

在类中任意合适位置（如 `openNotificationSettings()` 方法附近）添加：

```kotlin
    private fun sendTestFocusNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "hyperfocus_test_channel"
            val channel = NotificationChannel(
                channelId,
                "HyperFocusApi Test",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)

            val sendNotification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("测试课程")
                .setContentText("高等数学")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setAutoCancel(false)

            val intent = Intent()
            intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
            intent.data = Uri.fromParts("package", packageName, null)

            val baseInfo = FocusApi.baseinfo(
                title = "测试课程",
                colorTitle = "#FFFFFF",
                basetype = 1,
                content = "高等数学",
                colorContent = "#FFFFFF",
                subContent = "教科A-101",
                colorSubContent = "#CCCCCC",
                extraTitle = "",
                colorExtraTitle = "#FFFFFF",
                subTitle = "08:00 - 09:40",
                colorsubTitle = "#AAAAAA",
                specialTitle = "即将上课",
                colorSpecialTitle = "#FFFFFF",
            )

            val hintInfo = FocusApi.hintInfo(
                type = 1,
                titleLineCount = 2,
                title = "高等数学",
                colortitle = "#FFFFFF",
                content = "距离上课还有 5 分钟",
                colorContent = "#AAAAAA",
                actionInfo = FocusApi.actionInfo(
                    actionsIntent = intent.toUri(Intent.URI_INTENT_SCHEME),
                    actionsTitle = "查看课表",
                ),
            )

            val api = FocusApi.sendFocus(
                title = "测试课程",
                baseInfo = baseInfo,
                hintInfo = hintInfo,
                picbg = Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
                picmarkv2 = Icon.createWithResource(this, android.R.drawable.ic_menu_myplaces),
                picbgtype = 2,
                picmarkv2type = 2,
                builder = sendNotification,
                ticker = "即将上课：高等数学",
                picticker = Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
            )

            sendNotification.addExtras(api)
            notificationManager.notify(10001, sendNotification.build())
        } catch (e: Exception) {
            Log.e("HyperFocusApi", "sendTestFocus failed", e)
        }
    }
```

- [ ] **Step 4: Build 验证**

Run: `cd android && gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/mutx163/qingyu/MainActivity.kt
git commit -m "feat: add sendTestFocus notification via HyperFocusApi"
```

---


