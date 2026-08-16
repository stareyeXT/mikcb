package com.mutx163.qingyu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLiveUpdateNotificationRendererTest {
    @Test
    fun requestsAndroidPromotionOnlyWithoutXiaomiPayload() {
        assertTrue(
            shouldRequestAndroidLiveUpdatePromotion(
                shouldPromote = true,
                vendorSurfaceReady = false,
            ),
        )
        assertFalse(
            shouldRequestAndroidLiveUpdatePromotion(
                shouldPromote = true,
                vendorSurfaceReady = true,
            ),
        )
        assertFalse(
            shouldRequestAndroidLiveUpdatePromotion(
                shouldPromote = false,
                vendorSurfaceReady = false,
            ),
        )
    }
}
