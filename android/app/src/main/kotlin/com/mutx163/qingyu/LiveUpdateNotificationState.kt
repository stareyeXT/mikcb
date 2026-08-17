package com.mutx163.qingyu

import android.graphics.drawable.Icon
import android.os.Bundle

internal enum class LiveUpdateNotificationStage {
    BEFORE_CLASS,
    DURING_CLASS,
    DURING_CLASS_STATUS_BAR,
    BEFORE_END,
    AFTER_CLASS;

    val wireValue: String
        get() = when (this) {
            BEFORE_CLASS -> "beforeClass"
            DURING_CLASS -> "duringClass"
            DURING_CLASS_STATUS_BAR -> "duringClassStatusBar"
            BEFORE_END -> "beforeEnd"
            AFTER_CLASS -> "afterClass"
        }

    val isUpcoming: Boolean get() = this == BEFORE_CLASS
    val isDuringClass: Boolean get() = this == DURING_CLASS || this == DURING_CLASS_STATUS_BAR
    val isStatusBarOnly: Boolean get() = this == DURING_CLASS_STATUS_BAR
    val isEndingSoon: Boolean get() = this == BEFORE_END

    fun shouldPromote(promoteDuringClass: Boolean): Boolean = when (this) {
        DURING_CLASS_STATUS_BAR -> false
        DURING_CLASS -> promoteDuringClass
        BEFORE_CLASS, BEFORE_END, AFTER_CLASS -> true
    }

    fun showStandardNotification(showDuringClass: Boolean): Boolean = when (this) {
        DURING_CLASS -> showDuringClass
        BEFORE_CLASS, DURING_CLASS_STATUS_BAR, BEFORE_END, AFTER_CLASS -> true
    }

    companion object {
        fun fromWireValue(value: String?): LiveUpdateNotificationStage? = when (value) {
            "beforeClass" -> BEFORE_CLASS
            "duringClass" -> DURING_CLASS
            "duringClassStatusBar" -> DURING_CLASS_STATUS_BAR
            "beforeEnd" -> BEFORE_END
            "afterClass" -> AFTER_CLASS
            else -> null
        }
    }
}

internal data class LiveUpdateProgressState(
    val progressMax: Int,
    val progressUnits: Int,
    val progressPercent: Int,
    val nextMilestoneDisplayText: String?,
    val finalDismissDisplayText: String,
    val compactDisplayText: String,
    val criticalTimeText: String,
    val breakPointUnits: List<Int>,
    val updatesEverySecond: Boolean,
)

/**
 * Optional decorations applied to the platform notification after the base
 * Live Update notification has been built.  Keeping this type neutral avoids
 * making the Android renderer depend on a vendor-specific renderer.
 */
internal data class LiveUpdateNotificationDecoration(
    val smallIcon: Icon? = null,
    val largeIcon: Icon? = null,
    val extras: Bundle? = null,
    val suppressAndroidPromotion: Boolean = false,
    val isVendorSurfaceReady: Boolean = false,
)

internal data class LiveUpdateNotificationState(
    val nowMillis: Long,
    val stage: LiveUpdateNotificationStage,
    val shouldPromote: Boolean,
    val showStandardNotification: Boolean,
    val courseName: String,
    val shortCourseNameRaw: String,
    val location: String,
    val teacher: String,
    val nextCourseName: String,
    val startTimeText: String,
    val endTimeText: String,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val stageTitle: String,
    val title: String,
    val timeRangeText: String,
    val subText: String,
    val summaryText: String,
    val contentText: String,
    val expandedDetailText: String,
    val promotedContentText: String,
    val promotedExpandedDetailText: String,
    val islandCriticalText: String,
    val progress: LiveUpdateProgressState?,
)
