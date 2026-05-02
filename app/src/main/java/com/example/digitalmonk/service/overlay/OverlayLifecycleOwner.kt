package com.example.digitalmonk.service.overlay

import androidx.lifecycle.*
import androidx.savedstate.*

/**
 * A minimal combined owner for use inside a WindowManager-hosted ComposeView.
 * Attach to the view before setContent() is called.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart()   { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onResume()  { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    fun onDestroy() { lifecycleRegistry.currentState = Lifecycle.State.DESTROYED }
}