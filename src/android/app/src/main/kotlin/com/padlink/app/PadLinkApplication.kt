package com.padlink.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.padlink.app.input.TiltSource
import com.padlink.app.input.TiltStore
import com.padlink.app.dhizuku.DhizukuStatusBar
import com.padlink.app.layout.LayoutStore
import com.padlink.app.layout.PadLayout
import com.padlink.app.pref.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 进程级单例：连接、布局、设置都存在这儿。
 *
 * 提到 Application 是因为设置页是另一个 Activity —— 两边要看同一份连接状态、同一个布局。
 * 顺带还有个好处：界面重建不会把连接和传感器一起收掉。
 */
class PadLinkApplication : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var controller: PadLinkController
        private set

    lateinit var prefs: AppPrefs
        private set

    /** Dhizuku 状态栏策略：请求权限、在前台禁用、离开后恢复。 */
    lateinit var statusBar: DhizukuStatusBar
        private set

    private lateinit var layoutStore: LayoutStore

    /** 当前布局。两个 Activity 共用这一份，改了就存盘。 */
    var layout: PadLayout by mutableStateOf(PadLayout.Default)
        private set

    /** 设置页里点了「调整布局」时置位；主界面看到就进编辑模式。 */
    var pendingLayoutEdit by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        layoutStore = LayoutStore(this)
        layout = layoutStore.load()
        controller = PadLinkController(scope, TiltSource(this), TiltStore(this))
        statusBar = DhizukuStatusBar(this, prefs)
        statusBar.prepare()
    }

    fun updateLayout(next: PadLayout) {
        layout = next
        layoutStore.save(next)
    }
}
