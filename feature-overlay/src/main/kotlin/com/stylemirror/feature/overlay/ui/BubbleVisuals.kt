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
 * Two visual forms for the floating bubble. Open question in the P1.c spec
 * (docs/ideas/p1-floating-window.md §开放问题). T30.5 ships both as stubs so
 * the user can A/B them on a real device before T30.8 picks the default;
 * T30.7 wires the choice to OverlayConfigStore.
 *
 * Both forms render at 50–60% alpha so the bubble does not steal attention
 * from the underlying app.
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
