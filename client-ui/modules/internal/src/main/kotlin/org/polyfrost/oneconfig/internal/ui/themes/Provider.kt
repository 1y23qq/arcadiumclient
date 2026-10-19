package org.polyfrost.oneconfig.internal.ui.themes

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.round
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.notifications.v1.NotificationTheme
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.oneconfig.internal.ui.DESIGN_HEIGHT_DP
import org.polyfrost.oneconfig.internal.ui.DESIGN_WIDTH_DP
import org.polyfrost.oneconfig.internal.ui.EDGE_MARGIN_FRACTION

private var _settings by mutableStateOf(ThemeAccent.snapshot())
private var _accent by mutableStateOf(Color(0xFF3AA0FF))
private var _phase by mutableFloatStateOf(0f)

private var _chroma by mutableStateOf(ThemeConfig.accentColor.chroma)

val Accent: Color get() = _accent

internal fun accentSettings() = _settings
internal fun accentPhase() = _phase

fun updateAccent() {
    ThemeAccent.refresh()
    _settings = ThemeAccent.snapshot()
    _accent = if (_settings.gradient()) Color(GradientPresets.mix(_settings.start(), _settings.end(), 0.35))
        else Color(ThemeConfig.accentColor.argb)
    _chroma = !ThemeConfig.gradientAccent && ThemeConfig.accentColor.chroma
}

fun Modifier.accentBackground(shape: Shape, alpha: Float = 1f, enabled: Boolean = true): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind {
        if (enabled && alpha > 0f) drawOutline(outline, accentBrush(alpha))
    }
}

val LocalTheme = compositionLocalOf<UITheme> { error("A UI theme is required but was not provided") }

private const val GRID_ANCHOR_SP = 14f

private const val EM_STEP_PX = 5f

private const val MIN_EM_PX = 10f

private fun scrollbarStyle(theme: UITheme) = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 8.dp,
    shape = theme.scrollBarShape,
    hoverDurationMillis = 300,
    unhoverColor = theme.textColorSecondary.copy(alpha = 0.40f),
    hoverColor = theme.textColorSecondary.copy(alpha = 0.70f),
)

private val screenPlatform by lazy { runCatching { Platform.screen() }.getOrNull() }

private fun surfaceRatio(): Float = screenPlatform?.surfaceRatio()?.takeIf { it > 0f } ?: 1f

@Composable
fun pixelGridScale(scale: Float, max: Float, anchorSp: Float = GRID_ANCHOR_SP): Float {
    if (scale <= 0f) return scale
    val density = LocalDensity.current
    val anchorPx = anchorSp * density.fontScale * density.density * scale * surfaceRatio()
    return scale * snapScaleToPixelGrid(anchorPx, max / scale)
}

@Composable
private fun pixelGridDensity(designWidth: Dp, designHeight: Dp): Density {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val headroom = with(density) {
        minOf(
            (window.width  * EDGE_MARGIN_FRACTION) / designWidth.toPx(),
            (window.height * EDGE_MARGIN_FRACTION) / designHeight.toPx(),
        )
    }
    val scale = pixelGridScale(1f, headroom)
    return remember(density, scale) {
        if (scale == 1f) density else Density(density.density * scale, density.fontScale)
    }
}

internal fun snapScaleToPixelGrid(anchorPx: Float, max: Float): Float {
    if (anchorPx <= 0f) return 1f
    val fits = floor(anchorPx * max / EM_STEP_PX) * EM_STEP_PX
    val nearest = round(anchorPx / EM_STEP_PX) * EM_STEP_PX
    val em = maxOf(nearest, fits)
    val scale = em.coerceAtLeast(MIN_EM_PX) / anchorPx
    return if (scale <= max) scale else max
}

@Composable
fun Theme(content: @Composable () -> Unit) = Theme(pixelGrid = false, content = content)

@Composable
fun Theme(
    pixelGrid: Boolean,
    designWidth: Dp = DESIGN_WIDTH_DP.dp,
    designHeight: Dp = DESIGN_HEIGHT_DP.dp,
    content: @Composable () -> Unit,
) {
    updateAccent()

    val gradientAnimation = _settings.gradient() && _settings.animate() && _settings.speed() > 0
    LaunchedEffect(_chroma, gradientAnimation) {
        _phase = ThemeAccent.phaseAt(System.nanoTime()).toFloat()
        while (_chroma || gradientAnimation) {
            withFrameNanos {
                // Compose prewarming advances its frame clock.
                if (gradientAnimation) _phase = ThemeAccent.phaseAt(System.nanoTime()).toFloat()
                if (_chroma && !_settings.gradient()) _accent = Color(ThemeConfig.accentColor.argb)
            }
        }
    }

    val target = ThemeRegistry.activeTheme ?: error("No active theme provided")
    val base = animateTheme(target)
    val animated = if (_settings.gradient()) base.copy(accentTextColor = Color(_settings.foreground()))
        .withControlTrackColor(base.controlTrackColor) else base

    SideEffect { syncNotificationTheme(animated) }

    CompositionLocalProvider(
        LocalTheme provides animated,
        LocalScrollbarStyle provides remember(animated.scrollBarShape) { scrollbarStyle(animated) },
        LocalDensity provides if (pixelGrid) pixelGridDensity(designWidth, designHeight) else LocalDensity.current,
        content = content
    )
}

internal fun syncNotificationTheme(theme: UITheme) {
    val minecraft = theme.previewImage.startsWith("minecraft")
    NotificationTheme.set(
        background = PolyColor(theme.popupBackground.toArgb()),
        border = PolyColor(theme.borderColor.toArgb()),
        textPrimary = PolyColor(theme.textColor.toArgb()),
        textSecondary = PolyColor(theme.textColorSecondary.toArgb()),
        accent = PolyColor(Accent.toArgb()),
        subtleButton = PolyColor(theme.componentBackground.toArgb()),
        actionPrimaryText = PolyColor(theme.accentTextColor.toArgb()),
        actionSubtleText = PolyColor(theme.textColor.toArgb()),
        fontTitle = if (minecraft) "minecraft-bold" else "poppins-medium",
        fontBody = if (minecraft) "minecraft" else null,
        radiusCard = if (minecraft) 0f else 12f,
        radiusButton = if (minecraft) 0f else 4f,
        radiusProgress = if (minecraft) 0f else 3f,
    )
}
