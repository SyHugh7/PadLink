package com.padlink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.padlink.app.ui.theme.LocalPadPalette
import kotlin.math.min

/**
 * 自绘摇杆。回调给的是**屏幕坐标**（y 向下为正）。
 *
 * 转成协议语义（y 向上为正）由 `PadInputState` 统一负责——全项目只在那一个地方取反。
 */
@Composable
fun StickPad(
    modifier: Modifier = Modifier,
    knobOverride: Offset? = null,
    onEngaged: (Boolean) -> Unit = {},
    onChange: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val colors = LocalPadPalette.current

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val maxTravel = radius * 0.62f

                    fun track(position: Offset) {
                        val delta = position - center
                        val distance = delta.getDistance()
                        val clamped = if (distance > maxTravel && distance > 0f) {
                            delta * (maxTravel / distance)
                        } else {
                            delta
                        }
                        knob = clamped / maxTravel
                        pressed = true
                        onChange(knob.x, knob.y)
                    }

                    fun inCircle(position: Offset) = (position - center).getDistance() <= radius

                    var activeId: PointerId? = null

                    fun release() {
                        activeId = null
                        knob = Offset.Zero
                        pressed = false
                        onEngaged(false)
                        onChange(0f, 0f)
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 手指当前还在圆盘内才算捏住；滑出去就松手。
                            val hit = event.changes.firstOrNull {
                                it.pressed && it.id == activeId && inCircle(it.position)
                            } ?: event.changes.firstOrNull { it.pressed && inCircle(it.position) }

                            if (hit != null) {
                                if (activeId != hit.id) {
                                    activeId = hit.id
                                    onEngaged(true)
                                }
                                track(hit.position)
                                hit.consume()
                            } else {
                                // 滑出控件也继续按位置算值；只有抬手才归零。
                                val activeChange = event.changes.firstOrNull { it.id == activeId }
                                if (activeChange != null && activeChange.pressed) {
                                    track(activeChange.position)
                                    activeChange.consume()
                                } else {
                                    release()
                                }
                            }
                        }
                    } finally {
                        release()
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val maxTravel = radius * 0.62f
        val knobRadius = radius * 0.40f
        val shown = knobOverride ?: knob

        // 底盘 → 内凹 → 摇杆头，三层就够，不加十字线（容易显得乱）。
        drawCircle(color = colors.stickBase, radius = radius, center = center)
        drawCircle(color = colors.stickWell, radius = radius * 0.88f, center = center)
        drawCircle(
            color = colors.hairline,
            radius = radius,
            center = center,
            style = Stroke(radius * 0.045f),
        )
        drawCircle(
            color = if (pressed || shown != Offset.Zero) colors.stickKnobPressed else colors.stickKnob,
            radius = knobRadius,
            center = center + shown * maxTravel,
        )
    }
}
