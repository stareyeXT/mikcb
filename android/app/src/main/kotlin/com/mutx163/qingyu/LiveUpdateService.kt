package com.mutx163.qingyu

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

class LiveUpdateService : Service() {
    companion object {
        private const val TAG = "LiveUpdateService"
        private const val CHANNEL_ID = "live_update_channel"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val PREFS_NAME = "native_runtime_prefs"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        private const val ACTION_ENABLE_SILENT_MODE =
            "com.mutx163.qingyu.action.ENABLE_SILENT_MODE"
        private const val ACTION_ENABLE_DO_NOT_DISTURB =
            "com.mutx163.qingyu.action.ENABLE_DO_NOT_DISTURB"
        private const val ACTION_DISMISS_STATUS_BAR_STAGE =
            "com.mutx163.qingyu.action.DISMISS_STATUS_BAR_STAGE"
        private const val POST_PROMOTED_NOTIFICATIONS_PERMISSION =
            "android.permission.POST_PROMOTED_NOTIFICATIONS"

        @Volatile
        private var isServiceRunning = false

        @Volatile
        private var lastDebugSnapshot: Map<String, Any?> = emptyMap()

        @Volatile
        private var lastDebugUpdatedAtMillis = 0L

        @Volatile
        private var lastStopReason: String? = null

        fun buildDebugStatus(context: Context): Map<String, Any?> {
            val snapshot = lastDebugSnapshot
            val summary = copyStringKeyMap(snapshot["summary"]).apply {
                this["serviceRunning"] = isServiceRunning
                this["statusText"] = when {
                    isServiceRunning -> this["statusText"] ?: context.getString(R.string.debug_status_running)
                    else -> context.getString(R.string.debug_status_not_running)
                }
                this["isExpectedToShowIsland"] =
                    (this["isExpectedToShowIsland"] as? Boolean == true) && isServiceRunning
                this["isActuallyPromotable"] =
                    (this["isActuallyPromotable"] as? Boolean == true) && isServiceRunning
                this["notIslandReason"] =
                    if (isServiceRunning) {
                        this["notIslandReason"] ?: ""
                    } else {
                        lastStopReason ?: context.getString(R.string.debug_service_not_running)
                    }
            }

            val service = copyStringKeyMap(snapshot["service"]).apply {
                this["serviceRunning"] = isServiceRunning
                this["lastDebugUpdatedAtMillis"] = lastDebugUpdatedAtMillis
                this["lastStopReason"] = lastStopReason
            }
            val recentDiagnostics = linkedMapOf<String, Any?>(
                "enabled" to UmengDiagnosticReporter.isLiveDiagnosticsEnabled(context),
                "tail" to UmengDiagnosticReporter.readLiveDiagnosticsTail(context),
            )

            return linkedMapOf(
                "generatedAtMillis" to System.currentTimeMillis(),
                "summary" to summary,
                "environment" to buildEnvironmentSnapshot(context),
                "service" to service,
                "course" to copyStringKeyMap(snapshot["course"]),
                "timing" to copyStringKeyMap(snapshot["timing"]),
                "switches" to copyStringKeyMap(snapshot["switches"]),
                "display" to copyStringKeyMap(snapshot["display"]),
                "notification" to copyStringKeyMap(snapshot["notification"]),
                "recentDiagnostics" to recentDiagnostics,
            )
        }

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
            val scheduling = LiveUpdateScheduler.buildNextTriggerDebugInfo(context)

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

        private fun copyStringKeyMap(value: Any?): LinkedHashMap<String, Any?> {
            val source = value as? Map<*, *> ?: return linkedMapOf()
            val result = linkedMapOf<String, Any?>()
            source.forEach { (key, item) ->
                if (key is String) {
                    result[key] = item
                }
            }
            return result
        }

        private fun buildEnvironmentSnapshot(context: Context): Map<String, Any?> {
            return linkedMapOf(
                "androidVersion" to Build.VERSION.SDK_INT,
                "brand" to Build.BRAND,
                "manufacturer" to Build.MANUFACTURER,
                "device" to Build.DEVICE,
                "model" to Build.MODEL,
                "isXiaomiFamilyDevice" to isXiaomiFamilyDeviceCompat(),
                "hasNotificationPermission" to hasNotificationPermissionCompat(context),
                "hasPromotedPermissionDeclared" to isPromotedPermissionDeclaredCompat(context),
                "canPostPromotedNotifications" to canPostPromotedNotificationsCompat(context),
                "ignoringBatteryOptimizations" to isIgnoringBatteryOptimizationsCompat(context),
                "keepAliveAccessibilityEnabled" to KeepAliveAccessibilityStatus.isEnabled(context),
                "hideFromRecentsEnabled" to context
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HIDE_FROM_RECENTS, false),
            )
        }

        private fun updateDebugSnapshot(snapshot: Map<String, Any?>) {
            lastDebugSnapshot = snapshot
            lastDebugUpdatedAtMillis = System.currentTimeMillis()
            lastStopReason = null
        }

        private fun markServiceRunning() {
            isServiceRunning = true
            lastStopReason = null
            lastDebugUpdatedAtMillis = System.currentTimeMillis()
        }

        private fun markServiceStopped(reason: String) {
            isServiceRunning = false
            lastStopReason = reason
            lastDebugUpdatedAtMillis = System.currentTimeMillis()
        }

