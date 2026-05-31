package com.stylemirror.feature.overlay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

/**
 * Owns the lifecycle of the floating bubble window: creates the
 * [ComposeView], wires the view-tree owner trio, attaches it to
 * [WindowManager] with a TYPE_APPLICATION_OVERLAY layer, and translates raw
 * touches into either a click (tap-within-slop) or a drag (move-window-to).
 *
 * **Why drag is implemented at the View layer, not in Compose**
 *
 * Compose's `pointerInput` only sees gestures inside the composed UI; moving
 * the bubble means moving the `WindowManager` view itself, which Compose has
 * no handle on. The cleanest split is:
 *  - View-layer [View.OnTouchListener] decides "click vs drag" and updates
 *    [WindowManager.LayoutParams.x/y].
 *  - Compose handles only the visual rendering, not the gesture.
 *
 * **Why we manually own the lifecycle**
 *
 * [ComposeView] requires a [androidx.lifecycle.LifecycleOwner],
 * [androidx.lifecycle.ViewModelStoreOwner] and
 * [androidx.savedstate.SavedStateRegistryOwner] in its view tree. A bare
 * `Service` is not any of these. [OverlayLifecycleOwner] supplies all three.
 */
internal class BubbleHost(
    private val context: Context,
    private val onClick: () -> Unit,
) {
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun show(style: BubbleStyle) {
        if (composeView != null) return // already shown
        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    BubbleSurface(style = style)
                }
            }
        owner.start()

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = INITIAL_X_DP_FROM_RIGHT
                y = INITIAL_Y_DP_FROM_TOP
            }

        attachDragListener(view, params)
        windowManager().addView(view, params)
        composeView = view
        lifecycleOwner = owner
        layoutParams = params
    }

    fun hide() {
        val view = composeView ?: return
        try {
            windowManager().removeView(view)
        } catch (e: IllegalArgumentException) {
            android.util.Log.d("StyleMirrorOverlay", "removeView no-op: ${e.message}")
        }
        lifecycleOwner?.stop()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startParamX = 0
        var startParamY = 0
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startParamX + dx.toInt()
                        params.y = startParamY + dy.toInt()
                        windowManager().updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun windowManager(): WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    companion object {
        private const val INITIAL_X_DP_FROM_RIGHT: Int = 24
        private const val INITIAL_Y_DP_FROM_TOP: Int = 240
    }
}

@Composable
private fun BubbleSurface(style: BubbleStyle) {
    // T30.5 stub: tap is wired at the View layer (BubbleHost.onClick) and
    // goes to the service. T30.6 swaps this composable for one that toggles
    // between the bubble and the candidate panel based on host state.
    BubbleVisual(style = style)
}
