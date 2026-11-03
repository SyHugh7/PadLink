package com.padlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.padlink.app.PadLinkController

/**
 * 主界面点「设置」弹出的快捷面板。
 *
 * 只放最常用的四个：连接、重力、调布局、进完整设置。连接方式（ADB / WiFi）不在这里 ——
 * 那是 PC 端握手时定的，手机端不该再选一次。
 */
@Composable
fun QuickPanel(
    controller: PadLinkController,
    onAdjustLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.width(260.dp).padding(vertical = 6.dp)) {
                SwitchRow(
                    label = "连接状态",
                    checked = controller.isConnected,
                    status = if (controller.isConnected) "已连接" else "未连接",
                ) { on ->
                    if (on) controller.connect() else controller.disconnect()
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                SwitchRow("重力转向", controller.tiltEnabled, enabled = controller.tiltAvailable) { on ->
                    controller.enableTilt(on)
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { onDismiss(); onAdjustLayout() }) {
                        Text("调整布局", fontSize = 13.sp)
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                TextButton(
                    onClick = { onDismiss(); onOpenSettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("全部设置", fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(">", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    status: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = status,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
