package com.stylemirror.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stylemirror.domain.candidate.Candidate
import com.stylemirror.feature.overlay.candidate.OverlayCandidateController.UiState

/**
 * Expanded candidate panel rendered when the bubble is tapped. Stays
 * compact — anchored beside the bubble, scrollable internally if the LLM
 * happens to return long candidates.
 *
 * **Why no Material Card / surface elevation**
 *
 * Elevation in Compose draws a shadow with [android.graphics.Path] that
 * the WindowManager overlay layer renders unreliably across OEMs. A plain
 * background + corner radius gets us 99% of the visual polish without any
 * cross-OEM rendering quirks.
 */
@Composable
internal fun CandidatePanel(
    state: UiState,
    onCopy: (Candidate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(min = 280.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "风格镜像副驾",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        StateBody(state = state, onCopy = onCopy)
    }
}

@Composable
private fun StateBody(
    state: UiState,
    onCopy: (Candidate) -> Unit,
) {
    when (state) {
        UiState.Idle -> Unit
        UiState.Loading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("正在生成…", style = MaterialTheme.typography.bodyMedium)
            }

        UiState.Empty ->
            Text(
                text = "还没捕获到 Soul 的对话内容。请先在 Soul 打开对话页，等气泡上方提示出现新消息再点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

        is UiState.Ready ->
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.candidates.forEachIndexed { index, c ->
                    CandidateRow(index = index + 1, candidate = c, onClick = { onCopy(c) })
                }
            }

        is UiState.Error ->
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Composable
private fun CandidateRow(
    index: Int,
    candidate: Candidate,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = candidate.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text("复制") }
    }
}

/**
 * Top-level Compose subtree owned by the WindowManager overlay view: a
 * collapsed bubble when [state] is Idle, otherwise the panel sitting
 * directly above the bubble. The bubble itself stays drawn so the user can
 * tap it again to refresh, or drag the whole stack to a new position.
 *
 * The view-layer touch handler in BubbleHost is the source of truth for
 * tap-vs-drag detection over the bubble area; the panel uses Compose
 * onClick on its own buttons.
 */
@Composable
internal fun BubbleStack(
    style: BubbleStyle,
    state: UiState,
    onCopy: (Candidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        if (state !is UiState.Idle) {
            CandidatePanel(state = state, onCopy = onCopy, onDismiss = onDismiss)
            Spacer(Modifier.height(6.dp))
        }
        BubbleVisual(style = style)
    }
}
