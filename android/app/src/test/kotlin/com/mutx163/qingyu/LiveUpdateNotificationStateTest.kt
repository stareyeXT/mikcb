package com.mutx163.qingyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateNotificationStateTest {
    @Test
    fun beforeAndEndStagesPromote() {
        assertTrue(LiveUpdateNotificationStage.BEFORE_CLASS.shouldPromote(true))
        assertTrue(LiveUpdateNotificationStage.BEFORE_END.shouldPromote(true))
    }

    @Test
    fun duringClassFollowsPromotionSetting() {
        assertTrue(LiveUpdateNotificationStage.DURING_CLASS.shouldPromote(true))
        assertFalse(LiveUpdateNotificationStage.DURING_CLASS.shouldPromote(false))
    }

    @Test
    fun statusBarStageNeverPromotes() {
        assertFalse(LiveUpdateNotificationStage.DURING_CLASS_STATUS_BAR.shouldPromote(true))
        assertTrue(
            LiveUpdateNotificationStage.DURING_CLASS_STATUS_BAR.showStandardNotification(false)
        )
    }

    @Test
    fun afterClassPromotesAndShowsStandardNotification() {
        assertTrue(LiveUpdateNotificationStage.AFTER_CLASS.shouldPromote(true))
        assertTrue(LiveUpdateNotificationStage.AFTER_CLASS.shouldPromote(false))
        assertTrue(LiveUpdateNotificationStage.AFTER_CLASS.showStandardNotification(false))
    }

    @Test
    fun afterClassWireValueRoundTrips() {
        assertEquals(
            "afterClass",
            LiveUpdateNotificationStage.AFTER_CLASS.wireValue,
        )
        assertEquals(
            LiveUpdateNotificationStage.AFTER_CLASS,
            LiveUpdateNotificationStage.fromWireValue("afterClass"),
        )
    }
}
