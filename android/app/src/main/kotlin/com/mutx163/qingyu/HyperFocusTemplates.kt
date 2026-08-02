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

// ── HyperFocus Template System (top-level, shared by MainActivity and LiveUpdateService) ──

internal val hfDefaultTemplates = mapOf(
    "ticker_pre" to "课名",
    "ticker_active" to "课名",
    "ticker_post" to "课名",
    "islandA_pre" to "教室",
    "islandA_active" to "短课名",
    "islandA_post" to "短课名",
    "islandB_pre" to "",
    "islandB_active" to "上课中",
    "islandB_post" to "已下课",
    "baseTitle_pre" to "课名",
    "baseTitle_active" to "课名",
    "baseTitle_post" to "课名",
    "baseContent_pre" to "开始,结束",
    "baseContent_active" to "开始,结束",
    "baseContent_post" to "开始,结束",
    "baseSubcontent_pre" to "教室",
    "baseSubcontent_active" to "教室",
    "baseSubcontent_post" to "教室",
    "hintTitle_pre" to "",
    "hintTitle_active" to "上课中",
    "hintTitle_post" to "已下课",
    "hintContent_pre" to "即将上课",
    "hintContent_active" to "距离下课",
    "hintContent_post" to "已经下课",
    "hintSubcontent_pre" to "",
    "hintSubcontent_active" to "",
    "hintSubcontent_post" to "",
    "hintSubtitle_pre" to "",
    "hintSubtitle_active" to "",
    "hintSubtitle_post" to "",
)

internal fun loadHyperFocusTemplates(context: Context): Map<String, String> {
    val json = context.getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
        .getString("templates_json", null) ?: return hfDefaultTemplates
    return try {
        val obj = org.json.JSONObject(json)
        val merged = hfDefaultTemplates.toMutableMap()
        for (key in obj.keys()) {
            merged[key] = obj.optString(key, hfDefaultTemplates[key] ?: "")
        }
        merged
    } catch (_: Exception) {
        hfDefaultTemplates
    }
}

internal fun resolveTemplate(
    tpl: String,
    courseName: String,
    shortName: String,
    location: String,
    teacher: String,
    startTime: String,
    endTime: String,
    countdownText: String,
    elapsedText: String,
): String {
    if (tpl.contains("{")) {
        var result = tpl
        result = result.replace("{课名}", courseName)
        result = result.replace("{短课名}", shortName.ifBlank { courseName })
        result = result.replace("{教室}", location.ifBlank { courseName })
        result = result.replace("{教师}", teacher)
        result = result.replace("{开始}", startTime)
        result = result.replace("{结束}", endTime)
        result = result.replace("{倒计时}", countdownText)
        result = result.replace("{正计时}", elapsedText)
        return result
    }
    val variableMap = mapOf(
        "课名" to courseName,
        "短课名" to shortName.ifBlank { courseName },
        "教室" to location.ifBlank { courseName },
        "教师" to teacher,
        "开始" to startTime,
        "结束" to endTime,
        "倒计时" to countdownText,
        "正计时" to elapsedText,
    )
    return tpl.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token -> if (variableMap.containsKey(token)) variableMap[token] ?: "" else token }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

internal fun formatCountdownForTemplate(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

internal fun formatElapsedForTemplate(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
