package com.stylemirror.feature.overlay.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * P1.c live-capture entry point. Listens for window-content / window-state
 * events on the Soul package (filter set by accessibility_service_config.xml,
 * overridable via setServiceInfo in T30.7) and — in debug builds only —
 * dumps the active window's view hierarchy to logcat so we can design the
 * SoulNodeMatchers (T30.4) with real node IDs.
 *
 * ## Why dump in debug only
 *
 * The dumped tree contains the user's real conversation text. That is fine
 * for self-testing on the developer's device; it would be unacceptable in a
 * shipped release build. We gate on `BuildConfig.DEBUG` (Android sets
 * `ApplicationInfo.FLAG_DEBUGGABLE` accordingly) so the same APK can be
 * promoted to release without leaking text.
 *
 * ## 1Hz throttle
 *
 * Soul fires `TYPE_WINDOW_CONTENT_CHANGED` on every scroll frame and every
 * keystroke. Dumping at >1Hz floods logcat and inflates the parser's CPU
 * cost (T30.4 will share the same throttle). 1000ms is empirical: short
 * enough to feel responsive when the user pulls a new message, long enough
 * to keep CPU usage trivial.
 *
 * ## What this is NOT doing yet
 *
 * - No parsing into ConversationContext (T30.4)
 * - No write to OverlaySnapshotRepository (T30.4)
 * - No bubble UI interaction (T30.5/T30.6)
 *
 * The whole point of T30.3 is: get a real Soul logcat dump out of the
 * developer's device, then design the parser against it.
 */
class StyleMirrorAccessibilityService : AccessibilityService() {
    private var lastDumpAtMillis: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isInterestingEvent(event) || !isDebuggable()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastDumpAtMillis < DUMP_THROTTLE_MS) return
        lastDumpAtMillis = now
        dumpActiveWindow(event)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dumpActiveWindow(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        try {
            val tree = NodeTreeDumper.dump(root)
            Log.d(
                LOG_TAG,
                "── window dump (pkg=${event.packageName}, eventType=${event.eventType}) ──\n$tree",
            )
        } catch (t: Throwable) {
            // AccessibilityNodeInfo objects are pooled; a child can be
            // recycled mid-traversal and the API surfaces it as either
            // IllegalStateException or NullPointerException depending on
            // the OEM. Catching Throwable here is deliberate — the next
            // event will retry, and we never want a parser bug to crash
            // the whole accessibility subsystem.
            Log.w(LOG_TAG, "dump failed", t)
        } finally {
            root.recycle()
        }
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
        private const val DUMP_THROTTLE_MS: Long = 1_000L

        // Suppress unused-import lint warning by referencing Build at least once.
        @Suppress("unused")
        private val sdkSentinel: Int = Build.VERSION.SDK_INT
    }
}
