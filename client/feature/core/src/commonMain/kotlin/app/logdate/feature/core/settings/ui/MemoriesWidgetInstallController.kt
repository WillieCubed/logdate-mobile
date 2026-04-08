package app.logdate.feature.core.settings.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform bridge for surfacing and requesting the Memories home screen widget install flow.
 */
interface MemoriesWidgetInstallController {
    val uiState: StateFlow<MemoriesWidgetInstallUiState>

    suspend fun requestAddToHomeScreen()
}

sealed interface MemoriesWidgetInstallUiState {
    data object Hidden : MemoriesWidgetInstallUiState

    data object Available : MemoriesWidgetInstallUiState

    data object Unsupported : MemoriesWidgetInstallUiState
}

/**
 * Default controller for platforms that do not expose Android home screen widgets.
 */
class HiddenMemoriesWidgetInstallController : MemoriesWidgetInstallController {
    private val _uiState = MutableStateFlow<MemoriesWidgetInstallUiState>(MemoriesWidgetInstallUiState.Hidden)

    override val uiState: StateFlow<MemoriesWidgetInstallUiState> = _uiState.asStateFlow()

    override suspend fun requestAddToHomeScreen() = Unit
}
