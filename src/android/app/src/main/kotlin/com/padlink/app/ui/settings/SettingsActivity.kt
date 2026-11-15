@file:OptIn(ExperimentalMaterial3Api::class)

package com.padlink.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.padlink.app.PadLinkApplication
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle
import com.padlink.app.pref.AccentColor
import com.padlink.app.pref.ThemeMode
import com.padlink.app.ui.theme.PadLinkTheme
import com.padlink.app.ui.theme.previewPrimary

/**
 * 设置页的路由表。
 *
 * 首页只放分组入口，具体选项都在子页面里 —— 和参考设计一样：分区 + 独立卡片，
 * 点带箭头的卡片进下一级，返回键回到首页。
 */
private enum class SettingsRoute(val title: String) {
    HOME("设置"),
    THEME("主题设置"),
    TRIGGER("扳机设置"),
    DPAD("十字键设置"),
    TILT("重力转向"),
    HAPTIC("震动设置"),
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 和手柄界面保持一致：沉浸、隐藏系统栏、避开刘海。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val app = application as PadLinkApplication
            PadLinkTheme(app.prefs) {
                SettingsHost(app, onFinish = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as PadLinkApplication).statusBar.onForeground()
    }

    override fun onStop() {
        (application as PadLinkApplication).statusBar.onBackground()
        super.onStop()
    }
}

@Composable
private fun SettingsHost(app: PadLinkApplication, onFinish: () -> Unit) {
    // 隐藏系统栏后，顶部露出来的是 Activity 的窗口背景（主题默认是浅灰）。
    // 把它换成当前主题的 background，否则设置页最上面会留一条浅色横条。
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    SideEffect {
        (context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    }

    var routeName by rememberSaveable { mutableStateOf(SettingsRoute.HOME.name) }
    val route = SettingsRoute.entries.firstOrNull { it.name == routeName } ?: SettingsRoute.HOME

    BackHandler(enabled = route != SettingsRoute.HOME) {
        routeName = SettingsRoute.HOME.name
    }

    // 进子页从右侧滑入、返回时反向滑出，页面之间不再瞬切。
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState != SettingsRoute.HOME
            (
                slideInHorizontally(animationSpec = tween(280)) { if (forward) it / 8 else -it / 8 } +
                    fadeIn(animationSpec = tween(220))
                ).togetherWith(
                slideOutHorizontally(animationSpec = tween(280)) { if (forward) -it / 8 else it / 8 } +
                    fadeOut(animationSpec = tween(180)),
            )
        },
        label = "settingsPage",
    ) { current ->
        when (current) {
            SettingsRoute.HOME -> HomePage(
                app = app,
                onOpen = { routeName = it.name },
                onAdjustLayout = {
                    app.pendingLayoutEdit = true
                    app.controller.freezeInput()
                    onFinish()
                },
            )

            SettingsRoute.THEME -> ThemePage(
                app = app,
                onBack = { routeName = SettingsRoute.HOME.name },
            )

            SettingsRoute.TRIGGER -> TriggerPage(
                app = app,
                onBack = { routeName = SettingsRoute.HOME.name },
            )

            SettingsRoute.DPAD -> DPadPage(
                app = app,
                onBack = { routeName = SettingsRoute.HOME.name },
            )

            SettingsRoute.TILT -> TiltPage(
                app = app,
                onBack = { routeName = SettingsRoute.HOME.name },
            )

            SettingsRoute.HAPTIC -> HapticPage(
                app = app,
                onBack = { routeName = SettingsRoute.HOME.name },
            )
        }
    }
}

// ------------------------------------------------------------------ 首页

