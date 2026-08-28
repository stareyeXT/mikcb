package com.mutx163.qingyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateServiceLogicTest {
    @Test
    fun beforeClassQuickActionRestoresAfterClassEndWhenDue() {
        assertTrue(
            beforeClassQuickActionShouldRestoreAfterClassEnd(
                nowMillis = 1_700_000_000_000L,
                restoreAtMillis = 1_699_999_000_000L,
            )
        )
    }

    @Test
    fun beforeClassQuickActionDoesNotRestoreBeforeClassEnd() {
        assertFalse(
            beforeClassQuickActionShouldRestoreAfterClassEnd(
                nowMillis = 1_699_999_000_000L,
                restoreAtMillis = 1_700_000_000_000L,
            )
        )
    }

    @Test
    fun promotedApi36PlusDoesNotMirrorStatusIntoMiuiFocusHint() {
        assertFalse(
            liveShouldMirrorStatusIntoMiuiFocusHint(
                sdkInt = 36,
                shouldPromote = true,
            )
        )
    }

    @Test
    fun nonPromotedOrOlderBuildsKeepMiuiFocusHint() {
        assertTrue(
            liveShouldMirrorStatusIntoMiuiFocusHint(
                sdkInt = 35,
                shouldPromote = true,
            )
        )
        assertTrue(
            liveShouldMirrorStatusIntoMiuiFocusHint(
                sdkInt = 36,
                shouldPromote = false,
            )
        )
    }

    /**
     * 岛会话身份（Workmanager 周期重投递回归钉）：同课程同阶段同时间的重复启动
     * 必须得到相同 key，从而保留 islandSessionStartedAt 与 suppressed 状态，
     * 否则「岛消失时间」到期下岛后会被周期任务反复复活。
     */
    @Test
    fun islandSessionKeyStableForRedeliveredSameStagePayload() {
        val key = LiveUpdateService.buildIslandSessionKey(
            courseName = "高等数学",
            stage = "duringClass",
            startAtMillis = 1_000L,
            endAtMillis = 5_400_000L,
        )
        assertEquals(
            key,
            LiveUpdateService.buildIslandSessionKey(
                courseName = "高等数学",
                stage = "duringClass",
                startAtMillis = 1_000L,
                endAtMillis = 5_400_000L,
            ),
        )
        // 阶段或课程时间变化必须产生新会话
        assertNotEquals(
            key,
            LiveUpdateService.buildIslandSessionKey(
                courseName = "高等数学",
                stage = "beforeEnd",
                startAtMillis = 1_000L,
                endAtMillis = 5_400_000L,
            ),
        )
        assertNotEquals(
            key,
            LiveUpdateService.buildIslandSessionKey(
                courseName = "大学物理",
                stage = "duringClass",
                startAtMillis = 1_000L,
                endAtMillis = 5_400_000L,
            ),
        )
    }
}