        private fun hasNotificationPermissionCompat(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        private fun isPromotedPermissionDeclaredCompat(context: Context): Boolean {
            return try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_PERMISSIONS
                    )
                }
                packageInfo.requestedPermissions
                    ?.contains(POST_PROMOTED_NOTIFICATIONS_PERMISSION) == true
            } catch (e: Exception) {
                Log.w(TAG, DiagnosticLogMessages.LOG_INSPECT_PROMOTED_PERMISSION_FAILED, e)
                false
            }
        }

        private fun canPostPromotedNotificationsCompat(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= 36 &&
                context.getSystemService(NotificationManager::class.java)
                    ?.canPostPromotedNotifications() == true
        }

        private fun isIgnoringBatteryOptimizationsCompat(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true
            }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }

        private fun isXiaomiFamilyDeviceCompat(): Boolean {
            val brand = Build.BRAND.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()
            return manufacturer.contains("xiaomi") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco")
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private var courseName = ""
    private var shortCourseNameRaw = ""
    private var location = ""
    private var teacher = ""
    private var note = ""
    private var startTimeText = ""
    private var endTimeText = ""
    private var nextName = ""
    private var autoDismissAfterStartMinutes = 0
    private var activityStage = ""
    private var endSecondsCountdownThreshold = 60
    private var showCountdown = true
    private var countdownTextStyle = "smart"
    private var showStageText = true
    private var showCourseNameInIsland = true
    private var showLocationInIsland = true
    private var useShortNameInIsland = false
    private var hidePrefixText = false
    private var duringClassTimeDisplayMode = "nearest"
    private var enableMiuiIslandLabelImage = false
    private var miuiIslandLabelStyle = "text_only"
    private var miuiIslandLabelContent = "course_name"
    private var miuiIslandLabelFontColor = "#FFFFFF"
    private var miuiIslandLabelFontWeight = "bold"
    private var miuiIslandLabelRenderQuality = "standard"
    private var miuiIslandLabelFontSize = 14f
    private var miuiIslandLabelOffsetX = 0f
    private var miuiIslandLabelOffsetY = 0f
    private var miuiIslandLabelLogoPath: String? = null
    private var miuiIslandLabelLogoCornerRadius = 8f
    private var miuiIslandExpandedIconMode = "app_icon"
    private var miuiIslandExpandedIconPath: String? = null
    private var islandTimeoutPre = 300
    private var islandTimeoutActive = 600
    private var islandTimeoutPost = 600
    private var islandSessionStartedAt = 0L
    private var islandSuppressed = false
    private var iconAEnabled = false
    private var outEffectStatusEnabled = false
    private var outEffectStatusColor = ""
    private var startAtMillis = 0L
    private var endAtMillis = 0L
    private var beforeClassLeadMillis = 0L
    private var endReminderLeadMillis = 600_000L
    private var liveClassReminderStartMinutes = 0
    private var enableBeforeClass = true
    private var enableDuringClass = true
    private var enableBeforeEnd = true
    private var promoteDuringClass = true
    private var showNotificationDuringClass = true
    private var beforeClassQuickAction = "none"
    private var progressBreakOffsetsMillis = longArrayOf()
    private var progressMilestoneLabels = emptyList<String>()
    private var progressMilestoneTimeTexts = emptyList<String>()
    private var lastRemainingText = "-1"
    private var lastProgressUnits = -1
    private var lastCriticalTimeText = ""
    private var hasStartedForeground = false
    private var lastTickerStage: String? = null
    private var validateAgainstSchedule = true
    private var superIslandEngine = "builtIn"
    private val xiaomiSuperIslandRenderer by lazy {
        XiaomiSuperIslandNotificationRenderer(this)
    }
    private val androidLiveUpdateRenderer by lazy {
        AndroidLiveUpdateNotificationRenderer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val quickActionResult = when (intent?.action) {
                ACTION_ENABLE_SILENT_MODE -> {
                    readQuickActionTimingExtra(intent)
                    handleBeforeClassQuickAction(enableDoNotDisturb = false)
                    START_NOT_STICKY
                }
                ACTION_ENABLE_DO_NOT_DISTURB -> {
                    readQuickActionTimingExtra(intent)
                    handleBeforeClassQuickAction(enableDoNotDisturb = true)
                    START_NOT_STICKY
                }
                ACTION_DISMISS_STATUS_BAR_STAGE -> {
                    dismissStatusBarStage()
                    START_NOT_STICKY
                }
                else -> null
            }
            if (quickActionResult != null) {
                return quickActionResult
            }

            startForegroundSafely(intent)

            if (!hasCompleteLivePayload(intent)) {
                UmengDiagnosticReporter.record(
                    context = applicationContext,
                    category = "live_update_service_missing_payload",
                    message = DiagnosticLogMessages.LIVE_UPDATE_SERVICE_MISSING_PAYLOAD,
                    extras = mapOf(
                        "intentIsNull" to (intent == null),
                        "hasCourseName" to (!intent?.getStringExtra("courseName").isNullOrBlank()),
                        "hasStage" to (!intent?.getStringExtra("stage").isNullOrBlank()),
                        "hasStartAtMillis" to ((intent?.getLongExtra("startAtMillis", 0L) ?: 0L) > 0L),
                        "hasEndAtMillis" to ((intent?.getLongExtra("endAtMillis", 0L) ?: 0L) > 0L),
                    )
                )
                val resumed = LiveUpdateScheduler.reschedule(
                    applicationContext,
                    allowImmediateStart = true,
                    stopStaleSessions = true,
                )
                if (!resumed) {
                    stopAndRemoveNotification()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }

            courseName = sanitizeTextExtra(intent?.getStringExtra("courseName"))
            shortCourseNameRaw = sanitizeTextExtra(intent?.getStringExtra("shortName"))
            location = sanitizeTextExtra(intent?.getStringExtra("location"))
            teacher = sanitizeTextExtra(intent?.getStringExtra("teacher"))
            note = sanitizeTextExtra(intent?.getStringExtra("note"))
            startTimeText = sanitizeTextExtra(intent?.getStringExtra("startTime"))
            endTimeText = sanitizeTextExtra(intent?.getStringExtra("endTime"))
            nextName = sanitizeTextExtra(intent?.getStringExtra("nextName"))
            autoDismissAfterStartMinutes = intent?.getIntExtra("autoDismissAfterStartMinutes", 0) ?: 0
            activityStage = intent?.getStringExtra("stage").orEmpty()
            lastTickerStage = null
            endSecondsCountdownThreshold =
                intent?.getIntExtra("endSecondsCountdownThreshold", 60) ?: 60
            showCountdown = intent?.getBooleanExtra("showCountdown", true) ?: true
            countdownTextStyle = intent?.getStringExtra("countdownTextStyle") ?: "smart"
            showStageText = intent?.getBooleanExtra("showStageText", true) ?: true
            showCourseNameInIsland = intent?.getBooleanExtra("showCourseNameInIsland", true) ?: true
            showLocationInIsland = intent?.getBooleanExtra("showLocationInIsland", true) ?: true
            useShortNameInIsland = intent?.getBooleanExtra("useShortNameInIsland", false) ?: false
            hidePrefixText = intent?.getBooleanExtra("hidePrefixText", false) ?: false
            duringClassTimeDisplayMode =
                intent?.getStringExtra("duringClassTimeDisplayMode") ?: "nearest"
            enableMiuiIslandLabelImage =
                intent?.getBooleanExtra("enableMiuiIslandLabelImage", false) ?: false
            miuiIslandLabelStyle = intent?.getStringExtra("miuiIslandLabelStyle") ?: "text_only"
            miuiIslandLabelContent =
                intent?.getStringExtra("miuiIslandLabelContent") ?: "course_name"
            miuiIslandLabelFontColor =
                intent?.getStringExtra("miuiIslandLabelFontColor") ?: "#FFFFFF"
            miuiIslandLabelFontWeight =
                intent?.getStringExtra("miuiIslandLabelFontWeight") ?: "bold"
            miuiIslandLabelRenderQuality =
                intent?.getStringExtra("miuiIslandLabelRenderQuality") ?: "standard"
            miuiIslandLabelFontSize =
                intent?.getFloatExtra("miuiIslandLabelFontSize", 14f) ?: 14f
            miuiIslandLabelOffsetX =
                intent?.getFloatExtra("miuiIslandLabelOffsetX", 0f) ?: 0f
            miuiIslandLabelOffsetY =
                intent?.getFloatExtra("miuiIslandLabelOffsetY", 0f) ?: 0f
            miuiIslandLabelLogoPath =
                intent?.getStringExtra("miuiIslandLabelLogoPath")?.takeIf { it.isNotBlank() }
            miuiIslandLabelLogoCornerRadius =
                intent?.getFloatExtra("miuiIslandLabelLogoCornerRadius", 8f) ?: 8f
            miuiIslandExpandedIconMode =
                intent?.getStringExtra("miuiIslandExpandedIconMode") ?: "app_icon"
            miuiIslandExpandedIconPath =
                intent?.getStringExtra("miuiIslandExpandedIconPath")?.takeIf { it.isNotBlank() }
            islandTimeoutPre = intent?.getIntExtra("hfIslandTimeoutPre", 300) ?: 300
            islandTimeoutActive = intent?.getIntExtra("hfIslandTimeoutActive", 600) ?: 600
            islandTimeoutPost = intent?.getIntExtra("hfIslandTimeoutPost", 600) ?: 600
            iconAEnabled = intent?.getBooleanExtra("hfIconAEnabled", false) ?: false
            outEffectStatusEnabled = intent?.getBooleanExtra("hfOutEffectStatusEnabled", false) ?: false
            outEffectStatusColor = intent?.getStringExtra("hfOutEffectStatusColor") ?: ""
            beforeClassLeadMillis =
                intent?.getLongExtra("beforeClassLeadMillis", 0L)
                    ?.coerceAtLeast(0L)
                    ?: 0L
            endReminderLeadMillis =
                intent?.getLongExtra("endReminderLeadMillis", 600_000L)
                    ?.coerceAtLeast(0L)
                    ?: 600_000L
            liveClassReminderStartMinutes =
                intent?.getIntExtra("liveClassReminderStartMinutes", 0)?.coerceAtLeast(0) ?: 0
            enableBeforeClass = intent?.getBooleanExtra("enableBeforeClass", true) ?: true
            enableDuringClass = intent?.getBooleanExtra("enableDuringClass", true) ?: true
            enableBeforeEnd = intent?.getBooleanExtra("enableBeforeEnd", true) ?: true
            promoteDuringClass = intent?.getBooleanExtra("promoteDuringClass", true) ?: true
            showNotificationDuringClass =
                intent?.getBooleanExtra("showNotificationDuringClass", true) ?: true
            beforeClassQuickAction =
                intent?.getStringExtra("beforeClassQuickAction") ?: "none"
            validateAgainstSchedule =
                intent?.getBooleanExtra("validateAgainstSchedule", true) ?: true
            superIslandEngine = intent?.getStringExtra("superIslandEngine") ?: "builtIn"
            progressBreakOffsetsMillis =
                intent?.getLongArrayExtra("progressBreakOffsetsMillis") ?: longArrayOf()
            progressMilestoneLabels =
                intent?.getStringArrayListExtra("progressMilestoneLabels") ?: emptyList()
            progressMilestoneTimeTexts =
                intent?.getStringArrayListExtra("progressMilestoneTimeTexts") ?: emptyList()
            startAtMillis =
                intent?.getLongExtra("startAtMillis", 0L)?.takeIf { it > 0L }
                    ?: buildCourseTimeMillis(startTimeText)
                    ?: System.currentTimeMillis()
            endAtMillis =
                intent?.getLongExtra("endAtMillis", 0L)?.takeIf { it > 0L }
                    ?: buildCourseTimeMillis(endTimeText)
                    ?: startAtMillis

            lastRemainingText = "-1"
            lastProgressUnits = -1
            lastCriticalTimeText = ""
            islandSessionStartedAt = System.currentTimeMillis()
            islandSuppressed = false
            markServiceRunning()

            UmengDiagnosticReporter.record(
                context = applicationContext,
                category = "live_update_service_started",
                message = DiagnosticLogMessages.LIVE_UPDATE_SERVICE_STARTED,
                extras = mapOf(
                    "courseName" to courseName,
                    "stage" to activityStage,
                    "startAtMillis" to startAtMillis,
                    "endAtMillis" to endAtMillis,
                    "enableBeforeClass" to enableBeforeClass,
                    "enableDuringClass" to enableDuringClass,
                    "enableBeforeEnd" to enableBeforeEnd,
                    "promoteDuringClass" to promoteDuringClass,
                    "showNotificationDuringClass" to showNotificationDuringClass,
                    "showCourseNameInIsland" to showCourseNameInIsland,
                    "showLocationInIsland" to showLocationInIsland,
                    "enableMiuiIslandLabelImage" to enableMiuiIslandLabelImage,
                    "miuiIslandLabelFontSize" to miuiIslandLabelFontSize,
                    "miuiIslandLabelOffsetX" to miuiIslandLabelOffsetX,
                    "miuiIslandLabelOffsetY" to miuiIslandLabelOffsetY,
                    "hasMiuiIslandLabelLogoPath" to (!miuiIslandLabelLogoPath.isNullOrBlank()),
                    "miuiIslandLabelLogoCornerRadius" to miuiIslandLabelLogoCornerRadius,
                    "miuiIslandExpandedIconMode" to miuiIslandExpandedIconMode,
                )
            )

            val initialText = computeRemainingText(System.currentTimeMillis())
            lastRemainingText = initialText
            updateForegroundNotification(buildNotification(initialText))
            startTicker()
            START_STICKY
        } catch (e: Exception) {
            markServiceStopped(getString(R.string.stop_service_start_failed))
            UmengDiagnosticReporter.report(
                context = applicationContext,
                category = "live_update_service_start_failed",
                message = DiagnosticLogMessages.LIVE_UPDATE_SERVICE_START_FAILED,
                throwable = e,
                dedupeKey = "live_update_service_start_failed",
                extras = mapOf(
                    "courseName" to courseName,
                    "stage" to activityStage,
                )
            )
            if (hasStartedForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                hasStartedForeground = false
            }
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopTicker()
        if (isServiceRunning) {
            markServiceStopped(getString(R.string.stop_service_destroyed))
        }
        if (hasStartedForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            hasStartedForeground = false
        }
        if (validateAgainstSchedule) {
            LiveUpdateScheduler.onLiveUpdateStopped(applicationContext)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepAliveExperimentEnabled = isTaskRemovalKeepAliveEnabled()
        val keepAliveAccessibilityEnabled = isKeepAliveAccessibilityEnabled()
        getSharedPreferences("native_runtime_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_task_removed_at", System.currentTimeMillis())
            .apply()
        UmengDiagnosticReporter.record(
            context = applicationContext,
            category = "live_update_task_removed",
            message = DiagnosticLogMessages.LIVE_UPDATE_TASK_REMOVED,
            extras = mapOf(
                "courseName" to courseName,
                "stage" to activityStage,
                "keepAliveExperimentEnabled" to keepAliveExperimentEnabled,
                "hideFromRecentsEnabled" to isHideFromRecentsEnabled(),
                "keepAliveAccessibilityEnabled" to keepAliveAccessibilityEnabled,
            )
        )
        val resumed = LiveUpdateScheduler.reschedule(
            applicationContext,
            allowImmediateStart = true,
            stopStaleSessions = validateAgainstSchedule,
        )
        if (!resumed) {
            stopAndRemoveNotification()
        } else {
            UmengDiagnosticReporter.record(
                context = applicationContext,
                category = "live_update_task_removed_resumed",
                message = DiagnosticLogMessages.LIVE_UPDATE_TASK_REMOVED_RESUMED,
                extras = mapOf(
                    "courseName" to courseName,
                    "stage" to activityStage,
                    "keepAliveExperimentEnabled" to keepAliveExperimentEnabled,
                    "keepAliveAccessibilityEnabled" to keepAliveAccessibilityEnabled,
                )
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun isTaskRemovalKeepAliveEnabled(): Boolean {
        return isHideFromRecentsEnabled() || isKeepAliveAccessibilityEnabled()
    }

    private fun startForegroundSafely(intent: Intent?) {
        ensureNotificationChannel()
        if (hasStartedForeground) {
            return
        }

        val bootstrapTitle = intent?.getStringExtra("courseName")
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.notification_course_reminder_title, it) }
            ?: getString(R.string.app_display_name)
        val notification = buildBootstrapNotification(bootstrapTitle)
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE is API 34+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        hasStartedForeground = true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_live_update_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_live_update_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildBootstrapNotification(title: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_preparing_reminder))
            .setSmallIcon(R.drawable.ic_course)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateForegroundNotification(notification: Notification) {
        if (!hasStartedForeground) {
            startForeground(NOTIFICATION_ID, notification)
            hasStartedForeground = true
            return
        }

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
            ?: startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildBeforeClassQuickAction(): Notification.Action? {
        val (action, label) = when (beforeClassQuickAction) {
            "silent" -> ACTION_ENABLE_SILENT_MODE to getString(R.string.action_enable_silent)
            "do_not_disturb" -> ACTION_ENABLE_DO_NOT_DISTURB to getString(R.string.action_enable_dnd)
            else -> return null
        }
        val pendingIntent = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, LiveUpdateService::class.java).apply {
                this.action = action
                putExtra("endAtMillis", endAtMillis)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            label,
            pendingIntent,
        ).build()
    }

    private fun buildDismissStatusBarAction(): Notification.Action {
        val pendingIntent = PendingIntent.getService(
            this,
            ACTION_DISMISS_STATUS_BAR_STAGE.hashCode(),
            Intent(this, LiveUpdateService::class.java).apply {
                action = ACTION_DISMISS_STATUS_BAR_STAGE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            getString(R.string.action_close),
            pendingIntent,
        ).build()
    }

    private fun handleBeforeClassQuickAction(enableDoNotDisturb: Boolean) {
        val restoreAtMillis = endAtMillis.takeIf { it > 0L }
            ?: (System.currentTimeMillis() + 2 * 60 * 60_000L)
        val applied = if (enableDoNotDisturb) {
            val enabled = BeforeClassQuickActionRestore.enableDoNotDisturbMode(
                this,
                restoreAtMillis,
            )
            if (!enabled) {
                openNotificationPolicyAccessSettings()
            }
            enabled
        } else {
            val enabled = BeforeClassQuickActionRestore.enableSilentMode(
                this,
                restoreAtMillis,
            )
            if (!enabled) {
                openSoundSettings()
            }
            enabled
        }
        UmengDiagnosticReporter.record(
            context = applicationContext,
            category = "live_update_before_class_quick_action",
            message = DiagnosticLogMessages.LIVE_UPDATE_BEFORE_CLASS_QUICK_ACTION,
            extras = mapOf(
                "action" to if (enableDoNotDisturb) "do_not_disturb" else "silent",
                "applied" to applied,
                "courseName" to courseName,
                "stage" to activityStage,
            )
        )
        if (hasStartedForeground) {
            updateForegroundNotification(buildNotification(computeRemainingText(System.currentTimeMillis())))
        }
    }

    private fun dismissStatusBarStage() {
        markServiceStopped(getString(R.string.stop_status_bar_dismissed))
        UmengDiagnosticReporter.record(
            context = applicationContext,
            category = "live_update_status_bar_dismissed",
            message = DiagnosticLogMessages.LIVE_UPDATE_STATUS_BAR_DISMISSED,
            extras = mapOf(
                "courseName" to courseName,
                "stage" to activityStage,
            )
        )
        stopTicker()
        if (hasStartedForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            hasStartedForeground = false
        }
        stopSelf()
    }

    private fun readQuickActionTimingExtra(intent: Intent?) {
        val restoreAtMillis = intent?.getLongExtra("endAtMillis", 0L) ?: 0L
        if (restoreAtMillis > 0L) {
            endAtMillis = restoreAtMillis
        }
    }

    private fun restoreBeforeClassQuickActionIfClassEnded() {
        BeforeClassQuickActionRestore.restoreIfClassEnded(applicationContext)
    }

    private fun openNotificationPolicyAccessSettings() {
        openSettingsIntent(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        )
    }

    private fun openSoundSettings() {
        openSettingsIntent(Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    private fun openSettingsIntent(intent: Intent) {
        try {
            startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            Log.w(TAG, DiagnosticLogMessages.LOG_OPEN_SETTINGS_INTENT_FAILED, e)
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (fallbackError: Exception) {
                Log.w(TAG, DiagnosticLogMessages.LOG_OPEN_FALLBACK_SETTINGS_FAILED, fallbackError)
            }
        }
    }

    private fun isHideFromRecentsEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_FROM_RECENTS, false)
    }

    private fun hasCompleteLivePayload(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        val courseName = intent.getStringExtra("courseName")
        val stage = intent.getStringExtra("stage")
        val startAtMillis = intent.getLongExtra("startAtMillis", 0L)
        val endAtMillis = intent.getLongExtra("endAtMillis", 0L)
        return !courseName.isNullOrBlank() &&
            !stage.isNullOrBlank() &&
            startAtMillis > 0L &&
            endAtMillis > 0L &&
            endAtMillis >= startAtMillis
    }

    private fun isKeepAliveAccessibilityEnabled(): Boolean {
        return KeepAliveAccessibilityStatus.isEnabled(this)
    }

    private fun startTicker() {
        stopTicker()
        ticker = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                BeforeClassQuickActionRestore.restoreIfClassEnded(applicationContext, now)
                val stage = resolveStage(now)
                // 课后窗口调度器不感知（快照 stage 在课末即结束），跳过校验避免被提前停掉
                if (stage != "afterClass" &&
                    validateAgainstSchedule &&
                    !LiveUpdateScheduler.hasActiveLiveSelection(applicationContext, now)
                ) {
                    if (!LiveUpdateScheduler.reschedule(
                            applicationContext,
                            allowImmediateStart = true,
                            stopStaleSessions = true,
                        )
                    ) {
                        stopAndRemoveNotification()
                    }
                    return
                }
                if (autoDismissAfterStartMinutes > 0 &&
                    now >= startAtMillis + autoDismissAfterStartMinutes * 60_000L
                ) {
                    if (!LiveUpdateScheduler.reschedule(
                            applicationContext,
                            allowImmediateStart = true,
                            stopStaleSessions = validateAgainstSchedule,
                        )
                    ) {
                        stopAndRemoveNotification()
                    }
                    return
                }

                if (stage == null) {
                    if (!LiveUpdateScheduler.reschedule(
                            applicationContext,
                            allowImmediateStart = true,
                            stopStaleSessions = validateAgainstSchedule,
                        )
                    ) {
                        stopAndRemoveNotification()
                    }
                    return
                }

                // When stage transitions, reschedule so onStartCommand re-reads
                // the correct displaySettings for the new stage.
                if (lastTickerStage != null && stage != lastTickerStage) {
                    lastTickerStage = stage
                    if (stage == "afterClass") {
                        // 课后窗口本地渲染（模板与超时都在本服务内），不重读 intent
                        islandSessionStartedAt = now
                        islandSuppressed = false
                    } else {
                        if (!LiveUpdateScheduler.reschedule(
                                applicationContext,
                                allowImmediateStart = true,
                                stopStaleSessions = validateAgainstSchedule,
                            )
                        ) {
                            stopAndRemoveNotification()
                        }
                        return
                    }
                }
                lastTickerStage = stage

                // 课后窗口让位于已激活的下一节课（含其课前提醒），避免吞掉连堂课程
                if (stage == "afterClass" &&
                    validateAgainstSchedule &&
                    LiveUpdateScheduler.hasActiveLiveSelection(applicationContext, now)
                ) {
                    if (!LiveUpdateScheduler.reschedule(
                            applicationContext,
                            allowImmediateStart = true,
                            stopStaleSessions = true,
                        )
                    ) {
                        stopAndRemoveNotification()
                    }
                    return
                }

                val stageTimeoutSeconds = when (stage) {
                    "beforeClass" -> islandTimeoutPre
                    "afterClass" -> islandTimeoutPost
                    else -> islandTimeoutActive
                }
                // 岛消失时间到期：主动下岛（含 dismiss bundle），避免每次重推刷新系统超时
                if (!islandSuppressed &&
                    stageTimeoutSeconds > 0 &&
                    now - islandSessionStartedAt >= stageTimeoutSeconds * 1000L
                ) {
                    islandSuppressed = true
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification(computeRemainingText(now)))
                }

                if (now >= endAtMillis + islandTimeoutPost * 1000L + 30_000L) {
                    // Auto-remove 30s after the post window, especially for tests.
                    if (!LiveUpdateScheduler.reschedule(
                            applicationContext,
                            allowImmediateStart = true,
                            stopStaleSessions = validateAgainstSchedule,
                        )
                    ) {
                        stopAndRemoveNotification()
                    }
                    return
                }

                val currentText = computeRemainingText(now)
                val currentDuringClassProgress = if (stage == "duringClass") {
                    buildDuringClassProgress(now)
                } else {
                    null
                }
                val currentCriticalTimeText = currentDuringClassProgress?.criticalTimeText ?: currentText
                val shouldRefreshProgressThisTick =
                    currentDuringClassProgress?.updatesEverySecond == true
                val currentProgress =
                    if (shouldRefreshProgressThisTick) {
                        currentDuringClassProgress.progressUnits
                    } else {
                        -1
                    }
                if (currentText != lastRemainingText ||
                    currentProgress != lastProgressUnits ||
                    currentCriticalTimeText != lastCriticalTimeText
                ) {
                    lastRemainingText = currentText
                    lastProgressUnits = currentProgress
                    lastCriticalTimeText = currentCriticalTimeText
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification(currentText))
                }

                handler.postDelayed(this, computeNextTickDelayMillis(now, stage, currentDuringClassProgress))
            }
        }
        handler.post(ticker!!)
    }

    private fun stopTicker() {
        ticker?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        ticker = null
    }

    private fun computeRemainingText(now: Long): String {
        val stage = resolveStage(now)
        val timeUntilEnd = endAtMillis - now

        val prefixTextStart = if (hidePrefixText) "" else getString(R.string.prefix_until_class_start)
        val prefixTextEnd = if (hidePrefixText) "" else getString(R.string.prefix_until_class_end)

        return if (!showCountdown) {
            ""
        } else {
            when (stage) {
                "beforeClass" -> {
                    val timeUntilStart = (startAtMillis - now).coerceAtLeast(0L)
                    "${prefixTextStart}${formatCountdownDuration(
                        durationMillis = timeUntilStart,
                        secondsThresholdMillis = 60_000L,
                    )}"
                }
                "beforeEnd" -> {
                    "${prefixTextEnd}${formatCountdownDuration(
                        durationMillis = timeUntilEnd,
                        secondsThresholdMillis = endSecondsCountdownThreshold * 1000L,
                    )}"
                }
                "duringClass",
                "duringClassStatusBar" -> getString(R.string.stage_in_class)
                else -> ""
            }
        }
    }

    private fun resolveStage(now: Long): String? {
        if (now >= endAtMillis) {
            // 课后窗口：下课后的 islandTimeoutPost 秒内继续展示"已下课"
            if (islandTimeoutPost > 0 && now < endAtMillis + islandTimeoutPost * 1000L) {
                return "afterClass"
            }
            return null
        }
        // Do not show the before-class stage earlier than its window start.
        // (leadMillis == 0 means the caller did not supply a window; keep the
        // legacy behavior of trusting the start intent in that case.)
        if (beforeClassLeadMillis > 0L && now < startAtMillis - beforeClassLeadMillis) {
            return null
        }

        val reminderStart = if (liveClassReminderStartMinutes == 0) {
            startAtMillis
        } else {
            maxOf(startAtMillis, endAtMillis - liveClassReminderStartMinutes * 60_000L)
        }
        val endReminderStart = maxOf(startAtMillis, endAtMillis - endReminderLeadMillis)
        return when {
            now < startAtMillis -> if (enableBeforeClass) "beforeClass" else null
            liveClassReminderStartMinutes > 0 && now < reminderStart ->
                if (enableDuringClass && showNotificationDuringClass) {
                    "duringClassStatusBar"
                } else {
                    null
                }
            now < reminderStart -> null
            liveClassReminderStartMinutes > 0 && enableBeforeEnd -> "beforeEnd"
            liveClassReminderStartMinutes > 0 && canDisplayDuringStage() -> "duringClass"
            now >= endReminderStart && enableBeforeEnd -> "beforeEnd"
            now < endReminderStart && canDisplayDuringStage() -> "duringClass"
            now >= endReminderStart && canDisplayDuringStage() -> "duringClass"
            else -> null
        }
    }

    private fun canDisplayDuringStage(): Boolean {
        return enableDuringClass && (promoteDuringClass || showNotificationDuringClass)
    }

    private fun buildNotification(remainingText: String): Notification {
        val now = System.currentTimeMillis()
        val stage = LiveUpdateNotificationStage.fromWireValue(resolveStage(now))
            ?: return buildBootstrapNotification(courseName)
        val progress = if (stage == LiveUpdateNotificationStage.DURING_CLASS) {
            buildDuringClassProgress(now)
        } else {
            null
        }
        val shouldPromote = stage.shouldPromote(promoteDuringClass)
        val showStandardNotification = stage.showStandardNotification(showNotificationDuringClass)
        val shortCourseName = courseName.take(8) + if (courseName.length > 8) ".." else ""
        val islandName = if (useShortNameInIsland && shortCourseNameRaw.isNotBlank()) {
            shortCourseNameRaw
        } else {
            courseName
        }
        val islandCourseName = if (showCourseNameInIsland) islandName.take(5) else ""
        val visibleLocation = if (showLocationInIsland) location else ""
        val stageTitle = when (stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS -> getString(R.string.stage_before_class)
            LiveUpdateNotificationStage.BEFORE_END -> getString(R.string.stage_before_end)
            LiveUpdateNotificationStage.AFTER_CLASS -> getString(R.string.stage_after_class)
            else -> getString(R.string.stage_in_class)
        }
        val visibleStatusText = when {
            !showCountdown && showStageText -> stageTitle
            !showCountdown -> ""
            else -> remainingText.ifBlank { stageTitle }
        }
        val title = when (stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS ->
                getString(R.string.title_before_class, shortCourseName)
            LiveUpdateNotificationStage.BEFORE_END ->
                getString(R.string.title_before_end, shortCourseName)
            else -> shortCourseName
        }
        val shortNameLabel = shortCourseNameRaw.takeIf {
            it.isNotBlank() && it != courseName
        }
        val timeRangeText = if (startTimeText.isNotBlank() || endTimeText.isNotBlank()) {
            "$startTimeText - $endTimeText".trim()
        } else {
            ""
        }
        val subText = when (stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS -> listOfNotNull(
                timeRangeText.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.label_class_start_time, it) },
                visibleLocation.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.label_location, it) },
            ).joinToString("  ·  ")
            LiveUpdateNotificationStage.BEFORE_END -> listOfNotNull(
                timeRangeText.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.label_class_end_time, it) },
                visibleLocation.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.label_location, it) },
            ).joinToString("  ·  ")
            else -> ""
        }
        val summaryText = if (progress != null && showCountdown) {
            listOfNotNull(
                progress.nextMilestoneDisplayText,
                progress.finalDismissDisplayText,
                visibleLocation.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        } else {
            listOfNotNull(
                visibleLocation.takeIf { it.isNotBlank() },
                teacher.takeIf { it.isNotBlank() },
                visibleStatusText.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        val detailStatusText = when {
            progress != null && showCountdown -> null
            visibleStatusText.isNotBlank() && !shouldPromote -> visibleStatusText
            else -> null
        }
        val expandedDetailText = buildString {
            append(stageTitle)
            shortNameLabel?.let {
                append("\n").append(getString(R.string.detail_short_name, it))
            }
            if (progress != null && showCountdown) {
                progress.nextMilestoneDisplayText?.let {
                    append("\n").append(getString(R.string.detail_next_milestone, it))
                }
                append("\n").append(
                    getString(R.string.detail_final_dismiss, progress.finalDismissDisplayText)
                )
            } else {
                detailStatusText?.let {
                    append("\n").append(getString(R.string.detail_status, it))
                }
            }
            if (timeRangeText.isNotBlank()) {
                append("\n").append(getString(R.string.detail_time, timeRangeText))
            }
            if (location.isNotBlank()) {
                append("\n").append(getString(R.string.label_location, location))
            }
            if (teacher.isNotBlank()) {
                append("\n").append(getString(R.string.detail_teacher, teacher))
            }
            if (nextName.isNotBlank()) {
                append("\n").append(getString(R.string.detail_next_course, nextName))
            }
            if (note.isNotBlank()) {
                append("\n").append(getString(R.string.detail_note, note))
            }
        }
        val promotedContentText = if (progress != null && showCountdown) {
            listOfNotNull(
                progress.compactDisplayText,
                visibleLocation.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        } else {
            listOfNotNull(
                visibleStatusText.takeIf { it.isNotBlank() },
                timeRangeText.takeIf { it.isNotBlank() },
                visibleLocation.takeIf { it.isNotBlank() },
                teacher.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        val promotedExpandedDetailText = buildString {
            if (progress != null && showCountdown) {
                progress.nextMilestoneDisplayText?.let {
                    append(getString(R.string.detail_next_milestone, it)).append("\n")
                }
                append(getString(R.string.detail_final_dismiss, progress.finalDismissDisplayText))
            } else {
                detailStatusText?.let {
                    append(getString(R.string.detail_status, it))
                }
            }
            if (timeRangeText.isNotBlank()) {
                append("\n").append(getString(R.string.detail_time, timeRangeText))
            }
            if (location.isNotBlank()) {
                append("\n").append(getString(R.string.label_location, location))
            }
            if (teacher.isNotBlank()) {
                append("\n").append(getString(R.string.detail_teacher, teacher))
            }
            shortNameLabel?.let {
                append("\n").append(getString(R.string.detail_short_name, it))
            }
            if (nextName.isNotBlank()) {
                append("\n").append(getString(R.string.detail_next_course, nextName))
            }
            if (note.isNotBlank()) {
                append("\n").append(getString(R.string.detail_note, note))
            }
        }
        val contentText = when {
            !showStandardNotification -> ""
            progress != null -> promotedContentText
            shouldPromote && !showCourseNameInIsland && !showLocationInIsland ->
                visibleStatusText
            else -> listOf(islandCourseName, visibleLocation, teacher, visibleStatusText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
        val miuiFocusHintText = if (
            liveShouldMirrorStatusIntoMiuiFocusHint(
                sdkInt = Build.VERSION.SDK_INT,
                shouldPromote = shouldPromote,
            )
        ) {
            visibleStatusText
        } else {
            ""
        }
        val criticalStatusText = if (progress != null && showCountdown) {
            progress.criticalTimeText
        } else {
            visibleStatusText
        }
        val islandCriticalText = if (
            shouldPromote && !showCourseNameInIsland && !showLocationInIsland
        ) {
            criticalStatusText
        } else {
            listOf(islandCourseName, visibleLocation, criticalStatusText)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
        val miuiIslandLabelText = when (miuiIslandLabelContent) {
            "location" -> location
            "course_name_and_location" -> listOf(islandName, location)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            else -> islandName
        }
        val state = LiveUpdateNotificationState(
            nowMillis = now,
            stage = stage,
            shouldPromote = shouldPromote,
            showStandardNotification = showStandardNotification,
            courseName = courseName,
            shortCourseNameRaw = shortCourseNameRaw,
            location = location,
            teacher = teacher,
            nextCourseName = nextName,
            startTimeText = startTimeText,
            endTimeText = endTimeText,
            startAtMillis = startAtMillis,
            endAtMillis = endAtMillis,
            stageTitle = stageTitle,
            title = title,
            timeRangeText = timeRangeText,
            subText = subText,
            summaryText = summaryText,
            contentText = contentText,
            expandedDetailText = expandedDetailText,
            promotedContentText = promotedContentText,
            promotedExpandedDetailText = promotedExpandedDetailText,
            islandCriticalText = islandCriticalText,
            progress = progress,
        )
        val xiaomiSettings = XiaomiSuperIslandSettings(
            engine = superIslandEngine,
            showCountdown = showCountdown,
            countdownTextStyle = countdownTextStyle,
            visibleLocation = visibleLocation,
            islandName = islandName,
            focusHintText = miuiFocusHintText,
            progressBreakOffsetsMillis = progressBreakOffsetsMillis,
            progressMilestoneLabels = progressMilestoneLabels,
            enableLabelImage = enableMiuiIslandLabelImage,
            labelStyle = miuiIslandLabelStyle,
            labelText = miuiIslandLabelText,
            labelFontColor = miuiIslandLabelFontColor,
            labelFontWeight = miuiIslandLabelFontWeight,
            labelRenderQuality = miuiIslandLabelRenderQuality,
            labelFontSize = miuiIslandLabelFontSize,
            labelOffsetX = miuiIslandLabelOffsetX,
            labelOffsetY = miuiIslandLabelOffsetY,
            labelLogoPath = miuiIslandLabelLogoPath,
            labelLogoCornerRadius = miuiIslandLabelLogoCornerRadius,
            expandedIconMode = miuiIslandExpandedIconMode,
            expandedIconPath = miuiIslandExpandedIconPath,
            timeoutPre = islandTimeoutPre,
            timeoutActive = islandTimeoutActive,
            timeoutPost = islandTimeoutPost,
            iconAEnabled = iconAEnabled,
            outEffectEnabled = outEffectStatusEnabled,
            outEffectColor = outEffectStatusColor,
        )

        val xiaomi = if (islandSuppressed && superIslandEngine == "hyperFocusApi") {
            // 消失时间到期后持续携带 dismiss 参数，防止重推把岛重新唤起
            xiaomiSuperIslandRenderer.renderDismiss(superIslandEngine)
        } else {
            xiaomiSuperIslandRenderer.render(state, xiaomiSettings)
        }
        val decoration = xiaomi.decoration
        val android = androidLiveUpdateRenderer.render(
            state = state,
            decoration = decoration,
            requestPromotion = !islandSuppressed && shouldRequestAndroidLiveUpdatePromotion(
                shouldPromote = state.shouldPromote,
                vendorSurfaceReady = decoration.isVendorSurfaceReady,
            ),
            beforeClassAction = if (stage.isUpcoming) buildBeforeClassQuickAction() else null,
            dismissAction = if (stage.isStatusBarOnly) buildDismissStatusBarAction() else null,
        )
        val actuallyPromotable = shouldPromote && (
            decoration.isVendorSurfaceReady ||
                (android.canPostPromoted && android.hasPromotableCharacteristics == true)
            )
        val notIslandReason = when {
            !hasStartedForeground -> getString(R.string.debug_foreground_not_started)
            stage.isStatusBarOnly -> getString(R.string.debug_status_bar_only)
            !shouldPromote && stage.isDuringClass && !promoteDuringClass ->
                getString(R.string.debug_during_class_normal_notification)
            !shouldPromote -> getString(R.string.debug_promote_not_requested)
            !hasNotificationPermissionCompat(this) ->
                getString(R.string.debug_notification_permission_off)
            actuallyPromotable -> ""
            Build.VERSION.SDK_INT >= 36 && !isPromotedPermissionDeclaredCompat(this) ->
                getString(R.string.debug_promoted_permission_not_declared)
            Build.VERSION.SDK_INT >= 36 && !android.canPostPromoted &&
                !decoration.isVendorSurfaceReady ->
                getString(R.string.debug_system_denied_promoted)
            Build.VERSION.SDK_INT >= 36 &&
                android.hasPromotableCharacteristics == false &&
                !decoration.isVendorSurfaceReady ->
                getString(R.string.debug_notification_not_promotable)
            xiaomi.isXiaomiDevice && !decoration.isVendorSurfaceReady ->
                getString(R.string.debug_miui_focus_param_missing)
            Build.VERSION.SDK_INT < 36 && !xiaomi.isXiaomiDevice ->
                getString(R.string.debug_os_not_supported)
            else -> getString(R.string.debug_try_return_home)
        }
        updateDebugSnapshot(
            linkedMapOf(
                "summary" to linkedMapOf(
                    "serviceRunning" to true,
                    "currentStage" to activityStage,
                    "resolvedStage" to stage.wireValue,
                    "isExpectedToShowIsland" to shouldPromote,
                    "isActuallyPromotable" to actuallyPromotable,
                    "statusText" to if (actuallyPromotable) {
                        getString(R.string.debug_island_ready)
                    } else {
                        getString(R.string.debug_island_not_ready)
                    },
                    "notIslandReason" to notIslandReason,
                ),
                "service" to linkedMapOf(
                    "serviceRunning" to true,
                    "hasStartedForeground" to hasStartedForeground,
                    "activityStage" to activityStage,
                    "resolvedStage" to stage.wireValue,
                    "lastRemainingText" to lastRemainingText,
                    "lastProgressUnits" to lastProgressUnits,
                    "lastCriticalTimeText" to lastCriticalTimeText,
                ),
                "course" to linkedMapOf(
                    "courseName" to courseName,
                    "shortCourseNameRaw" to shortCourseNameRaw,
                    "nextCourseName" to nextName,
                    "location" to location,
                    "teacher" to teacher,
                    "note" to note,
                    "startTimeText" to startTimeText,
                    "endTimeText" to endTimeText,
                ),
                "timing" to linkedMapOf(
                    "nowMillis" to now,
                    "startAtMillis" to startAtMillis,
                    "endAtMillis" to endAtMillis,
                    "remainingToStartMillis" to (startAtMillis - now).coerceAtLeast(0L),
                    "remainingToEndMillis" to (endAtMillis - now).coerceAtLeast(0L),
                    "endReminderLeadMillis" to endReminderLeadMillis,
                    "liveClassReminderStartMinutes" to liveClassReminderStartMinutes,
                    "endSecondsCountdownThreshold" to endSecondsCountdownThreshold,
                    "autoDismissAfterStartMinutes" to autoDismissAfterStartMinutes,
                ),
                "notification" to linkedMapOf(
                    "shouldPromote" to shouldPromote,
                    "showStandardNotification" to showStandardNotification,
                    "statusBarOnly" to stage.isStatusBarOnly,
                    "canPostPromotedNotifications" to android.canPostPromoted,
                    "hasPromotableCharacteristics" to android.hasPromotableCharacteristics,
                    "miuiFocusParamPresent" to (xiaomi.legacyFocusParam != null),
                    "hyperFocusPresent" to (xiaomi.hyperFocusExtras != null),
                    "islandSuppressed" to islandSuppressed,
                    "androidPromotionRequested" to android.requestedPromotion,
                    "notificationTitle" to title,
                    "notificationContentText" to if (shouldPromote) {
                        promotedContentText
                    } else {
                        contentText
                    },
                    "notificationExpandedText" to if (shouldPromote) {
                        promotedExpandedDetailText
                    } else {
                        expandedDetailText
                    },
                    "visibleStatusText" to visibleStatusText,
                    "islandCriticalText" to islandCriticalText,
                ),
            )
        )
        return android.notification
    }
    private fun sanitizeTextExtra(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (normalized.equals("null", ignoreCase = true)) "" else normalized
    }

    private fun stopAndRemoveNotification() {
        restoreBeforeClassQuickActionIfClassEnded()
        markServiceStopped(getString(R.string.stop_reminder_ended))
        UmengDiagnosticReporter.record(
            context = applicationContext,
            category = "live_update_service_stopped",
            message = DiagnosticLogMessages.LIVE_UPDATE_SERVICE_STOPPED,
            extras = mapOf(
                "courseName" to courseName,
                "stage" to activityStage,
            )
        )
        stopTicker()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        LiveUpdateScheduler.onLiveUpdateStopped(applicationContext)
        stopSelf()
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

    private fun formatCountdownDuration(
        durationMillis: Long,
        secondsThresholdMillis: Long = 60_000L,
    ): String = CountdownFormat.formatDuration(durationMillis, countdownTextStyle, secondsThresholdMillis)

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

    private fun buildDuringClassProgress(now: Long): LiveUpdateProgressState? {
        val totalMillis = (endAtMillis - startAtMillis).coerceAtLeast(1L)
        val elapsedMillis = (now - startAtMillis).coerceIn(0L, totalMillis)
        val remainingMillis = (endAtMillis - now).coerceAtLeast(0L)
        val progressMax = 1000
        val progressUnits =
            ((elapsedMillis.toDouble() / totalMillis.toDouble()) * progressMax)
                .toInt()
                .coerceIn(0, progressMax)
        val progressPercent = ((progressUnits * 100L) / progressMax).toInt().coerceIn(0, 100)
        val breakPointUnits = progressBreakOffsetsMillis
            .map { offsetMillis ->
                ((offsetMillis.coerceIn(0L, totalMillis).toDouble() / totalMillis.toDouble()) * progressMax)
                    .toInt()
                    .coerceIn(1, progressMax - 1)
            }
            .distinct()
            .sorted()
        val nextMilestoneIndex =
            progressBreakOffsetsMillis.indexOfFirst { it > elapsedMillis }.takeIf { it >= 0 }
        val nextMilestoneLabel =
            nextMilestoneIndex?.let { progressMilestoneLabels.getOrNull(it)?.takeIf { label -> label.isNotBlank() } }
        val nextMilestoneRemainingText =
            nextMilestoneIndex?.let { formatCountdownDuration(progressBreakOffsetsMillis[it] - elapsedMillis) }
        val finalDismissRemainingText = formatCountdownDuration(remainingMillis)
        val nextMilestoneDisplayText =
            if (nextMilestoneLabel != null && nextMilestoneRemainingText != null) {
                "$nextMilestoneLabel $nextMilestoneRemainingText"
            } else if (nextMilestoneRemainingText != null) {
                nextMilestoneRemainingText
            } else {
                null
            }
        val finalDismissDisplayText = getString(R.string.final_dismiss_with_time, finalDismissRemainingText)
        val compactDisplayText = if (duringClassTimeDisplayMode == "total") {
            finalDismissDisplayText
        } else {
            nextMilestoneDisplayText ?: finalDismissDisplayText
        }
        val criticalTimeText = if (duringClassTimeDisplayMode == "total") {
            finalDismissRemainingText
        } else {
            nextMilestoneRemainingText ?: finalDismissRemainingText
        }
        return LiveUpdateProgressState(
            progressMax = progressMax,
            progressUnits = progressUnits,
            progressPercent = progressPercent,
            nextMilestoneDisplayText = nextMilestoneDisplayText,
            finalDismissDisplayText = finalDismissDisplayText,
            compactDisplayText = compactDisplayText,
            criticalTimeText = criticalTimeText,
            breakPointUnits = breakPointUnits,
            updatesEverySecond = shouldRefreshEverySecond(
                durationMillis = nextMilestoneIndex?.let { progressBreakOffsetsMillis[it] - elapsedMillis }
                    ?: remainingMillis,
                secondsThresholdMillis = 60_000L,
            ),
        )
    }

    private fun shouldRefreshEverySecond(
        durationMillis: Long,
        secondsThresholdMillis: Long,
    ): Boolean {
        if (!showCountdown) {
            return false
        }
        return when (countdownTextStyle) {
            "minute_second_cn",
            "minute_second_colon",
            "minute_second_min_s",
            "minute_second_min_slash_s",
            "second_only_cn",
            "second_only_short",
            "second_only_slash" -> true
            "smart",
            "smart_min_s" -> durationMillis <= secondsThresholdMillis
            else -> false
        }
    }

    private fun nextCountdownTextChangeDelayMillis(
        durationMillis: Long,
        secondsThresholdMillis: Long,
    ): Long {
        if (!showCountdown) {
            return 60_000L
        }
        val safeDurationMillis = durationMillis.coerceAtLeast(0L)
        val totalSeconds = (safeDurationMillis / 1000L).coerceAtLeast(0L)
        return when (countdownTextStyle) {
            "minute_second_cn",
            "minute_second_colon",
            "minute_second_min_s",
            "minute_second_min_slash_s",
            "second_only_cn",
            "second_only_short",
            "second_only_slash" -> 1_000L
            "minute_only_cn",
            "minute_only_min",
            "minute_only_slash" -> {
                val currentMinutes = (totalSeconds / 60L).coerceAtLeast(1L)
                if (currentMinutes <= 1L) {
                    safeDurationMillis.coerceAtLeast(1_000L)
                } else {
                    (safeDurationMillis - currentMinutes * 60_000L + 1L).coerceAtLeast(1_000L)
                }
            }
            else -> {
                when {
                    safeDurationMillis <= secondsThresholdMillis -> 1_000L
                    totalSeconds > 120L -> {
                        val currentMinutes = (totalSeconds / 60L).coerceAtLeast(1L)
                        (safeDurationMillis - currentMinutes * 60_000L + 1L).coerceAtLeast(1_000L)
                    }
                    totalSeconds > 60L -> {
                        (safeDurationMillis - secondsThresholdMillis + 1L).coerceAtLeast(1_000L)
                    }
                    else -> 1_000L
                }
            }
        }
    }

    private fun computeNextTickDelayMillis(
        now: Long,
        stage: String?,
        duringClassProgress: LiveUpdateProgressState?,
    ): Long {
        val refreshEverySecond = when (stage) {
            "beforeClass" -> shouldRefreshEverySecond(
                durationMillis = (startAtMillis - now).coerceAtLeast(0L),
                secondsThresholdMillis = 60_000L,
            )
            "beforeEnd" -> shouldRefreshEverySecond(
                durationMillis = (endAtMillis - now).coerceAtLeast(0L),
                secondsThresholdMillis = endSecondsCountdownThreshold * 1000L,
            )
            "duringClass" -> duringClassProgress?.updatesEverySecond == true
            else -> false
        }
        if (refreshEverySecond) {
            return 1000L
        }
        val stageDelay = when (stage) {
            "beforeClass" -> minOf(
                (startAtMillis - now).coerceAtLeast(1_000L),
                nextCountdownTextChangeDelayMillis(
                    durationMillis = (startAtMillis - now).coerceAtLeast(0L),
                    secondsThresholdMillis = 60_000L,
                ),
            )
            "beforeEnd" -> minOf(
                (endAtMillis - now).coerceAtLeast(1_000L),
                nextCountdownTextChangeDelayMillis(
                    durationMillis = (endAtMillis - now).coerceAtLeast(0L),
                    secondsThresholdMillis = endSecondsCountdownThreshold * 1000L,
                ),
            )
            "duringClass" -> {
                val elapsedMillis = (now - startAtMillis).coerceAtLeast(0L)
                val nextMilestoneDelay = progressBreakOffsetsMillis
                    .firstOrNull { it > elapsedMillis }
                    ?.minus(elapsedMillis)
                listOfNotNull(
                    nextMilestoneDelay?.takeIf { it > 0L },
                    (endAtMillis - now).takeIf { it > 0L },
                    nextCountdownTextChangeDelayMillis(
                        durationMillis = nextMilestoneDelay ?: (endAtMillis - now).coerceAtLeast(0L),
                        secondsThresholdMillis = 60_000L,
                    ),
                ).minOrNull() ?: 60_000L
            }
            "duringClassStatusBar" -> {
                val beforeEndStartMillis = maxOf(
                    startAtMillis,
                    endAtMillis - liveClassReminderStartMinutes * 60_000L,
                )
                listOfNotNull(
                    (beforeEndStartMillis - now).takeIf { it > 0L },
                    (endAtMillis - now).takeIf { it > 0L },
                ).minOrNull() ?: 60_000L
            }
            else -> 60_000L
        }
        return stageDelay.coerceIn(1_000L, 60_000L)
    }
}
