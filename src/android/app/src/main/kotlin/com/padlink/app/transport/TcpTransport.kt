package com.padlink.app.transport

import android.os.Build
import android.os.SystemClock
import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import com.padlink.core.RumbleFrame
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADB 模式：PC 做 TCP server，手机做 client 连自己的 `127.0.0.1`。
 * 隧道由 PC 端执行 `adb reverse` 建立，见 protocol.md §1.1。
 *
 * 连 `127.0.0.1` 在 Android 上**不需要任何权限**，这也是这条路线的便宜之处。
 *
 * 握手完成后会起两个后台线程：
 * - **读线程**：接 PC 的反向消息（心跳 `ping`、以后的震动帧），并立刻回 `pong`。
 * - **看门狗**：PC 端每 1 秒发一次心跳，超过 [INBOUND_TIMEOUT_MS] 什么都没收到，
 *   说明连接已经半开（拔线、隧道断、PC 被杀），主动收掉并通知上层。
 */
class TcpTransport(
    host: String,
    port: Int = ProtocolConstants.CONTROL_PORT,
    controller: String = "xbox360",
    players: Int = 1,
) : InputTransport {

    private val socket = Socket()
    private lateinit var out: OutputStream
    private lateinit var input: InputStream
    private val buffer = ByteArray(ProtocolConstants.FRAME_SIZE)

    /** `send()`（60Hz）和读线程回 `pong` 都会写同一个流，必须串行。 */
    private val writeLock = Any()

    /** 只让"连接断了"通知一次：读线程和看门狗可能同时发现。 */
    private val lost = AtomicBoolean(false)

    @Volatile
    private var closed = false

    /** 最近一次收到 PC 反向消息的时刻（`elapsedRealtime`）。 */
    @Volatile
    private var lastInboundAt = 0L

    override val describe: String = "ADB $host:$port"

    override var onLost: ((String) -> Unit)? = null

    override var onRumble: ((Int, Int) -> Unit)? = null

    init {
        try {
            // 协议 §7：不关 Nagle，小包会被攒到 ~40ms 才发，手感直接完蛋。
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MS)

            out = socket.getOutputStream()
            input = socket.getInputStream()

            // 协议 §4.1 的握手。服务端不做认证时 auth 为 none。
            sendLine(
                """{"v":${ProtocolConstants.VERSION},"type":"hello","device":"${deviceName()}",""" +
                    """"codecs":["binary"],"controller":"$controller","players":$players}"""
            )
            awaitControl("welcome")
            sendLine("""{"v":${ProtocolConstants.VERSION},"type":"start"}""")
            awaitControl("started")

            socket.soTimeout = 0              // 之后由看门狗负责超时
            lastInboundAt = SystemClock.elapsedRealtime()
            startReader()
            startWatchdog()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw TransportException("连不上 PC（$host:$port）：${e.message}", e)
        }
    }

    override fun send(frame: InputFrame) {
        frame.encodeInto(buffer)
        try {
            synchronized(writeLock) { out.write(buffer, 0, ProtocolConstants.FRAME_SIZE) }
        } catch (e: IOException) {
            throw TransportException("TCP 发送失败：${e.message}", e)
        }
    }

    override fun close() {
        if (closed) return
        closed = true

        // 协议 §4.2：正常退出打个招呼。失败也无所谓，连接马上就要关了。
        runCatching { sendLine("""{"v":${ProtocolConstants.VERSION},"type":"bye"}""") }
        runCatching { socket.close() }
    }

    /**
     * 读线程：PC → 手机方向的字节流。
     *
     * 切分规则与 PC 端一致（protocol.md §1.1 帧切分）：`{` 开头是一行 JSON 控制消息，
     * `0x50 0x4C` 是固定长度的二进制反向帧。**必须读完每一帧的字节**，否则后面的
     * 消息会错位。
     */
    private fun startReader() {
        Thread({
            try {
                while (!closed) {
                    when (val first = input.read()) {
                        -1 -> break

                        '{'.code -> {
                            val line = readLineBody(first)
                            lastInboundAt = SystemClock.elapsedRealtime()
                            handleControl(line)
                        }

                        ProtocolConstants.MAGIC_0.toInt() -> {
                            // 反向二进制帧（震动）固定 8 字节，首字节已读，补齐剩下 7 字节。
                            val frame = ByteArray(ProtocolConstants.RUMBLE_SIZE)
                            frame[0] = ProtocolConstants.MAGIC_0
                            readFully(frame, 1, ProtocolConstants.RUMBLE_SIZE - 1)
                            lastInboundAt = SystemClock.elapsedRealtime()
                            RumbleFrame.decodeOrNull(frame)?.let { onRumble?.invoke(it.left, it.right) }
                        }

                        // 不在任何帧里的杂音：丢掉。
                    }
                }

                notifyLost("PC 关闭了连接")
            } catch (e: Exception) {
                if (!closed) notifyLost(e.message ?: "连接中断")
            }
        }, "padlink-tcp-reader").apply {
            isDaemon = true
            start()
        }
    }

    /** 看门狗：半开连接上 `write()` 往往不会报错，只能靠"多久没收到 PC 的话"判断。 */
    private fun startWatchdog() {
        Thread({
            while (!closed) {
                try {
                    Thread.sleep(WATCHDOG_TICK_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }

                if (SystemClock.elapsedRealtime() - lastInboundAt > INBOUND_TIMEOUT_MS) {
                    notifyLost("PC 心跳超时")
                    return@Thread
                }
            }
        }, "padlink-tcp-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleControl(line: String) {
        // 当前只有心跳。不要上 JSON 解析库：一行一个字段，contains 足够。
        if (line.contains("\"ping\"")) {
            runCatching {
                sendLine("""{"v":${ProtocolConstants.VERSION},"type":"pong"}""")
            }
        }
    }

    private fun notifyLost(reason: String) {
        if (!lost.compareAndSet(false, true)) return
        closed = true
        runCatching { socket.close() }
        onLost?.invoke(reason)
    }

    private fun sendLine(line: String) {
        val bytes = (line + NEWLINE).toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            out.write(bytes)
            out.flush()
        }
    }

    /** 握手期用：读到指定控制消息为止。PC 的心跳可能在握手后立刻插进来，跳过它。 */
    private fun awaitControl(marker: String) {
        repeat(4) {
            if (readLine().contains(marker)) return
        }
        throw TransportException("握手失败：没等到 $marker")
    }

    /** 逐字节读到换行符。不用 BufferedReader——它会预读，把心跳吞进缓冲区。 */
    private fun readLine(): String {
        val builder = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw TransportException("PC 在握手过程中关闭了连接")
            if (b == NEWLINE_CODE) return builder.toString()
            if (b != CARRIAGE_RETURN_CODE) builder.append(b.toChar())
        }
    }

    private fun readLineBody(first: Int): String {
        val builder = StringBuilder().append(first.toChar())
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("连接已关闭")
            if (b == NEWLINE_CODE) return builder.toString()
            if (b != CARRIAGE_RETURN_CODE) builder.append(b.toChar())
        }
    }

    private fun readFully(destination: ByteArray, offset: Int, count: Int) {
        var filled = 0
        while (filled < count) {
            val read = input.read(destination, offset + filled, count - filled)
            if (read < 0) throw IOException("连接已关闭")
            filled += read
        }
    }

    private fun deviceName(): String =
        runCatching { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }.getOrDefault("android")

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 5000

        /** 看门狗检查间隔。 */
        const val WATCHDOG_TICK_MS = 500L

        /** PC 每 1 秒发一次 `ping`；3 秒没动静就当作断开。 */
        const val INBOUND_TIMEOUT_MS = 3000L

        const val NEWLINE = 10.toChar().toString()
        const val NEWLINE_CODE = 10
        const val CARRIAGE_RETURN_CODE = 13
    }
}
