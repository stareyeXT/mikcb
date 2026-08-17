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
}
