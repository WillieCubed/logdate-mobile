package app.logdate.feature.core.settings.account.recoverykey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.feature.core.settings.account.ConnectedServer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the recovery phrase this device would use stands. */
enum class PhraseStatus {
    /** This device has no recovery phrase. */
    MISSING,

    /** The phrase is on this device, but the person has not typed it back to confirm they saved it. */
    NOT_CONFIRMED,

    /** The phrase is on this device and the person confirmed they saved it. */
    CONFIRMED,
}

/** Whether the identity has a recovery key, and whether this device's phrase produces it. */
enum class KeyStatus {
    /** No recovery key is registered; the server alone controls the identity. */
    OFF,

    /** The registered recovery key is the one this device's phrase produces. */
    ON,

    /** A recovery key is registered, but this device's phrase produces a different one. */
    ON_FROM_OTHER_PHRASE,

    /** A recovery key is registered, and this device has no phrase to use it with. */
    ON_WITHOUT_PHRASE,
}

/** A request to the server that is under way. */
enum class RecoveryKeyAction {
    TURNING_ON,
    REPLACING_SERVER_KEY,
}

/** What the Recovery key screen shows. */
sealed interface RecoveryKeyUiState {
    data object Loading : RecoveryKeyUiState

    /** The identity could not be read from [serverName]. */
    data class LoadFailed(
        val serverName: String,
    ) : RecoveryKeyUiState

    data class Ready(
        val handle: String,
        val serverName: String,
        val phrase: PhraseStatus,
        val key: KeyStatus,
        val action: RecoveryKeyAction? = null,
        /** The last request's failure, until the person dismisses it or tries again. */
        val error: IdentityRecoveryFailure? = null,
        /** The server's key was just replaced with one the recovery key approved. */
        val replacedServerKey: Boolean = false,
    ) : RecoveryKeyUiState {
        val canTurnOn: Boolean get() = key == KeyStatus.OFF && phrase == PhraseStatus.CONFIRMED && action == null
        val canReplaceServerKey: Boolean get() = key == KeyStatus.ON && action == null
    }
}

/**
 * Sets up the recovery key a person holds for their identity and uses it to replace the server's
 * key. The key comes from the recovery phrase on this device; only its public half and signatures
 * reach the server.
 *
 * @param loadPhrase reads the stored recovery phrase, or `null` when this device has none
 * @param isPhraseConfirmed whether the person typed the phrase back to confirm they saved it
 */
class RecoveryKeyViewModel(
    private val identity: AccountIdentityRepository,
    private val connectedServer: ConnectedServer,
    private val loadPhrase: suspend () -> List<String>?,
    private val isPhraseConfirmed: suspend () -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow<RecoveryKeyUiState>(RecoveryKeyUiState.Loading)
    val state: StateFlow<RecoveryKeyUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /**
     * Reads the identity and this device's phrase again, such as after returning from saving or
     * entering the phrase. A request under way is left to finish.
     */
    fun refresh() {
        val current = _state.value
        if (current is RecoveryKeyUiState.Ready && current.action != null) return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { _state.value = load(previous = _state.value as? RecoveryKeyUiState.Ready) }
    }

    /** Registers the recovery key from this device's confirmed phrase on the identity. */
    fun turnOn() {
        val current = _state.value as? RecoveryKeyUiState.Ready ?: return
        if (!current.canTurnOn) return
        runAction(current, RecoveryKeyAction.TURNING_ON) { phrase -> identity.registerRecoveryKey(phrase).map { } }
    }

    /** Replaces the server's key with a new one that the recovery key approves. */
    fun replaceServerKey() {
        val current = _state.value as? RecoveryKeyUiState.Ready ?: return
        if (!current.canReplaceServerKey) return
        runAction(current, RecoveryKeyAction.REPLACING_SERVER_KEY) { phrase -> identity.recoverIdentity(phrase).map { } }
    }

    fun dismissError() {
        _state.update { state -> (state as? RecoveryKeyUiState.Ready)?.copy(error = null) ?: state }
    }

    private fun runAction(
        current: RecoveryKeyUiState.Ready,
        action: RecoveryKeyAction,
        request: suspend (List<String>) -> Result<Unit>,
    ) {
        _state.value = current.copy(action = action, error = null, replacedServerKey = false)
        viewModelScope.launch {
            val phrase =
                runCatching { loadPhrase() }
                    .onFailure { Napier.w("Could not read the recovery phrase", it) }
                    .getOrNull()
            if (phrase == null) {
                _state.value = current.copy(action = null, error = IdentityRecoveryFailure.DEVICE_ERROR)
                return@launch
            }
            request(phrase)
                .onSuccess {
                    val reloaded = load(previous = current)
                    _state.value =
                        if (reloaded is RecoveryKeyUiState.Ready) {
                            reloaded.copy(replacedServerKey = action == RecoveryKeyAction.REPLACING_SERVER_KEY)
                        } else {
                            reloaded
                        }
                }.onFailure { error ->
                    val failure = (error as? IdentityRecoveryException)?.failure ?: IdentityRecoveryFailure.REFUSED
                    _state.value = current.copy(action = null, error = failure)
                }
        }
    }

    private suspend fun load(previous: RecoveryKeyUiState.Ready?): RecoveryKeyUiState {
        val server = connectedServer.info.first()
        val serverName = server.displayName ?: server.host
        val status =
            identity.getIdentityStatus().getOrElse { error ->
                Napier.w("Could not load the identity for the recovery key", error)
                return RecoveryKeyUiState.LoadFailed(serverName)
            }
        val phrase =
            runCatching { loadPhrase() }
                .onFailure { Napier.w("Could not read the recovery phrase", it) }
                .getOrNull()
        val confirmed = phrase != null && runCatching { isPhraseConfirmed() }.getOrDefault(false)
        val registered = status.plcRecoveryDidKey
        var error: IdentityRecoveryFailure? = null
        val derived =
            phrase?.let { words ->
                identity
                    .derivePlcRecoveryDidKey(words.joinToString(" "))
                    .onFailure {
                        Napier.w("Could not derive the recovery key on this device", it)
                        error = IdentityRecoveryFailure.DEVICE_ERROR
                    }.getOrNull()
                    ?.recoveryDidKey
            }
        return RecoveryKeyUiState.Ready(
            handle = status.handle,
            serverName = serverName,
            phrase =
                when {
                    phrase == null -> PhraseStatus.MISSING
                    confirmed -> PhraseStatus.CONFIRMED
                    else -> PhraseStatus.NOT_CONFIRMED
                },
            key =
                when {
                    registered == null -> KeyStatus.OFF
                    derived == null -> KeyStatus.ON_WITHOUT_PHRASE
                    derived == registered -> KeyStatus.ON
                    else -> KeyStatus.ON_FROM_OTHER_PHRASE
                },
            error = error ?: previous?.error,
            replacedServerKey = previous?.replacedServerKey ?: false,
        )
    }
}
