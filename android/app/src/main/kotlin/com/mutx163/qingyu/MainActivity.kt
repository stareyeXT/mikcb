package com.mutx163.qingyu

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.webkit.URLUtil
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.xzakota.hyper.notification.common.model.TimerInfo
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.model.ActionInfo
import com.xzakota.hyper.notification.focus.model.BaseInfo
import com.xzakota.hyper.notification.focus.model.HintInfo
import com.xzakota.hyper.notification.focus.model.PicInfo
import com.xzakota.hyper.notification.island.model.BigIslandArea
import com.xzakota.hyper.notification.island.model.ImageTextInfo
import com.xzakota.hyper.notification.island.model.SameWidthDigitInfo
import com.xzakota.hyper.notification.island.model.ShareData
import com.xzakota.hyper.notification.island.model.SmallIslandArea
import com.xzakota.hyper.notification.island.model.TextInfo
import com.xzakota.hyper.notification.island.template.IslandTemplate
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.util.Locale
import java.io.File
import java.util.Calendar
import kotlin.math.ceil

class MainActivity : FlutterActivity() {
    companion object {
        private const val METHOD_CHANNEL = "com.mutx163.qingyu/miui_live"
        private const val SYSTEM_UI_CHANNEL = "com.mutx163.qingyu/system_ui"
        private const val UMENG_CHANNEL = "com.mutx163.qingyu/umeng_analytics"
        private const val HOME_WIDGET_CHANNEL = "com.mutx163.qingyu/home_widget"
        private const val EXAM_REMINDER_CHANNEL = "com.mutx163.qingyu/exam_reminder"
        private const val SUPPORT_CHANNEL = "com.mutx163.qingyu/support"
        private const val MIGRATION_CHANNEL = "com.mutx163.qingyu/migration"
        private const val CHANNEL_ID = "live_update_channel"
        const val HYPERFOCUS_TEST_CHANNEL_ID = "hyperfocus_test_channel"
        private const val TOMORROW_BRIEFING_TEST_NOTIFICATION_ID = 10002
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "native_runtime_prefs"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        private const val KEY_MANAGED_UPDATE_DOWNLOAD_IDS = "managed_update_download_ids"
        private const val POST_PROMOTED_NOTIFICATIONS_PERMISSION =
            "android.permission.POST_PROMOTED_NOTIFICATIONS"
        private const val ICS_CHANNEL = "com.mutx163.qingyu/ics_import"
        private const val LAN_EDIT_CHANNEL = "com.mutx163.qingyu/lan_edit"
        private const val FROSTED_BLUR_CHANNEL = "com.mutx163.qingyu/frosted_blur"
        private const val LAUNCH_URL_CHANNEL = "com.mutx163.qingyu/launch_url"
        private const val HAPTIC_CHANNEL = "com.mutx163.qingyu/haptic"

        /** Schemes allowed for the `launch_url` channel (feedback deep links). */
        private val ALLOWED_LAUNCH_SCHEMES = setOf(
            "https", "http", "coolmarket", "xhsdiscover", "mqqopensdkapi", "mqqapi", "weixin"
        )
    }

    private var notificationManager: NotificationManager? = null
    private var permissionResult: MethodChannel.Result? = null
    private data class PendingExternalImport(
        val kind: String,
        val fileName: String,
        val textContent: String? = null,
        val filePath: String? = null,
    )

