package com.stylemirror.feature.overlay.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.time.Instant

/**
 * P1.c live-capture entry point. Listens for window-content / window-state
 * events on the configured Soul package and:
 *  1. parses the active window into a [com.stylemirror.domain.conversation.ConversationContext]
 *     via [SoulNodeMatchers] (T30.4)
 *  2. publishes the result on [OverlaySnapshotRepository] so a click on the
 *     floating bubble (T30.6) can pick up the latest snapshot
 *  3. in debug builds only, also dumps the raw node tree to logcat —
 *     useful when tuning matchers against a real device.
 *
 * ## 1Hz throttle
 *
 * Soul fires `TYPE_WINDOW_CONTENT_CHANGED` on every scroll frame and every
 * keystroke. Parsing at >1Hz wastes CPU; the bubble click only ever needs
 * the latest snapshot, not every intermediate one. 1000ms is empirical:
 * short enough to feel responsive when a new message arrives, long enough
 * to keep CPU usage trivial.
 *
 * ## Privacy
 *
 * - The parser only emits Speaker.Mine / Theirs (see [SoulNodeMatchers]
 *   contract). No other-party text reaches the LLM until the user clicks
 *   the bubble (T30.6) and the candidate generator's existing redaction
 *   pipeline runs.
 * - The release-build dump path is disabled (gated on FLAG_DEBUGGABLE);
 *   `Log.d` calls in this file outside the dump path emit metadata only,
 *   no message text.
 */
class StyleMirrorAccessibilityService : AccessibilityService() {
    private var lastTickAtMillis: Long = 0L
    private var cachedScreenWidth: Int = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isInterestingEvent(event)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAtMillis < TICK_THROTTLE_MS) return
        lastTickAtMillis = now
        processActiveWindow(event)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processActiveWindow(event: AccessibilityEvent) {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        try {
            val tree = root.toNodeView()
            // Parse → publish to repository (production path).
            val ctx = SoulNodeMatchers.parse(
                root = tree,
                screenWidth = resolveScreenWidth(),
                now = Instant::now,
            )
            if (ctx != null) {
                OverlaySnapshotRepository.publish(ctx)
                if (isDebuggable()) {
                    Log.d(
                        SNAPSHOT_TAG,
                        "snapshot pkg=${event.packageName} mine=${ctx.myMessages.size} " +
                            "theirs=${ctx.theirMessages.size}",
                    )
                }
            }
            // Debug-only raw dump (kept for matcher tuning; never in release).
            if (isDebuggable()) {
                val dump = NodeTreeDumper.dump(treeFor = tree)
                Log.d(LOG_TAG, "── window dump (pkg=${event.packageName}) ──\n$dump")
            }
        } catch (t: Throwable) {
            // AccessibilityNodeInfo objects are pooled; a child can be
            // recycled mid-traversal and the API surfaces it as either
            // IllegalStateException or NullPointerException depending on
            // the OEM. Catching Throwable here is deliberate — the next
            // event will retry, and we never want a parser bug to crash
            // the whole accessibility subsystem.
            Log.w(LOG_TAG, "process failed", t)
        } finally {
            // Root is owned by the platform pool; recycling our reference
            // does not affect children we already recycled inside
            // toNodeView's traversal.
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun resolveScreenWidth(): Int {
        if (cachedScreenWidth > 0) return cachedScreenWidth
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return FALLBACK_SCREEN_WIDTH
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        cachedScreenWidth = metrics.widthPixels.takeIf { it > 0 } ?: FALLBACK_SCREEN_WIDTH
        return cachedScreenWidth
    }

    override fun onInterrupt() {
        // No long-running work to cancel.
    }

    private fun isInterestingEvent(event: AccessibilityEvent): Boolean =
        event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val LOG_TAG: String = "StyleMirrorOverlay"
        const val SNAPSHOT_TAG: String = "StyleMirrorSnapshot"
        private const val TICK_THROTTLE_MS: Long = 1_000L
        private const val FALLBACK_SCREEN_WIDTH: Int = 1080

        // Suppress unused-import lint warning by referencing Build at least once.
        @Suppress("unused")
        private val sdkSentinel: Int = Build.VERSION.SDK_INT
    }
}
