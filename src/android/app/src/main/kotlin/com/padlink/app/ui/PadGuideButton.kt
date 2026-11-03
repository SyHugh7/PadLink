package com.padlink.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.padlink.app.R
import com.padlink.app.ui.theme.LocalPadPalette

/**
 * Guide 键：Xbox 中间那颗西瓜键。
 *
 * 图案来自官网的单色 logo（`ic_xbox`），用 tint 跟着主题走 —— 直接烧成位图就只能在
 * 一种底色上用。按住时圆面变成主题色，logo 用反色。
 */
@Composable
fun PadGuideButton(
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = LocalPadPalette.current
    val logo: Painter = painterResource(R.drawable.ic_xbox)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    pressed = true
                    onPressChange(true)
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }

                    pressed = false
                    onPressChange(false)
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(color = if (pressed) colors.facePressed else colors.face, radius = radius)
                drawCircle(color = colors.hairline, radius = radius, style = Stroke(radius * 0.06f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = logo,
            contentDescription = "Guide",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(if (pressed) colors.labelPressed else colors.label),
            modifier = Modifier.fillMaxSize(0.50f),
        )
    }
}
