package com.stylemirror.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Stub renderings of the floating bubble. Both forms are click-only here;
 * T30.6 wires the click to OverlayCandidateController. Painted at 50–60%
 * alpha so the bubble does not steal attention from the underlying app.
 */
@Composable
internal fun BubbleVisual(
    style: BubbleStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        BubbleStyle.CIRCLE -> CircleBubble(modifier)
        BubbleStyle.SIDE_STRIP -> SideStripBubble(modifier)
    }
}

@Composable
private fun CircleBubble(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "镜",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SideStripBubble(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .width(12.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    )
}

/**
 * Placeholder candidate panel. T30.6 replaces the body with the actual
 * 3-candidate list bound to OverlayCandidateController.
 */
@Composable
internal fun CandidatePanelPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(width = 280.dp, height = 160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "候选生成接入中…\n(T30.6 实装)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
