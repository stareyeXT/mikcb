package com.mutx163.qingyu

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue

internal data class LiveUpdateRenderResult(
    val notification: Notification,
    val requestedPromotion: Boolean,
    val canPostPromoted: Boolean,
    val hasPromotableCharacteristics: Boolean?,
)

internal class AndroidLiveUpdateNotificationRenderer(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "live_update_channel"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }

    fun render(
        state: LiveUpdateNotificationState,
        decoration: LiveUpdateNotificationDecoration = LiveUpdateNotificationDecoration(),
        requestPromotion: Boolean = shouldRequestAndroidLiveUpdatePromotion(
            shouldPromote = state.shouldPromote,
            vendorSurfaceReady = decoration.suppressAndroidPromotion,
        ),
        beforeClassAction: Notification.Action? = null,
        dismissAction: Notification.Action? = null,
    ): LiveUpdateRenderResult {
        val requestedPromotion = requestPromotion
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        val iconRes = when (state.stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS -> R.drawable.ic_upcoming
            LiveUpdateNotificationStage.BEFORE_END -> R.drawable.ic_countdown
            else -> R.drawable.ic_course
        }
        val title = if (state.shouldPromote || state.showStandardNotification) state.title else ""
        val content = if (state.shouldPromote) state.promotedContentText else if (!state.showStandardNotification) "" else state.contentText
        val expanded = if (state.shouldPromote) state.promotedExpandedDetailText else if (!state.showStandardNotification) "" else state.expandedDetailText
        builder.apply {
            setContentTitle(title)
            setContentText(content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && decoration.smallIcon != null) setSmallIcon(decoration.smallIcon) else setSmallIcon(iconRes)
            decoration.largeIcon?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) setLargeIcon(it) }
            setContentIntent(pending)
            setOngoing(true)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            setCategory(if (state.stage.isStatusBarOnly) Notification.CATEGORY_REMINDER else Notification.CATEGORY_PROGRESS)
            setColorized(false)
            setShowWhen(!state.shouldPromote)
            setWhen(if (state.stage.isUpcoming) state.startAtMillis else state.endAtMillis)
            setUsesChronometer(false)
            val progress = state.progress
            if (progress != null) setProgress(progress.progressMax, progress.progressUnits, false) else setProgress(0, 0, false)
            if (state.showStandardNotification && !state.shouldPromote && state.subText.isNotBlank()) setSubText(state.subText)
            if (Build.VERSION.SDK_INT >= 36) {
                if (state.stage.isStatusBarOnly) {
                    setShortCriticalText("")
                    setExtras(Bundle())
                } else if (requestedPromotion) {
                    setShortCriticalText(state.islandCriticalText)
                    setExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
                } else {
                    setShortCriticalText("")
                    setExtras(Bundle())
                }
            }
        }
        beforeClassAction?.let(builder::addAction)
        dismissAction?.let(builder::addAction)
        val progress = state.progress
        if (progress != null && Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgress(progress.progressUnits)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(progress.progressMax)))
                    .setProgressTrackerIcon(
                        buildRoundedLauncherIcon(
                            sizePx = dp(28f).toInt(),
                            radiusPx = dp(9f),
                        ) ?: Icon.createWithResource(context, R.mipmap.ic_launcher),
                    )
                    .setProgressPoints(progress.breakPointUnits.map { Notification.ProgressStyle.Point(it) })
            )
        } else {
            builder.setStyle(Notification.BigTextStyle().setBigContentTitle(title).bigText(expanded).setSummaryText(if (state.showStandardNotification) state.summaryText else ""))
        }
        val notification = builder.build()
        decoration.extras?.let { notification.extras.putAll(it) }
        val manager = context.getSystemService(NotificationManager::class.java)
        val canPost = Build.VERSION.SDK_INT >= 36 && manager?.canPostPromotedNotifications() == true
        val promotable = if (Build.VERSION.SDK_INT >= 36) notification.hasPromotableCharacteristics() else null
        return LiveUpdateRenderResult(notification, requestedPromotion, canPost, promotable)
    }

    private fun buildRoundedLauncherIcon(sizePx: Int, radiusPx: Float): Icon? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val size = sizePx.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val path = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radiusPx, radiusPx, Path.Direction.CW) }
            canvas.save(); canvas.clipPath(path); drawable.setBounds(0, 0, size, size); drawable.draw(canvas); canvas.restore()
            Icon.createWithBitmap(bitmap)
        } catch (_: Exception) { null }
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    )
}

internal fun shouldRequestAndroidLiveUpdatePromotion(
    shouldPromote: Boolean,
    vendorSurfaceReady: Boolean,
): Boolean = shouldPromote && !vendorSurfaceReady
