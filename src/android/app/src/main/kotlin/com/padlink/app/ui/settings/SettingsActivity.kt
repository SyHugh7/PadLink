package com.padlink.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkApplication
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle
import com.padlink.app.pref.AccentColor
import com.padlink.app.pref.ThemeMode
import com.padlink.app.ui.theme.PadLinkTheme

/** 设置页。跟主界面分属两个 Activity，状态都在 [PadLinkApplication] 里共享。 */
class SettingsActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as PadLinkApplication).statusBar.onForeground()
    }

    override fun onStop() {
        (application as PadLinkApplication).statusBar.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as PadLinkApplication
            PadLinkTheme(app.prefs) {
                SettingsPage(app, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(app: PadLinkApplication, onBack: () -> Unit) {
    val prefs = app.prefs
    val controller = app.controller
    val layout = app.layout

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 18.sp) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ 返回", fontSize = 15.sp) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup("个性化") {
                ExpandableRow("主题设置", prefs.themeMode.label) {
                    ChipRow(
                        values = ThemeMode.entries,
                        selected = prefs.themeMode,
                        label = { it.label },
                        onSelect = { prefs.themeMode = it },
                    )
                }

                ExpandableRow("主题色", prefs.accentColor.label) {
                    ChipRow(
                        values = AccentColor.entries,
                        selected = prefs.accentColor,
                        label = { it.label },
                        onSelect = { prefs.accentColor = it },
                    )
                }
            }

            SettingsGroup("按键设置") {
                ExpandableRow(
                    title = "扳机键设置",
                    summary = if (layout.triggerStyle == TriggerStyle.SLIDE) "线性" else "扳机",
                ) {
                    Text("扳机形式", fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp))
                    ChipRow(
                        values = listOf(TriggerStyle.SLIDE, TriggerStyle.BUTTON),
                        selected = layout.triggerStyle,
                        label = { if (it == TriggerStyle.SLIDE) "线性" else "扳机" },
                        onSelect = { app.updateLayout(layout.copy(triggerStyle = it)) },
                    )

                    if (layout.triggerStyle == TriggerStyle.SLIDE) {
                        SliderRow(
                            label = "弱加载区",
                            display = "%.0f%%".format(layout.triggerGate * 100),
                            value = layout.triggerGate,
                            range = PadLayout.MIN_TRIGGER_GATE..PadLayout.MAX_TRIGGER_GATE,
                            onDrag = { app.updateLayout(layout.copy(triggerGate = it)) },
                            onCommit = {},
                        )

                        SliderRow(
                            label = "宽度",
                            display = "%.0f%%".format(
                                (layout.element(PadElementKind.TRIGGER_LEFT)?.size ?: 0.06f) * 100
                            ),
                            value = layout.element(PadElementKind.TRIGGER_LEFT)?.size ?: 0.06f,
                            range = PadLayout.MIN_SIZE..0.2f,
                            onDrag = {
                                app.updateLayout(
                                    layout.resize(PadElementKind.TRIGGER_LEFT, it)
                                        .resize(PadElementKind.TRIGGER_RIGHT, it)
                                )
                            },
                            onCommit = {},
                        )

                        SliderRow(
                            label = "长度",
                            display = "%.1f".format(layout.triggerAspect),
                            value = layout.triggerAspect,
                            range = PadLayout.MIN_TRIGGER_ASPECT..PadLayout.MAX_TRIGGER_ASPECT,
                            onDrag = { app.updateLayout(layout.copy(triggerAspect = it)) },
                            onCommit = {},
                        )
                    }
                }

                ExpandableRow(
                    title = "十字键设置",
                    summary = if (layout.dpadStyle == DPadStyle.COMBO) "8 向" else "四分",
                ) {
                    ChipRow(
                        values = listOf(DPadStyle.COMBO, DPadStyle.TRIANGLE),
                        selected = layout.dpadStyle,
                        label = { if (it == DPadStyle.COMBO) "8 向" else "四分" },
                        onSelect = { app.updateLayout(layout.copy(dpadStyle = it)) },
                    )
                }

                ExpandableRow(
                    title = "陀螺仪设置",
                    summary = if (controller.tiltEnabled) "重力开" else "重力关",
                ) {
                    SwitchRow(
                        label = "重力转向",
                        checked = controller.tiltEnabled,
                        onChange = { controller.enableTilt(it) },
                        enabled = controller.tiltAvailable,
                        hint = if (controller.tiltAvailable) null else "这台机器没有陀螺仪",
                    )

                    TextButton(
                        onClick = { controller.recenterTilt() },
                        enabled = controller.tiltEnabled,
                    ) { Text("重定中心", fontSize = 13.sp) }

                    val tilt = controller.tiltSettings

                    SliderRow(
                        label = "死区",
                        display = "%.1f°".format(tilt.deadZoneDeg),
                        value = tilt.deadZoneDeg,
                        range = 0f..6f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(deadZoneDeg = it), false) },
                        onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                    )
                    SliderRow(
                        label = "满舵角",
                        display = "%.0f°".format(tilt.maxAngleDeg),
                        value = tilt.maxAngleDeg,
                        range = 15f..60f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(maxAngleDeg = it), false) },
                        onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                    )
                    SliderRow(
                        label = "曲线",
                        display = "%.2f".format(tilt.curve),
                        value = tilt.curve,
                        range = 0.2f..3f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(curve = it), false) },
                        onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                    )
                    SliderRow(
                        label = "平滑",
                        display = "%.2f".format(tilt.smoothing),
                        value = tilt.smoothing,
                        range = 0.05f..0.8f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(smoothing = it), false) },
                        onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                    )
                    SliderRow(
                        label = "恢复延时",
                        display = "%.1fs".format(tilt.stickHoldSec),
                        value = tilt.stickHoldSec,
                        range = 0f..3f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(stickHoldSec = it), false) },
                        onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                    )

                    SwitchRow(
                        label = "PS4 全局体感",
                        checked = false,
                        onChange = {},
                        enabled = false,
                        hint = "还没做",
                    )
                }

                ExpandableRow("震动设置", "按下反馈") {
                    SwitchRow(
                        label = "PS4 震动",
                        checked = false,
                        onChange = {},
                        enabled = false,
                        hint = "还没做",
                    )

                    SwitchRow(
                        label = "按钮按下反馈",
                        checked = prefs.hapticFeedback,
                        onChange = { prefs.hapticFeedback = it },
                    )
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            app.pendingLayoutEdit = true
                            onBack()
                        },
                    ) { Text("调整布局", fontSize = 13.sp) }
                }

                SwitchRow(
                    label = "选择布局",
                    checked = false,
                    onChange = {},
                    enabled = false,
                    hint = "还没做",
                )
            }

            SettingsGroup("应用设置") {
                SwitchRow(
                    label = "退出前台时冻结",
                    checked = prefs.freezeOnBackground,
                    onChange = { prefs.freezeOnBackground = it },
                    hint = "离开界面时归零输入",
                )

                SwitchRow(
                    label = "前台时禁用状态栏",
                    checked = prefs.disableStatusBar,
                    onChange = { app.statusBar.setEnabled(it) },
                    enabled = app.statusBar.available,
                    hint = when {
                        !app.statusBar.available -> "需要 Dhizuku"
                        !app.statusBar.granted -> "需要 Dhizuku 授权"
                        else -> null
                    },
                )
            }

            // 底部留白，最后一项别贴着屏幕边
            Column(Modifier.padding(bottom = 32.dp)) {}
        }
    }
}
