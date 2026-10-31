package com.padlink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/** 弱加载区默认占控件高度的比例。 */
private const val DefaultGate = 0.4f

/** 再大滑块就该太短了。 */
private const val MaxGate = 0.65f

/**
 * 扳机·滑动条样式。
 *
 * 上半那个圆是**弱加载区**：手指按上去就算抓住了扳机，但输出恒为 0；手指往下挪进滑块
 * 才开始按位置换算。这样不用一开始就把手指精确按在 0 位上——手指有宽度，直接按滑块
 * 往往一按就已经是 10% 了，从头走满 0→100 根本做不到。
 *
 * 下半才是真正的滑块：填充跟着手指走，蓝色从顶部按比例盖下来。
 */
@Composable
fun TriggerSlider(
    modifier: Modifier = Modifier,
    label: String = "",
    gate: Float = DefaultGate,
    onChange: (Float) -> Unit = {},
) {
    var value by remember { mutableFloatStateOf(0f) }

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(gate) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    val gateFraction = gate.coerceIn(0f, MaxGate)
                    val gateHeight = size.height * gateFraction
                    val travel = (size.height - gateHeight).coerceAtLeast(1f)

                    fun track(position: Offset) {
                        // 弱加载区里：算按下，但值恒为 0；进了滑块才按位置换算。
                        value = if (position.y < gateHeight) {
                            0f
                        } else {
                            ((position.y - gateHeight) / travel).coerceIn(0f, 1f)
                        }
                        onChange(value)
                    }

                    track(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        track(change.position)
                        change.consume()
                    }

                    value = 0f
                    onChange(0f)
                }
            },
        ) {
            val gateHeight = size.height * gate.coerceIn(0f, MaxGate)
            val travel = size.height - gateHeight
            val outline = size.width * 0.09f

            // 弱加载区：一个圆，按上去有反应但不产生行程。
            val radius = min(size.width, gateHeight) / 2f
            val gateCenter = Offset(size.width / 2f, gateHeight / 2f)
            drawCircle(color = Color(0xFF232323), radius = radius, center = gateCenter)
            drawCircle(
                color = Color(0xFF4A4A4A),
                radius = radius,
                center = gateCenter,
                style = Stroke(width = outline),
            )

            // 滑块本体。
            val corner = CornerRadius(size.width * 0.28f)
            val barTop = gateHeight
            val barSize = Size(size.width, travel)

            drawRoundRect(
                color = Color(0xFF2C2C2C),
                topLeft = Offset(0f, barTop),
                size = barSize,
                cornerRadius = corner,
            )

            if (value > 0f) {
                drawRoundRect(
                    color = Color(0xFF4C8DFF),
                    topLeft = Offset(0f, barTop),
                    size = Size(size.width, travel * value),
                    cornerRadius = corner,
                )
            }

            drawRoundRect(
                color = Color(0xFF4A4A4A),
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
