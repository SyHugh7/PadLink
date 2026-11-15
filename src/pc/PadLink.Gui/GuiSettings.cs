using System.Text.Json;
using PadLink.Core.Protocol;

namespace PadLink.Gui;

/// <summary>PC 端本机设置。只存需要跨启动保留的项。</summary>
internal sealed class GuiSettings
{
    public int ControlPort { get; set; } = ProtocolConstants.ControlPort;

    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "PadLink", "settings.json");

    public static GuiSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var loaded = JsonSerializer.Deserialize<GuiSettings>(File.ReadAllText(FilePath));
                if (loaded is not null and { ControlPort: >= 1024 and <= 65535 })
                    return loaded;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"读取设置失败，使用默认端口：{ex.Message}");
        }

        return new GuiSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(
                FilePath,
                JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch (Exception ex)
        {
            Console.WriteLine($"保存设置失败：{ex.Message}");
        }
    }
}
