package com.stylemirror.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stylemirror.app.onboarding.OnboardingState
import com.stylemirror.app.onboarding.Stage
import com.stylemirror.app.onboarding.StyleFingerprintSummary

/**
 * Three-step onboarding shell. Single composable entry — caller decides the
 * navigation transition into the main screen via [onFinish].
 *
 * Steps:
 *   1. AskAliases — user types one or more nicknames they go by.
 *   2. AskCorpus — user pastes plain-text chat history.
 *   3. Working / Ready / Error — pipeline progress and review.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    aliases: String,
    pasteText: String,
    onAliasesChange: (String) -> Unit,
    onPasteChange: (String) -> Unit,
    onConfirmAliases: () -> Unit,
    onBackToAliases: () -> Unit,
    onRunProfiling: () -> Unit,
    onPickTextFile: () -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text("欢迎使用风格镜像", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "我们将基于你历史聊天中的发言，提取你的说话风格。\n仅用于本机生成候选回复，不会上传到云端用于训练。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        when (state) {
            OnboardingState.AskAliases ->
                AskAliasesStep(
                    aliases = aliases,
                    onAliasesChange = onAliasesChange,
                    onNext = onConfirmAliases,
                )

            OnboardingState.AskCorpus ->
                AskCorpusStep(
                    pasteText = pasteText,
                    onPasteChange = onPasteChange,
                    onBack = onBackToAliases,
                    onPickTextFile = onPickTextFile,
                    onNext = onRunProfiling,
                )

            is OnboardingState.Working ->
                WorkingStep(stage = state.stage)

            is OnboardingState.Ready ->
                ReadyStep(summary = state.summary, onFinish = onFinish)

            is OnboardingState.Error ->
                ErrorStep(message = state.message, onRetry = onRetry)
        }
    }
}

@Composable
private fun AskAliasesStep(
    aliases: String,
    onAliasesChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text("第 1 步 / 共 2 步：指认你自己", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "在你导入的聊天记录里，你会以哪些昵称出现？多个用逗号分隔。\n" +
            "示例：\"我, 张三, 小张, 阿张\"。",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = aliases,
        onValueChange = onAliasesChange,
        label = { Text("我的昵称（可多个）") },
        placeholder = { Text("我, 小张") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onNext,
        enabled = aliases.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("下一步")
    }
}

@Composable
private fun AskCorpusStep(
    pasteText: String,
    onPasteChange: (String) -> Unit,
    onBack: () -> Unit,
    onPickTextFile: () -> Unit,
    onNext: () -> Unit,
) {
    Text("第 2 步 / 共 2 步：粘贴聊天记录", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "支持以下格式（自动识别）：\n" +
            "  • 我：你好\n" +
            "  • 2024-01-15 14:30 我：你好\n" +
            "建议至少粘贴 30 条以上你自己的发言，画像才会准确。",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.OutlinedButton(
        onClick = onPickTextFile,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("从文件导入（.txt / .md / .html / .docx / .pdf）")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = pasteText,
        onValueChange = onPasteChange,
        label = { Text("粘贴聊天文本") },
        placeholder = { Text("我：刚下班\n张三：吃饭了吗\n我：还没") },
        modifier = Modifier.fillMaxWidth().height(240.dp),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Button(
            onClick = onNext,
            enabled = pasteText.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text("开始生成画像")
        }
    }
}

@Composable
private fun WorkingStep(stage: Stage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stage.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadyStep(
    summary: StyleFingerprintSummary,
    onFinish: () -> Unit,
) {
    Text("画像已生成", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "基于你的 ${summary.sampleCount} 条发言，6 维风格画像如下：",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DimensionRow(label = "语言风格", body = summary.linguistic)
            DimensionRow(label = "情感表达", body = summary.emotional)
            DimensionRow(label = "幽默类型", body = summary.humor)
            DimensionRow(label = "回避模式", body = summary.avoidance)
            DimensionRow(label = "节奏特征", body = summary.pacing)
            DimensionRow(label = "敏感话题", body = summary.sensitive)
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text("开始使用")
    }
}

@Composable
private fun DimensionRow(
    label: String,
    body: String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorStep(
    message: String,
    onRetry: () -> Unit,
) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重新开始")
    }
}
