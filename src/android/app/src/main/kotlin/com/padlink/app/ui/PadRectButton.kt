package com.padlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.padlink.app.ui.theme.LocalPadPalette

/**
 * 圆角矩形按钮。肩键（LB/RB）和按钮式扳机（LT/RT）都用它——
 * 它们本来就是同一种东西，区别只在语义。
 */
@Composable
fun PadRectButton(
    label: String,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 15.sp,
    pressedOverride: Boolean? = null,
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val isPressed = pressedOverride ?: pressed
    val colors = LocalPadPalette.current

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var pressedBy: PointerId? = null

                    fun release() {
                        if (pressedBy != null) {
                            pressedBy = null
                            pressed = false
                            onPressChange(false)
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val hit = event.changes.firstOrNull { change ->
                                change.pressed &&
                                    change.position.x in 0f..size.width.toFloat() &&
                                    change.position.y in 0f..size.height.toFloat()
                            }

                            if (hit != null) {
                                if (pressedBy == null) {
                                    pressed = true
                                    onPressChange(true)
                                }
                                pressedBy = hit.id
                                hit.consume()
                            } else {
                                release()
                            }
                        }
                    } finally {
                        release()
                    }
                }
            }
            .drawBehind {
                val corner = CornerRadius(size.height * 0.26f)
                drawRoundRect(
                    color = if (isPressed) colors.facePressed else colors.face,
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = colors.hairline,
                    cornerRadius = corner,
                    style = Stroke(width = size.height * 0.05f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isPressed) colors.labelPressed else colors.label,
            fontSize = labelSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}
