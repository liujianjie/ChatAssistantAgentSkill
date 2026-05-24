package com.stylemirror.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.stylemirror.app.CandidateItem

/**
 * A single candidate card showing the suggested reply with Adopt / Modify / Discard actions.
 *
 * [onModify] receives the edited text after the user confirms in the inline
 * edit field; [onAdopt] signals the text was used as-is; [onDiscard] dismisses.
 */
@Composable
fun CandidateCard(
    item: CandidateItem,
    onAdopt: (CandidateItem) -> Unit,
    onModify: (CandidateItem, String) -> Unit,
    onDiscard: (CandidateItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(item.id) { mutableStateOf(item.candidate.text) }
    var confirmingDiscard by remember(item.id) { mutableStateOf(false) }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("丢弃这条候选？") },
            text = { Text("丢弃后会作为反馈记录到画像调优。可以撤销吗？不可。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDiscard = false
                    onDiscard(item)
                }) { Text("确认丢弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) { Text("取消") }
            },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (editing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("修改回复") },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        editing = false
                        editText = item.candidate.text
                    }) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editText.isNotBlank()) {
                                onModify(item, editText)
                                editing = false
                            }
                        },
                    ) {
                        Text("确认")
                    }
                }
            } else {
                Text(text = item.candidate.text, style = MaterialTheme.typography.bodyMedium)
                item.candidate.styleMatchScore?.let { score ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "风格匹配 ${(score * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAdopt(item) }, modifier = Modifier.weight(1f)) {
                        Text("采纳")
                    }
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                        Text("修改")
                    }
                    OutlinedButton(onClick = { confirmingDiscard = true }, modifier = Modifier.weight(1f)) {
                        Text("丢弃")
                    }
                }
            }
        }
    }
}
