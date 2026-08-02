package com.mutx163.qingyu

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 测试焦点通知（id=10001）的自动取消调度。
 *
 * - 每次发送时重新调度，自动取消旧调度，避免多个 dismiss 互相干扰；
 * - 截止时间写入 prefs，进程被杀后重启时可通过 [reconcile] 自愈：
 *   已过期的卡死通知直接取消，未过期的重新调度。
 */
object TestFocusNotificationDismiss {
    private const val PREFS_NAME = "native_runtime_prefs"
    private const val KEY_DISMISS_AT = "test_focus_dismiss_at"
    private const val TEST_NOTIFICATION_ID = 10001

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    @Synchronized
    fun schedule(context: Context, dismissAt: Long) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        if (dismissAt <= now) {
            cancelNow(appContext)
            return
        }
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = Runnable {
            cancelNow(appContext)
        }
        handler.postDelayed(pendingRunnable!!, dismissAt - now)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISMISS_AT, dismissAt)
            .apply()
    }

    @Synchronized
    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissAt = prefs.getLong(KEY_DISMISS_AT, 0L)
        val hasActive = runCatching {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.activeNotifications.any { it.id == TEST_NOTIFICATION_ID }
        }.getOrDefault(false)
        if (!hasActive) {
            prefs.edit().remove(KEY_DISMISS_AT).apply()
            return
        }
        if (dismissAt <= System.currentTimeMillis()) {
            cancelNow(appContext)
        } else {
            schedule(appContext, dismissAt)
        }
    }

    private fun cancelNow(appContext: Context) {
        runCatching {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(TEST_NOTIFICATION_ID)
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DISMISS_AT)
            .apply()
    }
}