@Composable
private fun HomePage(
    app: PadLinkApplication,
    onOpen: (SettingsRoute) -> Unit,
    onAdjustLayout: () -> Unit,
) {
    val prefs = app.prefs
    val context = LocalContext.current

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    fun openProject() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SyHugh7/PadLink"))
            )
        }.onFailure {
            Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsScaffold(title = SettingsRoute.HOME.title) {
        SettingsPageTitle("设置")

        SettingsSection("个性化")
        CardStack {
            NavCard(
                title = "主题设置",
                subtitle = "深浅模式与主题色",
                icon = PaletteIcon,
                onClick = { onOpen(SettingsRoute.THEME) },
            )
        }

        SettingsSection("按键")
        CardStack {
            NavCard(
                title = "扳机设置",
                subtitle = "线性 / 按钮、弱加载区、宽度与长度",
                icon = Icons.Filled.Edit,
                onClick = { onOpen(SettingsRoute.TRIGGER) },
            )
            NavCard(
                title = "十字键设置",
                subtitle = "8 向或四分",
                icon = Icons.Filled.Menu,
                onClick = { onOpen(SettingsRoute.DPAD) },
            )
            NavCard(
                title = "重力转向",
                subtitle = "倾斜手机控制左摇杆",
                icon = Icons.Filled.Refresh,
                onClick = { onOpen(SettingsRoute.TILT) },
            )
            NavCard(
                title = "震动设置",
                subtitle = "按下反馈与强度",
                icon = VibrationIcon,
                onClick = { onOpen(SettingsRoute.HAPTIC) },
            )
            NavCard(
                title = "调整布局",
                subtitle = "回到手柄界面拖动控件",
                icon = Icons.Filled.Edit,
                onClick = onAdjustLayout,
            )
        }

        SettingsSection("应用")
        CardStack {
            SwitchCard(
                title = "退出前台时冻结",
                subtitle = "离开界面时归零输入",
                icon = Icons.Filled.Lock,
                checked = prefs.freezeOnBackground,
                onChange = { prefs.freezeOnBackground = it },
            )
            SwitchCard(
                title = "前台时禁用状态栏",
                subtitle = when {
                    !app.statusBar.available -> "需要安装 Dhizuku"
                    !app.statusBar.granted -> "开启时会请求 Dhizuku 授权"
                    else -> "前台时隐藏系统状态栏"
                },
                icon = Icons.Filled.Menu,
                checked = prefs.disableStatusBar,
                enabled = app.statusBar.available,
                onChange = { app.statusBar.setEnabled(it) },
            )
        }

        SettingsSection("其它")
        CardStack {
            DisabledCard(
                title = "选择布局",
                subtitle = "还没做",
                icon = Icons.AutoMirrored.Filled.List,
            )
            NavCard(
                title = "前往项目",
                subtitle = "给项目点个 Star 吧，这是对我最大的鼓励",
                icon = Icons.Filled.Star,
                onClick = { openProject() },
            )
            SettingsCard(
                title = "PadLink $versionName",
                subtitle = "Android 客户端",
                icon = Icons.Filled.Info,
            )
        }

        SettingsPageBottomSpacer()
    }
}

// ------------------------------------------------------------------ 子页面

@Composable
private fun ThemePage(app: PadLinkApplication, onBack: () -> Unit) {
    val prefs = app.prefs
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val previews = remember(dark) {
        AccentColor.entries.associateWith { color ->
            if (color == AccentColor.DYNAMIC) null else color.previewPrimary(dark)
        }
    }

    SettingsScaffold(title = SettingsRoute.THEME.title, onBack = onBack) {
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            CardLabel("主题模式")
            ChoiceRow(
                values = ThemeMode.entries,
                selected = prefs.themeMode,
                label = { it.label },
                onSelect = { prefs.themeMode = it },
            )

            CardDivider()

            CardLabel("主题色")
            ColorDotRow(
                values = AccentColor.entries,
                selected = prefs.accentColor,
                label = { it.label },
                swatch = { color -> previews[color] },
                onSelect = { prefs.accentColor = it },
            )
        }

        SettingsPageBottomSpacer()
    }
}

@Composable
private fun TriggerPage(app: PadLinkApplication, onBack: () -> Unit) {
    val layout = app.layout
    val triggerWidth = layout.element(PadElementKind.TRIGGER_LEFT)?.size ?: 0.06f

    SettingsScaffold(title = SettingsRoute.TRIGGER.title, onBack = onBack) {
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            CardLabel("形式")
            ChoiceRow(
                values = listOf(TriggerStyle.SLIDE, TriggerStyle.BUTTON),
                selected = layout.triggerStyle,
                label = { if (it == TriggerStyle.SLIDE) "滑动条" else "按钮" },
                onSelect = { app.updateLayout(layout.copy(triggerStyle = it)) },
            )

            if (layout.triggerStyle == TriggerStyle.SLIDE) {
                CardDivider()

                SliderRow(
                    label = "弱加载区",
                    valueText = "%.0f%%".format(layout.triggerGate * 100f),
                    value = layout.triggerGate,
                    range = PadLayout.MIN_TRIGGER_GATE..PadLayout.MAX_TRIGGER_GATE,
                    onDrag = { app.updateLayout(layout.copy(triggerGate = it), persist = false) },
                    onCommit = { app.flushLayout() },
                )

                SliderRow(
                    label = "宽度",
                    valueText = "%.0f%%".format(triggerWidth * 100f),
                    value = triggerWidth,
                    range = PadLayout.MIN_SIZE..0.2f,
                    onDrag = {
                        app.updateLayout(
                            layout.resize(PadElementKind.TRIGGER_LEFT, it)
                                .resize(PadElementKind.TRIGGER_RIGHT, it),
                            persist = false,
                        )
                    },
                    onCommit = { app.flushLayout() },
                )

                SliderRow(
                    label = "长度",
                    valueText = "%.1f".format(layout.triggerAspect),
                    value = layout.triggerAspect,
                    range = PadLayout.MIN_TRIGGER_ASPECT..PadLayout.MAX_TRIGGER_ASPECT,
                    onDrag = { app.updateLayout(layout.copy(triggerAspect = it), persist = false) },
                    onCommit = { app.flushLayout() },
                )
            }
        }

        SettingsPageBottomSpacer()
    }
}

