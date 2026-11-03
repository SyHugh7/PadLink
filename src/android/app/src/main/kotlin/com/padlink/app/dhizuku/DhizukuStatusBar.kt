package com.padlink.app.dhizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.padlink.app.pref.AppPrefs
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs

/**
 * 「前台时禁用状态栏」的总控。
 *
 * 走 Dhizuku 的 UserService：把 [PadLinkUserService] 加载到 Dhizuku（设备所有者）进程里，
 * 由它调用系统的 `setStatusBarDisabled()`。普通应用没有设备所有者权限，直接调会 SecurityException。
 *
 * 引用计数是给两个 Activity 用的：主界面跳设置页时，MainActivity 先 stop、
 * SettingsActivity 再 start，如果一 stop 就恢复，状态栏会在两屏之间闪一下。
 * 所以最后一个前台 Activity 离开后延迟 [RESTORE_DELAY_MS] 才恢复。
 *
 * 状态栏策略是**全局**的，必须保证离开应用时恢复。进程被杀时 onStop 不会跑，
 * 所以 [prepare] 会在下次启动补一次恢复（[AppPrefs.statusBarApplied] 是那条保险丝）。
 */
class DhizukuStatusBar(
    private val context: Context,
    private val prefs: AppPrefs,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var service: IPadLinkUserService? = null
    private var binding = false
    private var pending: Boolean? = null
    private var foreground = 0

    /** 设备上有没有可用的 Dhizuku。没有时开关应置灰。 */
    var available by mutableStateOf(false)
        private set

    /** 用户有没有在 Dhizuku 里给本应用授权。 */
    var granted by mutableStateOf(false)
        private set

    /** 最近一次系统调用的错误，只用于日志／排查，不往界面上堆。 */
    var lastError by mutableStateOf<String?>(null)
        private set

    private val restore = Runnable { applyState(false) }

    /** Application 启动时调一次。 */
    fun prepare() {
        available = runCatching { Dhizuku.init(context) }.getOrDefault(false)
        granted = available && runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)

        // 上次进程被强杀时可能没来得及恢复；这次启动补上。
        if (available && granted && prefs.statusBarApplied && !prefs.disableStatusBar) {
            applyState(false)
        }
    }

    /** Activity.onStart。 */
    fun onForeground() {
        foreground++
        handler.removeCallbacks(restore)
        if (prefs.disableStatusBar) applyState(true)
    }

    /** Activity.onStop。最后一个前台 Activity 走掉后稍等再恢复。 */
    fun onBackground() {
        foreground = (foreground - 1).coerceAtLeast(0)
        if (foreground == 0 && prefs.disableStatusBar) {
            handler.removeCallbacks(restore)
            handler.postDelayed(restore, RESTORE_DELAY_MS)
        }
    }

    /**
     * 设置页开关。未授权时会拉起 Dhizuku 的授权界面；授权成功后才真正打开。
     */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            prefs.disableStatusBar = false
            handler.removeCallbacks(restore)
            applyState(false)
            return
        }

        if (!available) return
        if (granted) {
            prefs.disableStatusBar = true
            applyState(true)
            return
        }

        requestPermission { ok ->
            granted = ok
            if (ok) {
                prefs.disableStatusBar = true
                applyState(true)
            }
        }
    }

    private fun requestPermission(onResult: (Boolean) -> Unit) {
        runCatching {
            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                override fun onRequestPermission(grantResult: Int) {
                    handler.post { onResult(grantResult == PackageManager.PERMISSION_GRANTED) }
                }
            })
        }.onFailure {
            Log.w(TAG, "请求 Dhizuku 权限失败", it)
            handler.post { onResult(false) }
        }
    }

    private fun applyState(disabled: Boolean) {
        val current = service
        if (current != null) {
            runCatching { current.setStatusBarDisabled(disabled) }
                .onSuccess {
                    prefs.statusBarApplied = disabled
                    lastError = null
                }
                .onFailure {
                    lastError = it.message
                    Log.w(TAG, "设置状态栏失败", it)
                }
            return
        }

        pending = disabled
        bind()
    }

    private fun bind() {
        if (binding || service != null || !available) return
        binding = true

        val args = DhizukuUserServiceArgs(ComponentName(context, PadLinkUserService::class.java))
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                binding = false
                service = binder?.let { IPadLinkUserService.Stub.asInterface(it) }
                val next = pending
                pending = null
                if (next != null) applyState(next)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binding = false
                service = null
            }
        }

        runCatching { Dhizuku.bindUserService(args, connection) }
            .onFailure {
                binding = false
                lastError = it.message
                Log.w(TAG, "绑定 Dhizuku UserService 失败", it)
            }
    }

    private companion object {
        const val TAG = "PadLinkDhizuku"
        const val RESTORE_DELAY_MS = 600L
    }
}
