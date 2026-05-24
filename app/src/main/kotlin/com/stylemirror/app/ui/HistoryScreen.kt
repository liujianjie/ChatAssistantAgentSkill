package com.stylemirror.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stylemirror.app.history.HistoryItem
import com.stylemirror.app.history.RollbackState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    items: List<HistoryItem>,
    rollback: RollbackState,
    onRollback: (version: Int) -> Unit,
    onDismissRollback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRollback by remember { mutableStateOf<HistoryItem?>(null) }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("画像版本历史", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("返回") }
        }

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                text = "暂无画像版本，先去导入聊天记录建画像吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.version }) { item ->
                    HistoryRow(
                        item = item,
                        onRollback = { pendingRollback = item },
                    )
                }
            }
        }
    }

    pendingRollback?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text("回滚到版本 v${target.version}？") },
            text = {
                Text("将基于该版本生成一个新的最新版本，原历史不会被删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRollback(target.version)
                    pendingRollback = null
                }) { Text("确认回滚") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) { Text("取消") }
            },
        )
    }

    when (rollback) {
        is RollbackState.Done ->
            AlertDialog(
                onDismissRequest = onDismissRollback,
                title = { Text("回滚成功") },
                text = { Text("已生成新版本 v${rollback.newVersion}，主页将使用该画像。") },
                confirmButton = { TextButton(onClick = onDismissRollback) { Text("好的") } },
            )

        is RollbackState.Error ->
            AlertDialog(
                onDismissRequest = onDismissRollback,
                title = { Text("回滚失败") },
                text = { Text(rollback.message) },
                confirmButton = { TextButton(onClick = onDismissRollback) { Text("关闭") } },
            )

        else -> Unit
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onRollback: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "v${item.version}${if (item.isLatest) "（当前）" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "${formatDate(item.createdAtEpochMs)} · 样本 ${item.sampleSize} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!item.isLatest) {
                    TextButton(onClick = onRollback) { Text("回滚到此版本") }
                }
            }
            item.summary?.let { s ->
                Spacer(Modifier.height(6.dp))
                Text(text = "语言：${s.linguistic}", style = MaterialTheme.typography.bodySmall)
                Text(text = "情感：${s.emotional}", style = MaterialTheme.typography.bodySmall)
                Text(text = "节奏：${s.pacing}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
