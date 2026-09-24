package app.logdate.feature.core.settings.account.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.feature.core.settings.account.ConnectedServer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    /** The server the account is deleted from, as the person knows it. */
    val serverName: String = "",
    val eraseThisDevice: Boolean = false,
    val phase: Phase = Phase.Ready,
) {
    sealed interface Phase {
        data object Ready : Phase

        data object Deleting : Phase

        data class Deleted(
            val erasedThisDevice: Boolean,
        ) : Phase

        data class Failed(
            val reason: DeleteAccountFailure,
        ) : Phase
    }
}

enum class DeleteAccountFailure {
    OFFLINE,

    /** The server does not offer account deletion. */
    UNAVAILABLE,
    NOT_SIGNED_IN,
    SERVER,
}

/**
 * Deletes the account and everything synced to it, and optionally erases this device too.
 *
 * The device is only erased once the server has confirmed the account is gone, so a refused
 * deletion never costs the person their local journal.
 */
class DeleteAccountViewModel(
    private val accountRepository: PasskeyAccountRepository,
    connectedServer: ConnectedServer,
    private val eraseThisDevice: suspend () -> Unit,
) : ViewModel() {
    private val choices = MutableStateFlow(DeleteAccountUiState())

    val state: StateFlow<DeleteAccountUiState> =
        combine(choices, connectedServer.info) { choices, server ->
            choices.copy(serverName = server.displayName ?: server.host)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DeleteAccountUiState())

    /** Starts over; the view model outlives a visit to the screen. */
    fun reset() {
        if (choices.value.phase == DeleteAccountUiState.Phase.Deleting) return
        choices.value = DeleteAccountUiState()
    }

    fun setEraseThisDevice(erase: Boolean) {
        choices.update { it.copy(eraseThisDevice = erase) }
    }

    fun delete() {
        val current = choices.value
        if (current.phase == DeleteAccountUiState.Phase.Deleting) return
        choices.update { it.copy(phase = DeleteAccountUiState.Phase.Deleting) }

        viewModelScope.launch {
            val result = accountRepository.deleteAccount()
            val phase =
                result.fold(
                    onSuccess = { DeleteAccountUiState.Phase.Deleted(erasedThisDevice = current.eraseThisDevice && erase()) },
                    onFailure = { error ->
                        Napier.w("Account deletion failed", error)
                        DeleteAccountUiState.Phase.Failed(error.toFailure())
                    },
                )
            choices.update { it.copy(phase = phase) }
        }
    }

    /** The account is already gone by now, so a failed erase is reported rather than left hanging. */
    private suspend fun erase(): Boolean =
        try {
            eraseThisDevice()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("The account was deleted but this device could not be erased", e)
            false
        }
}

private fun Throwable.toFailure(): DeleteAccountFailure =
    when {
        this is NotSignedInException -> DeleteAccountFailure.NOT_SIGNED_IN
        this is PasskeyApiException && errorCode == PasskeyApiErrorCodes.NETWORK_ERROR -> DeleteAccountFailure.OFFLINE
        this is PasskeyApiException && errorCode == PasskeyApiErrorCodes.DELETION_UNAVAILABLE -> DeleteAccountFailure.UNAVAILABLE
        else -> DeleteAccountFailure.SERVER
    }
