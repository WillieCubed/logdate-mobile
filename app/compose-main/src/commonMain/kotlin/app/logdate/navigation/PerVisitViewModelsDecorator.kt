package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.savedState
import app.logdate.ui.navigation.ViewModelsPerVisit
import app.logdate.ui.navigation.hasViewModelsPerVisit

/**
 * Gives entries marked with [ViewModelsPerVisit] a view model store of their own, cleared when the
 * entry leaves the back stack. Other entries keep the activity's store, so screens that share a
 * view model across several routes, such as onboarding, still share it.
 */
@Composable
fun <T : Any> rememberPerVisitViewModelsDecorator(): NavEntryDecorator<T> {
    val stores = viewModel { PerVisitViewModelStores() }
    return remember(stores) {
        NavEntryDecorator(onPop = stores::clear) { entry ->
            if (!entry.hasViewModelsPerVisit()) {
                entry.Content()
                return@NavEntryDecorator
            }
            val store = stores.storeFor(entry.contentKey)
            val owner =
                rememberSaveable(
                    store,
                    saver =
                        Saver<PerVisitSavedStateOwner, SavedState>(
                            save = { it.save() },
                            restore = { PerVisitSavedStateOwner(store, it) },
                        ),
                ) {
                    PerVisitSavedStateOwner(store)
                }
            val parentLifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(owner) {
                onDispose { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
            }
            DisposableEffect(owner, parentLifecycle) {
                val observer = LifecycleEventObserver { _, event -> owner.lifecycle.handleLifecycleEvent(event) }
                parentLifecycle.addObserver(observer)
                onDispose {
                    parentLifecycle.removeObserver(observer)
                }
            }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalSavedStateRegistryOwner provides owner,
            ) { entry.Content() }
        }
    }
}

/** Each visit owns its handle namespace; its store can outlive this composition during resizing. */
private class PerVisitSavedStateOwner(
    override val viewModelStore: ViewModelStore,
    restoredState: SavedState? = null,
) : ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    override val lifecycle = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory = SavedStateViewModelFactory()
    override val defaultViewModelCreationExtras: CreationExtras
        get() =
            MutableCreationExtras().apply {
                this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@PerVisitSavedStateOwner
                this[VIEW_MODEL_STORE_OWNER_KEY] = this@PerVisitSavedStateOwner
            }

    init {
        controller.performAttach()
        controller.performRestore(restoredState)
        enableSavedStateHandles()
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    fun save(): SavedState = savedState().also(controller::performSave)
}

/** Holds one store per visited entry; lives in the activity's store so it survives rotation. */
private class PerVisitViewModelStores : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStore>()

    fun storeFor(contentKey: Any): ViewModelStore = stores.getOrPut(contentKey) { ViewModelStore() }

    fun clear(contentKey: Any) {
        stores.remove(contentKey)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
