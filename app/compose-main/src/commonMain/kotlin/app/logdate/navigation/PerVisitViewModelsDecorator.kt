package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntryDecorator
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
            val owner =
                remember(entry.contentKey) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = stores.storeFor(entry.contentKey)
                    }
                }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
        }
    }
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
