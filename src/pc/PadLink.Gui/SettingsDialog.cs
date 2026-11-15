using PadLink.Core.Protocol;

namespace PadLink.Gui;

/// <summary>
/// 设置弹窗。跟 Android 端一个路子：顶上只留一颗「设置」，具体开关都收在这里。
/// </summary>
internal sealed class SettingsDialog : Form
{
    private readonly MainForm _owner;
    private Button _startButton = null!;
    private Button _stopButton = null!;
    private Button _adbButton = null!;
    private Button _xboxChip = null!;
    private Button _psChip = null!;
    private NumericUpDown _portInput = null!;

    public SettingsDialog(MainForm owner)
    {
        _owner = owner;

        Text = "PadLink 设置";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterParent;
        MaximizeBox = false;
        MinimizeBox = false;
        ShowInTaskbar = false;
        BackColor = PadTheme.Background;
        ForeColor = PadTheme.Text;
        ClientSize = new Size(460, 400);
        Font = PadTheme.Body;

        Build();

        // 服务可能被主界面或托盘改过状态，每次打开都对一遍。
        SyncServiceButtons();
    }

    private void Build()
    {
        var y = 16;

        AddSection("服务", ref y);

        _startButton = PadTheme.MakeButton("启动", 92);
        _startButton.Location = new Point(20, y);
        _startButton.Click += (_, _) =>
        {
            _owner.StartServer();
            SyncServiceButtons();
        };

        _stopButton = PadTheme.MakeButton("停止", 92);
        _stopButton.Location = new Point(120, y);
        _stopButton.Click += (_, _) =>
        {
            _owner.StopServer();
            SyncServiceButtons();
        };

        _adbButton = PadTheme.MakeButton("ADB 隧道", 104);
        _adbButton.Location = new Point(220, y);
        _adbButton.Click += (_, _) => _owner.EstablishAdbTunnel();

        Controls.Add(_startButton);
        Controls.Add(_stopButton);
        Controls.Add(_adbButton);
        y += 46;

        AddSection("手柄类型", ref y);

        _xboxChip = PadTheme.MakeChip("Xbox 360", _owner.PreferredController == ControllerType.Xbox360, 132);
        _xboxChip.Location = new Point(20, y);
        _xboxChip.Click += (_, _) => SelectController(ControllerType.Xbox360);

        _psChip = PadTheme.MakeChip("PlayStation 4", _owner.PreferredController == ControllerType.Ds4, 132);
        _psChip.Location = new Point(160, y);
        _psChip.Click += (_, _) => SelectController(ControllerType.Ds4);

        Controls.Add(_xboxChip);
        Controls.Add(_psChip);
        y += 30;

        var hint = new Label
        {
            Text = "PS4 走 HID 设备，Steam 能认出是 PlayStation 手柄；Xbox 360 走 XInput，兼容性最广。",
            ForeColor = PadTheme.TextMuted,
            Font = PadTheme.BodySmall,
            Location = new Point(20, y),
            Size = new Size(420, 34),
        };
        Controls.Add(hint);
        y += 44;

        AddSection("端口", ref y);

        var portLabel = new Label
        {
            Text = "控制端口（ADB，两端填同一个）",
            ForeColor = PadTheme.Text,
            Location = new Point(20, y),
            Size = new Size(420, 20),
        };
        Controls.Add(portLabel);

        _portInput = new NumericUpDown
        {
            Minimum = 1024,
            Maximum = 65535,
            Value = Math.Clamp(_owner.ControlPort, 1024, 65535),
            Location = new Point(20, y + 26),
            Size = new Size(110, 26),
            BackColor = PadTheme.Surface,
            ForeColor = PadTheme.Text,
            BorderStyle = BorderStyle.FixedSingle,
            Font = PadTheme.Body,
            TextAlign = HorizontalAlignment.Right,
            DecimalPlaces = 0,
        };
        Controls.Add(_portInput);

        var applyPort = PadTheme.MakeButton("应用", 72);
        applyPort.Location = new Point(140, y + 26);
        applyPort.Click += (_, _) =>
        {
            _owner.SetControlPort((int)_portInput.Value);
            SyncServiceButtons();
        };
        Controls.Add(applyPort);

        var portHint = new Label
        {
            Text = $"UDP {ProtocolConstants.UdpPort}（WiFi，固定） · 改端口会重建服务",
            ForeColor = PadTheme.TextMuted,
            Font = PadTheme.BodySmall,
            Location = new Point(20, y + 58),
            Size = new Size(420, 30),
        };
        Controls.Add(portHint);
        y += 96;

        var close = PadTheme.MakeButton("关闭", 92);
        close.Location = new Point(ClientSize.Width - 112, ClientSize.Height - 48);
        close.Click += (_, _) => Close();
        Controls.Add(close);

        AcceptButton = close;
        CancelButton = close;
    }

    private void AddSection(string title, ref int y)
    {
        var label = new Label
        {
            Text = title,
            ForeColor = PadTheme.Accent,
            Font = new Font(PadTheme.Body, FontStyle.Bold),
            Location = new Point(20, y),
            Size = new Size(400, 20),
        };
        Controls.Add(label);
        y += 26;
    }

    private void SelectController(ControllerType type)
    {
        if (_owner.PreferredController == type) return;

        _owner.SetControllerType(type);
        PadTheme.ApplyChipState(_xboxChip, type == ControllerType.Xbox360);
        PadTheme.ApplyChipState(_psChip, type == ControllerType.Ds4);
        SyncServiceButtons();
    }

    private void SyncServiceButtons()
    {
        var running = _owner.IsRunning;
        _startButton.Enabled = !running;
        _stopButton.Enabled = running;
        _adbButton.Enabled = running;
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        DarkTitleBar.Apply(this);
    }
}
