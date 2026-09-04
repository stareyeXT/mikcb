package com.mutx163.qingyu

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.CalendarContract
import com.xzakota.hyper.notification.focus.FocusNotification

/**
 * Renders the nightly "tomorrow briefing" notification (22:00) in one of
 * three forms chosen by device capability, mirroring how the live-update
 * pipeline picks its engine:
 *  - Xiaomi family → HyperFocus super island (focus extras, no ProgressStyle)
 *  - Android 16+ (SDK 36) → live-updates style (Notification.ProgressStyle)
 *  - everything else → plain BigTextStyle
 *
 * Kept independent of [LiveUpdateService] so AlarmManager can post straight
 * from [ExamReminderScheduler.handleFire] without a foreground service.
 */
object TomorrowBriefingNotificationBuilder {
    const val STYLE_AUTO = "auto"
    const val TAP_ACTION_OPEN_APP = "openApp"
    const val TAP_ACTION_OPEN_CALENDAR = "openCalendar"
    const val BUSINESS_BRIEFING = "course_briefing"

    enum class Form { SUPER_ISLAND, LIVE_UPDATES, PLAIN }

    /** Pure decision, unit-tested. */
    fun chooseForm(isXiaomiFamily: Boolean, sdkInt: Int): Form {
        return when {
            isXiaomiFamily -> Form.SUPER_ISLAND
            sdkInt >= BuildVersionCodesLiveUpdates -> Form.LIVE_UPDATES
            else -> Form.PLAIN
        }
    }

    // Notification.ProgressStyle ships with Android 16 (API 36).
    private const val BuildVersionCodesLiveUpdates = 36

