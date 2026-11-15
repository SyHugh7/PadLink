package com.padlink.app

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.padlink.app.pref.AppPrefs
import kotlin.math.roundToLong

/**
 * 马达输出：按键反馈 + 游戏回传震动。
 *
 * 直接驱动马达，不走 Compose 的 `LocalHapticFeedback`（那套最终是
 * `View.performHapticFeedback()`，既需要 VIBRATE 权限，又会被系统「触感反馈」开关掐掉）。
 *
 * 光直接 `vibrate()` 还不够：Android 默认把 one-shot 归到 `USAGE_TOUCH`，
 * 这台机器的 `VibrationIntensities: TOUCH = OFF`，事件会被标成 `ignored_for_settings`，
 * 马达不响。所以 33+ 显式指定 `USAGE_HARDWARE_FEEDBACK` —— 它对应「硬件反馈」强度，
 * 和触感开关无关。
 */
class Haptics(context: Context, private val prefs: AppPrefs) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** 短促一下。时长会乘上设置里的「震动乘区」，默认 30ms。 */
    fun tick(durationMs: Long = DEFAULT_DURATION_MS) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return

        val duration = (durationMs * prefs.hapticScale)
            .roundToLong()
            .coerceIn(1L, MAX_DURATION_MS)
        vibrate(VibrationEffect.createOneShot(duration, AMPLITUDE))
    }

    /**
     * 游戏回传的震动。手机只有一个马达，取左右里较大的那个。
     *
     * 用 1 秒的一次性震动而不是几十毫秒的短震：游戏可能只在碰撞那一刻发一次强度，
     * 下一次更新会直接替换当前效果；强度为 0 则立即停。
     */
    fun rumble(left: Int, right: Int) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return

        val level = maxOf(left, right).coerceIn(0, 255)
        if (level == 0) {
            runCatching { motor.cancel() }
            return
        }

        val amplitude = if (motor.hasAmplitudeControl()) level.coerceAtLeast(1) else 255

        // 用 one-shot 而不是 waveform：这台机器的 VibratorManager 会把重复的
        // waveform 请求记成 ignored_for_ongoing，one-shot 与按键反馈走同一条路，稳定。
        // 游戏持续震动时，下一次回调会替换当前效果。
        vibrate(VibrationEffect.createOneShot(RUMBLE_PULSE_MS, amplitude))
    }

    private fun vibrate(effect: VibrationEffect) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return

        runCatching {
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
        const val MAX_DURATION_MS = 200L
        const val AMPLITUDE = 160

        /** 一次回传震动最长持续多久；下一次回传会把它换掉。 */
        const val RUMBLE_PULSE_MS = 1000L
    }
}
