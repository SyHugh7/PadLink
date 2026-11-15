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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.padlink.app.ui.theme.LocalPadPalette
import com.padlink.core.DPad
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * 十字键·8 方向样式。转成 8 方向枚举（protocol.md §5.3），
 * 比四个独立布尔更贴合真实十字键，也不会出现"同时按下上和下"这种组合。
 *
 * 按下反馈是一片从圆心扇出去的 45° 扇形，加中心点变色——之前那圈小弧太不显眼了。
 */
@Composable
fun DPadPad(
    modifier: Modifier = Modifier,
    activeOverride: DPad? = null,
    onHaptic: (Long) -> Unit = {},
    onDirection: (DPad) -> Unit = {},
) {
    var active by remember { mutableStateOf(DPad.NEUTRAL) }
    val colors = LocalPadPalette.current

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val deadZone = radius * 0.32f
                    var activeId: PointerId? = null
                    var lastDirection = DPad.NEUTRAL

                    fun track(position: Offset) {
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val direction = if (hypot(dx, dy) < deadZone) {
                            DPad.NEUTRAL
                        } else {
                            // 屏幕 y 向下为正，取负换成"上为正"再算角度。
                            directionFromDegrees(Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())))
                        }
                        if (direction != lastDirection) {
                            if (direction != DPad.NEUTRAL) onHaptic(20L)
                            lastDirection = direction
                        }
                        active = direction
                        onDirection(direction)
                    }

                    fun inCircle(position: Offset) =
                        hypot(position.x - center.x, position.y - center.y) <= radius

                    fun release() {
                        activeId = null
                        lastDirection = DPad.NEUTRAL
                        active = DPad.NEUTRAL
                        onDirection(DPad.NEUTRAL)
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val hit = event.changes.firstOrNull {
                                it.pressed && it.id == activeId && inCircle(it.position)
                            } ?: event.changes.firstOrNull { it.pressed && inCircle(it.position) }

                            if (hit != null) {
                                if (activeId != hit.id) {
                                    activeId = hit.id
                                    lastDirection = DPad.NEUTRAL
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
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val shown = activeOverride ?: active

        drawCircle(color = colors.stickBase, radius = radius, center = center)

        // 按下方向的扇形，垫在内圈下面，看起来像外环亮了一格。
        if (shown != DPad.NEUTRAL) {
            drawArc(
                color = colors.dpadArc,
                startAngle = degreesToStartAngle(shown) - 22.5f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
            )
        }

        drawCircle(color = colors.dpadWell, radius = radius * 0.62f, center = center)

        // 四个方向的臂，提示这是个十字键。
        val arm = radius * 0.5f
        val thickness = radius * 0.16f
        val armColor = colors.dpadArm
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y - arm), Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y + thickness / 2f), Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - arm, center.y - thickness / 2f), Size(arm - thickness, thickness))
        drawRect(armColor, Offset(center.x + thickness / 2f, center.y - thickness / 2f), Size(arm - thickness, thickness))

        drawCircle(
            color = if (shown != DPad.NEUTRAL) colors.dpadCenterPressed else colors.dpadCenter,
            radius = radius * 0.15f,
            center = center,
        )
        drawCircle(color = colors.hairline, radius = radius, center = center, style = Stroke(radius * 0.05f))
    }
}

/** 角度（0=东，逆时针为正）→ 8 方向。 */
internal fun directionFromDegrees(degrees: Double): DPad {
    val normalized = ((degrees + 22.5) % 360.0 + 360.0) % 360.0
    return when ((normalized / 45.0).toInt() % 8) {
        0 -> DPad.EAST
        1 -> DPad.NORTH_EAST
        2 -> DPad.NORTH
        3 -> DPad.NORTH_WEST
        4 -> DPad.WEST
        5 -> DPad.SOUTH_WEST
        6 -> DPad.SOUTH
        else -> DPad.SOUTH_EAST
    }
}

/** 方向 → drawArc 的起始角（Compose 的 0° 是三点钟方向，顺时针为正）。 */
private fun degreesToStartAngle(direction: DPad): Float = when (direction) {
    DPad.NORTH -> -90f
    DPad.NORTH_EAST -> -45f
    DPad.EAST -> 0f
    DPad.SOUTH_EAST -> 45f
    DPad.SOUTH -> 90f
    DPad.SOUTH_WEST -> 135f
    DPad.WEST -> 180f
    DPad.NORTH_WEST -> -135f
    DPad.NEUTRAL -> 0f
}
