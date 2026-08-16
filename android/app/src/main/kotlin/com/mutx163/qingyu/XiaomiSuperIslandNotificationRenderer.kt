package com.mutx163.qingyu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import com.xzakota.hyper.notification.focus.FocusNotification
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil

internal enum class XiaomiSuperIslandPayloadMode { NONE, HYPER_FOCUS, LEGACY_FOCUS }

internal fun selectXiaomiSuperIslandPayloadMode(
    isXiaomiDevice: Boolean,
    shouldPromote: Boolean,
    statusBarOnly: Boolean,
    engine: String,
): XiaomiSuperIslandPayloadMode {
    if (!isXiaomiDevice || !shouldPromote || statusBarOnly) return XiaomiSuperIslandPayloadMode.NONE
    return when (engine) {
        "hyperFocusApi" -> XiaomiSuperIslandPayloadMode.HYPER_FOCUS
        "builtIn" -> XiaomiSuperIslandPayloadMode.LEGACY_FOCUS
        else -> XiaomiSuperIslandPayloadMode.NONE
    }
}

internal fun isXiaomiSuperIslandPayloadReady(
    mode: XiaomiSuperIslandPayloadMode,
    hyperFocusBuilt: Boolean,
    legacyFocusBuilt: Boolean,
): Boolean = when (mode) {
    XiaomiSuperIslandPayloadMode.HYPER_FOCUS -> hyperFocusBuilt
    XiaomiSuperIslandPayloadMode.LEGACY_FOCUS -> legacyFocusBuilt
    XiaomiSuperIslandPayloadMode.NONE -> false
}

internal fun hyperFocusTemplateStage(stage: LiveUpdateNotificationStage): String =
    when (stage) {
        LiveUpdateNotificationStage.BEFORE_CLASS -> "pre"
        else -> "active"
    }

internal data class XiaomiSuperIslandSettings(
    val engine: String,
    val showCountdown: Boolean,
    val countdownTextStyle: String,
    val visibleLocation: String,
    val islandName: String,
    val focusHintText: String,
    val progressBreakOffsetsMillis: LongArray,
    val progressMilestoneLabels: List<String>,
    val enableLabelImage: Boolean,
    val labelStyle: String,
    val labelText: String,
    val labelFontColor: String,
    val labelFontWeight: String,
    val labelRenderQuality: String,
    val labelFontSize: Float,
    val labelOffsetX: Float,
    val labelOffsetY: Float,
    val labelLogoPath: String?,
    val labelLogoCornerRadius: Float,
    val expandedIconMode: String,
    val expandedIconPath: String?,
    val timeoutPre: Int,
    val timeoutActive: Int,
    val timeoutPost: Int,
    val iconAEnabled: Boolean,
    val outEffectEnabled: Boolean,
    val outEffectColor: String,
)

internal data class XiaomiSuperIslandRenderResult(
    val payloadMode: XiaomiSuperIslandPayloadMode,
    val isXiaomiDevice: Boolean,
    val hyperFocusExtras: Bundle? = null,
    val legacyFocusParam: String? = null,
    val labelBitmap: Bitmap? = null,
    val expandedIcon: Icon? = null,
    val isIslandReady: Boolean = false,
) {
    val decoration: LiveUpdateNotificationDecoration
        get() = LiveUpdateNotificationDecoration(
            smallIcon = labelBitmap?.let { Icon.createWithBitmap(it) },
            largeIcon = expandedIcon,
            extras = Bundle().apply {
                hyperFocusExtras?.let(::putAll)
                legacyFocusParam?.let { putString("miui.focus.param", it) }
            }.takeIf { it.size() > 0 },
            suppressAndroidPromotion = isIslandReady,
            isVendorSurfaceReady = isIslandReady,
        )
}

internal class XiaomiSuperIslandNotificationRenderer(private val context: Context) {
    private var cachedLabelKey: String? = null
    private var cachedLabelBitmap: Bitmap? = null

