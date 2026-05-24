package com.stylemirror.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stylemirror.app.CandidateItem
import com.stylemirror.app.GenerateState

/**
 * Main screen: paste conversation, generate candidates, and handle feedback.
 *
 * Uses [GenerateState] for the async candidate generation lifecycle.
 * Feedback actions delegate back to the ViewModel via the provided lambdas so
 * this composable stays pure and Preview-friendly.
 */
@Composable
fun MainScreen(
    pasteText: String,
    generateState: GenerateState,
    onPasteChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onAdopt: (CandidateItem) -> Unit,
    onModify: (CandidateItem, String) -> Unit,
    onDiscard: (CandidateItem) -> Unit,
    onOpenSettings: () -> Unit,
    onReprofile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("风格镜像", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onReprofile) { Text("重新画像") }
                TextButton(onClick = onOpenSettings) { Text("设置") }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pasteText,
            onValueChange = onPasteChange,
            label = { Text("粘贴对话内容") },
            placeholder = { Text("我：你好\n对方：在吗") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            maxLines = 10,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onGenerate,
            enabled = pasteText.isNotBlank() && generateState !is GenerateState.Generating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("生成候选回复")
        }

        Spacer(Modifier.height(16.dp))

        when (generateState) {
            is GenerateState.Idle -> Unit

            is GenerateState.Generating ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is GenerateState.Error ->
                Text(
                    text = generateState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )

            is GenerateState.Ready -> {
                Text("候选回复", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(generateState.items, key = { it.id.value }) { item ->
                        CandidateCard(
                            item = item,
                            onAdopt = onAdopt,
                            onModify = onModify,
                            onDiscard = onDiscard,
                        )
                    }
                }
            }
        }
    }
}
