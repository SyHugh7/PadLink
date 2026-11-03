package com.padlink.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 按键反馈震动。
 *
 * **直接驱动马达**，不走 Compose 的 `LocalHapticFeedback`：后者最终是
 * `View.performHapticFeedback()`，既需要 VIBRATE 权限，又会被系统「触感反馈」开关
 * 掐掉。有的机器（比如手上这台）`haptic_feedback_enabled` 默认就是 0，
 * 表现就是设置里开了、权限也给了，但按下去一声都没有。
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** 30ms 的短促一下。没有马达的机器直接静默返回。 */
    fun tick() {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return
        runCatching {
            motor.vibrate(VibrationEffect.createOneShot(DURATION_MS, AMPLITUDE))
        }.onFailure { Log.w(TAG, "震动失败", it) }
    }

    private companion object {
        const val TAG = "PadLinkHaptics"
        const val DURATION_MS = 30L
        const val AMPLITUDE = 160
    }
}
