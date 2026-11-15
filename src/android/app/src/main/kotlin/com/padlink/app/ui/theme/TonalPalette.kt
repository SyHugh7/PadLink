package com.padlink.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Material 3 的色调板（tonal palette）。
 *
 * M3 的配色不是“挑几个好看的颜色”，而是一组按感知亮度（L*）排好的色阶：
 * 同一个色相 + 彩度，从 tone 0（黑）到 tone 100（白）。组件只从对应色阶取固定 tone，
 * 因此深浅主题之间的层次、对比度是规范保证的，而不是手工调出来的。
 *
 * 这里用 CIELCh(ab) 近似 M3 的 HCT：hue / chroma 取自种子色，tone 直接就是 L*。
 * 超出 sRGB 色域的色阶逐级降彩度，避免硬裁切把色相带偏。
 */
internal class TonalPalette(
    private val hue: Float,
    private val chroma: Float,
) {
    /** [tone] 是 0（黑）到 100（白）的目标亮度。 */
    fun tone(tone: Float): Color {
        val lightness = tone.coerceIn(0f, 100f)
        var currentChroma = max(0f, chroma)

        while (true) {
            labToColor(lightness, currentChroma, hue)?.let { return it }
            if (currentChroma < 0.5f) {
                // 灰度总能落在色域内；真到了这一步说明参数本身异常，退回黑。
                return labToColor(lightness, 0f, hue) ?: Color.Black
            }
            currentChroma *= 0.92f
        }
    }
}

/** 种子色的色相与彩度（CIELCh）。 */
internal fun Color.hueChroma(): Pair<Float, Float> {
    val (x, y, z) = toXyz()
    val fx = pivotRgb(x / WHITE_X)
    val fy = pivotRgb(y / WHITE_Y)
    val fz = pivotRgb(z / WHITE_Z)

    val a = 500f * (fx - fy)
    val b = 200f * (fy - fz)
    val hue = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat()
    return ((hue + 360f) % 360f) to hypot(a, b)
}

private fun Color.toXyz(): Triple<Float, Float, Float> {
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    val r = linear(red)
    val g = linear(green)
    val b = linear(blue)

    return Triple(
        0.4124f * r + 0.3576f * g + 0.1805f * b,
        0.2126f * r + 0.7152f * g + 0.0722f * b,
        0.0193f * r + 0.1192f * g + 0.9505f * b,
    )
}

private fun pivotRgb(value: Float): Float =
    if (value > 0.008856f) value.toDouble().pow(1.0 / 3.0).toFloat() else 7.787f * value + 16f / 116f

private fun pivotInverse(value: Float): Float {
    val cube = value * value * value
    return if (cube > 0.008856f) cube else (value - 16f / 116f) / 7.787f
}

/** CIELCh → sRGB。返回 null 表示落在 sRGB 色域之外，调用方应降低彩度重试。 */
private fun labToColor(lightness: Float, chroma: Float, hueDegrees: Float): Color? {
    val radians = Math.toRadians(hueDegrees.toDouble())
    val a = chroma * cos(radians).toFloat()
    val b = chroma * sin(radians).toFloat()

    val fy = (lightness + 16f) / 116f
    val fx = fy + a / 500f
    val fz = fy - b / 200f

    val x = WHITE_X * pivotInverse(fx)
    val y = WHITE_Y * pivotInverse(fy)
    val z = WHITE_Z * pivotInverse(fz)

    val r = 3.2406f * x - 1.5372f * y - 0.4986f * z
    val g = -0.9689f * x + 1.8758f * y + 0.0415f * z
    val blue = 0.0557f * x - 0.2040f * y + 1.0570f * z

    // 允许一点点浮点误差，真正的越界才会被拒绝。
    if (r < -0.0005f || r > 1.0005f) return null
    if (g < -0.0005f || g > 1.0005f) return null
    if (blue < -0.0005f || blue > 1.0005f) return null

    fun encode(channel: Float): Float {
        val clamped = channel.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) clamped * 12.92f
        else 1.055f * clamped.pow(1f / 2.4f) - 0.055f
    }

    return Color(encode(r), encode(g), encode(blue))
}

// D65 白点。
private const val WHITE_X = 0.95047f
private const val WHITE_Y = 1.00000f
private const val WHITE_Z = 1.08883f
