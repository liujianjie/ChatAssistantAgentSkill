package com.stylemirror.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stylemirror.app.ProfileIoState

/**
 * Settings screen: API key entry + profile export/import (P9).
 *
 * [apiKeyHint] is a masked display of the currently stored key (e.g. "sk-a…x9k2"),
 * or empty when no key is stored. The raw key is never surfaced here.
 */
@Composable
@Suppress("LongParameterList")
fun SettingsScreen(
    apiKeyHint: String,
    profileIoState: ProfileIoState,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onExportProfile: () -> Unit,
    onImportProfile: () -> Unit,
    onDismissProfileIo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Spacer(Modifier.height(16.dp))

        Text("API Key 设置", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (apiKeyHint.isNotEmpty()) {
            Text(
                text = "已保存：$apiKeyHint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onClear) { Text("清除", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("DeepSeek API Key") },
            placeholder = { Text("sk-…") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(draft)
                draft = ""
            },
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("画像备份", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "导出后保管好 JSON 文件，重装/换机时可导入恢复（不含原始聊天，仅画像本身）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onExportProfile,
            enabled = profileIoState !is ProfileIoState.Working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("导出画像")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImportProfile,
            enabled = profileIoState !is ProfileIoState.Working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("导入画像")
        }

        when (profileIoState) {
            ProfileIoState.Idle -> Unit
            ProfileIoState.Working -> {
                Spacer(Modifier.height(8.dp))
                Text("处理中…", style = MaterialTheme.typography.bodySmall)
            }
            is ProfileIoState.Success -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    profileIoState.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onDismissProfileIo) { Text("好的") }
            }
            is ProfileIoState.Error -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    profileIoState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onDismissProfileIo) { Text("关闭") }
            }
        }
    }
}
