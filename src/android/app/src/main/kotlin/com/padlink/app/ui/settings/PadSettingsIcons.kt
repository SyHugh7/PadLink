package com.padlink.app.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 设置页用的两个图标。
 *
 * material-icons-core 里没有调色盘和震动，按 Material Icons 的同名路径自绘，
 * 这样不用引入体积很大的 material-icons-extended。
 */

/** 调色盘：主题设置。圆孔用逆时针子路径挖空。 */
val PaletteIcon: ImageVector = ImageVector.Builder(
    name = "Palette",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
        curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
        curveTo(12.83f, 21f, 13.5f, 20.33f, 13.5f, 19.5f)
        curveTo(13.5f, 19.11f, 13.35f, 18.76f, 13.11f, 18.49f)
        curveTo(12.88f, 18.23f, 12.73f, 17.88f, 12.73f, 17.5f)
        curveTo(12.73f, 16.67f, 13.4f, 16f, 14.23f, 16f)
        lineTo(16f, 16f)
        curveTo(18.76f, 16f, 21f, 13.76f, 21f, 11f)
        curveTo(21f, 6.58f, 16.97f, 3f, 12f, 3f)
        close()

        // 四个颜料孔
        circle(6.5f, 10.5f, 1.5f)
        circle(9.5f, 6.5f, 1.5f)
        circle(14.5f, 6.5f, 1.5f)
        circle(17.5f, 10.5f, 1.5f)
    }
}.build()

/** 震动：手机机身 + 两侧波纹。 */
val VibrationIcon: ImageVector = ImageVector.Builder(
    name = "Vibration",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // 左右两侧的短波
        moveTo(0f, 15f)
        lineTo(2f, 15f)
        lineTo(2f, 9f)
        lineTo(0f, 9f)
        close()

        moveTo(3f, 17f)
        lineTo(5f, 17f)
        lineTo(5f, 7f)
        lineTo(3f, 7f)
        close()

        moveTo(22f, 9f)
        lineTo(22f, 15f)
        lineTo(24f, 15f)
        lineTo(24f, 9f)
        close()

        moveTo(19f, 17f)
        lineTo(21f, 17f)
        lineTo(21f, 7f)
        lineTo(19f, 7f)
        close()

        // 机身外轮廓
        moveTo(16.5f, 3f)
        lineTo(7.5f, 3f)
        curveTo(6.67f, 3f, 6f, 3.67f, 6f, 4.5f)
        lineTo(6f, 19.5f)
        curveTo(6f, 20.33f, 6.67f, 21f, 7.5f, 21f)
        lineTo(16.5f, 21f)
        curveTo(17.33f, 21f, 18f, 20.33f, 18f, 19.5f)
        lineTo(18f, 4.5f)
        curveTo(18f, 3.67f, 17.33f, 3f, 16.5f, 3f)
        close()

        // 内屏（反向外轮廓，NonZero 下挖空）
        moveTo(16f, 19f)
        lineTo(8f, 19f)
        lineTo(8f, 5f)
        lineTo(16f, 5f)
        close()
    }
}.build()

/** 在 vector path 里加一个逆时针圆（配合 NonZero 填充形成孔洞）。 */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(
        horizontalEllipseRadius = r,
        verticalEllipseRadius = r,
        theta = 0f,
        isMoreThanHalf = true,
        isPositiveArc = true,
        x1 = cx - r,
        y1 = cy,
    )
    arcTo(
        horizontalEllipseRadius = r,
        verticalEllipseRadius = r,
        theta = 0f,
        isMoreThanHalf = true,
        isPositiveArc = true,
        x1 = cx + r,
        y1 = cy,
    )
    close()
}