    fun render(
        state: LiveUpdateNotificationState,
        settings: XiaomiSuperIslandSettings,
    ): XiaomiSuperIslandRenderResult {
        val isXiaomiDevice = isXiaomiFamilyDevice()
        val mode = selectXiaomiSuperIslandPayloadMode(
            isXiaomiDevice = isXiaomiDevice,
            shouldPromote = state.shouldPromote,
            statusBarOnly = state.stage.isStatusBarOnly,
            engine = settings.engine,
        )
        val label = resolveLabelBitmap(settings)
        val icon = resolveExpandedIcon(settings)
        val hint = settings.focusHintText
        return when (mode) {
            XiaomiSuperIslandPayloadMode.HYPER_FOCUS -> {
                val extras = buildHyperFocusBundle(state, settings, hint)
                XiaomiSuperIslandRenderResult(
                    payloadMode = mode,
                    isXiaomiDevice = isXiaomiDevice,
                    hyperFocusExtras = extras,
                    labelBitmap = label,
                    expandedIcon = icon,
                    isIslandReady = isXiaomiSuperIslandPayloadReady(
                        mode = mode,
                        hyperFocusBuilt = extras != null,
                        legacyFocusBuilt = false,
                    ),
                )
            }
            XiaomiSuperIslandPayloadMode.LEGACY_FOCUS -> {
                val param = buildMiuiFocusParam(state, settings, hint)
                XiaomiSuperIslandRenderResult(
                    payloadMode = mode,
                    isXiaomiDevice = isXiaomiDevice,
                    legacyFocusParam = param,
                    labelBitmap = label,
                    expandedIcon = icon,
                    isIslandReady = isXiaomiSuperIslandPayloadReady(
                        mode = mode,
                        hyperFocusBuilt = false,
                        legacyFocusBuilt = param != null,
                    ),
                )
            }
            XiaomiSuperIslandPayloadMode.NONE -> XiaomiSuperIslandRenderResult(
                payloadMode = mode,
                isXiaomiDevice = isXiaomiDevice,
                labelBitmap = label,
                expandedIcon = icon,
            )
        }
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics,
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics,
    )

    private fun resolveLabelBitmap(settings: XiaomiSuperIslandSettings): Bitmap? {
        if (!settings.enableLabelImage || !isXiaomiFamilyDevice() || settings.labelText.isBlank()) return null
        val key = listOf(
            settings.labelText, settings.labelStyle,
            settings.labelFontColor, settings.labelFontWeight,
            settings.labelRenderQuality, settings.labelFontSize,
            settings.labelOffsetX, settings.labelOffsetY,
            settings.labelLogoPath.orEmpty(), settings.labelLogoCornerRadius,
        ).joinToString("|")
        if (key == cachedLabelKey) return cachedLabelBitmap
        cachedLabelKey = key
        cachedLabelBitmap = buildLabelBitmap(settings)
        return cachedLabelBitmap
    }

