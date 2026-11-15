package com.padlink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.padlink.app.ui.theme.LocalPadPalette

/**
 * 圆形按键。按住期间保持按下，即使手指滑出按钮范围——跟真实手柄手感一致。
 *
 * 配色来自 [LocalPadPalette]：未按下是中性面，按下是主题色。
 */
@Composable
fun PadButton(
    label: String,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 16.sp,
    pressedOverride: Boolean? = null,
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val isPressed = pressedOverride ?: pressed
    val colors = LocalPadPalette.current

    Box(
        modifier = modifier
            .aspectRatio(1f)
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
                            // 只看当前手指是否还在圆形区域内，滑出就释放。
                            val hit = event.changes.firstOrNull { change ->
                                change.pressed &&
                                    (change.position - Offset(size.width / 2f, size.height / 2f))
                                        .getDistance() <= minOf(size.width, size.height) / 2f
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
                val radius = minOf(size.width, size.height) / 2f
                drawCircle(color = if (isPressed) colors.facePressed else colors.face, radius = radius)
                drawCircle(color = colors.hairline, radius = radius, style = Stroke(radius * 0.06f))
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
