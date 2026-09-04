package com.mutx163.qingyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateServiceLogicTest {
    @Test
    fun internalBreakIsBeforeNextSectionAndExcludesBoundary() {
        val start = 1_000_000L
        val offsets = longArrayOf(45 * 60_000L, 55 * 60_000L)

        assertEquals(
            start + 55 * 60_000L,
            liveInternalBreakWindow(start + 50 * 60_000L, start, offsets)?.last,
        )
        assertEquals(
            start + 55 * 60_000L,
            liveInternalBreakWindow(start + 45 * 60_000L, start, offsets)?.last,
        )
        assertEquals(null, liveInternalBreakWindow(start + 55 * 60_000L, start, offsets))
    }

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

    @Test
    fun afterClassWindowIsIndependentFromIslandNotificationLifecycle() {
        assertEquals(10 * 60_000L, LiveUpdateService.AFTER_CLASS_DISPLAY_WINDOW_MILLIS)
    }

    @Test
    fun islandPresentationSessionChangesWhenTheStageChanges() {
        val course = "高等数学"
        val duringClass = LiveUpdateService.buildIslandPresentationSessionKey(
            course, "duringClass", 1_000L, 5_400_000L,
        )
        val beforeEnd = LiveUpdateService.buildIslandPresentationSessionKey(
            course, "beforeEnd", 1_000L, 5_400_000L,
        )

        assertTrue(duringClass != beforeEnd)
    }
}