    private var pendingExternalImport: PendingExternalImport? = null
    private var pendingOpenLanEdit = false
    private var pendingDebugRoute: Map<String, Any?>? = null
    private var flutterChannel: MethodChannel? = null
    private var lanEditChannel: MethodChannel? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        // Fix: when launched via ACTION_SEND / ACTION_VIEW from another app (e.g.
        // a file manager), the caller may NOT set FLAG_ACTIVITY_NEW_TASK, which
        // causes our Activity to run inside the caller's task.  The Recents
        // screen then shows the caller's label & icon instead of ours.
        // Detect this situation (not task root + external intent) and redirect
        // into our own task before proceeding.
        if (!isTaskRoot && intent != null &&
            (intent.action == Intent.ACTION_SEND ||
             intent.action == Intent.ACTION_SEND_MULTIPLE ||
             intent.action == Intent.ACTION_VIEW)) {
            val relaunch = Intent(this, MainActivity::class.java).apply {
                action = intent.action
                type = intent.type
                intent.clipData?.let { clipData = it }
                putExtras(intent)
                intent.data?.let { data = it }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            startActivity(relaunch)
            finish()
            return
        }

        window.setBackgroundDrawable(
            SplashLayerDrawable(
                this,
                applicationInfo.loadLabel(packageManager),
            ),
        )
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Match Flutter's edge-to-edge mode on Android versions before the
        // target-SDK enforcement introduced by Android 15.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Debug deep links first so automation routes never fall into import.
        handleDebugDeepLinkIntent(intent)
        handleExternalImportIntent(intent)
        handleLanEditIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugDeepLinkIntent(intent)
        handleExternalImportIntent(intent)
        handleLanEditIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        applyPersistedHideFromRecents()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        // 公平运行内存：绑定 Flutter 通道（原生广播本身不依赖引擎）。
        FairMemoryAdapter.attachFlutterEngine(flutterEngine.dartExecutor.binaryMessenger)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            MemoryStatsCollector.methodChannelName,
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getMemorySnapshot" -> {
                    try {
                        result.success(MemoryStatsCollector.buildSnapshot(applicationContext))
                    } catch (error: Exception) {
                        result.error(
                            "MEMORY_SNAPSHOT_FAILED",
                            error.message,
                            null,
                        )
                    }
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SYSTEM_UI_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getTransitionAnimationScale" -> {
                        val scale = Settings.Global.getFloat(
                            contentResolver,
                            Settings.Global.TRANSITION_ANIMATION_SCALE,
                            1.0f,
                        )
                        result.success(scale.toDouble())
                    }
                    "getDisplayCornerRadiusDp" -> {
                        result.success(readDisplayCornerRadiusDp())
                    }
                    "getFontWeightAdjustment" -> {
                        // Android 12+ (API 31) 暴露系统字体粗细增量；未设置为
                        // FONT_WEIGHT_ADJUSTMENT_UNDEFINED(Int.MAX_VALUE)。低版本/未定义
                        // 时返回 null，交由 Dart 侧回退到 MediaQuery.boldText。
                        val value: Int? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val adj = resources.configuration.fontWeightAdjustment
                                if (adj ==
                                    android.content.res.Configuration
                                        .FONT_WEIGHT_ADJUSTMENT_UNDEFINED
                                ) {
                                    null
                                } else {
                                    adj
                                }
                            } else {
                                null
                            }
                        result.success(value)
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, HAPTIC_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "edgeTick" -> {
                        try {
                            result.success(performEdgeHapticTick())
                        } catch (error: Exception) {
                            Log.w("EdgeHaptic", "edgeTick failed", error)
                            result.error("HAPTIC_FAILED", error.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, FROSTED_BLUR_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isSupported" -> result.success(FrostedBlur.isSupported())
                    "blurPng" -> {
                        val bytes = call.argument<ByteArray>("bytes")
                        val radius = (call.argument<Double>("radius") ?: 16.0).toFloat()
                        if (bytes == null) {
                            result.error("INVALID_ARGUMENTS", "Missing PNG bytes", null)
                            return@setMethodCallHandler
                        }
                        Thread {
                            try {
                                val blurred = FrostedBlur.blurPng(bytes, radius)
                                runOnUiThread {
                                    if (blurred == null) {
                                        result.error(
                                            "BLUR_FAILED",
                                            "Native blur returned null",
                                            null,
                                        )
                                    } else {
                                        result.success(blurred)
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error("BLUR_FAILED", e.message, null)
                                }
                            }
                        }.start()
                    }
                    "blurRgba" -> {
                        val bytes = call.argument<ByteArray>("bytes")
                        val width = call.argument<Int>("width") ?: 0
                        val height = call.argument<Int>("height") ?: 0
                        val radius = (call.argument<Double>("radius") ?: 16.0).toFloat()
                        if (bytes == null || width <= 0 || height <= 0) {
                            result.error("INVALID_ARGUMENTS", "Missing RGBA payload", null)
                            return@setMethodCallHandler
                        }
                        Thread {
                            try {
                                val blurred = FrostedBlur.blurRgba(bytes, width, height, radius)
                                runOnUiThread {
                                    if (blurred == null) {
                                        result.error(
                                            "BLUR_FAILED",
                                            "Native RGBA blur returned null",
                                            null,
                                        )
                                    } else {
                                        result.success(blurred)
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error("BLUR_FAILED", e.message, null)
                                }
                            }
                        }.start()
                    }
                    else -> result.notImplemented()
                }
            }

        // ── Feedback: direct Intent launch (bypasses url_launcher on MIUI) ──
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LAUNCH_URL_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "launch" -> {
                        val urlString = call.argument<String>("url")
                        if (urlString.isNullOrEmpty()) {
                            result.error("INVALID_URL", "url is null or empty", null)
                            return@setMethodCallHandler
                        }
                        val parsed = Uri.parse(urlString)
                        val scheme = parsed.scheme?.lowercase()
                        if (scheme == null || scheme !in ALLOWED_LAUNCH_SCHEMES) {
                            Log.w("MainActivity", "launch_url: blocked scheme '$scheme' for $urlString")
                            result.success(false)
                            return@setMethodCallHandler
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            result.success(true)
                        } catch (e: ActivityNotFoundException) {
                            Log.w("MainActivity", "launch_url: no activity for $urlString", e)
                            result.success(false)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "launch_url: failed for $urlString", e)
                            result.success(false)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
            .also { flutterChannel = it }
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "initialize" -> result.success(true)
                    "checkNotificationPermission" -> result.success(hasNotificationPermission())
                    "requestNotificationPermission" -> {
                        if (hasNotificationPermission()) {
                            result.success(true)
                        } else {
                            permissionResult = result
                            requestNotificationPermission()
                        }
                    }

                    "checkPromotedSupport" -> result.success(checkPromotedSupport())
                    "isIgnoringBatteryOptimizations" ->
                        result.success(isIgnoringBatteryOptimizations())
                    "openNotificationSettings" -> {
                        openNotificationSettings()
                        result.success(true)
                    }
                    "openPromotedSettings" -> {
                        openPromotedSettings()
                        result.success(true)
                    }
                    "openAutoStartSettings" -> {
                        openAutoStartSettings()
                        result.success(true)
                    }
                    "openBatteryOptimizationSettings" -> {
                        openBatteryOptimizationSettings()
                        result.success(true)
                    }
                    "openAccessibilitySettings" -> {
                        openAccessibilitySettings()
                        result.success(true)
                    }
                    "isAutoStartEnabled" -> {
                        result.success(isAutoStartEnabled())
                    }
                    "isKeepAliveAccessibilityEnabled" -> {
                        result.success(isKeepAliveAccessibilityEnabled())
                    }
                    "setHideFromRecents" -> {
                        val hidden = call.arguments as? Boolean ?: false
                        persistHideFromRecents(hidden)
                        setHideFromRecents(hidden)
                        result.success(true)
                    }

                    "startLiveUpdate" -> {
                        val data = call.arguments as? Map<String, Any>
                        if (data != null) {
                            startLiveUpdateService(data)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing live update payload", null)
                        }
                    }

                    "stopLiveUpdate" -> {
                        stopLiveUpdateService()
                        result.success(true)
                    }
                    "getLiveUpdateDebugStatus" -> {
                        result.success(LiveUpdateService.buildDebugStatus(this))
                    }
                    "getHyperFocusDebugStatus" -> {
                        result.success(LiveUpdateService.buildHyperFocusDebugStatus(this))
                    }
                    "syncScheduleSnapshot" -> {
                        val snapshotJson = call.arguments as? String
                        if (snapshotJson != null) {
                            LiveUpdateScheduler.syncSnapshot(this, snapshotJson)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing schedule snapshot", null)
                        }
                    }
                    "clearScheduleSnapshot" -> {
                        LiveUpdateScheduler.clearSnapshot(this)
                        stopLiveUpdateService()
                        result.success(true)
                    }
                    "suspendScheduleTriggers" -> {
                        val untilMillis = (call.arguments as? Number)?.toLong()
                        if (untilMillis != null) {
                            LiveUpdateScheduler.suspendScheduleTriggers(this, untilMillis)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing suspend deadline", null)
                        }
                    }

                    "sendTestFocus" -> {
                        val args = call.arguments as? Map<String, String>
                        sendTestFocusNotification(args) { failure ->
                            if (failure == null) {
                                result.success(true)
                            } else {
                                result.error("SEND_TEST_FOCUS_FAILED", failure, null)
                            }
                        }
                    }

                    "saveHyperFocusTemplates" -> {
                        val json = call.arguments as? String ?: ""
                        getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
                            .edit()
                            .putString("templates_json", json)
                            .apply()
                        result.success(true)
                    }

                    "getHyperFocusTemplates" -> {
                        val json = getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
                            .getString("templates_json", null)
                        result.success(json)
                    }

                    "getPendingExternalImport" -> {
                        val pending = pendingExternalImport
                        pendingExternalImport = null
                        result.success(
                            pending?.let {
                                mapOf(
                                    "kind" to it.kind,
                                    "fileName" to it.fileName,
                                    "textContent" to it.textContent,
                                    "filePath" to it.filePath,
                                )
                            },
                        )
                    }

                    "getPendingDebugRoute" -> {
                        val pending = pendingDebugRoute
                        pendingDebugRoute = null
                        result.success(pending)
                    }

                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, UMENG_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "initializeIfNeeded" -> {
                        val initialized = UmengApplication.initializeAnalyticsIfNeeded(applicationContext)
                        result.success(initialized)
                    }
                    "triggerTestCrash" -> {
                        UmengApplication.initializeAnalyticsIfNeeded(applicationContext)
                        Handler(Looper.getMainLooper()).post {
                            throw RuntimeException("Manual Umeng U-APM test crash")
                        }
                        result.success(true)
                    }
                    "triggerTestAnr" -> {
                        UmengApplication.initializeAnalyticsIfNeeded(applicationContext)
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Thread.sleep(30000L)
                            } catch (_: InterruptedException) {
                            }
                        }
                        result.success(true)
                    }
                    "reportCustomLog" -> {
                        val data = call.arguments as? Map<*, *>
                        if (data == null) {
                            result.error("INVALID_ARGUMENTS", "Missing log payload", null)
                            return@setMethodCallHandler
                        }
                        UmengDiagnosticReporter.report(
                            context = applicationContext,
                            category = data["category"] as? String ?: "flutter_diagnostic",
                            message = data["message"] as? String ?: "",
                            level = data["level"] as? String,
                            stackTrace = data["stackTrace"] as? String,
                            dedupeKey = data["dedupeKey"] as? String
                                ?: (data["category"] as? String ?: "flutter_diagnostic"),
                            extras = buildMap {
                                put("error", data["error"])
                            }
                        )
                        result.success(true)
                    }
                    "recordDiagnosticEvent" -> {
                        val data = call.arguments as? Map<*, *>
                        if (data == null) {
                            result.error("INVALID_ARGUMENTS", "Missing log payload", null)
                            return@setMethodCallHandler
                        }
                        @Suppress("UNCHECKED_CAST")
                        val extras = (data["extras"] as? Map<String, Any?>) ?: emptyMap()
                        UmengDiagnosticReporter.record(
                            context = applicationContext,
                            category = data["category"] as? String ?: "flutter_diagnostic_event",
                            message = data["message"] as? String ?: "",
                            level = data["level"] as? String,
                            extras = extras,
                        )
                        result.success(true)
                    }
                    "setLiveDiagnosticsEnabled" -> {
                        val enabled = call.arguments as? Boolean ?: false
                        UmengDiagnosticReporter.setLiveDiagnosticsEnabled(
                            applicationContext,
                            enabled
                        )
                        result.success(true)
                    }
                    "exportLiveDiagnosticsFile" -> {
                        result.success(
                            UmengDiagnosticReporter.exportLiveDiagnosticsFile(applicationContext)
                        )
                    }
                    "readLiveDiagnosticsText" -> {
                        result.success(
                            UmengDiagnosticReporter.readLiveDiagnosticsText(applicationContext)
                        )
                    }
                    "clearLiveDiagnostics" -> {
                        result.success(
                            UmengDiagnosticReporter.clearLiveDiagnostics(applicationContext)
                        )
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, HOME_WIDGET_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "canRequestPinWidget" -> {
                        result.success(canRequestPinWidget())
                    }
                    "requestPinWidget" -> {
                        val arguments = call.arguments as? Map<*, *>
                        val widgetType = arguments?.get("widgetType") as? String
                        if (widgetType.isNullOrBlank()) {
                            result.error("INVALID_ARGUMENTS", "Missing widget type", null)
                        } else {
                            result.success(requestPinWidget(widgetType))
                        }
                    }
                    "syncSnapshot" -> {
                        val data = call.arguments as? Map<String, Any?>
                        if (data != null) {
                            HomeWidgetStorage.syncSnapshot(applicationContext, data)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing widget snapshot", null)
                        }
                    }
                    "clearSnapshot" -> {
                        HomeWidgetStorage.clearSnapshot(applicationContext)
                        result.success(true)
                    }
                    "scheduleRefresh" -> {
                        val payload = call.arguments as? Map<String, Any?>
                        val triggerAtMillis = payload
                            ?.get("triggerAtMillis") as? List<*>
                        if (triggerAtMillis != null) {
                            HomeWidgetStorage.scheduleRefresh(
                                applicationContext,
                                triggerAtMillis.mapNotNull { (it as? Number)?.toLong() }
                            )
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing widget refresh times", null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, EXAM_REMINDER_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "reconcile" -> {
                        val payload = call.arguments as? Map<*, *>
                        val fires = payload?.get("fires") as? List<*>
                        val activeExamIds = (payload?.get("activeExamIds") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.toSet()
                            ?: emptySet()
                        val activeFireKeys = if (payload?.containsKey("activeFireKeys") == true) {
                            (payload["activeFireKeys"] as? List<*>)
                                ?.mapNotNull { it as? String }
                                ?.toSet()
                                ?: emptySet()
                        } else {
                            null
                        }
                        if (fires != null) {
                            ExamReminderScheduler.reconcile(
                                applicationContext,
                                fires,
                                activeExamIds,
                                activeFireKeys,
                            )
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENTS", "Missing exam reminder fires", null)
                        }
                    }
                    "clear" -> {
                        ExamReminderScheduler.clear(applicationContext)
                        result.success(true)
                    }
                    "sendTomorrowBriefingTest" -> {
                        val args = call.arguments as? Map<*, *>
                        val title = args?.get("title") as? String ?: "明天有早八"
                        val body = args?.get("body") as? String ?: ""
                        val hasEarlyClass = args?.get("hasEarlyClass") as? Boolean ?: false
                        val firstClassStartMillis =
                            (args?.get("firstClassStartMillis") as? Number)?.toLong() ?: 0L
                        val islandA = args?.get("islandA") as? String ?: ""
                        val islandB = args?.get("islandB") as? String ?: ""
                        val calendarHour = (args?.get("calendarHour") as? Number)?.toInt() ?: 8
                        val calendarMinute = (args?.get("calendarMinute") as? Number)?.toInt() ?: 0
                        val calendarTitle = args?.get("calendarTitle") as? String ?: "早八课程"
                        val manager =
                            notificationManager ?: getSystemService(
                                Context.NOTIFICATION_SERVICE,
                            ) as? NotificationManager
                        if (manager == null) {
                            result.error("SEND_BRIEFING_TEST_FAILED", "No NotificationManager", null)
                            return@setMethodCallHandler
                        }
                        try {
                            manager.createNotificationChannel(
                                NotificationChannel(
                                    HYPERFOCUS_TEST_CHANNEL_ID,
                                    "HyperFocusApi Test",
                                    NotificationManager.IMPORTANCE_HIGH,
                                ),
                            )
                            val posted = TomorrowBriefingNotificationBuilder.post(
                                context = applicationContext,
                                notificationId = TOMORROW_BRIEFING_TEST_NOTIFICATION_ID,
                                title = title,
                                body = body,
                                tapAction = if (hasEarlyClass) {
                                    TomorrowBriefingNotificationBuilder.TAP_ACTION_OPEN_CALENDAR
                                } else {
                                    TomorrowBriefingNotificationBuilder.TAP_ACTION_OPEN_APP
                                },
                                calendarHour = calendarHour,
                                calendarMinute = calendarMinute,
                                calendarTitle = calendarTitle,
                                islandA = islandA,
                                islandB = islandB,
                                firstClassStartMillis = firstClassStartMillis,
                            )
                            if (!posted) {
                                result.success("通知权限未授予，无法发送测试通知")
                                return@setMethodCallHandler
                            }
                            // 正式岛（id=2001）与测试通知同包 focus 互斥（HyperOS
                            // FocusPlugin.isSameModule），提前告知原因而不是只报"未显示"
                            val liveIslandActive =
                                manager.activeNotifications.any { it.id == 2001 }
                            result.success(
                                if (liveIslandActive) {
                                    "测试通知已提交，但正式岛正在运行，系统可能互斥拦截；" +
                                        "可先在实时上课设置中暂停服务后再测"
                                } else {
                                    null
                                },
                            )
                        } catch (e: Exception) {
                            Log.e("ExamReminder", "sendTomorrowBriefingTest failed", e)
                            result.error("SEND_BRIEFING_TEST_FAILED", e.message, null)
                        }
                    }
                    "openSystemCalendarEvent" -> {
                        val args = call.arguments as? Map<*, *>
                        val hour = (args?.get("hour") as? Number)?.toInt()
                        val minute = (args?.get("minute") as? Number)?.toInt()
                        val message = args?.get("message") as? String
                        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                            result.error("INVALID_ARGUMENTS", "Invalid calendar event time", null)
                            return@setMethodCallHandler
                        }
                        val now = Calendar.getInstance()
                        val begin = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            if (!after(now)) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        val end = (begin.clone() as Calendar).apply {
                            add(Calendar.HOUR_OF_DAY, 1)
                        }
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = android.provider.CalendarContract.Events.CONTENT_URI
                            putExtra(android.provider.CalendarContract.Events.TITLE, message ?: "早八课程")
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.timeInMillis)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "早八课程起床提醒")
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                        }
                        try {
                            if (intent.resolveActivity(packageManager) == null) {
                                result.success(false)
                                return@setMethodCallHandler
                            }
                            startActivity(intent)
                            result.success(true)
                        } catch (_: ActivityNotFoundException) {
                            result.success(false)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SUPPORT_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "enqueueSystemDownload" -> {
                        val arguments = call.arguments as? Map<*, *>
                        val url = arguments?.get("url") as? String
                        val fileName = arguments?.get("fileName") as? String
                        val title = arguments?.get("title") as? String
                        val description = arguments?.get("description") as? String
                        if (url.isNullOrBlank()) {
                            result.error("INVALID_ARGUMENTS", "Missing download url", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val downloadId = enqueueSystemDownload(
                                url = url,
                                fileName = fileName,
                                title = title,
                                description = description,
                            )
                            result.success(downloadId)
                        } catch (e: Exception) {
                            result.error("DOWNLOAD_ENQUEUE_FAILED", e.message, null)
                        }
                    }
                    "getSystemDownloadProgress" -> {
                        val arguments = call.arguments as? Map<*, *>
                        val downloadId = (arguments?.get("downloadId") as? Number)?.toLong()
                        if (downloadId == null) {
                            result.error("INVALID_ARGUMENTS", "Missing download id", null)
                            return@setMethodCallHandler
                        }
                        try {
                            result.success(querySystemDownloadProgress(downloadId))
                        } catch (e: Exception) {
                            result.error("DOWNLOAD_QUERY_FAILED", e.message, null)
                        }
                    }
                    "saveImageToGallery" -> {
                        val arguments = call.arguments as? Map<*, *>
                        val bytes = arguments?.get("bytes") as? ByteArray
                        val fileName = arguments?.get("fileName") as? String ?: "qingyu_kebiao.png"
                        val mimeType = arguments?.get("mimeType") as? String ?: "image/png"
                        if (bytes == null || bytes.isEmpty()) {
                            result.error("INVALID_ARGUMENTS", "Missing image bytes", null)
                            return@setMethodCallHandler
                        }
                        try {
                            val savedUri = saveImageToGallery(bytes, fileName, mimeType)
                            if (savedUri == null) {
                                result.error("SAVE_FAILED", "Failed to save image to gallery", null)
                            } else {
                                result.success(savedUri)
                            }
                        } catch (e: Exception) {
                            result.error("SAVE_FAILED", e.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, MIGRATION_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "findInstalledPackage" -> {
                        val packageNames = (call.arguments as? List<*>)?.mapNotNull {
                            it as? String
                        } ?: emptyList()
                        result.success(findInstalledPackage(packageNames))
                    }
                    "openPackage" -> {
                        val packageName = call.arguments as? String
                        if (packageName.isNullOrBlank()) {
                            result.success(false)
                        } else {
                            result.success(openPackage(packageName))
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LAN_EDIT_CHANNEL)
            .also { lanEditChannel = it }
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startLanEditForeground" -> {
                        try {
                            startLanEditForegroundService()
                            result.success(true)
                        } catch (e: Exception) {
                            val tag = when (e) {
                                is SecurityException -> DiagnosticLogMessages.LOG_LAN_FOREGROUND_START_DENIED
                                else -> DiagnosticLogMessages.LOG_LAN_FOREGROUND_START_FAILED
                            }
                            Log.e("MainActivity", tag, e)
                            result.error(
                                "START_FOREGROUND_FAILED",
                                e.message,
                                e.javaClass.simpleName,
                            )
                        }
                    }
                    "stopLanEditForeground" -> {
                        stopLanEditForegroundService()
                        result.success(true)
                    }
                    "getPendingLanEditOpen" -> {
                        val pending = pendingOpenLanEdit
                        pendingOpenLanEdit = false
                        result.success(pending)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    /**
     * Debug-only deep links for adb / Android CLI automation.
     * Scheme: mikcb-debug://path?query=value
     * Only honored on non-release package ids (*.debug / *.profile).
     */
    private fun handleDebugDeepLinkIntent(intent: Intent) {
        if (!isDebugAutomationPackage()) {
            return
        }
        val action = intent.action ?: return
        if (action != Intent.ACTION_VIEW) {
            return
        }
        val data = intent.data ?: return
        if (data.scheme != "mikcb-debug") {
            return
        }
        // Prefer path-absolute form: mikcb-debug:///settings/live
        // (empty host). Host-based form mikcb-debug://settings/live is also
        // accepted for convenience.
        val host = data.host?.trim().orEmpty()
        val pathSegment = data.path
            ?.trim()
            .orEmpty()
            .removePrefix("/")
        val path = when {
            host.isEmpty() && pathSegment.isEmpty() -> "home"
            host.isEmpty() -> pathSegment
            pathSegment.isEmpty() -> host
            else -> "$host/$pathSegment"
        }.trim().removePrefix("/")
        if (path.isEmpty()) {
            return
        }
        val query = mutableMapOf<String, String>()
        for (name in data.queryParameterNames) {
            val value = data.getQueryParameter(name) ?: continue
            query[name] = value
        }
        pendingDebugRoute = mapOf(
            "path" to path,
            "query" to query,
        )
        notifyDebugRouteReceived()
    }

    private fun isDebugAutomationPackage(): Boolean {
        val packageName = applicationContext.packageName
        return packageName.endsWith(".debug") || packageName.endsWith(".profile")
    }

    private fun notifyDebugRouteReceived() {
        try {
            flutterChannel?.invokeMethod("onDebugRouteReceived", null)
        } catch (error: Exception) {
            Log.w("MainActivity", "notifyDebugRouteReceived failed", error)
        }
    }

    private fun handleExternalImportIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return

        // Debug automation deep links share ACTION_VIEW with file imports.
        // Never open them as content URIs — that floods logcat with
        // FileNotFoundException: No content provider: mikcb-debug://...
        val dataScheme = intent.data?.scheme
        if (dataScheme == "mikcb-debug") {
            return
        }

        val uri = resolveImportUri(intent) ?: return
        if (uri.scheme == "mikcb-debug") {
            return
        }
        val mimeType = intent.type?.takeIf { it.isNotBlank() }
            ?: contentResolver.getType(uri)
        val fileName = resolveImportDisplayName(uri)
        val bytes = readImportBytes(uri) ?: return
        val kind = detectImportKind(fileName, mimeType, bytes) ?: return

        val pending = when (kind) {
            "ics", "backup" -> {
                val text = bytes.toString(Charsets.UTF_8)
                if (kind == "ics" && !text.contains("VCALENDAR", ignoreCase = true)) {
                    return
                }
                if (kind == "backup" && !text.contains("\"mikcb\"")) {
                    return
                }
                PendingExternalImport(kind = kind, fileName = fileName, textContent = text)
            }
            "spreadsheet" -> {
                val cachedFile = copyBytesToImportCache(bytes, fileName) ?: return
                PendingExternalImport(
                    kind = kind,
                    fileName = fileName,
                    filePath = cachedFile.absolutePath,
                )
            }
            else -> return
        }

        pendingExternalImport = pending
        notifyExternalImportReceived()
    }

    private fun resolveImportUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND -> extractSendStreamUri(intent)
            else -> intent.data
        }
    }

    private fun extractSendStreamUri(intent: Intent): Uri? {
        // Plain-text shares use EXTRA_TEXT and have no stream URI. File shares (CSV, ICS,
        // JSON, XLSX, etc.) always attach EXTRA_STREAM regardless of MIME type.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun resolveImportDisplayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            val name = cursor.getString(index)?.trim().orEmpty()
                            if (name.isNotEmpty()) {
                                return name
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "${DiagnosticLogMessages.LOG_RESOLVE_IMPORT_DISPLAY_NAME_FAILED}：$uri", e)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "import" }
    }

    private fun readImportBytes(uri: Uri): ByteArray? {
        if (uri.scheme == "mikcb-debug") {
            return null
        }
        return try {
            contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        } catch (e: Exception) {
            Log.e("MainActivity", "${DiagnosticLogMessages.LOG_READ_IMPORT_BYTES_FAILED}：$uri", e)
            null
        }
    }

    private fun detectImportKind(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val normalizedMime = mimeType?.lowercase(Locale.US)
        val textPreview = if (bytes.size <= 65536) {
            bytes.toString(Charsets.UTF_8)
        } else {
            bytes.copyOf(65536).toString(Charsets.UTF_8)
        }

        when {
            extension == "ics" || normalizedMime?.startsWith("text/calendar") == true -> {
                return "ics"
            }
            extension == "mikcb" -> return "backup"
            extension == "json" || normalizedMime == "application/json" -> return "backup"
            extension == "csv" ||
                normalizedMime == "text/csv" ||
                normalizedMime == "text/comma-separated-values" -> {
                return "spreadsheet"
            }
            extension == "xlsx" ||
                normalizedMime ==
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                return "spreadsheet"
            }
        }

        if (normalizedMime == "application/octet-stream" || normalizedMime == "*/*") {
            when (extension) {
                "ics" -> return "ics"
                "mikcb", "json" -> return "backup"
                "csv", "xlsx" -> return "spreadsheet"
            }
        }

        if (textPreview.contains("VCALENDAR", ignoreCase = true)) {
            return "ics"
        }
        if (textPreview.trimStart().startsWith("{") && textPreview.contains("\"mikcb\"")) {
            return "backup"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (extension == "xlsx" || extension.isEmpty())
        ) {
            return "spreadsheet"
        }

        return null
    }

    private fun copyBytesToImportCache(bytes: ByteArray, fileName: String): File? {
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val cacheRoot = File(cacheDir, "external_imports").apply { mkdirs() }
            val target = File(cacheRoot, "${System.currentTimeMillis()}_$safeName")
            target.outputStream().use { output -> output.write(bytes) }
            target
        } catch (e: Exception) {
            Log.e("MainActivity", DiagnosticLogMessages.LOG_CACHE_EXTERNAL_IMPORT_FAILED, e)
            null
        }
    }

    private fun notifyExternalImportReceived() {
        try {
            flutterChannel?.invokeMethod("onExternalImportReceived", null)
        } catch (_: Exception) {
        }
    }

    private fun canRequestPinWidget(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        return appWidgetManager?.isRequestPinAppWidgetSupported == true
    }

    private fun requestPinWidget(widgetType: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "unsupported"
        }
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
            ?: return "unsupported"
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            return "unsupported"
        }
        val provider = resolveWidgetProvider(widgetType) ?: return "invalid_widget_type"
        return if (appWidgetManager.requestPinAppWidget(provider, null, null)) {
            "requested"
        } else {
            "failed"
        }
    }

