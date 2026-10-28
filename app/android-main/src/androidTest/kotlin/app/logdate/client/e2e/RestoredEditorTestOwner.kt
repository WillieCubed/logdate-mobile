package app.logdate.client.e2e

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.viewmodel.resolveViewModel

/** Gives repository probes the same owner-backed Koin resolution used by an editor visit. */
@OptIn(KoinInternalApi::class)
internal class RestoredEditorTestOwner(
    restoredState: SavedState? = null,
) : SavedStateRegistryOwner,
    ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry(this)
    override val viewModelStore = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(restoredState)
        enableSavedStateHandles()
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    fun editor(draftManager: DraftManager? = null): EntryEditorViewModel =
        resolveViewModel(
            EntryEditorViewModel::class,
            viewModelStore,
            extras =
                MutableCreationExtras().apply {
                    this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@RestoredEditorTestOwner
                    this[VIEW_MODEL_STORE_OWNER_KEY] = this@RestoredEditorTestOwner
                },
            scope = GlobalContext.get().scopeRegistry.rootScope,
            parameters = draftManager?.let { manager -> { parametersOf(manager) } },
        )

    fun save(): SavedState = savedState().also(controller::performSave)

    fun close() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
