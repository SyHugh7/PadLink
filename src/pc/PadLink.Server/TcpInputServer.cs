using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// TCP 输入通道（ADB 模式），见 <c>protocol/protocol.md</c> §1.1。
/// <para>
/// 为什么 ADB 模式必须走 TCP：<c>adb forward</c> / <c>adb reverse</c> 只转发 TCP，<b>不支持 UDP</b>。
/// </para>
/// <para>
/// 一条连接上混跑两种东西，按<b>首字节</b>区分：
/// <c>0x50 0x4C</c>（"PL"）是二进制帧（长度由方向决定，手机→PC 就是 28 字节输入帧）；
/// <c>0x7B</c>（"{"）是一行 JSON 控制消息，读到 <c>\n</c> 为止。
/// </para>
/// </summary>
public sealed class TcpInputServer : IDisposable
{
    private readonly TcpListener _listener;
    private readonly SessionRegistry _registry;

    public TcpInputServer(int port, SessionRegistry registry)
    {
        _registry = registry;
        Port = port;

        _listener = new TcpListener(IPAddress.Any, port);
        _listener.Start();
    }

    public int Port { get; }

    public async Task RunAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            TcpClient client;
            try
            {
                client = await _listener.AcceptTcpClientAsync(token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (SocketException ex)
            {
                Console.WriteLine($"  TCP 接受连接失败：{ex.Message}");
                continue;
            }

            // 每条连接一个任务，互不阻塞。
            _ = Task.Run(() => ServeAsync(client, token), CancellationToken.None);
        }
    }

    private async Task ServeAsync(TcpClient client, CancellationToken token)
    {
        var remote = client.Client.RemoteEndPoint as IPEndPoint ?? new IPEndPoint(IPAddress.Loopback, 0);

        // 协议 §7 要求：不关 Nagle，小包会被攒到 ~40ms 才发，手感直接完蛋。
        client.NoDelay = true;

        Console.WriteLine($"  TCP 连接建立 ← {remote}");

        var stream = client.GetStream();
        var writeLock = new SemaphoreSlim(1, 1);
        using var pingCts = CancellationTokenSource.CreateLinkedTokenSource(token);
        var pingTask = PingLoopAsync(stream, writeLock, pingCts.Token);

        // 震动回调是同步的，写入是异步的：fire-and-forget，丢一帧震动不影响输入。
        var rumbleSink = new Action<RumbleFrame>(frame => _ = SendRumbleAsync(stream, writeLock, frame));

        try
        {
            var buffer = new byte[8192];
            var mode = ParseMode.Idle;
            var frameBuffer = new byte[ProtocolConstants.FrameSize];
            var frameFilled = 0;
            var line = new StringBuilder();

            while (!token.IsCancellationRequested)
            {
                var read = await stream.ReadAsync(buffer, token);
                if (read == 0) break;                       // 对端关闭

                for (var i = 0; i < read; i++)
                {
                    var b = buffer[i];
                    switch (mode)
                    {
                        case ParseMode.Idle:
                            if (b == ProtocolConstants.Magic0)
                            {
                                mode = ParseMode.Binary;
                                frameBuffer[0] = b;
                                frameFilled = 1;
                            }
                            else if (b == (byte)'{')
                            {
                                mode = ParseMode.Json;
                                line.Clear().Append('{');
                            }
                            // 其它字节：不在任何帧里的杂音，丢掉。
                            break;

                        case ParseMode.Binary:
                            frameBuffer[frameFilled++] = b;
                            if (frameFilled == ProtocolConstants.FrameSize)
                            {
                                HandleFrame(frameBuffer, remote, rumbleSink);
                                mode = ParseMode.Idle;
                                frameFilled = 0;
                            }

                            break;

                        case ParseMode.Json:
                            if (b == 10)
                            {
                                await HandleControlAsync(stream, line.ToString(), remote, writeLock, token);
                                mode = ParseMode.Idle;
                            }
                            else
                            {
                                line.Append((char)b);
                            }

                            break;
                    }
                }
            }
        }
        catch (OperationCanceledException)
        {
            // 正常停止
        }
        catch (IOException ex)
        {
            Console.WriteLine($"  TCP 连接 {remote} 中断：{ex.Message}");
        }
        finally
        {
            pingCts.Cancel();
            try
            {
                await pingTask;
            }
            catch (OperationCanceledException)
            {
                // 正常停止
            }

            client.Dispose();

            // 连接没了就是手机拔了。ADB 模式下一条连接就是一个会话，
            // 不必再让用户对着已消失的虚拟手柄等五分钟的空闲回收。
            _registry.RemoveByRemote(remote);

            Console.WriteLine($"  TCP 连接关闭 × {remote}");
        }
    }

