package com.mutx163.qingyu

import android.os.Build
import java.util.Locale

/** Shared device-family detection used by both the live-island renderer and
 *  the tomorrow-briefing notification builder. */
object XiaomiDeviceFamily {
    fun isXiaomiFamilyDevice(): Boolean {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
    }
}
