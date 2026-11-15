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

/**
 * PadLink 主题。
 *
 * 严格按 Material 3 的配色规范：种子色先展开成 primary / secondary / tertiary /
 * neutral / neutralVariant 五组色调板，再按官方槽位表取 tone。
 * 所有界面颜色都从 [MaterialTheme.colorScheme] 取，不在组件里写死色值。
 *
 * Android 12+ 且用户选择「动态」时，直接用壁纸生成的那套方案。
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
        else -> materialScheme(prefs.accentColor.seedColor(), dark)
    }

    // 主页的自绘手柄控件在 Canvas 里读不到 MaterialTheme，仍从这份调色板取色。
    CompositionLocalProvider(LocalPadPalette provides padPalette(scheme)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 种子色 → 完整 M3 配色。
 *
 * 彩度上限来自 M3 基线：primary 最艳，secondary 同色相但明显收敛，
 * tertiary 换到 +60° 色相，neutral / neutralVariant 只带一点点色相。
 */
internal fun materialScheme(seed: Color, dark: Boolean): ColorScheme {
    val (hue, seedChroma) = seed.hueChroma()

    val primary = TonalPalette(hue, seedChroma.coerceIn(36f, 64f))
    val secondary = TonalPalette(hue, (seedChroma * 0.45f).coerceIn(14f, 20f))
    val tertiary = TonalPalette(hue + 60f, (seedChroma * 0.60f).coerceIn(20f, 32f))
    val neutral = TonalPalette(hue, 4f)
    val neutralVariant = TonalPalette(hue, 8f)
    val error = TonalPalette(25f, 60f)

    return if (dark) {
        darkColorScheme(
            primary = primary.tone(80f),
            onPrimary = primary.tone(20f),
            primaryContainer = primary.tone(30f),
            onPrimaryContainer = primary.tone(90f),
            inversePrimary = primary.tone(40f),
            secondary = secondary.tone(80f),
            onSecondary = secondary.tone(20f),
            secondaryContainer = secondary.tone(30f),
            onSecondaryContainer = secondary.tone(90f),
            tertiary = tertiary.tone(80f),
            onTertiary = tertiary.tone(20f),
            tertiaryContainer = tertiary.tone(30f),
            onTertiaryContainer = tertiary.tone(90f),
            background = neutral.tone(6f),
            onBackground = neutral.tone(90f),
            surface = neutral.tone(6f),
            onSurface = neutral.tone(90f),
            surfaceVariant = neutralVariant.tone(30f),
            onSurfaceVariant = neutralVariant.tone(80f),
            surfaceTint = primary.tone(80f),
            inverseSurface = neutral.tone(90f),
            inverseOnSurface = neutral.tone(20f),
            error = error.tone(80f),
            onError = error.tone(20f),
            errorContainer = error.tone(30f),
            onErrorContainer = error.tone(90f),
            outline = neutralVariant.tone(60f),
            outlineVariant = neutralVariant.tone(30f),
            scrim = neutral.tone(0f),
            surfaceBright = neutral.tone(24f),
            surfaceDim = neutral.tone(6f),
            surfaceContainerLowest = neutral.tone(4f),
            surfaceContainerLow = neutral.tone(10f),
            surfaceContainer = neutral.tone(12f),
            surfaceContainerHigh = neutral.tone(17f),
            surfaceContainerHighest = neutral.tone(22f),
        )
    } else {
        lightColorScheme(
            primary = primary.tone(40f),
            onPrimary = primary.tone(100f),
            primaryContainer = primary.tone(90f),
            onPrimaryContainer = primary.tone(10f),
            inversePrimary = primary.tone(80f),
            secondary = secondary.tone(40f),
            onSecondary = secondary.tone(100f),
            secondaryContainer = secondary.tone(90f),
            onSecondaryContainer = secondary.tone(10f),
            tertiary = tertiary.tone(40f),
            onTertiary = tertiary.tone(100f),
            tertiaryContainer = tertiary.tone(90f),
            onTertiaryContainer = tertiary.tone(10f),
            background = neutral.tone(98f),
            onBackground = neutral.tone(10f),
            surface = neutral.tone(98f),
            onSurface = neutral.tone(10f),
            surfaceVariant = neutralVariant.tone(90f),
            onSurfaceVariant = neutralVariant.tone(30f),
            surfaceTint = primary.tone(40f),
            inverseSurface = neutral.tone(20f),
            inverseOnSurface = neutral.tone(95f),
            error = error.tone(40f),
            onError = error.tone(100f),
            errorContainer = error.tone(90f),
            onErrorContainer = error.tone(10f),
            outline = neutralVariant.tone(50f),
            outlineVariant = neutralVariant.tone(80f),
            scrim = neutral.tone(0f),
            surfaceBright = neutral.tone(98f),
            surfaceDim = neutral.tone(87f),
            surfaceContainerLowest = neutral.tone(100f),
            surfaceContainerLow = neutral.tone(96f),
            surfaceContainer = neutral.tone(94f),
            surfaceContainerHigh = neutral.tone(92f),
            surfaceContainerHighest = neutral.tone(90f),
        )
    }
}

/**
 * 自绘手柄控件的配色。
 *
 * Canvas 的 draw lambda 不是 @Composable，读不到 MaterialTheme，所以在组合阶段
 * 从 [ColorScheme] 派生一份。语义跟随 M3：静止是 surface 容器色 + onSurface，
 * 按下是 primary + onPrimary。
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
    dpadArm = scheme.onSurface.copy(alpha = 0.20f),
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

/** 预设主题色的种子值。动态取色不走这里。 */
private fun AccentColor.seedColor(): Color = when (this) {
    AccentColor.BLUE, AccentColor.DYNAMIC -> Color(0xFF0061A4)
    AccentColor.PURPLE -> Color(0xFF6750A4)
    AccentColor.GREEN -> Color(0xFF386A20)
    AccentColor.ORANGE -> Color(0xFF8B5000)
    AccentColor.PINK -> Color(0xFFA03E78)
}

/** 设置页主题色圆点用的实际 primary。动态取色没有静态值，由调用方另处理。 */
fun AccentColor.previewPrimary(dark: Boolean): Color =
    materialScheme(seedColor(), dark).primary