@Composable
private fun DPadPage(app: PadLinkApplication, onBack: () -> Unit) {
    val layout = app.layout

    SettingsScaffold(title = SettingsRoute.DPAD.title, onBack = onBack) {
        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            CardLabel("样式")
            ChoiceRow(
                values = listOf(DPadStyle.COMBO, DPadStyle.TRIANGLE),
                selected = layout.dpadStyle,
                label = { if (it == DPadStyle.COMBO) "8 向" else "四分" },
                onSelect = { app.updateLayout(layout.copy(dpadStyle = it)) },
            )
        }

        SettingsPageBottomSpacer()
    }
}

@Composable
private fun TiltPage(app: PadLinkApplication, onBack: () -> Unit) {
    val controller = app.controller
    val tilt = controller.tiltSettings

    SettingsScaffold(title = SettingsRoute.TILT.title, onBack = onBack) {
        Spacer(Modifier.height(8.dp))

        SwitchCard(
            title = "重力转向",
            subtitle = if (controller.tiltAvailable) "倾斜手机控制左摇杆" else "这台机器没有陀螺仪",
            icon = Icons.Filled.Refresh,
            checked = controller.tiltEnabled,
            enabled = controller.tiltAvailable,
            onChange = { controller.enableTilt(it) },
        )

        if (controller.tiltEnabled) {
            Spacer(Modifier.height(8.dp))

            SettingsCardGroup {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedButton(onClick = { controller.recenterTilt() }) {
                        Text("重定中心")
                    }
                }

                CardDivider()

                SliderRow(
                    label = "死区",
                    valueText = "%.1f°".format(tilt.deadZoneDeg),
                    value = tilt.deadZoneDeg,
                    range = 0f..6f,
                    onDrag = { controller.applyTiltSettings(tilt.copy(deadZoneDeg = it), false) },
                    onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                )
                SliderRow(
                    label = "满舵角",
                    valueText = "%.0f°".format(tilt.maxAngleDeg),
                    value = tilt.maxAngleDeg,
                    range = 15f..60f,
                    onDrag = { controller.applyTiltSettings(tilt.copy(maxAngleDeg = it), false) },
                    onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                )
                SliderRow(
                    label = "曲线",
                    valueText = "%.2f".format(tilt.curve),
                    value = tilt.curve,
                    range = 0.2f..3f,
                    onDrag = { controller.applyTiltSettings(tilt.copy(curve = it), false) },
                    onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                )
                SliderRow(
                    label = "平滑",
                    valueText = "%.2f".format(tilt.smoothing),
                    value = tilt.smoothing,
                    range = 0.05f..0.8f,
                    onDrag = { controller.applyTiltSettings(tilt.copy(smoothing = it), false) },
                    onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                )
                SliderRow(
                    label = "恢复延时",
                    valueText = "%.1fs".format(tilt.stickHoldSec),
                    value = tilt.stickHoldSec,
                    range = 0f..3f,
                    onDrag = { controller.applyTiltSettings(tilt.copy(stickHoldSec = it), false) },
                    onCommit = { controller.applyTiltSettings(controller.tiltSettings, true) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        DisabledCard(
            title = "PS4 全局体感",
            subtitle = "还没做",
            icon = Icons.Filled.Refresh,
        )

        SettingsPageBottomSpacer()
    }
}

@Composable
private fun HapticPage(app: PadLinkApplication, onBack: () -> Unit) {
    val prefs = app.prefs

    SettingsScaffold(title = SettingsRoute.HAPTIC.title, onBack = onBack) {
        Spacer(Modifier.height(8.dp))

        SwitchCard(
            title = "按钮按下反馈",
            subtitle = "直接驱动马达，不受系统触感开关影响",
            icon = VibrationIcon,
            checked = prefs.hapticFeedback,
            onChange = {
                prefs.hapticFeedback = it
                if (it) app.haptics.tick()
            },
        )

        Spacer(Modifier.height(8.dp))

        SettingsCardGroup {
            SliderRow(
                label = "震动强度",
                valueText = "%.1f×".format(prefs.hapticScale),
                value = prefs.hapticScale,
                range = 0.5f..2f,
                steps = 14,
                onDrag = { prefs.hapticScale = it },
                onCommit = {},
                enabled = prefs.hapticFeedback,
            )
        }

        Spacer(Modifier.height(8.dp))

        SwitchCard(
            title = "游戏震动",
            subtitle = "接收 PC 回传的手柄震动",
            icon = VibrationIcon,
            checked = prefs.rumbleEnabled,
            onChange = { prefs.rumbleEnabled = it },
        )

        SettingsPageBottomSpacer()
    }
}

/** 一组卡片之间的统一间距。 */
@Composable
private fun CardStack(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
