package com.stylemirror.feature.overlay.ui

/**
 * Two visual forms for the floating bubble. Open question in the P1.c spec
 * (docs/ideas/p1-floating-window.md §开放问题). T30.5 ships both as stubs so
 * the user can A/B them on a real device before we commit; T30.7 wires the
 * choice to OverlayConfigStore.
 */
enum class BubbleStyle {
    /**
     * 48dp half-translucent circle pinned to the right edge by default,
     * drag-anywhere. Mainstream, neutral, low-key — the default.
     */
    CIRCLE,

    /**
     * 12dp x 40dp half-translucent strip glued to the right edge of the
     * screen. Lower visual weight; easier to ignore, harder to hit.
     */
    SIDE_STRIP,
}
