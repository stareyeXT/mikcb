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

    /** 与正式/测试两条路径的 resolve lambda 同构：变量替换 + 空值兜底。 */
    private fun testResolve(tpl: String): String = resolveTemplate(
        tpl = tpl,
        courseName = "高等数学",
        shortName = "高数",
        location = "A101",
        teacher = "张老师",
        startTime = "08:00",
        endTime = "09:40",
        countdownText = "45:00",
        elapsedText = "",
    )

    /**
     * B 区槽位决策（bundle 级回归锚点）：数字路径走 timerInfo、纯文字必须进
     * imageTextInfoRight 槽位、post 阶段永不走秒——对应 AGENTS.md 要求的三条验证路径。
     */
    @Test
    fun islandBSlotFollowsTemplateStrictly() {
        val now = 1_000_000L
        val target = now + 5 * 60_000L

        // 数字路径：模板含「倒计时」token 且开关开启 → 系统走秒，标签去掉该 token
        val timerSlot = resolveIslandBSlot("距离下课,倒计时", true, false, target, now, ::testResolve)
        assertTrue(timerSlot is IslandBSlot.SystemTimerDigits)
        assertEquals("距离下课", (timerSlot as IslandBSlot.SystemTimerDigits).label)
        assertEquals(target, timerSlot.timerWhenMillis)

        // 文字路径：纯文字模板即使开关开着也走文字槽位，绝不进 sameWidthDigitInfo
        val textSlot = resolveIslandBSlot("上课中", true, false, target, now, ::testResolve)
        assertEquals(IslandBSlot.IslandText("上课中"), textSlot)

        // 静态数字路径：解析结果为纯数字时进静态数字槽位
        val digitsSlot = resolveIslandBSlot("{结束}", true, false, target, now) { tpl -> testResolve(tpl) }
        assertEquals(IslandBSlot.StaticDigits("09:40"), digitsSlot)

        // 开关关闭：含 token 的模板退化为静态分支（倒计时变量解析为静态文本）
        val disabled = resolveIslandBSlot("倒计时", false, false, target, now, ::testResolve)
        assertEquals(IslandBSlot.StaticDigits("45:00"), disabled)

        // post 阶段：即使模板写了「倒计时」也不走秒
        val post = resolveIslandBSlot("已下课,倒计时", true, true, target, now, ::testResolve)
        assertTrue(post is IslandBSlot.IslandText)

        // 空/全空白模板：不渲染 B 区
        assertEquals(IslandBSlot.None, resolveIslandBSlot("", true, false, target, now, ::testResolve))
        assertEquals(
            IslandBSlot.None,
            resolveIslandBSlot("正计时", true, true, target, now) { _ -> "" },
        )
    }

    /** 过期/未设置的计时目标不得交给 HyperOS 走秒（会被判过期直接下岛），须退回静态分支。 */
    @Test
    fun islandBSlotFallsBackToStaticBranchWhenTimerTargetInvalid() {
        val now = 1_000_000L

        assertFalse(hyperFocusTimerTargetIsValid(0L, now))
        assertFalse(hyperFocusTimerTargetIsValid(now - 1_000L, now))
        assertTrue(hyperFocusTimerTargetIsValid(now + 1_000L, now))

        val expired = resolveIslandBSlot("距离下课,倒计时", true, false, now - 1_000L, now, ::testResolve)
        // 退回后整段模板按普通文本解析（含中文），绝不携带系统走秒
        assertEquals(IslandBSlot.IslandText("距离下课 45:00"), expired)

        val unset = resolveIslandBSlot("倒计时", true, false, 0L, now, ::testResolve)
        assertEquals(IslandBSlot.StaticDigits("45:00"), unset)

        assertFalse(hyperFocusHintWantsSystemTimer(true, false, 0L, now))
        assertFalse(hyperFocusHintWantsSystemTimer(true, false, now - 1_000L, now))
        assertTrue(hyperFocusHintWantsSystemTimer(true, false, now + 1_000L, now))
        assertFalse(hyperFocusHintWantsSystemTimer(false, false, now + 1_000L, now))
        assertFalse(hyperFocusHintWantsSystemTimer(true, true, now + 1_000L, now))
    }
}
