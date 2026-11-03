package com.padlink.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.padlink.app.layout.PadLayout
import com.padlink.app.ui.ConfirmDialog
import com.padlink.app.ui.ElementSettingsDialog
import com.padlink.app.ui.ExportDialog
import com.padlink.app.ui.GamepadCanvas
import com.padlink.app.ui.QuickPanel
import com.padlink.app.ui.Selection
import com.padlink.app.ui.TopBar
import com.padlink.app.ui.settings.SettingsActivity
import com.padlink.app.ui.theme.PadLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 手柄界面得一直亮着，也不能被状态栏占掉空间。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val app = application as PadLinkApplication
            PadLinkTheme(app.prefs) {
                PadLinkApp(app)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as PadLinkApplication).statusBar.onForeground()
    }

    override fun onStop() {
        val app = application as PadLinkApplication
        app.statusBar.onBackground()
        super.onStop()
        // 切走时主动归零，别让 PC 端还顶着油门。
        if (app.prefs.freezeOnBackground) app.controller.freezeInput()
    }

    override fun onDestroy() {
        // 真退出才收资源。旋转或系统重建时 controller 还要接着用 —— 它是 Application 级的。
        if (isFinishing) (application as PadLinkApplication).controller.release()
        super.onDestroy()
    }
}

@Composable
private fun PadLinkApp(app: PadLinkApplication) {
    val context = LocalContext.current
    val controller = app.controller
    val layout = app.layout

    // 按下反馈：直接驱动马达。系统「触感反馈」关着也能响，不依赖 performHapticFeedback。
    LaunchedEffect(Unit) {
        controller.onHaptic = { duration ->
            if (app.prefs.hapticFeedback) app.haptics.tick(duration)
        }
    }

    var editing by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var showQuickPanel by remember { mutableStateOf(false) }

    var elementSettings by remember { mutableStateOf<Selection?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // 设置页里点了「调整布局」：那边只置个标志，回来时在这儿接上。
    LaunchedEffect(app.pendingLayoutEdit) {
        if (app.pendingLayoutEdit) {
            editing = true
            selection = null
            app.pendingLayoutEdit = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        TopBar(
            controller = controller,
            editing = editing,
            selection = selection,
            onOpenSettings = { showQuickPanel = true },
            onOpenElementSettings = { selection?.let { elementSettings = it } },
            onToggleEnabled = {
                app.updateLayout(
                    when (val current = selection) {
                        is Selection.Element -> layout.toggleEnabled(current.kind)
                        is Selection.Sub -> layout.updateSubKey(current.kind, current.index) {
                            it.copy(enabled = !it.enabled)
                        }

                        null -> layout
                    }
                )
            },
            onReset = { showResetConfirm = true },
            onExport = { showExport = true },
            onDone = {
                editing = false
                selection = null
                // 编辑过程中每次拖动都已经存过了，这里不用再存。
            },
            // 画布是后加的兄弟节点，会盖在顶栏上面；编辑时顶栏必须抬到最高层，
            // 否则右上角那几个按钮会被手柄控件挡住、点不到。
            modifier = Modifier.zIndex(1f),
        )

        GamepadCanvas(
            controller = controller,
            layout = layout,
            editing = editing,
            selection = selection,
            onSelectionChange = { selection = it },
            onLayoutChange = { app.updateLayout(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showQuickPanel) {
        QuickPanel(
            controller = controller,
            onAdjustLayout = {
                editing = true
                selection = null
            },
            onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            onDismiss = { showQuickPanel = false },
        )
    }

    elementSettings?.let { target ->
        ElementSettingsDialog(
            selection = target,
            layout = layout,
            onDismiss = { elementSettings = null },
            onApply = {
                app.updateLayout(it)
                elementSettings = null
            },
        )
    }

    if (showExport) {
        ExportDialog(
            layout = layout,
            onDismiss = { showExport = false },
            onImport = { imported ->
                app.updateLayout(imported)
                showExport = false
            },
        )
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "重置布局",
            message = "所有控件的位置、大小、角度都恢复默认。",
            confirmText = "重置",
            onConfirm = {
                app.updateLayout(PadLayout.Default)
                selection = null
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    // ADB 模式的地址是固定的 127.0.0.1，插着线打开就该能用。
    LaunchedEffect(Unit) {
        if (controller.mode == TransportMode.ADB && !controller.isConnected) controller.connect()
    }
}
