package com.stylemirror.feature.overlay.config

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.stylemirror.feature.overlay.ui.BubbleStyle

/**
 * Persisted P1.c overlay settings — kept on plain [SharedPreferences] (no
 * encryption needed: none of these fields are secrets — see the global
 * CLAUDE.md "API Key 存放优先级" section).
 *
 * Exposes:
 *  - [enabled] — master toggle. When false the [FloatingBubbleService] is
 *    not started even if the user granted SYSTEM_ALERT_WINDOW.
 *  - [bubbleStyle] — A/B for T30.5 (CIRCLE vs SIDE_STRIP). T30.7 lets the
 *    user flip it from Settings; T30.9 baseline picks a default.
 *  - [soulPackageName] — fallback for niche distribution channels where
 *    Soul ships under a non-standard package id. T30.3 logcat dump sanity-
 *    checks this.
 */
class OverlayConfigStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var bubbleStyle: BubbleStyle
        get() {
            val raw = prefs.getString(KEY_BUBBLE_STYLE, null) ?: return BubbleStyle.CIRCLE
            return runCatching { BubbleStyle.valueOf(raw) }.getOrDefault(BubbleStyle.CIRCLE)
        }
        set(value) = prefs.edit().putString(KEY_BUBBLE_STYLE, value.name).apply()

    var soulPackageName: String
        get() = prefs.getString(KEY_SOUL_PACKAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SOUL_PACKAGE
        set(value) = prefs.edit().putString(KEY_SOUL_PACKAGE, value.trim()).apply()

    companion object {
        private const val PREFS_NAME = "stylemirror_overlay_config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BUBBLE_STYLE = "bubble_style"
        private const val KEY_SOUL_PACKAGE = "soul_package_name"
        const val DEFAULT_SOUL_PACKAGE: String = "cn.soulapp.android"
    }
}

/**
 * Probe helpers backing the Settings screen's "permission status" rows.
 * Both are pure reads of system state, so callers do not need to cache them
 * — Compose `LaunchedEffect(Unit)` on every recomposition is fine.
 */
object OverlayPermissionProbe {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClassName: String,
    ): Boolean {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
        val expectedId = "${context.packageName}/$serviceClassName"
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id == expectedId || it.id?.endsWith("/$serviceClassName") == true }
    }
}
