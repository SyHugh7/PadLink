package com.padlink.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.layout.PadLayout
import com.padlink.app.ui.theme.LocalPadPalette

/**
 * 扳机·滑动条样式。
 *
 * 就是一根圆角长条，分两段：
 *
 * - **顶端那一段是弱加载区**（暗色）：按上去算抓住了扳机，但输出恒为 0
 * - **下面那段是行程**（主题色，跟着手指走）
 *
 * 为什么要顶端那一段：手指有宽度，想从 0 开始往下滑，按下去的位置往往已经在 10% 以上了，
 * 一按上就有值，从头走满 0→100 根本做不到。留一段不响应的，按在哪儿都还是 0。
 *
 * 长条的长和宽各自可调：宽度由元素自己的 `size` 管，长度是 `PadLayout.triggerAspect`。
 */
@Composable
fun TriggerSlider(
    modifier: Modifier = Modifier,
    label: String = "",
    gate: Float = PadLayout.DEFAULT_TRIGGER_GATE,
    valueOverride: Float? = null,
    onHaptic: (Long) -> Unit = {},
    onChange: (Float) -> Unit = {},
) {
    var value by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val shown = valueOverride ?: value
    val colors = LocalPadPalette.current

    // 按下去必须看得见：弱加载区里值恒为 0，光盯填充根本判断不出到底抓住没有。
    val gateFill by animateColorAsState(
        targetValue = if (pressed || shown > 0f) colors.triggerGatePressed else colors.triggerGate,
        animationSpec = tween(120),
        label = "gateFill",
    )
    val barStroke by animateColorAsState(
        targetValue = if (pressed || shown > 0f) colors.triggerOutlinePressed else colors.triggerOutline,
        animationSpec = tween(120),
        label = "barStroke",
    )

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(gate) {
                awaitPointerEventScope {
                    var activeId: PointerId? = null
                    var firstSample = true
                    var inStrong = false

                    fun track(position: Offset) {
                        val gateFraction = gate.coerceIn(PadLayout.MIN_TRIGGER_GATE, PadLayout.MAX_TRIGGER_GATE)
                        val gateHeight = (size.height * gateFraction).coerceIn(0f, size.height.toFloat())
                        val travel = (size.height - gateHeight).coerceAtLeast(1f)
                        val strong = position.y >= gateHeight

                        if (firstSample) {
                            onHaptic(if (strong) 30L else 20L)
                            inStrong = strong
                            firstSample = false
                        } else if (strong != inStrong) {
                            onHaptic(20L)
                            inStrong = strong
                        }

                        value = if (position.y < gateHeight) {
                            0f
                        } else {
                            ((position.y - gateHeight) / travel).coerceIn(0f, 1f)
                        }
                        onChange(value)
                    }

                    fun inBar(position: Offset) =
                        position.x in 0f..size.width.toFloat() &&
                            position.y in 0f..size.height.toFloat()

                    fun release() {
                        activeId = null
                        firstSample = true
                        inStrong = false
                        value = 0f
                        pressed = false
                        onChange(0f)
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 手指当前还在长条内才算抓住扳机；滑出去就归零。
                            val hit = event.changes.firstOrNull {
                                it.pressed && it.id == activeId && inBar(it.position)
                            } ?: event.changes.firstOrNull { it.pressed && inBar(it.position) }

                            if (hit != null) {
                                if (activeId != hit.id) {
                                    activeId = hit.id
                                    firstSample = true
                                    inStrong = false
                                    pressed = true
                                }
                                track(hit.position)
                                hit.consume()
                            } else {
                                // 滑出空白时保持最后的值；只有抬手才释放。
                                val stillDown = event.changes.any { it.id == activeId && it.pressed }
                                if (!stillDown) release()
                            }
                        }
                    } finally {
                        release()
                    }
                }
            },
        ) {
            val gateFraction = gate.coerceIn(PadLayout.MIN_TRIGGER_GATE, PadLayout.MAX_TRIGGER_GATE)
            val gateHeight = size.height * gateFraction
            val travel = size.height - gateHeight
            val corner = CornerRadius(size.width * 0.28f)
            val outline = size.width * 0.09f

            // 整条底色
            drawRoundRect(color = colors.triggerTrack, cornerRadius = corner)

            // 内容都裁到圆角里，免得顶端那段画出方角
            val shape = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner))
            }

            clipPath(shape) {
                if (gateHeight > 0f) {
                    drawRect(color = gateFill, size = Size(size.width, gateHeight))
                }

                if (shown > 0f) {
                    drawRect(
                        color = colors.triggerFill,
                        topLeft = Offset(0f, gateHeight),
                        size = Size(size.width, travel * shown),
                    )
                }
            }

            // 两段的分界
            if (gateHeight > 0f) {
                drawLine(
                    color = colors.triggerOutline,
                    start = Offset(0f, gateHeight),
                    end = Offset(size.width, gateHeight),
                    strokeWidth = outline * 0.6f,
                )
            }

            drawRoundRect(
                color = barStroke,
                cornerRadius = corner,
                style = Stroke(width = outline),
            )
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.triggerLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
