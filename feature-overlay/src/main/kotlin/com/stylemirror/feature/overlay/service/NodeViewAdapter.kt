package com.stylemirror.feature.overlay.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Eagerly materialize an [AccessibilityNodeInfo] subtree into a pure-data
 * [NodeView] tree, recursing through children and recycling each
 * AccessibilityNodeInfo as soon as we're done reading from it.
 *
 * **Why eager / not lazy**
 *
 * `AccessibilityNodeInfo` instances are pooled by Android and become
 * invalid the moment we recycle them. A lazy adapter would keep references
 * alive past the parser's intent, racing the platform's recycling. Eager
 * conversion is one allocation per node now, but zero ambiguity later —
 * the [NodeView] stays valid until the GC collects it.
 *
 * **Depth limit** is a defensive cap against pathological trees (Soul has
 * been observed at depth ≈ 18).
 */
private const val MAX_RECURSE_DEPTH = 30

internal fun AccessibilityNodeInfo.toNodeView(): NodeView = toNodeViewInternal(depth = 0)

@Suppress("DEPRECATION") // AccessibilityNodeInfo.recycle is deprecated on API 33+ but
// still callable and required on older platforms our minSdk supports.
private fun AccessibilityNodeInfo.toNodeViewInternal(depth: Int): NodeView {
    val rawBounds = Rect().also { getBoundsInScreen(it) }
    val bounds = BoundsRect(rawBounds.left, rawBounds.top, rawBounds.right, rawBounds.bottom)
    val cn = className?.toString()
    val txt = text?.toString()
    val viewId = viewIdResourceName

    val children =
        if (depth >= MAX_RECURSE_DEPTH) {
            emptyList()
        } else {
            buildList(childCount) {
                for (i in 0 until childCount) {
                    val child = getChild(i) ?: continue
                    try {
                        add(child.toNodeViewInternal(depth + 1))
                    } finally {
                        // Recycle as soon as we've copied its data out.
                        child.recycle()
                    }
                }
            }
        }

    return NodeView(
        viewIdResourceName = viewId,
        className = cn,
        text = txt,
        boundsInScreen = bounds,
        children = children,
    )
}
