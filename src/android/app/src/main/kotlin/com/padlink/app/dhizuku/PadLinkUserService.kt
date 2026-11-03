package com.padlink.app.dhizuku

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * 由 Dhizuku 反射加载、在 **Dhizuku 进程**里运行的 UserService。
 *
 * 这里能调 `DevicePolicyManager.setStatusBarDisabled()`，是因为 Dhizuku 自己是设备所有者；
 * 普通应用进程调这个 API 会直接 SecurityException。类必须有 `(Context)` 构造器 ——
 * Dhizuku 的服务端就是这么实例化它的。
 */
class PadLinkUserService(context: Context) : IPadLinkUserService.Stub() {

    private val manager: DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    private val owner: ComponentName? = manager?.let { policy ->
        policy.activeAdmins?.firstOrNull { admin ->
            policy.isDeviceOwnerApp(admin.packageName) || policy.isProfileOwnerApp(admin.packageName)
        }
    }

    @SuppressLint("MissingPermission")
    override fun setStatusBarDisabled(disabled: Boolean) {
        val policy = manager ?: error("DevicePolicyManager 不可用")
        val admin = owner ?: error("Dhizuku 不是设备所有者")
        if (!policy.setStatusBarDisabled(admin, disabled)) {
            error("系统拒绝了状态栏策略")
        }
        Log.i(TAG, "setStatusBarDisabled($disabled) via $admin")
    }

    @SuppressLint("MissingPermission")
    override fun isStatusBarDisabled(): Boolean =
        manager?.isStatusBarDisabled() ?: false

    private companion object {
        const val TAG = "PadLinkDhizuku"
    }
}
