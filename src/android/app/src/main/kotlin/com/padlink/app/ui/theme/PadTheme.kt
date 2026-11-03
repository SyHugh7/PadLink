package com.padlink.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.padlink.app.pref.AccentColor
import com.padlink.app.pref.AppPrefs
import com.padlink.app.pref.ThemeMode

/**
 * 应用主题。
 *
 * 深色是主调（手柄界面多在暗处用），亮色留给白天窗边。
 * 主题色走 Material3 的 primary，动态取色只在 Android 12+ 有用，老系统退回预设蓝。
 */
@Composable
fun PadLinkTheme(prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val scheme = when {
        prefs.accentColor == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme(primary = prefs.accentColor.seedColor(), onPrimary = Color(0xFF101927))
        else -> lightColorScheme(primary = prefs.accentColor.seedColor(), onPrimary = Color.White)
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

/** 预设主题色的种子值。动态取色不走这里。 */
private fun AccentColor.seedColor(): Color = when (this) {
    AccentColor.BLUE, AccentColor.DYNAMIC -> Color(0xFF4C8DFF)
    AccentColor.PURPLE -> Color(0xFFB388FF)
    AccentColor.GREEN -> Color(0xFF81C784)
    AccentColor.ORANGE -> Color(0xFFFFB74D)
    AccentColor.PINK -> Color(0xFFF06292)
}