    fun post(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        tapAction: String,
        calendarHour: Int,
        calendarMinute: Int,
        calendarTitle: String,
        islandA: String,
        islandB: String,
        firstClassStartMillis: Long,
    ): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return false
            }
        }
        ExamReminderScheduler.ensureChannel(context)

        val form = chooseForm(
            isXiaomiFamily = XiaomiDeviceFamily.isXiaomiFamilyDevice(),
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
        val contentIntent = buildContentIntent(
            context,
            notificationId,
            tapAction,
            calendarHour,
            calendarMinute,
            calendarTitle,
        )

        val builder = Notification.Builder(context, ExamReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            builder.setLargeIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        }

        val notification = when (form) {
            Form.SUPER_ISLAND -> builder
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .addExtras(
                    buildSuperIslandExtras(
                        context,
                        title = title,
                        body = body,
                        islandA = islandA,
                        islandB = islandB,
                        tapAction = tapAction,
                        calendarHour = calendarHour,
                        calendarMinute = calendarMinute,
                        calendarTitle = calendarTitle,
                    ),
                )
                .build()

            Form.LIVE_UPDATES -> {
                val withProgress = builder
                    .setContentText(body)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                val progressPercent =
                    if (firstClassStartMillis > 0L) computeProgress(firstClassStartMillis) else null
                if (progressPercent != null &&
                    android.os.Build.VERSION.SDK_INT >= BuildVersionCodesLiveUpdates
                ) {
                    val style = Notification.ProgressStyle()
                        .setStyledByProgress(true)
                        .setProgress(progressPercent)
                    withProgress.setProgress(progressPercent, progressPercent, false)
                    withProgress.setStyle(style)
                } else {
                    withProgress.setStyle(Notification.BigTextStyle().bigText(body))
                }
                withProgress.build()
            }

            Form.PLAIN -> builder
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        }

        return try {
            manager.notify(notificationId, notification)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Progress from now until the first course starts; null when unmeasurable. */
    private fun computeProgress(firstClassStartMillis: Long): Int? {
        // Live-updates shows a progress bar tracking the remaining night
        // (22:00 fire → first class).
        val fireTime = firstClassStartMillis - MAX_BRIEFING_LEAD_MILLIS
        val now = System.currentTimeMillis()
        if (firstClassStartMillis <= fireTime) return null
        val fraction = ((now - fireTime).toDouble() / (firstClassStartMillis - fireTime))
            .coerceIn(0.0, 1.0)
        return (fraction * 100).toInt().coerceIn(0, 100)
    }

    private const val MAX_BRIEFING_LEAD_MILLIS = 10L * 60L * 60L * 1000L // 22:00 → 08:00

    /** Tap behavior: early class → system calendar prefill; otherwise open app. */
    fun buildContentIntent(
        context: Context,
        notificationId: Int,
        tapAction: String,
        calendarHour: Int,
        calendarMinute: Int,
        calendarTitle: String,
    ): PendingIntent {
        if (tapAction == TAP_ACTION_OPEN_CALENDAR) {
            val calendarIntent = buildCalendarInsertIntent(
                context,
                calendarHour,
                calendarMinute,
                calendarTitle,
            )
            if (calendarIntent != null) {
                return PendingIntent.getActivity(
                    context,
                    notificationId,
                    calendarIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Mirrors MainActivity's openSystemCalendarEvent handler. Returns null when
     * no calendar app can receive ACTION_INSERT, so callers can fall back to
     * opening the app.
     */
    fun buildCalendarInsertIntent(
        context: Context,
        hour: Int,
        minute: Int,
        title: String,
    ): Intent? {
        val now = java.util.Calendar.getInstance()
        val begin = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        val end = (begin.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1)
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "早八课程" })
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
            putExtra(CalendarContract.Events.DESCRIPTION, "早八课程起床提醒")
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        }
        return if (intent.resolveActivity(context.packageManager) == null) null else intent
    }

    /**
     * V3 focus template for the super island, mirroring the layout used by
     * first-party HyperOS islands (charge-tip / mishare):
     *  - iconTextInfo: expanded focus card with app icon + title/content lines
     *  - textButton: up to two bottom action buttons (HyperOS V3 protocol reads
     *    the "action" key, so the JSON is post-processed from actionIntent)
     *  - island areas: A = icon + highlighted text, B = text-only
     *    (sameWidthDigitInfo only accepts digits/timers and would blank the
     *    island with "digit is empty", so text always goes through
     *    imageTextInfoRight)
     */
    fun buildSuperIslandExtras(
        context: Context,
        title: String,
        body: String,
        islandA: String,
        islandB: String,
        tapAction: String,
        calendarHour: Int,
        calendarMinute: Int,
        calendarTitle: String,
    ): android.os.Bundle {
        val launchAppUri = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.toUri(Intent.URI_INTENT_SCHEME) ?: ""
        val summary = body.lineSequence().firstOrNull()?.take(24) ?: ""
        val actionTitleText = if (tapAction == TAP_ACTION_OPEN_CALENDAR) "设早八提醒" else "查看课表"
        val actionIntentText = if (tapAction == TAP_ACTION_OPEN_CALENDAR) {
            val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, calendarTitle.ifBlank { "早八课程" })
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                putExtra(
                    CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                    resolveNextOccurrence(calendarHour, calendarMinute),
                )
            }
            calendarIntent.toUri(Intent.URI_INTENT_SCHEME)
        } else {
            launchAppUri
        }
        val firstLine = body.lineSequence().firstOrNull()?.take(20) ?: ""
        // 展开态模板 15 要求图片来自 pics Bundle（key 任意），静态图用应用图标兜底
        val focusIconKey = "qingyu_briefing_focus_icon"

        val bundle = FocusNotification.buildV3 {
            business = BUSINESS_BRIEFING
            updatable = true
            enableFloat = true
            reopen = HYPER_FOCUS_REOPEN_VALUE
            ticker = title
            aodTitle = title
            islandFirstFloat = true

            // 展开态模板 15「新图文组件 + 按钮组件 1」：应用图标 + 主要文本 + 次要文本
            // animIconInfo.src 必须指向 pics Bundle 里的 key，用应用图标静态图
            iconTextInfo {
                this.title = islandA.ifBlank { title }
                content = summary
                animIconInfo {
                    type = 0
                    src = appIconIcon(context)?.let { createPicture(focusIconKey, it) } ?: ""
                }
            }

            // 按钮组件 1（文字按钮，唯一）：早八跳日历，无早八打开 app
            textButton { list ->
                list.add(
                    com.xzakota.hyper.notification.focus.model.ActionInfo().apply {
                        type = 2
                        actionIntent = actionIntentText
                        actionTitle = actionTitleText
                        clickWithCollapse = true
                    },
                )
            }

            // 摘要态：文字全部放 B 区右侧（imageTextInfoRight），A 区仅保留应用图标。
            // 之前 A 区带文字（imageTextInfoLeft.textInfo）会把岛宽度分配给左侧，
            // 右侧文字被挤压。A 区 textInfo 留空格兜底（部分版本要求非空才渲染图标）。
            island {
                islandProperty = 1
                // 摘要态自动收起时间（秒）；与通知本体并存，超时后仅保留通知
                islandTimeout = 300
                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        textInfo { this.title = " " }
                        picInfo { type = 1 }
                    }
                    // B 区主文字：islandA（早八标题/门数）。islandB 形如「08:00 高数」
                    // 不是纯数字，只能走 imageTextInfoRight（sameWidthDigitInfo 塞文字
                    // 会触发 digit is empty 岛空白）。
                    imageTextInfoRight {
                        type = 2
                        textInfo {
                            this.title = islandA.ifBlank { title }
                            showHighlightColor = true
                        }
                    }
                    if (islandB.isNotBlank()) {
                        imageTextInfoRight {
                            textInfo {
                                this.title = islandB
                                showHighlightColor = true
                            }
                        }
                    } else if (firstLine.isNotBlank()) {
                        imageTextInfoRight {
                            textInfo {
                                this.title = firstLine
                                showHighlightColor = true
                            }
                        }
                    }
                }
                smallIslandArea {
                    combinePicInfo {
                        picInfo { type = 1 }
                    }
                }
                shareData {
                    this.title = title
                    content = summary
                }
            }
        }

        // HyperOS V3 协议只认 textButton[].action（不认 actionIntent），
        // 与 HyperIsland 模板库 fixTextButtonJson 同款修正。
        return fixTextButtonJson(bundle)
    }

    /**
     * Square app icon for the focus-card animIconInfo (≥224px recommended).
     * HyperOS V3 TemplateFactoryV3.wrapNotification 强转 pics 里的值为 Icon，
     * 直接塞 Bitmap 会抛 ClassCastException 导致展开态渲染失败，必须包一层 Icon。
     */
    private fun appIconIcon(context: Context): android.graphics.drawable.Icon? = try {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = maxOf(224, drawable.intrinsicWidth.coerceAtLeast(1))
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        android.graphics.drawable.Icon.createWithBitmap(bitmap)
    } catch (_: Exception) {
        null
    }

    /** Renames textButton[].actionIntent → action so HyperOS V3 dispatches button taps. */
    private fun fixTextButtonJson(bundle: android.os.Bundle): android.os.Bundle {
        val json = bundle.getString("miui.focus.param") ?: return bundle
        return try {
            val root = org.json.JSONObject(json)
            val pv2 = root.optJSONObject("param_v2") ?: return bundle
            val buttons = pv2.optJSONArray("textButton") ?: return bundle
            for (i in 0 until buttons.length()) {
                val button = buttons.optJSONObject(i) ?: continue
                val intent = button.optString("actionIntent")
                if (intent.isNotEmpty()) {
                    button.put("action", intent)
                    button.remove("actionIntent")
                    button.remove("actionIntentType")
                }
            }
            bundle.putString("miui.focus.param", root.toString())
            bundle
        } catch (_: Exception) {
            bundle
        }
    }

    private fun resolveNextOccurrence(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val begin = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        return begin.timeInMillis
    }
}
