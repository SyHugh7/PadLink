package com.padlink.app.pref

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 主题模式。 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    val label: String
        get() = when (this) {
            LIGHT -> "亮"
            DARK -> "暗"
            SYSTEM -> "跟随系统"
        }
}

/** 主题色。DYNAMIC 是 Android 12+ 的动态取色（取壁纸颜色），老系统上退回 BLUE。 */
enum class AccentColor {
    DYNAMIC,
    BLUE,
    PURPLE,
    GREEN,
    ORANGE,
    PINK,
    ;

    val label: String
        get() = when (this) {
            DYNAMIC -> "动态"
            BLUE -> "蓝"
            PURPLE -> "紫"
            GREEN -> "绿"
            ORANGE -> "橙"
            PINK -> "粉"
        }
}

/**
 * 应用级设置：主题、震动反馈、退出前台冻结。
 *
 * 和布局（`PadLayout`）、重力手感（`TiltStore`）分开存：那两份会跟着布局导出/导入走，
 * 这份是本机偏好，不随存档迁移。
 *
 * 每个字段内部用 Compose 的 State 存 —— 设置在设置页里改，主界面要立刻跟着变。
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var themeModeState by mutableStateOf(loadThemeMode())
    private var accentColorState by mutableStateOf(loadAccentColor())
    private var hapticState by mutableStateOf(prefs.getBoolean(KEY_HAPTIC, true))
    private var freezeState by mutableStateOf(prefs.getBoolean(KEY_FREEZE, false))
    private var statusBarState by mutableStateOf(prefs.getBoolean(KEY_STATUS_BAR, false))
    private var statusBarAppliedState by mutableStateOf(prefs.getBoolean(KEY_STATUS_BAR_APPLIED, false))
    private var hapticScaleState by mutableStateOf(prefs.getFloat(KEY_HAPTIC_SCALE, 1f))

    var themeMode: ThemeMode
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var accentColor: AccentColor
        get() = accentColorState
        set(value) {
            accentColorState = value
            prefs.edit().putString(KEY_ACCENT, value.name).apply()
        }

    /** 按键按下时给一下触感。 */
    var hapticFeedback: Boolean
        get() = hapticState
        set(value) {
            hapticState = value
            prefs.edit().putBoolean(KEY_HAPTIC, value).apply()
        }

    /** 震动时长乘区，默认 1.0；所有控件的 20ms / 30ms 都乘它。 */
    var hapticScale: Float
        get() = hapticScaleState
        set(value) {
            hapticScaleState = value
            prefs.edit().putFloat(KEY_HAPTIC_SCALE, value).apply()
        }

    /** 退出前台时把输入归零并停发，回到前台再恢复。 */
    var freezeOnBackground: Boolean
        get() = freezeState
        set(value) {
            freezeState = value
            prefs.edit().putBoolean(KEY_FREEZE, value).apply()
        }

    /** 应用在前台时禁用状态栏。需要 Dhizuku 授权，真正的调用在 `DhizukuStatusBar`。 */
    var disableStatusBar: Boolean
        get() = statusBarState
        set(value) {
            statusBarState = value
            prefs.edit().putBoolean(KEY_STATUS_BAR, value).apply()
        }

    /**
     * 状态栏此刻是不是真的被禁用着。
     *
     * 不是用户设置，是给下次启动补恢复用的保险丝：进程被强杀时 onStop 不会跑，
     * 这个标记还会是 true，下次启动看到它就知道要先恢复。
     */
    var statusBarApplied: Boolean
        get() = statusBarAppliedState
        set(value) {
            statusBarAppliedState = value
            prefs.edit().putBoolean(KEY_STATUS_BAR_APPLIED, value).apply()
        }

    private fun loadThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM

    private fun loadAccentColor(): AccentColor =
        prefs.getString(KEY_ACCENT, null)
            ?.let { name -> AccentColor.entries.firstOrNull { it.name == name } }
            ?: AccentColor.DYNAMIC

    private companion object {
        const val PREFS_NAME = "padlink-app"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_ACCENT = "accentColor"
        const val KEY_HAPTIC = "hapticFeedback"
        const val KEY_HAPTIC_SCALE = "hapticScale"
        const val KEY_FREEZE = "freezeOnBackground"
        const val KEY_STATUS_BAR = "disableStatusBar"
        const val KEY_STATUS_BAR_APPLIED = "statusBarApplied"
    }
}
