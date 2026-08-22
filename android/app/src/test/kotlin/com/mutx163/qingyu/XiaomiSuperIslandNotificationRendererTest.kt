package com.mutx163.qingyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiSuperIslandNotificationRendererTest {
    @Test
    fun selectsHyperFocusEngineOnlyForPromotedXiaomiStage() {
        assertEquals(
            XiaomiSuperIslandPayloadMode.HYPER_FOCUS,
            selectXiaomiSuperIslandPayloadMode(
                isXiaomiDevice = true,
                shouldPromote = true,
                statusBarOnly = false,
                engine = "hyperFocusApi",
            ),
        )
    }

    @Test
    fun selectsLegacyEngineForBuiltInMode() {
        assertEquals(
            XiaomiSuperIslandPayloadMode.LEGACY_FOCUS,
            selectXiaomiSuperIslandPayloadMode(
                isXiaomiDevice = true,
                shouldPromote = true,
                statusBarOnly = false,
                engine = "builtIn",
            ),
        )
    }

    @Test
    fun neverBuildsIslandPayloadOutsideXiaomiPromotedPath() {
        assertEquals(
            XiaomiSuperIslandPayloadMode.NONE,
            selectXiaomiSuperIslandPayloadMode(false, true, false, "hyperFocusApi"),
        )
        assertEquals(
            XiaomiSuperIslandPayloadMode.NONE,
            selectXiaomiSuperIslandPayloadMode(true, false, false, "hyperFocusApi"),
        )
        assertEquals(
            XiaomiSuperIslandPayloadMode.NONE,
            selectXiaomiSuperIslandPayloadMode(true, true, true, "hyperFocusApi"),
        )
    }

    @Test
    fun payloadMustBuildBeforeItSuppressesAndroidLiveUpdate() {
        assertFalse(
            isXiaomiSuperIslandPayloadReady(
                XiaomiSuperIslandPayloadMode.HYPER_FOCUS,
                hyperFocusBuilt = false,
                legacyFocusBuilt = false,
            ),
        )
        assertTrue(
            isXiaomiSuperIslandPayloadReady(
                XiaomiSuperIslandPayloadMode.LEGACY_FOCUS,
                hyperFocusBuilt = false,
                legacyFocusBuilt = true,
            ),
        )
    }

    @Test
    fun beforeEndUsesTheActiveTemplateUntilTheCourseActuallyEnds() {
        assertEquals(
            "pre",
            hyperFocusTemplateStage(LiveUpdateNotificationStage.BEFORE_CLASS),
        )
        assertEquals(
            "active",
            hyperFocusTemplateStage(LiveUpdateNotificationStage.DURING_CLASS),
        )
        assertEquals(
            "active",
            hyperFocusTemplateStage(LiveUpdateNotificationStage.BEFORE_END),
        )
    }

    @Test
    fun afterClassUsesThePostTemplate() {
        assertEquals(
            "post",
            hyperFocusTemplateStage(LiveUpdateNotificationStage.AFTER_CLASS),
        )
    }

    @Test
    fun statusBarOnlyDuringClassStillUsesActiveTemplate() {
        assertEquals(
            "active",
            hyperFocusTemplateStage(LiveUpdateNotificationStage.DURING_CLASS_STATUS_BAR),
        )
    }

    /**
     * 钉住 Kotlin 兜底默认值与 Flutter 侧 _defaultTemplates（live_settings_subpages.dart）的
     * 关键一致点：islandB_active 两端必须同为「倒计时」，否则从未保存过模板的设备
     * 正式路径走文字分支、设置页保存后跳变为数字分支。
     */
    @Test
    fun islandBActiveDefaultMatchesFlutterSide() {
        assertEquals("倒计时", hfDefaultTemplates["islandB_active"])
        assertEquals("", hfDefaultTemplates["islandB_pre"])
        assertEquals("已下课", hfDefaultTemplates["islandB_post"])
    }

    @Test
    fun countdownFormattingUsesAsciiDigitsForAllLocales() {
        assertEquals("00:00", formatCountdownForTemplate(0L))
        assertEquals("00:00", formatCountdownForTemplate(-5L))
        assertEquals("00:59", formatCountdownForTemplate(59_000L))
        assertEquals("01:05", formatCountdownForTemplate(65_000L))
        assertEquals("1:01:05", formatCountdownForTemplate(3_665_000L))
        assertEquals("25:00:00", formatElapsedForTemplate(90_000_000L))
    }

    /**
     * 岛右侧后缀严格按模板渲染：只有模板含「倒计时」token 且开关开启、非课后阶段，
     * 才渲染系统走秒数字；否则纯文字，开关不再强制注入倒计时。
     */
    @Test
    fun islandWantsSystemTimerOnlyWhenTemplateRequestsIt() {
        assertTrue(islandWantsSystemTimer("倒计时", showCountdown = true, isPost = false))
        assertTrue(islandWantsSystemTimer("距离下课,{倒计时}", showCountdown = true, isPost = false))
        assertFalse(islandWantsSystemTimer("上课中", showCountdown = true, isPost = false))
        // 开关关闭：即使模板写了「倒计时」也不走系统计时（退化为静态数字文本分支）
        assertFalse(islandWantsSystemTimer("倒计时", showCountdown = false, isPost = false))
        // 课后阶段永不走秒
        assertFalse(islandWantsSystemTimer("倒计时", showCountdown = true, isPost = true))
    }

    @Test
    fun islandLabelWithoutTimerTokensStripsOnlyTheTimerToken() {
        assertEquals("", islandLabelWithoutTimerTokens("倒计时"))
        assertEquals("", islandLabelWithoutTimerTokens("{倒计时}"))
        assertEquals("距离下课", islandLabelWithoutTimerTokens("距离下课,倒计时"))
        assertEquals("上课中", islandLabelWithoutTimerTokens(" 上课中 "))
    }
}
