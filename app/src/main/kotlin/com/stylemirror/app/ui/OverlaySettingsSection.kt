package com.stylemirror.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stylemirror.feature.overlay.config.OverlayConfigStore
import com.stylemirror.feature.overlay.config.OverlayPermissionProbe
import com.stylemirror.feature.overlay.service.FloatingBubbleService
import com.stylemirror.feature.overlay.ui.BubbleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * P1.c overlay settings block injected into [SettingsScreen]'s slot. Lives
 * in the app module (not feature-overlay) because it directly drives the
 * Activity-scoped service start/stop and system-settings intents — those
 * concerns belong with the host, not with the overlay feature itself.
 *
 * Permission state is re-probed on every ON_RESUME so a detour into system
 * Settings reflects back without the user needing to reopen the screen.
 */
@Composable
fun OverlaySettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { OverlayConfigStore(context) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(store.enabled) }
    var soulPackage by remember { mutableStateOf(store.soulPackageName) }
    var bubbleStyle by remember { mutableStateOf(store.bubbleStyle) }
    var canDrawOverlays by remember { mutableStateOf(OverlayPermissionProbe.canDrawOverlays(context)) }
    var accessibilityOn by remember {
        mutableStateOf(
            OverlayPermissionProbe.isAccessibilityServiceEnabled(context, ACCESSIBILITY_SERVICE_CLASS),
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            canDrawOverlays = OverlayPermissionProbe.canDrawOverlays(context)
            accessibilityOn = OverlayPermissionProbe.isAccessibilityServiceEnabled(context, ACCESSIBILITY_SERVICE_CLASS)
        }
    }

    Column(modifier = modifier) {
        Text("悬浮窗副驾", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "在 Soul 内打开对话时显示悬浮气泡，点气泡才生成 3 候选。首次启用前请阅读权限引导：docs/p1c-permission-setup.md",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("总开关", modifier = Modifier.padding(end = 12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    store.enabled = checked
                    if (checked && canDrawOverlays && accessibilityOn) {
                        FloatingBubbleService.start(context)
                    } else if (!checked) {
                        FloatingBubbleService.stop(context)
                    }
                },
                enabled = canDrawOverlays && accessibilityOn,
            )
        }
        if (!canDrawOverlays || !accessibilityOn) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "需要先授予下面两个权限，总开关才能打开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))

        PermissionRow(
            label = "无障碍服务",
            granted = accessibilityOn,
            buttonText = "前往无障碍设置",
            onClick = { openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS, withPackageUri = false) },
        )
        Spacer(Modifier.height(8.dp))
        PermissionRow(
            label = "在其他应用上方显示",
            granted = canDrawOverlays,
            buttonText = "前往悬浮窗权限",
            onClick = { openSystemSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackageUri = true) },
        )
        Spacer(Modifier.height(16.dp))

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("气泡形态（A/B 试用，最终版 T30.9 定）", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            BubbleStyleChip(
                label = "圆形气泡",
                selected = bubbleStyle == BubbleStyle.CIRCLE,
                onClick = {
                    bubbleStyle = BubbleStyle.CIRCLE
                    store.bubbleStyle = BubbleStyle.CIRCLE
                    if (enabled && canDrawOverlays) restartService(context, scope)
                },
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            BubbleStyleChip(
                label = "侧边折叠条",
                selected = bubbleStyle == BubbleStyle.SIDE_STRIP,
                onClick = {
                    bubbleStyle = BubbleStyle.SIDE_STRIP
                    store.bubbleStyle = BubbleStyle.SIDE_STRIP
                    if (enabled && canDrawOverlays) restartService(context, scope)
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = soulPackage,
            onValueChange = { soulPackage = it },
            label = { Text("Soul 包名（默认 ${OverlayConfigStore.DEFAULT_SOUL_PACKAGE}）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { store.soulPackageName = soulPackage },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存包名")
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (granted) "✓ $label" else "✗ $label",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(buttonText)
        }
    }
}

@Composable
private fun BubbleStyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(if (selected) "● $label" else label) },
    )
}

private fun openSystemSettings(
    context: Context,
    action: String,
    withPackageUri: Boolean,
) {
    val intent =
        Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (withPackageUri) data = Uri.parse("package:${context.packageName}")
        }
    runCatching { context.startActivity(intent) }
}

private fun restartService(
    context: Context,
    scope: CoroutineScope,
) {
    FloatingBubbleService.stop(context)
    scope.launch {
        delay(SERVICE_RESTART_DELAY_MS)
        FloatingBubbleService.start(context)
    }
}

private const val ACCESSIBILITY_SERVICE_CLASS =
    "com.stylemirror.feature.overlay.service.StyleMirrorAccessibilityService"
private const val SERVICE_RESTART_DELAY_MS = 50L
