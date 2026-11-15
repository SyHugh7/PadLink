package com.padlink.app.transport

import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import com.padlink.core.RumbleFrame
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * WiFi 模式：单播 UDP 到 PC。
 *
 * 整个会话共用一个 socket，**源端口必须稳定**——PC 靠 (IP, 源端口) 认出是哪个会话
 * （protocol.md §1）。所以这里绝不中途重建 socket。
 *
 * 同一个 socket 也接收 PC 发回来的震动帧（§6）：PC 把 8 字节帧发到本 socket 的源端口。
 */
class UdpTransport(
    host: String,
    port: Int = ProtocolConstants.UDP_PORT,
) : InputTransport {

    private val socket = DatagramSocket()
    private val target = InetSocketAddress(host, port)

    /** 复用同一块缓冲区：60Hz 下每帧新建数组会白白制造 GC 压力。 */
    private val buffer = ByteArray(ProtocolConstants.FRAME_SIZE)

    @Volatile
    private var closed = false

    override val describe: String = "WiFi $host:$port"

    /** UDP 没有连接可断；发送失败由 [FramePump] 捕获后转告上层。 */
    override var onLost: ((String) -> Unit)? = null

    override var onRumble: ((Int, Int) -> Unit)? = null

    init {
        startReceiver()
    }

    override fun send(frame: InputFrame) {
        frame.encodeInto(buffer)
        try {
            socket.send(DatagramPacket(buffer, ProtocolConstants.FRAME_SIZE, target))
        } catch (e: IOException) {
            throw TransportException("UDP 发送失败：${e.message}", e)
        }
    }

    override fun close() {
        closed = true
        socket.close()
    }

    /** 反向通道：PC → 手机，固定 8 字节震动帧。 */
    private fun startReceiver() {
        Thread({
            val packet = DatagramPacket(ByteArray(ProtocolConstants.RUMBLE_SIZE), ProtocolConstants.RUMBLE_SIZE)
            while (!closed) {
                try {
                    socket.receive(packet)
                    RumbleFrame.decodeOrNull(packet.data, packet.offset, packet.length)
                        ?.let { onRumble?.invoke(it.left, it.right) }
                } catch (e: IOException) {
                    // close() 会打断 receive；真出错也退出，发送侧会报错。
                    break
                }
            }
        }, "padlink-udp-rumble").apply {
            isDaemon = true
            start()
        }
    }
}
