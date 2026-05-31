package com.stylemirror.feature.overlay.service

/**
 * Pure-data view of a node in the accessibility tree. Decouples
 * [SoulNodeMatchers] from [android.view.accessibility.AccessibilityNodeInfo]
 * so the parser can be unit-tested with hand-built trees (which is the only
 * way given AccessibilityNodeInfo is sealed by the platform).
 *
 * The adapter from `AccessibilityNodeInfo` to this type is intentionally
 * kept thin and lives next to the service (see [toNodeView]) — keeping the
 * conversion at the boundary, not inside the parser.
 *
 * **Why not [android.graphics.Rect]**
 *
 * The matcher is an Android library file but is unit-tested on plain JVM
 * (no Robolectric). `android.graphics.Rect` is a framework stub class —
 * calling its constructor in a JVM test would throw "Method … not mocked".
 * A tiny in-house [BoundsRect] avoids the dependency without losing
 * anything we use (only `left/top/right/bottom`).
 */
internal data class NodeView(
    val viewIdResourceName: String?,
    val className: String?,
    val text: String?,
    val boundsInScreen: BoundsRect,
    val children: List<NodeView>,
)

/** Plain rectangle in screen pixels. See [NodeView] for the rationale. */
internal data class BoundsRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
