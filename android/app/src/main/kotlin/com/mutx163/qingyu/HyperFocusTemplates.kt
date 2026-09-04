package com.mutx163.qingyu

import android.content.Context
import java.util.Locale

// ── HyperFocus Template System (top-level, shared by MainActivity and LiveUpdateService) ──

internal val hfDefaultTemplates = mapOf(
    "ticker_pre" to "课名",
    "ticker_active" to "课名",
    "ticker_post" to "课名",
    "islandA_pre" to "教室",
    "islandA_active" to "短课名",
    "islandA_post" to "短课名",
    "islandB_pre" to "",
    "islandB_active" to "倒计时",
    "islandB_post" to "已下课",
    "baseTitle_pre" to "课名",
    "baseTitle_active" to "课名",
    "baseTitle_post" to "课名",
    "baseContent_pre" to "开始,结束",
    "baseContent_active" to "开始,结束",
    "baseContent_post" to "开始,结束",
    "baseSubcontent_pre" to "教室",
    "baseSubcontent_active" to "教室",
    "baseSubcontent_post" to "教室",
    "hintTitle_pre" to "",
    "hintTitle_active" to "上课中",
    "hintTitle_post" to "已下课",
    "hintContent_pre" to "即将上课",
    "hintContent_active" to "距离下课",
    "hintContent_post" to "已经下课",
    "hintSubcontent_pre" to "",
    "hintSubcontent_active" to "",
    "hintSubcontent_post" to "",
    "hintSubtitle_pre" to "",
    "hintSubtitle_active" to "",
    "hintSubtitle_post" to "",
)

internal fun loadHyperFocusTemplates(context: Context): Map<String, String> {
    val json = context.getSharedPreferences("hyper_focus_templates", Context.MODE_PRIVATE)
        .getString("templates_json", null) ?: return hfDefaultTemplates
    return try {
        val obj = org.json.JSONObject(json)
        val merged = hfDefaultTemplates.toMutableMap()
        for (key in obj.keys()) {
            merged[key] = obj.optString(key, hfDefaultTemplates[key] ?: "")
        }
        merged
    } catch (_: Exception) {
        hfDefaultTemplates
    }
}

internal fun resolveTemplate(
    tpl: String,
    courseName: String,
    shortName: String,
    location: String,
    teacher: String,
    startTime: String,
    endTime: String,
    countdownText: String,
    elapsedText: String,
): String {
    if (tpl.contains("{")) {
        var result = tpl
        result = result.replace("{课名}", courseName)
        result = result.replace("{短课名}", shortName.ifBlank { courseName })
        result = result.replace("{教室}", location.ifBlank { courseName })
        result = result.replace("{教师}", teacher)
        result = result.replace("{开始}", startTime)
        result = result.replace("{结束}", endTime)
        result = result.replace("{倒计时}", countdownText)
        result = result.replace("{正计时}", elapsedText)
        return result
    }
    val variableMap = mapOf(
        "课名" to courseName,
        "短课名" to shortName.ifBlank { courseName },
        "教室" to location.ifBlank { courseName },
        "教师" to teacher,
        "开始" to startTime,
        "结束" to endTime,
        "倒计时" to countdownText,
        "正计时" to elapsedText,
    )
    return tpl.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { token -> if (variableMap.containsKey(token)) variableMap[token] ?: "" else token }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

internal fun formatCountdownForTemplate(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatElapsedForTemplate(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** 岛右侧后缀是否请求系统走秒计时：模板含「倒计时」token 且开关开启、非课后阶段。 */
internal fun islandWantsSystemTimer(rawTemplate: String, showCountdown: Boolean, isPost: Boolean): Boolean {
    if (!showCountdown || isPost) return false
    return rawTemplate.split(",")
        .map { it.trim() }
        .any { it == "倒计时" || it == "{倒计时}" }
}

/** 去掉模板中的「倒计时」token，剩余部分作为走秒数字旁的标签。 */
internal fun islandLabelWithoutTimerTokens(rawTemplate: String): String {
    return rawTemplate.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "倒计时" && it != "{倒计时}" }
        .joinToString(",")
}

/** buildV3 的 reopen 字段取值：同 id 通知 cancel 后重发需显式申请重新上岛。 */
internal const val HYPER_FOCUS_REOPEN_VALUE = "reopen"

/**
 * 计时目标是否可安全交给 HyperOS 渲染系统走秒：目标未设置（0）或已过期时
 * 发 timerInfo 会被判为已过期直接下岛，必须退回静态分支。
 */
internal fun hyperFocusTimerTargetIsValid(timerWhenMillis: Long, nowMillis: Long): Boolean {
    return timerWhenMillis > 0L && timerWhenMillis > nowMillis
}

/** hintInfo 区系统走秒开关：与岛右侧无关，仅由总开关、阶段和目标有效性决定。 */
internal fun hyperFocusHintWantsSystemTimer(
    showCountdown: Boolean,
    isPost: Boolean,
    timerWhenMillis: Long,
    nowMillis: Long,
    templateRequestsTimer: Boolean = true,
): Boolean = templateRequestsTimer && showCountdown && !isPost &&
    hyperFocusTimerTargetIsValid(timerWhenMillis, nowMillis)

/** 岛右侧（B 区）最终渲染槽位。 */
internal sealed class IslandBSlot {
    /** 系统走秒数字：sameWidthDigitInfo + timerInfo，[label] 为数字旁小字（可为空）。 */
    data class SystemTimerDigits(val label: String, val timerWhenMillis: Long) : IslandBSlot()

    /** 静态纯数字：sameWidthDigitInfo（该组件只接受数字内容）。 */
    data class StaticDigits(val content: String) : IslandBSlot()

    /** 纯文字：imageTextInfoRight——非数字文字严禁进入 sameWidthDigitInfo（digit is empty）。 */
    data class IslandText(val content: String) : IslandBSlot()

    /** 模板为空/解析为空：不渲染 B 区。 */
    object None : IslandBSlot()
}

private val islandBDigitsOnly = Regex("[0-9.:：\\-]+")

/**
 * 岛右侧（B 区）槽位决策：正式路径（XiaomiSuperIslandNotificationRenderer）与
 * 测试路径（MainActivity.sendTestFocusNotificationInner）必须共用本函数，
 * 禁止两侧各自写分支判断——历史上守卫条件漂移导致「测试和正式不一样」。
 */
internal fun resolveIslandBSlot(
    rawTemplate: String,
    showCountdown: Boolean,
    isPost: Boolean,
    timerWhenMillis: Long,
    nowMillis: Long,
    resolve: (String) -> String,
): IslandBSlot {
    if (rawTemplate.isBlank()) return IslandBSlot.None
    if (islandWantsSystemTimer(rawTemplate, showCountdown, isPost) &&
        hyperFocusTimerTargetIsValid(timerWhenMillis, nowMillis)
    ) {
        return IslandBSlot.SystemTimerDigits(
            label = resolve(islandLabelWithoutTimerTokens(rawTemplate)),
            timerWhenMillis = timerWhenMillis,
        )
    }
    val text = resolve(rawTemplate)
    if (text.isEmpty()) return IslandBSlot.None
    return if (text.matches(islandBDigitsOnly)) IslandBSlot.StaticDigits(text) else IslandBSlot.IslandText(text)
}
