package com.stylemirror.feature.overlay.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stylemirror.feature.overlay.candidate.OverlayCandidateController
import kotlin.math.abs

/**
 * Owns the lifecycle of the floating overlay window: creates the
 * [ComposeView], wires the view-tree owner trio, attaches it to
 * [WindowManager] with a TYPE_APPLICATION_OVERLAY layer, and translates raw
 * touches over the bubble into either a click (tap-within-slop, triggers
 * the controller) or a drag (move-window-to).
 *
 * **Tap vs drag at the View layer**
 *
 * Compose's `pointerInput` only sees gestures inside the composed UI;
 * moving the bubble means moving the `WindowManager` view itself, which
 * Compose has no handle on. The cleanest split is:
 *  - View-layer [View.OnTouchListener] decides "click vs drag" while the
 *    state is collapsed. If a tap happens, [OverlayCandidateController.trigger]
 *    runs; the panel rendered for non-Idle states uses Compose onClick.
 *  - When the panel is open, the touch listener returns `false` so Compose
 *    sees the events — that means dragging is disabled while expanded,
 *    which is intentional (user closes the panel, then drags).
 *
 * **Lifecycle**
 *
 * [ComposeView] requires a [androidx.lifecycle.LifecycleOwner],
 * [androidx.lifecycle.ViewModelStoreOwner] and
 * [androidx.savedstate.SavedStateRegistryOwner]. A bare `Service` is none
 * of those — [OverlayLifecycleOwner] supplies the trio.
 */
internal class BubbleHost(
    private val context: Context,
    private val controller: OverlayCandidateController,
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
                    val state by controller.state.collectAsState()
                    BubbleStack(
                        style = style,
                        state = state,
                        onCopy = ::copyToClipboard,
                        onDismiss = controller::dismiss,
                    )
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

        attachTouchListener(view, params)
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
            android.util.Log.d(TAG, "removeView no-op: ${e.message}")
        }
        lifecycleOwner?.stop()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener(
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
            // While the panel is open, let Compose handle taps on its
            // buttons — drag is intentionally disabled in that mode.
            if (controller.state.value !is OverlayCandidateController.UiState.Idle) {
                return@setOnTouchListener false
            }
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
                    if (!isDragging) controller.trigger()
                    true
                }
                else -> false
            }
        }
    }

    private fun copyToClipboard(candidate: com.stylemirror.domain.candidate.Candidate) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("StyleMirror candidate", candidate.text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
        private const val TAG = "StyleMirrorOverlay"
        private const val INITIAL_X_DP_FROM_RIGHT: Int = 24
        private const val INITIAL_Y_DP_FROM_TOP: Int = 240
    }
}
