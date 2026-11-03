package com.padlink.app.input

import android.os.SystemClock
import com.padlink.core.AxisRange
import com.padlink.core.ControllerType
import com.padlink.core.DPad
import com.padlink.core.InputFrame
import kotlin.math.roundToInt

/**
 * 手柄的当前输入。UI 层写它，发送循环读它。
 *
 * 触屏坐标在这里换算成协议的**物理语义**——这是全项目唯一做这个换算的地方：
 * 屏幕 y 向下为正，协议 y 向上为正（protocol.md §5.4）。
 * 别在 UI 里到处取反，那样迟早有一处漏了，表现就是"上下颠倒"。
 *
 * 也是全项目唯一做**输入源仲裁**的地方：触摸摇杆和重力都想写左摇杆 X，而字段是裸
 * `@Volatile`，同时写就是谁后写谁赢。规则是**摇杆优先**：手一摸上去就接管，
 * 松开后再等 [setTiltX] 的 `holdMs`，重力才回来。
 */
class PadInputState(
    private val controller: ControllerType = ControllerType.XBOX360,
    private val player: Int = 0,
) {
    // 分散的 @Volatile 字段：理论上可能读到几十微秒内的混合状态，对输入无影响，
    // 换来的是零分配、零锁。发送循环每 16ms 才读一次，实际几乎不会撞上。
    @Volatile var leftX = 0f
    @Volatile var leftY = 0f
    @Volatile var rightX = 0f
    @Volatile var rightY = 0f

    /** [GamepadButtons] 位掩码。 */
    @Volatile var buttons = 0

    @Volatile var dpad = DPad.NEUTRAL
    @Volatile var leftTrigger = 0f
    @Volatile var rightTrigger = 0f

    /** 左摇杆是不是正被按着。 */
    @Volatile private var stickEngaged = false

    /** 最后一次松开左摇杆的时刻（uptimeMillis）。重力靠它算还要等多久。 */
    @Volatile private var stickReleasedAt = 0L

    /** 左摇杆按下 / 松开。松开要记时间，重力才知道该等多久再接管。 */
    fun setStickEngaged(engaged: Boolean) {
        stickEngaged = engaged
        if (!engaged) stickReleasedAt = SystemClock.uptimeMillis()
    }

    /** 左摇杆的触摸回调（屏幕坐标）。摇杆永远优先，重力开着也照写。 */
    fun setLeftStick(x: Float, y: Float) {
        leftX = x
        leftY = y
    }

    /**
     * 重力解算出的左摇杆 X。摇杆按着、或者刚松开还不到 [holdMs]，就让位。
     *
     * 让位时干脆不写：留着摇杆给的值（松手通常是 0），别让重力在指头底下抢方向盘。
     */
    fun setTiltX(value: Float, holdMs: Long) {
        if (stickEngaged) return
        if (holdMs > 0 && SystemClock.uptimeMillis() - stickReleasedAt < holdMs) return
        leftX = value
    }

    /** 全部回零。退出前台时用：别让 PC 端卡在最后一个状态上。 */
    fun reset() {
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        buttons = 0
        dpad = DPad.NEUTRAL
        leftTrigger = 0f
        rightTrigger = 0f
        stickEngaged = false
        stickReleasedAt = 0L
    }

    fun toFrame(): InputFrame = InputFrame(
        controller = controller,
        player = player,
        buttons = buttons,
        dpad = dpad,
        leftX = axis(leftX),
        leftY = axis(-leftY),                  // 屏幕往下 → 协议往上
        rightX = axis(rightX),
        rightY = axis(-rightY),
        leftTrigger = trigger(leftTrigger),
        rightTrigger = trigger(rightTrigger),
    )

    private fun axis(value: Float): Short =
        (value.coerceIn(-1f, 1f) * AxisRange.AXIS_MAX).roundToInt().toShort()

    private fun trigger(value: Float): Int =
        (value.coerceIn(0f, 1f) * AxisRange.TRIGGER_MAX).roundToInt()
}
