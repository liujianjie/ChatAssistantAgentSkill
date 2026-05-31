package com.stylemirror.feature.overlay.service

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import java.time.Instant

/**
 * Heuristic parser turning a Soul accessibility node tree into a
 * [ConversationContext]. Generic-first by design: relies only on screen
 * geometry + text presence + class type. Once T30.3 logcat dumps are
 * collected from real devices, the matcher set will be tightened with
 * Soul-specific resource ids (e.g. `cn.soulapp.android:id/iv_avatar`) — the
 * geometry path stays as fallback for chat layout variants.
 *
 * ## Speaker disambiguation
 *
 * In Soul (and almost every IM UI on Android), the user's own messages are
 * pinned to the right edge of the screen and the counterpart's are pinned
 * left. We use the horizontal center of each text node's bounds vs the
 * screen midline to label `Mine` / `Theirs`. This single signal
 * intentionally does the heavy lifting because:
 *  - it survives obfuscated builds where ids are stripped
 *  - it survives Soul UI redesigns as long as the chat-bubble convention
 *    holds (it has across the entire Android IM ecosystem for a decade)
 *
 * Failure mode: a center-aligned system message ("xxx 已撤回") sits in the
 * middle and would be classified arbitrarily. We exclude those in
 * [isLikelySystemNotice] and ignore short noise.
 *
 * ## Privacy red line
 *
 * The parser only ever produces `Message.Mine` and `Message.Theirs` —
 * unattributed text is dropped, not bucketed into one side as a fallback.
 * That preserves the type-level guarantee from
 * `ConversationContext.myMessages` / `theirMessages` (only Me ever feeds
 * style profiling).
 *
 * ## Ordering
 *
 * Real Soul nodes carry no per-message timestamp accessible to the
 * accessibility API. We reconstruct order from the vertical position
 * (top of `boundsInScreen` ascending = older above, newer below — the
 * Android IM convention) and synthesize a monotonically increasing
 * `sentAt` based on a clock the caller injects.
 */
internal object SoulNodeMatchers {
    private const val MIN_TEXT_LEN = 1
    private const val MAX_TEXT_LEN = 2000

    /** Phrases that show up as system/ephemeral chat-list inserts. */
    private val SYSTEM_NOTICE_HINTS =
        listOf("已撤回", "已添加你为好友", "你和对方已是好友", "正在输入")

    /** ClassName fragments that should never become message content. */
    private val IGNORED_CLASS_FRAGMENTS =
        listOf("EditText", "Button", "ImageView", "ImageButton", "Switch", "ProgressBar")

    /**
     * Parse a Soul window snapshot.
     *
     * @param root The accessibility tree root for the active Soul window.
     * @param screenWidth Pixel width of the device screen — used as the
     *   midline for left/right speaker disambiguation. Caller pulls this
     *   from `WindowManager.defaultDisplay`.
     * @param now Clock for synthesizing timestamps. Pass `Instant::now` in
     *   prod; pass a fixed instant in tests.
     * @return null when no usable messages were extracted (empty tree,
     *   only buttons, only system notices). Caller should skip update.
     */
    fun parse(
        root: NodeView,
        screenWidth: Int,
        now: () -> Instant,
    ): ConversationContext? {
        if (screenWidth <= 0) return null
        val midline = screenWidth / 2

        val ordered = collectTextNodes(root).sortedBy { it.boundsInScreen.top }
        val baseInstant = now()
        val messages =
            ordered.mapIndexedNotNull { index, node ->
                toMessage(
                    node = node,
                    midline = midline,
                    sentAt = baseInstant.minusSeconds((ordered.size - 1 - index).toLong()),
                )
            }
        return messages
            .takeIf { it.isNotEmpty() }
            ?.let {
                ConversationContext(
                    partnerId = PartnerId(SOUL_OVERLAY_PARTNER_ID),
                    messages = it,
                )
            }
    }

    private fun collectTextNodes(root: NodeView): List<NodeView> {
        val out = mutableListOf<NodeView>()

        fun walk(node: NodeView) {
            if (isUsableMessageNode(node)) out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun isUsableMessageNode(node: NodeView): Boolean {
        val text = node.text?.trim().orEmpty()
        if (text.length !in MIN_TEXT_LEN..MAX_TEXT_LEN) return false
        if (isLikelySystemNotice(text)) return false
        val cn = node.className.orEmpty()
        val r = node.boundsInScreen
        val hasArea = r.right > r.left && r.bottom > r.top
        val notIgnoredClass = IGNORED_CLASS_FRAGMENTS.none { cn.contains(it) }
        return hasArea && notIgnoredClass
    }

    private fun isLikelySystemNotice(text: String): Boolean = SYSTEM_NOTICE_HINTS.any { text.contains(it) }

    private fun toMessage(
        node: NodeView,
        midline: Int,
        sentAt: Instant,
    ): Message? {
        val text = node.text?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return null
        val r = node.boundsInScreen
        val centerX = (r.left + r.right) / 2
        // Reject ambiguous middle-aligned items even after the system-notice
        // filter — could be a date separator we missed.
        val tolerance = (midline * AMBIGUOUS_MIDLINE_TOLERANCE_FRACTION).toInt()
        if (kotlin.math.abs(centerX - midline) < tolerance) return null

        val isMine = centerX > midline
        val id = MessageId("soul:${r.top}:${text.hashCode()}")
        return if (isMine) {
            Message.Mine(id = id, content = text, sentAt = sentAt)
        } else {
            Message.Theirs(id = id, content = text, sentAt = sentAt, displayName = "对方")
        }
    }

    /**
     * How close to the midline a node has to be before we drop it as
     * "ambiguous". 6% of half-screen ≈ 22 px on a 1080p display, enough to
     * absorb minor padding without rejecting genuine left/right bubbles.
     */
    private const val AMBIGUOUS_MIDLINE_TOLERANCE_FRACTION = 0.06

    /**
     * Synthetic partner id for any Soul conversation captured from the
     * overlay. P1.c does not introspect the partner — the user is in front
     * of one specific chat, full stop. If P2 ever needs per-partner stats
     * we'll plumb the conversation header text through here.
     */
    const val SOUL_OVERLAY_PARTNER_ID: String = "soul-overlay-active"
}
