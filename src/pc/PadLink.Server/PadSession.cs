using System.Net;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// 一个手机会话：把收到的帧喂给虚拟手柄，同时守住 fail-safe 与丢包统计。
/// </summary>
public sealed class PadSession : IDisposable
{
    private const uint SequenceJumpIgnoreThreshold = 300;

    private bool _hasSequence;

    /// <summary>震动往哪儿发。null 表示会话还没挂上传输通道。</summary>
    private volatile Action<RumbleFrame>? _sendRumble;

    public PadSession(IPEndPoint remote, IVirtualController controller)
    {
        Remote = remote;
        Controller = controller;

        // 游戏的震动是驱动回调进来的；这里只负责交给当前连接的发送端。
        Controller.Rumble += OnRumble;
    }

    public IPEndPoint Remote { get; private set; }

    public IVirtualController Controller { get; }

    /// <summary>设置震动回传通道（UDP/TCP 各自的发送实现）。</summary>
    public void SetRumbleSink(Action<RumbleFrame>? sink) => _sendRumble = sink;

    private void OnRumble(object? sender, RumbleEventArgs e)
        => _sendRumble?.Invoke(new RumbleFrame(Controller.Player, e.Left, e.Right));

    public InputTimeoutGuard Guard { get; } = new(ProtocolConstants.FailSafeTimeout);

    /// <summary>最近一次收到数据帧的时间戳（<see cref="TimeProvider.GetTimestamp"/>）。</summary>
    public long LastFrameTimestamp { get; private set; } = TimeProvider.System.GetTimestamp();

    /// <summary>
    /// 最近一次收到<b>任何</b>流量的时间：数据帧或控制通道的心跳。
    /// 回收会话看这个，不看 <see cref="LastFrameTimestamp"/>——手机不动≠手机拔了。
    /// </summary>
    private long _lastAliveTimestamp = TimeProvider.System.GetTimestamp();

    /// <summary>空闲时长（没有输入帧），界面显示用。</summary>
    public TimeSpan IdleFor => TimeProvider.System.GetElapsedTime(LastFrameTimestamp);

    /// <summary>静默时长（既没有输入帧也没有心跳），会话回收用。</summary>
    public TimeSpan SilentFor => TimeProvider.System.GetElapsedTime(_lastAliveTimestamp);

    /// <summary>收到对端心跳（`pong`）：连接还活着，只是这段时间没有输入。</summary>
    public void NoteAlive()
    {
        _lastAliveTimestamp = TimeProvider.System.GetTimestamp();
    }

    public uint LastSequence { get; private set; }

    public long FramesReceived { get; private set; }

    public long FramesLost { get; private set; }

    public bool Stale => Guard.IsStale;

    /// <summary>
    /// 最近一次提交给虚拟手柄的状态——也就是这台手柄正在"输出"的东西。
    /// 界面的手柄输出面板读它。
    /// <para>
    /// 网络线程写、界面线程读，而结构体不是原子的：偶尔可能读到"新的一半 + 旧的一半"。
    /// 只用于显示，下一帧就正过来了，不值得为它上锁。
    /// </para>
    /// </summary>
    public GamepadState LastState { get; private set; } = GamepadState.Neutral;

    public void OnFrame(in InputFrame frame)
    {
        if (_hasSequence)
        {
            // uint 环绕算术天然处理 seq 回绕；跳变过大视为对端重启，不计入丢包。
            var lost = frame.Sequence - (LastSequence + 1);
            if (lost <= SequenceJumpIgnoreThreshold) FramesLost += lost;
        }

        _hasSequence = true;
        LastSequence = frame.Sequence;
        LastFrameTimestamp = TimeProvider.System.GetTimestamp();
        NoteAlive();
        FramesReceived++;

        if (Guard.IsStale)
            Console.WriteLine($"  [P{Controller.Player}] 连接恢复，重新接管输入");

        Guard.OnFrame();

        var state = GamepadState.FromFrame(frame);
        LastState = state;
        Controller.Submit(state);
    }

    /// <summary>周期调用。返回 true 表示本次刚进入失效状态并已归零。</summary>
    public bool PollFailSafe()
    {
        if (!Guard.Poll()) return false;

        LastState = GamepadState.Neutral;
        Controller.Submit(GamepadState.Neutral);
        Console.WriteLine($"  [P{Controller.Player}] 超过 {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms 未收到数据，已归零输入");
        return true;
    }

    public void Dispose()
    {
        Controller.Rumble -= OnRumble;
        Controller.Dispose();
    }

    /// <summary>
    /// 同一台手机换了源端口（应用重启、UDP socket 重建）时接管会话。
    /// <para>
    /// 不新建虚拟手柄：一台手柄就该对应一个虚拟设备，否则 XInput 只有 4 个槽位，
    /// 重连几次就被历史设备占满了。
    /// </para>
    /// </summary>
    public void Rebind(IPEndPoint newRemote)
    {
        Remote = newRemote;
        LastFrameTimestamp = TimeProvider.System.GetTimestamp();
        NoteAlive();

        // 新来源的 seq 从头开始，不能让它的第一帧被当成"丢了 N 帧"。
        _hasSequence = false;
    }
}