    private fun resolveWidgetProvider(widgetType: String): ComponentName? {
        val providerClass = when (widgetType) {
            "compact" -> TodayCompactWidgetProvider::class.java
            "mini_list" -> TodayMiniListWidgetProvider::class.java
            "medium" -> TodayMediumWidgetProvider::class.java
            "large" -> TodayLargeWidgetProvider::class.java
            else -> null
        } ?: return null
        return ComponentName(this, providerClass)
    }

    private fun enqueueSystemDownload(
        url: String,
        fileName: String?,
        title: String?,
        description: String?,
    ): Long {
        val downloadManager =
            getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager unavailable")
        val resolvedFileName = sanitizeDownloadFileName(
            fileName?.takeIf { it.isNotBlank() }
                ?: URLUtil.guessFileName(url, null, "application/vnd.android.package-archive")
        )
        cleanupManagedUpdateDownloads(downloadManager)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType("application/vnd.android.package-archive")
            setTitle(title?.takeIf { it.isNotBlank() } ?: resolvedFileName)
            if (!description.isNullOrBlank()) {
                setDescription(description)
            }
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setVisibleInDownloadsUi(true)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                resolvedFileName
            )
        }
        val downloadId = downloadManager.enqueue(request)
        rememberManagedUpdateDownload(downloadId)
        return downloadId
    }

    private fun querySystemDownloadProgress(downloadId: Long): Map<String, Any?> {
        val downloadManager =
            getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager unavailable")
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return mapOf(
                    "status" to "unknown",
                    "downloadedBytes" to 0L,
                    "totalBytes" to -1L,
                    "reason" to null,
                )
            }

            val status = when (
                cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
            ) {
                DownloadManager.STATUS_PENDING -> "pending"
                DownloadManager.STATUS_RUNNING -> "running"
                DownloadManager.STATUS_PAUSED -> "paused"
                DownloadManager.STATUS_SUCCESSFUL -> "successful"
                DownloadManager.STATUS_FAILED -> "failed"
                else -> "unknown"
            }
            val downloadedBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val totalBytes = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else null
            return mapOf(
                "status" to status,
                "downloadedBytes" to downloadedBytes,
                "totalBytes" to totalBytes,
                "reason" to reason,
            )
        }
    }

    private fun sanitizeDownloadFileName(fileName: String): String {
        val trimmed = fileName.trim().ifEmpty { "mikcb_update.apk" }
        val normalized = trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (normalized.lowercase().endsWith(".apk")) {
            normalized
        } else {
            "$normalized.apk"
        }
    }

    private fun cleanupManagedUpdateDownloads(downloadManager: DownloadManager) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trackedIds = prefs.getStringSet(KEY_MANAGED_UPDATE_DOWNLOAD_IDS, emptySet()).orEmpty()
        val ids = trackedIds.mapNotNull { it.toLongOrNull() }.toLongArray()
        if (ids.isNotEmpty()) {
            runCatching {
                downloadManager.remove(*ids)
            }
        }
        prefs.edit().remove(KEY_MANAGED_UPDATE_DOWNLOAD_IDS).apply()
    }

    private fun rememberManagedUpdateDownload(downloadId: Long) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_MANAGED_UPDATE_DOWNLOAD_IDS, setOf(downloadId.toString()))
            .apply()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                // Retry any reminder whose alarm fired while notification
                // permission was denied, without rebuilding past fires in
                // Flutter (which could duplicate successful notifications).
                ExamReminderScheduler.handleBootReschedule(applicationContext)
            }
            permissionResult?.success(granted)
            permissionResult = null
        }
    }

    private fun checkPromotedSupport(): Map<String, Any> {
        return mapOf(
            "androidVersion" to Build.VERSION.SDK_INT,
            "hasNotificationPermission" to hasNotificationPermission(),
            "hasPromotedPermission" to isPromotedPermissionDeclared(),
            "canPostPromoted" to canPostPromotedNotifications(),
        )
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun recordHyperFocusTestResult(stage: String, succeeded: Boolean, message: String) {
        getSharedPreferences("hyper_focus_test", Context.MODE_PRIVATE)
            .edit()
            .putString("last_stage", stage)
            .putBoolean("last_succeeded", succeeded)
            .putString("last_message", message)
            .putLong("last_at_millis", System.currentTimeMillis())
            .apply()
    }

    private fun sendTestFocusNotification(
        args: Map<String, String>?,
        onDone: (String?) -> Unit,
    ) {
        val stage = args?.get("stage") ?: "pre"
        val failure = try {
            sendTestFocusNotificationInner(args)
        } catch (e: Throwable) {
            Log.e("HyperFocusApi", "sendTestFocus failed", e)
            "发送异常：${e.message ?: e.javaClass.simpleName}"
        }
        if (failure != null) {
            recordHyperFocusTestResult(stage, false, failure)
            onDone(failure)
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeIds = notificationManager.activeNotifications.map { it.id }
            val testChannelState = notificationManager.getNotificationChannel(HYPERFOCUS_TEST_CHANNEL_ID)
            val liveChannelState = notificationManager.getNotificationChannel("live_update_channel")
            Log.d(
                "HyperFocusApi",
                "post-inspect: activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}",
            )
            val activeContainsTest = activeIds.contains(10001)
            UmengDiagnosticReporter.record(
                context = applicationContext,
                category = "send_test_focus_submitted",
                message = DiagnosticLogMessages.SEND_TEST_FOCUS_SUBMITTED,
                extras = mapOf(
                    "stage" to stage,
                    "activeIds" to activeIds,
                    "activeContainsTest" to activeContainsTest,
                    "testChannelImportance" to (testChannelState?.importance ?: -1),
                    "liveChannelImportance" to (liveChannelState?.importance ?: -1),
                )
            )
            if (!activeContainsTest) {
                recordHyperFocusTestResult(
                    stage,
                    false,
                    "已提交但系统未显示（activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}）",
                )
                onDone("已提交但系统未显示（activeIds=$activeIds testChannel=${testChannelState?.importance} liveChannel=${liveChannelState?.importance}）")
            } else {
                recordHyperFocusTestResult(stage, true, "")
                onDone(null)
            }
        }, 400L)
    }

    private fun sendTestFocusNotificationInner(args: Map<String, String>?): String? {
        return try {
            val courseName = args?.get("courseName") ?: "高等数学"
            val shortName = args?.get("shortName") ?: courseName
            val startTime = args?.get("startTime") ?: "08:00"
            val endTime = args?.get("endTime") ?: "09:40"
            val location = args?.get("location") ?: "教科A-101"
            val teacher = args?.get("teacher") ?: ""
            val stage = args?.get("stage") ?: "pre"
            Log.d("HyperFocusApi", "sendTestFocus stage=$stage")

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                Log.e("HyperFocusApi", "notifications disabled")
                UmengDiagnosticReporter.record(
                    context = applicationContext,
                    category = "send_test_focus_permission_blocked",
                    message = DiagnosticLogMessages.SEND_TEST_FOCUS_PERMISSION_BLOCKED,
                    extras = mapOf("stage" to stage)
                )
                return "系统通知权限未开启，请先在设置中开启通知权限"
            }

            val now = System.currentTimeMillis()
            val showCountdown = args?.get("showCountdown")?.toBooleanStrictOrNull() ?: true
            val templateStage = when (stage) {
                "active", "beforeEnd", "duringClass", "duringClassStatusBar" -> "active"
                "post", "afterClass" -> "post"
                else -> "pre"
            }
            val startAtMillis = args?.get("startAtMillis")?.toLongOrNull()?.takeIf { it > 0L }
            val endAtMillis = args?.get("endAtMillis")?.toLongOrNull()?.takeIf { it > 0L }
            val realStart = startAtMillis ?: buildCourseTimeMillis(startTime)
            val realEnd = endAtMillis ?: buildCourseTimeMillis(endTime)
            val hasRealTime = realStart != null && realEnd != null && realEnd > realStart
            // 大课拆小节断点（相对 startAtMillis 的毫秒偏移），用于测试时把倒计时指向下一小节下课点
            val progressBreakOffsetsMillis = args?.get("progressBreakOffsetsMillis")
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?: emptyList()
            val nextMilestoneAtMillis = if (realStart != null && progressBreakOffsetsMillis.isNotEmpty()) {
                progressBreakOffsetsMillis
                    .map { realStart + it }
                    .filter { it > now }
                    .minOrNull()
            } else {
                null
            }
            val classStartAt: Long
            val classEndAt: Long
            val timerTarget: Long
            val hintText: String
            when (templateStage) {
                "active" -> {
                    classStartAt = now - 10 * 60_000L
                    classEndAt = now + 5 * 60_000L
                    timerTarget = nextMilestoneAtMillis ?: classEndAt
                    hintText = "距下课还有 5 分钟"
                }
                "post" -> {
                    classStartAt = now - 20 * 60_000L
                    classEndAt = now - 60_000L
                    timerTarget = 0L
                    hintText = "已下课"
                }
                else -> {
                    when {
                        hasRealTime && realStart!! > now -> {
                            classStartAt = realStart
                            classEndAt = realEnd!!
                            timerTarget = classStartAt
                            hintText = "距离上课还有 ${((classStartAt - now) / 60_000L + 1)} 分钟"
                        }
                        hasRealTime && realEnd!! > now -> {
                            classStartAt = realEnd
                            classEndAt = realEnd
                            timerTarget = nextMilestoneAtMillis ?: classEndAt
                            hintText = "距下课还有 ${((timerTarget - now) / 60_000L + 1)} 分钟"
                        }
                        else -> {
                            classStartAt = now + 5 * 60_000L
                            classEndAt = classStartAt + 100 * 60_000L
                            timerTarget = classStartAt
                            hintText = "距离上课还有 5 分钟"
                        }
                    }
                }
            }

            UmengDiagnosticReporter.record(
                context = applicationContext,
                category = "send_test_focus_started",
                message = DiagnosticLogMessages.SEND_TEST_FOCUS_STARTED,
                extras = mapOf(
                    "stage" to stage,
                    "courseName" to courseName,
                    "shortName" to shortName,
                    "startTime" to startTime,
                    "endTime" to endTime,
                    "location" to location,
                    "templateStage" to templateStage,
                    "classStartAt" to classStartAt,
                    "classEndAt" to classEndAt,
                    "timerTarget" to timerTarget,
                    "now" to now,
                )
            )

            val templates = loadHyperFocusTemplates(this)

            // 与 XiaomiSuperIslandNotificationRenderer.buildHyperFocusBundle 同步：
            // 倒计时/正计时按阶段实时计算，否则模板里的 {倒计时}/{正计时} 变量
            // 会被 resolveTemplate 替换为空并整段丢弃，测试通知与正式渲染不一致。
            val activeTarget = nextMilestoneAtMillis ?: classEndAt
            val countdownText = when {
                templateStage == "post" -> ""
                templateStage == "pre" ->
                    formatCountdownForTemplate((classStartAt - now).coerceAtLeast(0L))
                else ->
                    formatCountdownForTemplate((activeTarget - now).coerceAtLeast(0L))
            }
            val elapsedText = if (templateStage == "active") {
                formatElapsedForTemplate((now - classStartAt).coerceAtLeast(0L))
            } else {
                ""
            }

            val r = { tpl: String ->
                resolveTemplate(
                    tpl = tpl,
                    courseName = courseName,
                    shortName = shortName,
                    location = location,
                    teacher = teacher,
                    startTime = startTime,
                    endTime = endTime,
                    countdownText = countdownText,
                    elapsedText = elapsedText,
                )
            }

            val tickerText = r(templates["ticker_$templateStage"] ?: "")
            val islandAText = r(templates["islandA_$templateStage"] ?: "")
            val islandBRaw = templates["islandB_$templateStage"] ?: ""
            val baseTitleText = r(templates["baseTitle_$templateStage"] ?: "")
            val baseContentText = r(templates["baseContent_$templateStage"] ?: "")
            val baseSubcontentText = r(templates["baseSubcontent_$templateStage"] ?: "")
            val hintTitleText = r(templates["hintTitle_$templateStage"] ?: "")
            val hintContentText = r(templates["hintContent_$templateStage"] ?: "")
            val hintSubcontentText = r(templates["hintSubcontent_$templateStage"] ?: "")
            val hintSubtitleText = r(templates["hintSubtitle_$templateStage"] ?: "")

            val launchAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            val openAppUri = launchAppIntent.toUri(Intent.URI_INTENT_SCHEME)

            // 与 XiaomiSuperIslandNotificationRenderer 同步：B 区槽位决策与 hint 区
            // 计时有效性判定共用 HyperFocusTemplates 中的同一实现（同步纪律）
            val islandBSlot = resolveIslandBSlot(
                rawTemplate = islandBRaw,
                showCountdown = showCountdown,
                isPost = templateStage == "post",
                timerWhenMillis = timerTarget,
                nowMillis = now,
                resolve = r,
            )
            val extras = FocusNotification.buildV3 {
                business = "course_schedule"
                updatable = true
                enableFloat = true
                // 与 XiaomiSuperIslandNotificationRenderer 同步：同 id cancel 后重发需显式重新上岛
                reopen = HYPER_FOCUS_REOPEN_VALUE
                ticker = tickerText
                aodTitle = tickerText
                islandFirstFloat = true
                outEffectSrc = if (args?.get("hfOutEffectStatusEnabled")?.toBooleanStrictOrNull() ?: false) "outer_glow" else ""
                outEffectColor = if (args?.get("hfOutEffectStatusEnabled")?.toBooleanStrictOrNull() ?: false) (args?.get("hfOutEffectStatusColor") ?: "") else ""

                baseInfo {
                    type = 2
                    title = baseTitleText
                    content = listOfNotNull(
                        baseContentText.ifBlank { null },
                        baseSubcontentText.ifBlank { null },
                    ).joinToString(" · ")
                }

                picInfo {
                    if (args?.get("hfIconAEnabled")?.toBooleanStrictOrNull() ?: false) {
                        type = 1
                    }
                }

                // 字段映射与 XiaomiSuperIslandNotificationRenderer.buildHyperFocusBundle 保持一致：
                // title ← 主要小文本1，content ← 前置文本1；
                // specialTitle ← 前置文本2，subTitle ← 主要小文本2。
                hintInfo {
                    type = 2
                    // 测试路径与正式路径一致：倒计时模板由 title 承载；正式
                    // 服务会按秒重发通知，避免 HyperOS 的 hintInfo 计时快照停滞。
                    title = hintTitleText
                    content = hintContentText.ifBlank { hintText }
                    subTitle = hintSubtitleText
                    specialTitle = hintSubcontentText

                    actionInfo {
                        actionIntentType = 1
                        actionIntent = openAppUri
                        actionTitle = "查看课表"
                    }
                }

                island {
                    islandProperty = 1

                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            textInfo {
                                title = islandAText
                                showHighlightColor = true
                            }
                            picInfo {
                                if (args?.get("hfIconAEnabled")?.toBooleanStrictOrNull() ?: false) {
                                    type = 1
                                }
                            }
                            if (templateStage == "active" && classEndAt > classStartAt && now >= classStartAt) {
                                progressInfo {
                                    progress = ((now - classStartAt) * 100 / (classEndAt - classStartAt)).toInt().coerceIn(0, 100)
                                    colorReach = args?.get("hfOutEffectStatusColor")?.toString().takeIf { it.isNullOrBlank().not() } ?: "#FFFFFFFF"
                                }
                            }
                        }

                        when (val slot = islandBSlot) {
                            is IslandBSlot.SystemTimerDigits -> sameWidthDigitInfo {
                                timerInfo {
                                    timerType = -1
                                    timerWhen = slot.timerWhenMillis
                                    timerSystemCurrent = now
                                }
                                if (slot.label.isNotEmpty()) content = slot.label
                                turnAnim = true
                                showHighlightColor = true
                            }
                            is IslandBSlot.StaticDigits -> sameWidthDigitInfo {
                                content = slot.content
                                turnAnim = true
                                showHighlightColor = true
                            }
                            is IslandBSlot.IslandText -> imageTextInfoRight {
                                textInfo {
                                    title = slot.content
                                    showHighlightColor = true
                                }
                            }
                            IslandBSlot.None -> {}
                        }
                    }

                    smallIslandArea {
                        combinePicInfo {
                            picInfo {
                                type = 1
                            }
                            if (templateStage == "active" && classEndAt > classStartAt && now >= classStartAt) {
                                progressInfo {
                                    progress = ((now - classStartAt) * 100 / (classEndAt - classStartAt)).toInt().coerceIn(0, 100)
                                    colorReach = args?.get("hfOutEffectStatusColor")?.toString().takeIf { it.isNullOrBlank().not() } ?: "#FFFFFFFF"
                                }
                            }
                        }
                    }

                    shareData {
                        title = courseName
                        content = location
                    }
                }
            }

            if (args?.get("hfOutEffectStatusEnabled")?.toBooleanStrictOrNull() ?: false) {
                extras.putString("miui.bigIsland.effect.src", "outer_glow")
                extras.putString("miui.effect.src", "outer_glow")
            }

            notificationManager.createNotificationChannel(
                NotificationChannel(HYPERFOCUS_TEST_CHANNEL_ID, "HyperFocusApi Test", NotificationManager.IMPORTANCE_HIGH)
            )
            val channel = notificationManager.getNotificationChannel(HYPERFOCUS_TEST_CHANNEL_ID)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.e("HyperFocusApi", "test channel blocked, importance=${channel?.importance}")
                UmengDiagnosticReporter.record(
                    context = applicationContext,
                    category = "send_test_focus_channel_blocked",
                    message = DiagnosticLogMessages.SEND_TEST_FOCUS_CHANNEL_BLOCKED,
                    extras = mapOf(
                        "stage" to stage,
                        "channelImportance" to (channel?.importance ?: -1),
                    )
                )
                return "测试通知渠道已被关闭，请在系统通知设置中恢复该渠道"
            }

            val notification = Notification.Builder(this, HYPERFOCUS_TEST_CHANNEL_ID)
                .setContentTitle(baseTitleText.ifBlank { courseName })
                .setContentText(baseContentText.ifBlank { "查看课表" })
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setAutoCancel(false)
                .addExtras(extras)
                .build()

            notificationManager.notify(10001, notification)
            val dismissAt = if (timerTarget > 0L) {
                minOf(timerTarget, now + 30 * 60_000L)
            } else {
                // post 阶段无计时目标：与正式路径一致，保留固定课后窗口。
                now + LiveUpdateService.AFTER_CLASS_DISPLAY_WINDOW_MILLIS
            }
            TestFocusNotificationDismiss.schedule(applicationContext, dismissAt)
            Log.d("HyperFocusApi", "notify(10001) called, stage=$stage")
            // 正式岛（id=2001）与测试岛同包 focus 互斥（HyperOS FocusPlugin.isSameModule）：
            // 服务运行中时测试通知可能被系统拒绝上岛，提前告知原因而不是只报"未显示"
            val liveIslandActive =
                notificationManager.activeNotifications.any { it.id == 2001 }
            if (liveIslandActive) {
                return "测试通知已提交，但正式岛正在运行，系统可能互斥拦截；" +
                    "可先在实时上课设置中暂停服务后再测"
            }
            null
        } catch (e: Exception) {
            Log.e("HyperFocusApi", "sendTestFocus failed", e)
            UmengDiagnosticReporter.report(
                context = applicationContext,
                category = "send_test_focus_failed",
                message = DiagnosticLogMessages.SEND_TEST_FOCUS_FAILED,
                throwable = e,
                dedupeKey = "send_test_focus_failed",
                extras = mapOf(
                    "courseName" to (args?.get("courseName") ?: "高等数学"),
                    "stage" to (args?.get("stage") ?: "pre"),
                )
            )
            throw e
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        } catch (e: Exception) {
            Log.w("MainActivity", DiagnosticLogMessages.LOG_OPEN_NOTIFICATION_SETTINGS_FAILED, e)
            openAppDetailsSettings()
        }
    }

    private fun openPromotedSettings() {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Fallback below.
            }
        }

        openNotificationSettings()
    }

    private fun openAutoStartSettings() {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val intents = mutableListOf<Intent>()

        when (brand) {
            "xiaomi", "poco", "redmi" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                intents += Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", packageName)
                    putExtra("package_name", packageName)
                }
            }
            "oppo", "realme", "oneplus" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                }
                if (brand == "oneplus") {
                    intents += Intent().apply {
                        component = ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    }
                }
            }
            "vivo" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            "huawei", "honor" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
            }
            "samsung" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
            }
            "asus" -> {
                intents += Intent().apply {
                    component = ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                }
                intents += Intent().apply {
                    component = ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                    )
                }
            }
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Try the next screen.
            }
        }

        openAppDetailsSettings()
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
                return
            } catch (_: Exception) {
                // Fallback below.
            }

            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                return
            } catch (_: Exception) {
                // Fallback below.
            }
        }

        openAppDetailsSettings()
    }

    private fun openAppDetailsSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            Log.w("MainActivity", DiagnosticLogMessages.LOG_OPEN_APP_DETAILS_FAILED, e)
        }
    }

    private fun findInstalledPackage(packageNames: List<String>): String? {
        for (packageName in packageNames) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                return packageName
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun openPackage(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveImageToGallery(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String? {
        val safeFileName = if (fileName.contains(".")) fileName else "$fileName.png"
        val resolver = applicationContext.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeFileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/${getString(R.string.pictures_folder_name)}"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    output.flush()
                } ?: return null
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        @Suppress("DEPRECATION")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        @Suppress("DEPRECATION")
        val inserted = MediaStore.Images.Media.insertImage(
            resolver,
            bitmap,
            safeFileName,
            getString(R.string.payment_qr_description)
        )
        return inserted?.takeIf { it.isNotBlank() }
    }

    private fun isPromotedPermissionDeclared(): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }

            packageInfo.requestedPermissions
                ?.contains(POST_PROMOTED_NOTIFICATIONS_PERMISSION) == true
        } catch (e: Exception) {
            Log.w("MainActivity", DiagnosticLogMessages.LOG_INSPECT_PROMOTED_PERMISSION_FAILED, e)
            false
        }
    }

    private fun canPostPromotedNotifications(): Boolean {
        return Build.VERSION.SDK_INT >= 36 &&
            notificationManager?.canPostPromotedNotifications() == true
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_live_update_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_live_update_desc)
            }
            notificationManager?.createNotificationChannel(channel)
            ExamReminderScheduler.ensureChannel(this)
        }
    }

    private fun startLiveUpdateService(data: Map<String, Any>) {
        try {
            UmengDiagnosticReporter.record(
                context = applicationContext,
                category = "live_update_start_requested",
                message = DiagnosticLogMessages.LIVE_UPDATE_START_REQUESTED,
                extras = mapOf(
                    "stage" to data["stage"],
                    "hasCurrentCourse" to (data["currentCourse"] != null),
                    "hasNextCourse" to (data["nextCourse"] != null),
                )
            )
            val intent = LiveUpdateScheduler.buildServiceIntentFromMethodPayload(this, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            UmengDiagnosticReporter.report(
                context = applicationContext,
                category = "live_update_start_failed",
                message = DiagnosticLogMessages.LIVE_UPDATE_START_FAILED_CHANNEL,
                throwable = e,
                dedupeKey = "live_update_start_failed",
            )
            throw e
        }
    }

    private fun stopLiveUpdateService() {
        UmengDiagnosticReporter.record(
            context = applicationContext,
            category = "live_update_stop_requested",
            message = DiagnosticLogMessages.LIVE_UPDATE_STOP_REQUESTED,
        )
        stopService(Intent(this, LiveUpdateService::class.java))
        // Re-arm the next future trigger instead of cancelling it outright.
        // Flutter calls stopLiveUpdate on every refresh without an active
        // course; dropping the exact alarm here would leave only the 15-min
        // WorkManager backup, delaying the next before-class reminder.
        // (reschedule cancels the old alarm itself and honors holiday /
        // suspension state; with a cleared snapshot it is a no-op.)
        LiveUpdateScheduler.reschedule(applicationContext, allowImmediateStart = false)
    }

    private fun startLanEditForegroundService() {
        val intent = LanEditForegroundService.buildStartIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLanEditForegroundService() {
        stopService(
            Intent(this, LanEditForegroundService::class.java).apply {
                action = LanEditForegroundService.ACTION_STOP
            },
        )
    }

    private fun handleLanEditIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(LanEditForegroundService.EXTRA_OPEN_LAN_EDIT, false) != true) {
            return
        }
        pendingOpenLanEdit = true
        intent.removeExtra(LanEditForegroundService.EXTRA_OPEN_LAN_EDIT)
        try {
            lanEditChannel?.invokeMethod("onLanEditNotificationTapped", null)
        } catch (_: Exception) {
        }
    }

    private fun persistHideFromRecents(hidden: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_FROM_RECENTS, hidden)
            .apply()
    }

    private fun isHideFromRecentsEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_FROM_RECENTS, false)
    }

    private fun applyPersistedHideFromRecents() {
        setHideFromRecents(isHideFromRecentsEnabled())
    }

    private fun setHideFromRecents(hidden: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        try {
            val activityManager = getSystemService(ActivityManager::class.java)
            activityManager?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(hidden)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", DiagnosticLogMessages.LOG_UPDATE_RECENTS_VISIBILITY_FAILED, e)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("package_name", packageName)
                    putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                    putExtra(
                        "android.intent.extra.COMPONENT_NAME",
                        ComponentName(
                            this@MainActivity,
                            KeepAliveAccessibilityService::class.java
                        ).flattenToString()
                    )
                }
            )
            return
        } catch (e: ActivityNotFoundException) {
            // Fall through to the general accessibility settings page.
        } catch (_: Exception) {
            // Fall through to the general accessibility settings page.
        }

        try {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
        }
    }

    private fun isAutoStartEnabled(): Boolean {
        // 1. 尝试 AppOps 反射检测（小米/OPPO/Vivo/一加 等）
        val appOpsResult = checkAutoStartViaAppOps()
        if (appOpsResult != null) return appOpsResult

        // 2. 回退到电池优化检测（三星/华为/荣耀/通用）
        val batteryResult = checkAutoStartViaBattery()
        if (batteryResult != null) return batteryResult

        // 3. 兜底：乐观默认
        return true
    }

    /**
     * 通过 AppOps 反射检测自启动状态。
     * 适用：小米 (MIUI)、OPPO (ColorOS)、Vivo、一加 (OxygenOS) 等。
     * 不适用时返回 null（如 Pixel 等原生 Android）。
     */
    private fun checkAutoStartViaAppOps(): Boolean? {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            // OP_AUTO_START = 10008 (小米/OPPO/Vivo 等厂商通用)
            val result = method.invoke(
                appOps, 10008, android.os.Process.myUid(), packageName
            ) as Int
            when (result) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED -> false
                else -> null // MODE_DEFAULT 等，说明此 OP 不适用于当前设备
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通过电池优化状态间接推断。
     * 适用：三星（sleeping apps）、华为/荣耀（EMUI）等使用电池策略限制后台的厂商。
     * 对于 Pixel 等原生设备也适用（但通常不需要自启动权限）。
     */
    private fun checkAutoStartViaBattery(): Boolean? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) {
            null
        }
    }

    private fun isKeepAliveAccessibilityEnabled(): Boolean {
        return KeepAliveAccessibilityStatus.isEnabled(this)
    }

    private fun readDisplayCornerRadiusDp(): Double {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = window.decorView.rootWindowInsets
            val corner = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            val radiusPx = corner?.radius ?: 0
            if (radiusPx > 0) {
                return radiusPx.toDouble() / resources.displayMetrics.density.toDouble()
            }
        }
        return 28.0
    }

    /**
     * Soft edge-arrival tick (CLOCK_TICK). Kept for optional native callers;
     * Dart currently uses Flutter [HapticFeedback.selectionClick] for intensity.
     */
    private fun performEdgeHapticTick(): String {
        val decorView = window?.decorView
        if (decorView != null) {
            @Suppress("DEPRECATION")
            val feedbackFlags =
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            if (decorView.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    feedbackFlags,
                )
            ) {
                return "view_clock_tick"
            }
            if (decorView.performHapticFeedback(
                    HapticFeedbackConstants.CONTEXT_CLICK,
                    feedbackFlags,
                )
            ) {
                return "view_context_click"
            }
        }

        val vibrator = resolveDefaultVibrator()
        if (vibrator != null && vibrator.hasVibrator()) {
            // Soft tick only — avoid long/high-amplitude oneshots.
            val durationMs = 8L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude =
                    if (vibrator.hasAmplitudeControl()) {
                        48
                    } else {
                        VibrationEffect.DEFAULT_AMPLITUDE
                    }
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                return "vibrator_oneshot_${durationMs}ms"
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
            return "vibrator_legacy_${durationMs}ms"
        }

        return "no_haptic"
    }

    private fun resolveDefaultVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun buildCourseTimeMillis(timeText: String): Long? {
        val parts = timeText.split(":")
        if (parts.size != 2) {
            return null
        }

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        FairMemoryAdapter.detachFlutterEngine()
        super.cleanUpFlutterEngine(flutterEngine)
    }
}

internal fun liveShouldMirrorStatusIntoMiuiFocusHint(
    sdkInt: Int,
    shouldPromote: Boolean,
): Boolean {
    return !(sdkInt >= 36 && shouldPromote)
}
