package com.stylemirror.feature.overlay.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pretty-prints an [AccessibilityNodeInfo] tree as an indented multiline
 * string. Used by [StyleMirrorAccessibilityService] in debug builds to dump
 * Soul's UI hierarchy to logcat so we can design the SoulNodeMatchers (T30.4)
 * with real data.
 *
 * Output line format (one per node):
 *   `<indent><idx> [<class>] id=<viewId|-> bounds=<l,t,r,b> text=<truncated>`
 *
 * - `idx` is the zero-based child position under its parent
 * - `viewId` is the resource name (e.g. `cn.soulapp.android:id/iv_avatar`),
 *   or `-` when null (most leaf TextViews are anonymous in obfuscated builds)
 * - `text` is truncated to [TEXT_PREVIEW_LIMIT] chars and stripped of newlines
 *   to keep one node per logcat line; full text length is appended as
 *   `(len=N)` so we can tell if Soul is rendering oversized text we'd want
 *   to capture.
 *
 * **Privacy**: this dumper is for the developer running an instrumented build
 * to study Soul's view structure. It MUST NOT run in release builds (caller's
 * responsibility — see [StyleMirrorAccessibilityService] dump gate). The
 * truncated text WILL include the user's real Soul conversations, which is
 * acceptable for self-testing but unacceptable for shipped release builds.
 */
internal object NodeTreeDumper {
    private const val TEXT_PREVIEW_LIMIT = 60
    private const val INDENT = "  "
    private const val MAX_DEPTH = 30

    fun dump(root: AccessibilityNodeInfo?): String {
        if (root == null) return "<null root>"
        val sb = StringBuilder()
        appendNode(sb, root, depth = 0, idx = 0)
        return sb.toString()
    }

    private fun appendNode(
        sb: StringBuilder,
        node: AccessibilityNodeInfo,
        depth: Int,
        idx: Int,
    ) {
        if (depth > MAX_DEPTH) {
            sb.append(INDENT.repeat(depth)).append("…(max depth)\n")
            return
        }
        repeat(depth) { sb.append(INDENT) }
        sb.append(idx).append(' ')
        sb.append('[').append(node.className?.let { simpleClassName(it.toString()) } ?: "?").append(']')
        sb.append(" id=").append(node.viewIdResourceName ?: "-")
        sb.append(" bounds=").append(boundsString(node))
        node.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
            sb.append(" text=").append(previewText(it))
            sb.append(" (len=").append(it.length).append(')')
        }
        sb.append('\n')
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                appendNode(sb, child, depth + 1, i)
            } finally {
                child.recycle()
            }
        }
    }

    private fun boundsString(node: AccessibilityNodeInfo): String {
        val r = Rect()
        node.getBoundsInScreen(r)
        return "${r.left},${r.top},${r.right},${r.bottom}"
    }

    private fun simpleClassName(full: String): String = full.substringAfterLast('.').takeIf { it.isNotEmpty() } ?: full

    private fun previewText(s: String): String {
        val flat = s.replace('\n', '⏎').replace('\r', '⏎')
        return if (flat.length <= TEXT_PREVIEW_LIMIT) flat else flat.take(TEXT_PREVIEW_LIMIT) + "…"
    }
}
