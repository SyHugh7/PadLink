package com.padlink.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** 弱加载区相对**滑块高度**的默认比例。 */
private const val DefaultGate = 0.4f

/** 再大判定区就该喧宾夺主了。 */
private const val MaxGate = 0.65f

private const val Accent = 0xFF4C8DFF.toInt()

/**
 * 扳机·滑动条样式。
 *
 * 上方那个圆是**弱加载区**：手指按上去算抓住了扳机，但输出恒为 0；手指往下挪进滑块
 * 才开始按位置换算。这样不用一开始就把手指精确按在 0 位上——手指有宽度，直接按滑块
 * 往往一按就已经是 10% 了，从头走满 0→100 根本做不到。
 *
 * 它是**额外加在滑块上方**的一块：滑块那一截的高度和行程跟原来一模一样，
 * 多出来的高度由 [PadLayout.aspectOf] 算进去。
 *
 * 下方是原来的滑块：填充跟着手指走，蓝色从顶部按比例盖下来。
 */
@Composable
fun TriggerSlider(
    modifier: Modifier = Modifier,
    label: String = "",
    gate: Float = DefaultGate,
    onChange: (Float) -> Unit = {},
) {
    var value by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }

    // 按下去必须看得见：判定区里值恒为 0，光盯填充根本判断不出到底抓住没有。
    val gateFill by animateColorAsState(
        targetValue = if (pressed) Color(0xFF313D52) else Color(0xFF232323),
        animationSpec = tween(120),
        label = "gateFill",
    )
    val gateStroke by animateColorAsState(
        targetValue = if (pressed) Color(Accent) else Color(0xFF4A4A4A),
        animationSpec = tween(120),
        label = "gateStroke",
    )
    val barStroke by animateColorAsState(
        targetValue = if (pressed) Color(0xFF707070) else Color(0xFF4A4A4A),
        animationSpec = tween(120),
        label = "barStroke",
    )

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(gate) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true

                    val gateFraction = gate.coerceIn(0f, MaxGate)
                    // 控件总高 = 滑块（原高）+ 判定区。先把滑块那截还原出来。
                    val sliderHeight = (size.height / (1f + gateFraction)).coerceAtLeast(1f)
                    val gateHeight = size.height - sliderHeight
                    // 圆比判定区高的时候会压到滑块上，所以判定按“圆内”算，而不是按 y 分界。
                    val radius = min(size.width.toFloat(), gateHeight)

                    fun track(position: Offset) {
                        // 弱加载区里：算按下，但值恒为 0；进了滑块才按位置换算。
                        val dx = position.x - size.width / 2f
                        val dy = position.y - gateHeight / 2f
                        value = if (dx * dx + dy * dy <= radius * radius) {
                            0f
                        } else {
                            ((position.y - gateHeight) / sliderHeight).coerceIn(0f, 1f)
                        }
                        onChange(value)
                    }

                    try {
                        track(down.position)
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            track(change.position)
                            change.consume()
                        }
                    } finally {
                        value = 0f
                        pressed = false
                        onChange(0f)
                    }
                }
            },
        ) {
            val gateFraction = gate.coerceIn(0f, MaxGate)
            val sliderHeight = size.height / (1f + gateFraction)
            val gateHeight = size.height - sliderHeight
            val outline = size.width * 0.09f

            // 弱加载区：一个圆，按上去有反馈但不产生行程。
            val radius = min(size.width, gateHeight)
            val gateCenter = Offset(size.width / 2f, gateHeight / 2f)
            drawCircle(color = gateFill, radius = radius, center = gateCenter)
            drawCircle(
                color = gateStroke,
                radius = radius,
                center = gateCenter,
                style = Stroke(width = outline),
            )

            // 滑块本体：高度是原来的，没被判定区吃掉。
            val corner = CornerRadius(size.width * 0.28f)
            val barTop = gateHeight
            val barSize = Size(size.width, sliderHeight)

            drawRoundRect(
                color = Color(0xFF2C2C2C),
                topLeft = Offset(0f, barTop),
                size = barSize,
                cornerRadius = corner,
            )

            if (value > 0f) {
                drawRoundRect(
                    color = Color(Accent),
                    topLeft = Offset(0f, barTop),
                    size = Size(size.width, sliderHeight * value),
                    cornerRadius = corner,
                )
            }

            drawRoundRect(
                color = barStroke,
                topLeft = Offset(0f, barTop),
                size = barSize,
                cornerRadius = corner,
                style = Stroke(width = outline),
            )
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color(0xFFEDEDED),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