    /// <summary>
    /// 控制通道心跳。协议 §7 要求手机端一段时间收不到反向消息就判定断开；
    /// 这里每秒发一次，既保活，也能让"TCP 还是开着的，但对面已经没了"的半开连接暴露出来。
    /// </summary>
    private static async Task PingLoopAsync(
        NetworkStream stream, SemaphoreSlim writeLock, CancellationToken token)
    {
        try
        {
            // 握手是 hello → welcome → start → started：先让出两秒，别把 ping 插进握手。
            await Task.Delay(TimeSpan.FromSeconds(2), token);

            using var timer = new PeriodicTimer(TimeSpan.FromSeconds(1));
            while (await timer.WaitForNextTickAsync(token))
            {
                await SendLineAsync(
                    stream,
                    new { v = ProtocolConstants.Version, type = "ping" },
                    writeLock,
                    token);
            }
        }
        catch (OperationCanceledException)
        {
            // 正常停止
        }
        catch (Exception ex) when (ex is IOException or ObjectDisposedException)
        {
            // 连接坏了，读循环会收尾。
        }
    }

    private void HandleFrame(byte[] bytes, IPEndPoint remote, Action<RumbleFrame> rumbleSink)
    {
        if (!InputFrame.TryDecode(bytes, out var frame, out var error))
        {
            Console.WriteLine($"  TCP 丢弃不合规帧：{error}");
            return;
        }

        try
        {
            var session = _registry.GetOrCreate(remote, frame);
            session.SetRumbleSink(rumbleSink);
            session.OnFrame(frame);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"  TCP 处理输入失败：{ex.Message}");
        }
    }

    /// <summary>把驱动回调的震动写成 8 字节反向帧（协议 §6）。</summary>
    private static async Task SendRumbleAsync(
        NetworkStream stream, SemaphoreSlim writeLock, RumbleFrame frame)
    {
        try
        {
            var bytes = frame.Encode();
            await writeLock.WaitAsync();
            try
            {
                await stream.WriteAsync(bytes);
                await stream.FlushAsync();
            }
            finally
            {
                writeLock.Release();
            }
        }
        catch (Exception ex) when (ex is IOException or ObjectDisposedException)
        {
            // 连接已经断了，读循环会收尾。
        }
    }

    /// <summary>控制通道的 JSON 消息（协议 §4）。当前只做最小握手与心跳。</summary>
    private async Task HandleControlAsync(
        NetworkStream stream,
        string json,
        IPEndPoint remote,
        SemaphoreSlim writeLock,
        CancellationToken token)
    {
        JsonElement root;
        try
        {
            root = JsonDocument.Parse(json).RootElement.Clone();
        }
        catch (JsonException)
        {
            Console.WriteLine($"  TCP 控制消息不是合法 JSON，忽略：{json}");
            return;
        }

        var type = root.TryGetProperty("type", out var typeElement) ? typeElement.GetString() : null;

        switch (type)
        {
            case "hello":
                var controller = root.TryGetProperty("controller", out var c) ? c.GetString() : "xbox360";
                Console.WriteLine($"  TCP hello：{controller}");
                await SendLineAsync(stream, new
                {
                    v = ProtocolConstants.Version,
                    type = "welcome",
                    udp_port = ProtocolConstants.UdpPort,
                    codec = "binary",
                    rate = ProtocolConstants.ActiveRateHz,
                    player = 0,
                    controller = controller ?? "xbox360",
                }, writeLock, token);
                break;

            case "start":
                await SendLineAsync(
                    stream,
                    new { v = ProtocolConstants.Version, type = "started", player = 0 },
                    writeLock,
                    token);
                break;

            case "pong":
                // 心跳应答：连接还在，只是没有输入。回收会话看这个时间，不看数据帧。
                _registry.NoteAlive(remote);
                break;

            case "bye":
                Console.WriteLine("  TCP 收到 bye");
                break;

            default:
                Console.WriteLine($"  TCP 控制消息（未处理）：{type}");
                break;
        }
    }

    private static async Task SendLineAsync(
        NetworkStream stream, object payload, SemaphoreSlim writeLock, CancellationToken token)
    {
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload) + (char)10);
        await writeLock.WaitAsync(token);
        try
        {
            await stream.WriteAsync(bytes, token);
            await stream.FlushAsync(token);
        }
        finally
        {
            writeLock.Release();
        }
    }

    public void Dispose() => _listener.Stop();

    private enum ParseMode
    {
        Idle,
        Binary,
        Json,
    }
}
