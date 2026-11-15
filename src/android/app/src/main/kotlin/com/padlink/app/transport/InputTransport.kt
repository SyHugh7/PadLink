package com.padlink.app.transport

import com.padlink.core.InputFrame

/**
 * 输入帧的出口。两种传输模式各一个实现：WiFi 走 UDP，ADB 走 TCP。
 * <para>见 protocol/protocol.md §1。</para>
 */
interface InputTransport : AutoCloseable {
    /** 给界面看的描述，例如 `WiFi 192.168.10.165:42313`。 */
    val describe: String

    /**
     * 连接断了（读线程 EOF、心跳超时等）。实现方可能在 IO 线程调用，
     * 上层负责切回主线程再改界面状态。
     *
     * <para>为什么不让上层靠 `send()` 抛异常发现：TCP 半开连接上写操作可能
     * 很久都不报错，用户看到的就是"界面还显示已连接，PC 那边却早收不到了"。</para>
     */
    var onLost: ((String) -> Unit)?

    /**
     * PC 回传的游戏震动（左/右马达 0..255）。可能在 IO 线程调用。
     * 手机只有一个马达，上层取两者较大值。
     */
    var onRumble: ((Int, Int) -> Unit)?

    /**
     * 发一帧。会被 [FramePump] 以 60Hz 调用，实现里别做重活。
     * @throws TransportException 传输已坏（上层据此显示断线）
     */
    fun send(frame: InputFrame)

    override fun close()
}

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
