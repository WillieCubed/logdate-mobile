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
 * @property canMoveAccount whether this device can move the account to another server
 */
data class HostingUiState(
    val server: ConnectedServerInfo? = null,
    val health: ServerHealth? = null,
    val canMoveAccount: Boolean = false,
)

/** Shows which server the account lives on and whether it is answering. */
class HostingViewModel(
    private val connectedServer: ConnectedServer,
    private val canMoveAccount: suspend () -> Boolean = { false },
) : ViewModel() {
    private val health = MutableStateFlow<ServerHealth?>(null)
    private val movable = MutableStateFlow(false)
    private var checkJob: Job? = null

    val state: StateFlow<HostingUiState> =
        combine(connectedServer.info, health, movable) { server, health, movable -> HostingUiState(server, health, movable) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HostingUiState())

    init {
        checkAgain()
        viewModelScope.launch { movable.value = runCatching { canMoveAccount() }.getOrDefault(false) }
    }

    /** Asks the server again. A check already under way is left to finish. */
    fun checkAgain() {
        if (checkJob?.isActive == true) return
        health.value = null
        checkJob = viewModelScope.launch { health.value = connectedServer.refresh() }
    }
}
