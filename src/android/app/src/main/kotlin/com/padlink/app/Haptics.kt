package com.padlink.app

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 按键反馈震动。
 *
 * 直接驱动马达，不走 Compose 的 `LocalHapticFeedback`（那套最终是
 * `View.performHapticFeedback()`，既需要 VIBRATE 权限，又会被系统「触感反馈」开关掐掉）。
 *
 * 光直接 `vibrate()` 还不够：Android 默认把 one-shot 归到 `USAGE_TOUCH`，
 * 这台机器的 `VibrationIntensities: TOUCH = OFF`，事件会被标成 `ignored_for_settings`，
 * 马达不响。所以 33+ 显式指定 `USAGE_HARDWARE_FEEDBACK` —— 它对应「硬件反馈」强度，
 * 和触感开关无关。
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** 短促一下。默认 30ms；八向切换和滑块强弱边界用 10ms。 */
    fun tick(durationMs: Long = DEFAULT_DURATION_MS) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return

        runCatching {
            val effect = VibrationEffect.createOneShot(durationMs, AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                    .build()
                motor.vibrate(effect, attributes)
            } else {
                motor.vibrate(effect)
            }
        }.onFailure { Log.w(TAG, "震动失败", it) }
    }

    private companion object {
        const val TAG = "PadLinkHaptics"
        const val DURATION_MS = 30L
        const val DEFAULT_DURATION_MS = DURATION_MS
        const val AMPLITUDE = 160
    }
}
