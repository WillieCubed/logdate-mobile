package app.logdate.feature.core.settings.account.hosting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.feature.core.settings.account.ConnectedServer
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @property health `null` while the server is being checked
 */
data class HostingUiState(
    val server: ConnectedServerInfo? = null,
    val health: ServerHealth? = null,
)

/** Shows which server the account lives on and whether it is answering. */
class HostingViewModel(
    private val connectedServer: ConnectedServer,
) : ViewModel() {
    private val health = MutableStateFlow<ServerHealth?>(null)
    private var checkJob: Job? = null

    val state: StateFlow<HostingUiState> =
        combine(connectedServer.info, health) { server, health -> HostingUiState(server, health) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HostingUiState())

    init {
        checkAgain()
    }

    /** Asks the server again. A check already under way is left to finish. */
    fun checkAgain() {
        if (checkJob?.isActive == true) return
        health.value = null
        checkJob = viewModelScope.launch { health.value = connectedServer.refresh() }
    }
}
