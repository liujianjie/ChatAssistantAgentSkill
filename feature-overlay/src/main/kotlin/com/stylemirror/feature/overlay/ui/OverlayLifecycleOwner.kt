package com.stylemirror.feature.overlay.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The minimum trio Compose insists on whenever a `ComposeView` is hosted
 * outside an Activity (`WindowManager.addView` in our case): a
 * [LifecycleOwner], a [ViewModelStoreOwner], and a [SavedStateRegistryOwner].
 *
 * Without this, the very first `setContent { … }` call throws
 * `IllegalStateException: ViewTreeLifecycleOwner not found from …`.
 *
 * **Lifecycle semantics**
 *
 * The owner is driven manually by the host service:
 *  - `start()` advances state to RESUMED — call after `WindowManager.addView`
 *    so Compose's recomposer is allowed to schedule frames.
 *  - `stop()` collapses state to DESTROYED — call before
 *    `WindowManager.removeView` so any DisposableEffect / coroutine launched
 *    inside the composition cleans up.
 *
 * Stick to that order. Removing the view before `stop()` leaks the
 * recomposer; calling `stop()` after the view is already removed leaks the
 * view-tree owner registration.
 */
internal class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
