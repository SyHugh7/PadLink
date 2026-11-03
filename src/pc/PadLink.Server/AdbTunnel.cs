using System.Diagnostics;

namespace PadLink.Server;

/// <summary>
/// adb reverse 隧道管理，见 <c>protocol/protocol.md</c> §1.1。
/// <para>
/// 方向别记反：<c>adb reverse tcp:A tcp:B</c> 的语义是
/// "<b>设备</b>上连 <c>localhost:A</c> → 转到<b>主机</b>的 B"。
/// 所以 PC 做 server 监听 B，手机做 client 连自己的 <c>127.0.0.1:A</c>，数据就打到 PC 上了。
/// </para>
/// </summary>
public static class AdbTunnel
{
    private static readonly string[] CandidateCommands =
    [
        "adb",                                  // 走 PATH
        @"C:\adb\adb.exe",
        @"C:\platform-tools\adb.exe",
        @"C:\Users\Public\adb.exe",
    ];

    // 找到一次就记住。WatchAdbAsync 每 3 秒轮询一次，没缓存的话在没装 adb 的机器上
    // 会反复起 4 个进程试错。
    private static readonly object FindGate = new();
    private static string? _cachedAdb;
    private static bool _findResolved;

    /// <summary>找一个能用的 adb；都没有就返回 null。结果在进程生命周期内缓存。</summary>
    public static string? FindAdb()
    {
        lock (FindGate)
        {
            if (_findResolved) return _cachedAdb;

            foreach (var candidate in CandidateCommands)
            {
                var (ok, _) = Run(candidate, "version", 4000);
                if (ok)
                {
                    _cachedAdb = candidate;
                    break;
                }
            }

            _findResolved = true;
            return _cachedAdb;
        }
    }

    /// <summary>已连接（且已授权）的设备序列号。空列表表示没插线或没授权。</summary>
    public static IReadOnlyList<string> ListDevices(string adb)
    {
        var (ok, output) = Run(adb, "devices", 5000);
        if (!ok) return [];

        return output
            .Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Skip(1)                                        // 第一行是 "List of devices attached"
            .Select(line => line.Split('\t', ' ', StringSplitOptions.RemoveEmptyEntries))
            .Where(parts => parts.Length >= 2 && parts[1] is "device")
            .Select(parts => parts[0])
            .ToList();
    }

    /// <summary>建立隧道。设备上连 localhost:port 就会打到主机的同一个端口。</summary>
    public static (bool Ok, string Message) EstablishReverse(string adb, int port)
        => RunChecked(adb, $"reverse tcp:{port} tcp:{port}");

    public static (bool Ok, string Message) RemoveReverse(string adb, int port)
        => RunChecked(adb, $"reverse --remove tcp:{port}");

    public static (bool Ok, string Message) ListReverses(string adb)
        => RunChecked(adb, "reverse --list");

    private static (bool Ok, string Message) RunChecked(string adb, string arguments)
    {
        var (ok, output) = Run(adb, arguments, 8000);
        var message = output.Trim();
        if (string.IsNullOrEmpty(message)) message = ok ? "ok" : "（无输出）";
        return (ok, message);
    }

    private static (bool Ok, string Output) Run(string fileName, string arguments, int timeoutMs)
    {
        try
        {
            using var process = Process.Start(new ProcessStartInfo(fileName, arguments)
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            });

            if (process is null) return (false, "无法启动进程");

            // 先把两根管道异步读起来，再等退出。
            // 之前是同步 ReadToEnd() 再 WaitForExit(timeout)：adb 一旦卡住，
            // ReadToEnd 会一直等，超时形同虚设 —— 表现就是启动卡十几秒。
            var stdoutTask = process.StandardOutput.ReadToEndAsync();
            var stderrTask = process.StandardError.ReadToEndAsync();

            if (!process.WaitForExit(timeoutMs))
            {
                try { process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { }
                try { Task.WaitAll(new[] { stdoutTask, stderrTask }, 1000); } catch (AggregateException) { }
                return (false, "超时");
            }

            var stdout = stdoutTask.GetAwaiter().GetResult();
            var stderr = stderrTask.GetAwaiter().GetResult();

            var combined = string.IsNullOrWhiteSpace(stderr) ? stdout : stdout + stderr;
            return (process.ExitCode == 0, combined);
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }
}
