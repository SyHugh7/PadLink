package com.padlink.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.padlink.app.pref.AccentColor
import com.padlink.app.pref.AppPrefs
import com.padlink.app.pref.ThemeMode
import kotlin.math.abs

/**
 * 应用主题。
 *
 * 深色是主调（手柄界面多在暗处用），亮色留给白天窗边。
 *
 * 主题色不是只换一个 `primary` —— 那样开关、chip、选中态仍会留着 Material 默认紫。
 * 这里从种子色派生整套 accent 槽位（primary / secondary / tertiary 及各自 container），
 * 所有 M3 组件和自绘控件都从同一份 [ColorScheme] 取色；Android 12+ 的动态取色直接用
 * 壁纸生成的整套方案，因此和预设色走的是同一条链路。
 */
@Composable
fun PadLinkTheme(prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val dynamic = prefs.accentColor == AccentColor.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> accentScheme(prefs.accentColor.seedColor(), dark)
    }

    CompositionLocalProvider(LocalPadPalette provides padPalette(scheme)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 由种子色派生完整的 Material3 配色。
 *
 * 只替换带“颜色”的槽位；surface / background 仍用 M3 基线的中性色 —— 手柄界面要的是
 * 安静的底，不是一张彩色卡片。
 */
private fun accentScheme(seed: Color, dark: Boolean): ColorScheme {
    val (hue, saturation, _) = seed.toHsl()
    val s = saturation.coerceIn(0.12f, 0.9f)

    // 深色下主题色要够亮才能在暗底上看见，亮色下则相反。
    val primary = hsl(hue, s * 0.85f, if (dark) 0.70f else 0.42f)
    val onPrimary = hsl(hue, s * 0.35f, if (dark) 0.14f else 0.99f)

    val primaryContainer = hsl(hue, s * 0.45f, if (dark) 0.28f else 0.88f)
    val onPrimaryContainer = hsl(hue, s * 0.50f, if (dark) 0.92f else 0.18f)

    // secondary 保持同色相、明显更低饱和；tertiary 换个相邻色相，避免界面只有一个颜色。
    val secondary = hsl(hue, s * 0.28f, if (dark) 0.74f else 0.40f)
    val onSecondary = hsl(hue, s * 0.20f, if (dark) 0.16f else 0.99f)
    val secondaryContainer = hsl(hue, s * 0.20f, if (dark) 0.30f else 0.90f)
    val onSecondaryContainer = hsl(hue, s * 0.28f, if (dark) 0.92f else 0.18f)

    val tertiaryHue = hue + 55f
    val tertiary = hsl(tertiaryHue, s * 0.32f, if (dark) 0.76f else 0.42f)
    val onTertiary = hsl(tertiaryHue, s * 0.22f, if (dark) 0.16f else 0.99f)
    val tertiaryContainer = hsl(tertiaryHue, s * 0.24f, if (dark) 0.30f else 0.90f)
    val onTertiaryContainer = hsl(tertiaryHue, s * 0.30f, if (dark) 0.92f else 0.18f)

    val base = if (dark) darkColorScheme() else lightColorScheme()

    // 背景也跟着主题色走：只留一点点色相（6%–10% 饱和），
    // 否则「蓝/紫/绿/橙/粉」五个预设会共用同一套灰色底，看起来像没生效。
    fun surface(lightness: Float, saturation: Float = if (dark) 0.065f else 0.080f) =
        hsl(hue, saturation, lightness)

    val onSurface = if (dark) hsl(hue, 0.030f, 0.930f) else hsl(hue, 0.100f, 0.120f)
    val onSurfaceVariant = if (dark) hsl(hue, 0.040f, 0.780f) else hsl(hue, 0.060f, 0.340f)
    val outline = if (dark) hsl(hue, 0.050f, 0.580f) else hsl(hue, 0.060f, 0.500f)
    val outlineVariant = if (dark) hsl(hue, 0.050f, 0.320f) else hsl(hue, 0.100f, 0.780f)
    val inverseSurface = if (dark) hsl(hue, 0.030f, 0.920f) else hsl(hue, 0.080f, 0.200f)
    val inverseOnSurface = if (dark) hsl(hue, 0.040f, 0.120f) else hsl(hue, 0.050f, 0.940f)

    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = hsl(hue, s * 0.85f, if (dark) 0.42f else 0.72f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surfaceTint = primary,
        background = if (dark) surface(0.055f) else surface(0.985f),
        onBackground = onSurface,
        surface = if (dark) surface(0.060f) else surface(0.980f),
        onSurface = onSurface,
        surfaceVariant = if (dark) surface(0.200f) else surface(0.900f),
        onSurfaceVariant = onSurfaceVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = if (dark) surface(0.260f, 0.055f) else surface(0.995f),
        surfaceDim = if (dark) surface(0.100f) else surface(0.870f, 0.070f),
        surfaceContainerLowest = if (dark) surface(0.035f) else surface(1.0f),
        surfaceContainerLow = if (dark) surface(0.075f) else surface(0.965f),
        surfaceContainer = if (dark) surface(0.100f) else surface(0.945f),
        surfaceContainerHigh = if (dark) surface(0.130f, 0.060f) else surface(0.925f),
        surfaceContainerHighest = if (dark) surface(0.170f, 0.055f) else surface(0.900f),
    )
}

/**
 * 自绘手柄控件的配色。全部从 [ColorScheme] 派生，深浅色主题自动跟着走。
 *
 * 单独抽出来是因为 Canvas 的 draw lambda 不是 @Composable，不能在里面读 MaterialTheme；
 * 在组合阶段取一次，再带进绘制阶段。
 */
@Immutable
data class PadPalette(
    /** 控件未按下时的底色。 */
    val face: Color,
    val facePressed: Color,
    val label: Color,
    val labelPressed: Color,
    val hairline: Color,
    val stickBase: Color,
    val stickWell: Color,
    val stickKnob: Color,
    val stickKnobPressed: Color,
    val dpadArc: Color,
    val dpadWell: Color,
    val dpadArm: Color,
    val dpadCenter: Color,
    val dpadCenterPressed: Color,
    val triggerTrack: Color,
    val triggerGate: Color,
    val triggerGatePressed: Color,
    val triggerFill: Color,
    val triggerOutline: Color,
    val triggerOutlinePressed: Color,
    val triggerLabel: Color,
    val selectionBorder: Color,
    val selectionFill: Color,
    val selectionText: Color,
    val disabledBorder: Color,
    val disabledFill: Color,
    val disabledText: Color,
    val editText: Color,
    val hint: Color,
    val accent: Color,
    val onAccent: Color,
)

val LocalPadPalette = staticCompositionLocalOf { padPalette(darkColorScheme()) }

private fun padPalette(scheme: ColorScheme): PadPalette = PadPalette(
    face = scheme.surfaceContainerHighest,
    facePressed = scheme.primary,
    label = scheme.onSurface,
    labelPressed = scheme.onPrimary,
    hairline = scheme.outline.copy(alpha = 0.35f),
    stickBase = scheme.surfaceContainerHighest,
    stickWell = scheme.surfaceContainerLow,
    stickKnob = scheme.onSurfaceVariant,
    stickKnobPressed = scheme.primary,
    dpadArc = scheme.primary.copy(alpha = 0.60f),
    dpadWell = scheme.surfaceContainerLow,
    dpadArm = scheme.onSurface.copy(alpha = 0.16f),
    dpadCenter = scheme.onSurfaceVariant.copy(alpha = 0.55f),
    dpadCenterPressed = scheme.primary,
    triggerTrack = scheme.surfaceContainerHighest,
    triggerGate = scheme.surfaceContainerLow,
    triggerGatePressed = scheme.primaryContainer,
    triggerFill = scheme.primary,
    triggerOutline = scheme.outlineVariant,
    triggerOutlinePressed = scheme.outline,
    triggerLabel = scheme.onSurface,
    selectionBorder = scheme.primary,
    selectionFill = scheme.primary.copy(alpha = 0.22f),
    selectionText = scheme.primary,
    disabledBorder = scheme.outline.copy(alpha = 0.55f),
    disabledFill = scheme.surfaceContainerLow.copy(alpha = 0.55f),
    disabledText = scheme.onSurfaceVariant.copy(alpha = 0.70f),
    editText = scheme.onSurfaceVariant,
    hint = scheme.onSurfaceVariant,
    accent = scheme.primary,
    onAccent = scheme.onPrimary,
)

/**
 * 预设主题色的种子值。都压过饱和度 —— 原来的高饱和色在暗底上太刺眼，也容易和手柄
 * 按下的高亮抢注意力。动态取色不走这里。
 */
private fun AccentColor.seedColor(): Color = when (this) {
    AccentColor.BLUE, AccentColor.DYNAMIC -> Color(0xFF6E9BD6)
    AccentColor.PURPLE -> Color(0xFFA996C9)
    AccentColor.GREEN -> Color(0xFF8FB79B)
    AccentColor.ORANGE -> Color(0xFFCBAA80)
    AccentColor.PINK -> Color(0xFFC79AA9)
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return Triple(0f, 0f, l)

    val s = d / (1f - abs(2f * l - 1f))
    val h = when (max) {
        r -> 60f * (((g - b) / d + 6f) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Triple(h, s, l)
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r, g, b) = when ((h / 60f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
