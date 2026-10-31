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
import com.padlink.app.layout.PadLayout

/**
 * 滑块宽度占控件宽度的比例。
 *
 * 特意留出余量：弱加载区的圆最宽可以到控件宽度，滑块只有 62%，圆才比滑块明显大一圈。
 * 如果滑块也占满宽度，圆最多只能和它一样宽，那就白做了。
 */
private const val SliderWidthRatio = 0.62f

private const val Accent = 0xFF4C8DFF.toInt()

/**
 * 扳机·滑动条样式。
 *
 * 上方那个圆是**弱加载区**：手指按上去算抓住了扳机，但输出恒为 0；手指往下挪进滑块
 * 才开始按位置换算。这样不用一开始就把手指精确按在 0 位上——手指有宽度，直接按滑块
 * 往往一按就已经是 10% 了，从头走满 0→100 根本做不到。
 *
 * 尺寸关系（控件宽 W、高 H）：
 * - 圆直径 = W × gate，取值 0.62 ~ 1.0，所以圆的直径和判定区高度**始终相等**，圆底正好贴着滑块顶
 * - 滑块高 = H − 圆直径，也就是原来的 2.4W，一点没被占
 * - 滑块宽 = W × 0.62，居中
 *
 * 圆的直径封顶在控件宽度：Canvas 画得出界，但触摸命不中界外，画出界就成了"看得见按不到"。
 */
@Composable
fun TriggerSlider(
    modifier: Modifier = Modifier,
    label: String = "",
    gate: Float = PadLayout.DEFAULT_TRIGGER_GATE,
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

                    val gateFraction = gate.coerceIn(PadLayout.MIN_TRIGGER_GATE, PadLayout.MAX_TRIGGER_GATE)
                    val gateHeight = (size.width * gateFraction).coerceAtMost(size.width.toFloat())
                    val sliderHeight = (size.height - gateHeight).coerceAtLeast(1f)
                    val radius = (size.width * gateFraction / 2f).coerceAtMost(size.width / 2f)

                    fun track(position: Offset) {
                        // 圆内算弱加载区：抓住扳机但值为 0。圆外才按位置换算。
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
            val gateFraction = gate.coerceIn(PadLayout.MIN_TRIGGER_GATE, PadLayout.MAX_TRIGGER_GATE)
            val gateDiameter = size.width * gateFraction
            val gateHeight = gateDiameter          // 判定区高度 = 圆直径，圆底正好贴滑块顶
            val sliderHeight = size.height - gateHeight
            val sliderWidth = size.width * SliderWidthRatio
            val barLeft = (size.width - sliderWidth) / 2f
            val outline = size.width * 0.09f

            // 弱加载区：一个圆，按上去有反馈但不产生行程。
            val radius = gateDiameter / 2f
            val gateCenter = Offset(size.width / 2f, gateHeight / 2f)
            drawCircle(color = gateFill, radius = radius, center = gateCenter)
            drawCircle(
                color = gateStroke,
                radius = radius,
                center = gateCenter,
                style = Stroke(width = outline),
            )

            // 滑块本体：比控件窄，高度是原来的 2.4 个宽，没被判定区吃掉。
            val corner = CornerRadius(sliderWidth * 0.28f)
            val barTop = gateHeight
            val barSize = Size(sliderWidth, sliderHeight)

            drawRoundRect(
                color = Color(0xFF2C2C2C),
                topLeft = Offset(barLeft, barTop),
                size = barSize,
                cornerRadius = corner,
            )

            if (value > 0f) {
                drawRoundRect(
                    color = Color(Accent),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(sliderWidth, sliderHeight * value),
                    cornerRadius = corner,
                )
            }

            drawRoundRect(
                color = barStroke,
                topLeft = Offset(barLeft, barTop),
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