    private fun buildLabelBitmap(settings: XiaomiSuperIslandSettings): Bitmap? {
        val scale = when (settings.labelRenderQuality) { "high" -> 3f; "ultra" -> 4f; else -> 2f }
        val withIcon = settings.labelStyle == "icon_and_text"
        val typeface = when (settings.labelFontWeight) {
            "regular" -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val color = parseColor(settings.labelFontColor)
        val base = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface; textSize = sp(settings.labelFontSize.coerceIn(1f, 32f)); this.color = color
            isFakeBoldText = settings.labelFontWeight == "bold"
        }
        val iconDp = if (withIcon) 24f else 0f
        val gapDp = if (withIcon) 3f else 0f
        val padDp = if (withIcon) 3f else .75f
        val maxWidth = dp(132f - padDp * 2f - iconDp - gapDp).coerceAtLeast(dp(28f))
        var size = settings.labelFontSize.coerceIn(1f, 32f)
        while (size > 1f && base.measureText(settings.labelText) > maxWidth) { size -= 1f; base.textSize = sp(size) }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface; textSize = sp(size) * scale; this.color = color
            isFakeBoldText = settings.labelFontWeight == "bold"
            setShadowLayer(dp(.75f) * scale, 0f, dp(.25f) * scale, 0x44000000)
        }
        val text = TextUtils.ellipsize(settings.labelText, base, maxWidth, TextUtils.TruncateAt.END).toString()
        val bounds = android.graphics.Rect(); paint.getTextBounds(text, 0, text.length, bounds)
        val textWidth = paint.measureText(text)
        val iconSize = (dp(iconDp) * scale).toInt(); val gap = dp(gapDp) * scale; val pad = dp(padDp) * scale
        val width = ceil(pad * 2 + textWidth + if (withIcon) iconSize + gap else 0f).toInt()
        val height = maxOf(ceil(pad * 2 + maxOf(bounds.height().toFloat(), iconSize.toFloat())).toInt(), (dp(18f) * scale).toInt())
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        var textX = pad
        if (withIcon) {
            val top = (height - iconSize) / 2
            val custom = settings.labelLogoPath?.let { decodeSquareBitmap(it, iconSize) }
            if (custom != null) drawRoundedBitmap(canvas, custom, pad, top.toFloat(), iconSize.toFloat(), dp(settings.labelLogoCornerRadius))
            else context.packageManager.getApplicationIcon(context.packageName).apply { setBounds(pad.toInt(), top, pad.toInt() + iconSize, top + iconSize); draw(canvas) }
            textX += iconSize + gap
        } else textX = ((width - textWidth) / 2f + dp(settings.labelOffsetX)).coerceIn(pad, width - pad - textWidth)
        if (withIcon) textX = (textX + dp(settings.labelOffsetX)).coerceIn(pad, width - pad - textWidth)
        val baseline = height / 2f - (bounds.top + bounds.bottom) / 2f + dp(settings.labelOffsetY)
        canvas.drawText(text, textX, baseline, paint)
        return bitmap
    }

    private fun buildHyperFocusBundle(
        state: LiveUpdateNotificationState,
        settings: XiaomiSuperIslandSettings,
        remaining: String,
    ): Bundle? = try {
        val templates = loadHyperFocusTemplates(context)
        val key = hyperFocusTemplateStage(state.stage)
        val target = if (key == "pre") state.startAtMillis else state.endAtMillis
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
        val resolve: (String) -> String = { template -> resolveTemplate(template, state.courseName, state.shortCourseNameRaw, state.location, state.teacher, state.startTimeText, state.endTimeText, "", "") }
        FocusNotification.buildV3 {
            business = "course_schedule"; updatable = true; enableFloat = true
            ticker = resolve(templates["ticker_$key"] ?: "{课名}"); aodTitle = ticker; islandFirstFloat = true
            outEffectSrc = if (settings.outEffectEnabled) "outer_glow" else ""
            outEffectColor = if (settings.outEffectEnabled) settings.outEffectColor else ""
            baseInfo { type = 2; title = resolve(templates["baseTitle_$key"] ?: "{课名}"); content = listOfNotNull(resolve(templates["baseContent_$key"] ?: "").ifBlank { null }, resolve(templates["baseSubcontent_$key"] ?: "").ifBlank { null }).joinToString(" · ") }
            picInfo { if (settings.iconAEnabled) type = 1 }
            hintInfo {
                type = 2; title = resolve(templates["hintTitle_$key"] ?: ""); content = remaining.ifBlank { title }
                subTitle = resolve(templates["hintSubtitle_$key"] ?: ""); extraTitle = resolve(templates["hintContent_$key"] ?: ""); specialTitle = resolve(templates["hintSubcontent_$key"] ?: "")
                if (settings.showCountdown) timerInfo { timerType = -1; timerWhen = target; timerSystemCurrent = state.nowMillis }
                actionInfo { actionIntentType = 1; actionIntent = launch.toUri(Intent.URI_INTENT_SCHEME); actionTitle = "查看课表" }
            }
            island {
                islandProperty = 1; islandTimeout = when (key) { "pre" -> settings.timeoutPre; "post" -> settings.timeoutPost; else -> settings.timeoutActive }
                bigIslandArea {
                    imageTextInfoLeft { type = 1; textInfo { title = resolve(templates["islandA_$key"] ?: "{课名}"); showHighlightColor = true }; picInfo { if (settings.iconAEnabled) type = 1 } }
                    val b = templates["islandB_$key"] ?: ""
                    if (b.isNotEmpty()) sameWidthDigitInfo { if (settings.showCountdown && b.contains("倒计时")) timerInfo { timerType = -1; timerWhen = target; timerSystemCurrent = state.nowMillis } else content = resolve(b); turnAnim = true; showHighlightColor = true }
                }
                smallIslandArea { }; shareData { title = state.courseName; content = state.location }
            }
        }.also { if (settings.outEffectEnabled) { it.putString("miui.bigIsland.effect.src", "outer_glow"); it.putString("miui.effect.src", "outer_glow") } }
    } catch (error: Exception) {
        Log.e(TAG, "buildHyperFocusBundle failed", error)
        null
    }

    private fun buildMiuiFocusParam(
        state: LiveUpdateNotificationState,
        settings: XiaomiSuperIslandSettings,
        remaining: String,
    ): String? = try {
        val extraInfo = JSONObject().apply {
            if (settings.visibleLocation.isNotBlank()) put("location", settings.visibleLocation)
            if (state.teacher.isNotBlank()) put("teacher", state.teacher)
            if (state.timeRangeText.isNotBlank()) put("time", state.timeRangeText)
            if (state.nextCourseName.isNotBlank()) put("nextCourse", state.nextCourseName)
        }
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", "class_schedule")
            put("updatable", true)
            put("enableFloat", true)
            put("ticker", state.title)
            put(
                "baseInfo",
                JSONObject().apply {
                    put("title", state.title)
                    put("content", state.promotedContentText.ifBlank { remaining })
                    put("type", 2)
                },
            )
            if (remaining.isNotBlank()) {
                put(
                    "hintInfo",
                    JSONObject().apply {
                        put("type", 1)
                        put("title", remaining)
                    },
                )
            }
            if (extraInfo.length() > 0) put("extraInfo", extraInfo)
            put("param_island", buildIslandSummary(state, settings))
        }
        JSONObject().apply { put("param_v2", paramV2) }.toString()
    } catch (error: Exception) {
        Log.w(TAG, DiagnosticLogMessages.LOG_BUILD_MIUI_FOCUS_PARAM_FAILED, error)
        null
    }

    private fun buildIslandSummary(
        state: LiveUpdateNotificationState,
        settings: XiaomiSuperIslandSettings,
    ): JSONObject {
        val totalMillis = (state.endAtMillis - state.startAtMillis).coerceAtLeast(1L)
        val elapsedMillis = (state.nowMillis - state.startAtMillis).coerceIn(0L, totalMillis)
        val progressPercent = state.progress?.progressPercent
            ?: ((elapsedMillis.toDouble() / totalMillis.toDouble()) * 100)
                .toInt()
                .coerceIn(0, 100)
        val islandContentText = when (state.stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS,
            LiveUpdateNotificationStage.BEFORE_END -> remainingTextForIsland(state, settings)
            else -> state.progress?.compactDisplayText ?: state.stageTitle
        }
        val bigIslandArea = JSONObject().apply {
            put(
                "imageTextInfoLeft",
                JSONObject().apply {
                    put("type", 1)
                    put(
                        "textInfo",
                        JSONObject().apply {
                            put("title", settings.islandName)
                            put("content", islandContentText)
                        },
                    )
                    if (state.stage == LiveUpdateNotificationStage.DURING_CLASS &&
                        state.progress != null
                    ) {
                        put("progressInfo", buildLegacyProgressInfo(progressPercent))
                    }
                },
            )
            if (state.stage == LiveUpdateNotificationStage.DURING_CLASS &&
                state.progress != null
            ) {
                val milestonePoints = buildMilestonePoints(settings, totalMillis)
                put(
                    "progressTextInfo",
                    JSONObject().apply {
                        put(
                            "progressInfo",
                            buildLegacyProgressInfo(progressPercent).apply {
                                milestonePoints.firstOrNull()?.let {
                                    put("picMiddle", it.picKey)
                                }
                            },
                        )
                        put(
                            "textInfo",
                            JSONObject().apply {
                                put(
                                    "title",
                                    state.progress.nextMilestoneDisplayText
                                        ?: state.progress.finalDismissDisplayText,
                                )
                            },
                        )
                    },
                )
            }
        }
        return JSONObject().apply {
            put("islandProperty", 1)
            put("islandTimeout", 3600)
            put("bigIslandArea", bigIslandArea)
            put("smallIslandArea", JSONObject())
        }
    }

    private fun buildLegacyProgressInfo(progressPercent: Int): JSONObject =
        JSONObject().apply {
            put("progress", progressPercent)
            put("colorReach", "#4CAF50")
            put("colorUnReach", "#33FFFFFF")
        }

    private fun remainingTextForIsland(
        state: LiveUpdateNotificationState,
        settings: XiaomiSuperIslandSettings,
    ): String =
        when (state.stage) {
            LiveUpdateNotificationStage.BEFORE_CLASS -> {
                val remaining = state.startAtMillis - state.nowMillis
                if (remaining > 0L) {
                    context.getString(
                        R.string.remaining_until_class_start,
                        CountdownFormat.formatDuration(
                            remaining,
                            settings.countdownTextStyle,
                            60_000L,
                        ),
                    )
                } else {
                    state.stageTitle
                }
            }
            LiveUpdateNotificationStage.BEFORE_END -> {
                val remaining = state.endAtMillis - state.nowMillis
                if (remaining > 0L) {
                    context.getString(
                        R.string.remaining_until_class_end,
                        CountdownFormat.formatDuration(
                            remaining,
                            settings.countdownTextStyle,
                            60_000L,
                        ),
                    )
                } else {
                    state.stageTitle
                }
            }
            else -> state.stageTitle
        }

    private data class MilestonePoint(
        val position: Int,
        val picKey: String,
    )

    private fun buildMilestonePoints(
        settings: XiaomiSuperIslandSettings,
        totalMillis: Long,
    ): List<MilestonePoint> {
        if (settings.progressBreakOffsetsMillis.isEmpty() || totalMillis <= 0L) {
            return emptyList()
        }
        return settings.progressBreakOffsetsMillis.indices.mapNotNull { index ->
            settings.progressMilestoneLabels.getOrNull(index) ?: return@mapNotNull null
            val position = (
                settings.progressBreakOffsetsMillis[index].toDouble() /
                    totalMillis.toDouble() * 100
                ).toInt().coerceIn(1, 99)
            MilestonePoint(position, "miui.focus.pic_milestone_$index")
        }.distinctBy { it.position }.sortedBy { it.position }
    }

    private fun resolveExpandedIcon(settings: XiaomiSuperIslandSettings): Icon? = when (settings.expandedIconMode) {
        "hidden" -> null
        "custom_image" -> settings.expandedIconPath?.let { decodeSquareBitmap(it, dp(56f).toInt().coerceAtLeast(96)) }?.let(Icon::createWithBitmap)
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Icon.createWithResource(context, R.mipmap.ic_launcher) else null
    }

    private fun parseColor(value: String): Int = try { val s = value.trim().removePrefix("#"); when (s.length) { 6 -> (0xFF000000 or s.toLong(16)).toInt(); 8 -> s.toLong(16).toInt(); else -> 0xFFFFFFFF.toInt() } } catch (_: Exception) { 0xFFFFFFFF.toInt() }

    private fun decodeSquareBitmap(path: String, target: Int): Bitmap? {
        val source = BitmapFactory.decodeFile(path) ?: return null; val side = minOf(source.width, source.height); if (side <= 0) { source.recycle(); return null }
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side); if (cropped != source) source.recycle()
        val scaled = Bitmap.createScaledBitmap(cropped, target.coerceAtLeast(1), target.coerceAtLeast(1), true); if (scaled != cropped) cropped.recycle(); return scaled
    }

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, size: Float, radius: Float) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader; isFilterBitmap = true })
    }

    companion object {
        private const val TAG = "XiaomiIslandRenderer"
    }
}
