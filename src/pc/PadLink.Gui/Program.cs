using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace PadLink.Gui;

internal static class Program
{
    /// <summary>第二个实例用它把已有窗口从托盘叫回来。</summary>
    internal const int RestoreMessage = 0x8000 + 1;

    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();

        // 一个进程只能占一套端口。第二次双击时激活已有窗口，而不是让服务启动失败。
        using var mutex = new Mutex(initiallyOwned: true, @"Local\PadLink.Gui", out var isFirst);
        if (!isFirst)
        {
            ActivateExisting();
            return;
        }

        // 出了未处理异常要“报错”，不要“窗口突然消失”。
        // 日志仍然照写（MainForm 里还挂了一个处理器），这里再给一个能读的对话框。
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) =>
        {
            Console.WriteLine($"未处理的界面异常：{e.Exception}");
            MessageBox.Show(
                $"PadLink 遇到错误：{e.Exception.Message}",
                "PadLink",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        };

        try
        {
            Application.Run(new MainForm());
        }
        catch (Exception ex)
        {
            // 窗口都还没起来就失败：日志照写，然后给一个能读的提示。
            Console.WriteLine($"启动失败：{ex}");
            MessageBox.Show(
                $"PadLink 启动失败：{ex.Message}",
                "PadLink",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
    }

    private static void ActivateExisting()
    {
        var hwnd = FindWindow(null, "PadLink");
        if (hwnd == IntPtr.Zero) return;

        PostMessage(hwnd, RestoreMessage, IntPtr.Zero, IntPtr.Zero);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr FindWindow(string? className, string windowName);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool PostMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);
}
