package com.mutx163.qingyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TomorrowBriefingNotificationBuilderTest {
    @Test
    fun `chooseForm prefers super island on xiaomi family devices`() {
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.SUPER_ISLAND,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = true,
                sdkInt = 28,
            ),
        )
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.SUPER_ISLAND,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = true,
                sdkInt = 36,
            ),
        )
    }

    @Test
    fun `chooseForm picks live updates on sdk 36 plus`() {
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.LIVE_UPDATES,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = false,
                sdkInt = 36,
            ),
        )
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.LIVE_UPDATES,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = false,
                sdkInt = 40,
            ),
        )
    }

    @Test
    fun `chooseForm falls back to plain below sdk 36`() {
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.PLAIN,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = false,
                sdkInt = 35,
            ),
        )
        assertEquals(
            TomorrowBriefingNotificationBuilder.Form.PLAIN,
            TomorrowBriefingNotificationBuilder.chooseForm(
                isXiaomiFamily = false,
                sdkInt = 28,
            ),
        )
    }

    @Test
    fun `fire style round-trips through json persistence`() {
        val fire = ExamReminderScheduler.Fire(
            examId = "tomorrow_briefing:2026-09-01",
            offsetMinutes = 0,
            fireAtMillis = 1796261600000L,
            examStartMillis = 0L,
            title = "明天有早八 · 高等数学 08:00",
            body = "08:00-08:45 高等数学 · A101",
            requestCode = 12345,
            openRoute = "/",
            style = TomorrowBriefingNotificationBuilder.STYLE_AUTO,
            tapAction = TomorrowBriefingNotificationBuilder.TAP_ACTION_OPEN_CALENDAR,
            calendarHour = 8,
            calendarMinute = 0,
            calendarTitle = "高等数学（明日早八）",
            islandA = "早八 高等数学",
            islandB = "08:00 高等数学",
            firstClassStartMillis = 1796265600000L,
        )
        assertEquals(TomorrowBriefingNotificationBuilder.STYLE_AUTO, fire.style)
        assertEquals(
            TomorrowBriefingNotificationBuilder.TAP_ACTION_OPEN_CALENDAR,
            fire.tapAction,
        )
        assertEquals("早八 高等数学", fire.islandA)
        assertEquals(1796265600000L, fire.firstClassStartMillis)
    }

    @Test
    fun `default fire fields stay plain exam reminder`() {
        val fire = ExamReminderScheduler.Fire(
            examId = "exam-1",
            offsetMinutes = 60,
            fireAtMillis = 1796261600000L,
            examStartMillis = 1796265200000L,
            title = "期末考试",
            body = "08:30-10:30 · A-301",
            requestCode = 999,
        )
        assertEquals("normal", fire.style)
        assertEquals("openApp", fire.tapAction)
        assertEquals("/exams", fire.openRoute)
        assertEquals(8, fire.calendarHour)
        assertEquals(0, fire.calendarMinute)
        assertEquals("", fire.calendarTitle)
        assertEquals("", fire.islandA)
        assertEquals("", fire.islandB)
        assertEquals(0L, fire.firstClassStartMillis)
        assertTrue(fire.style != TomorrowBriefingNotificationBuilder.STYLE_AUTO)
    }
}
