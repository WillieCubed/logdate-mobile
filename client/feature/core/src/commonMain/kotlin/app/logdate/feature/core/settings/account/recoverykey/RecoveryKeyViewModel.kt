package app.logdate.feature.core.settings.account.recoverykey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.feature.core.settings.account.ConnectedServer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    /** No recovery key is registered; only the server's key can change the identity. */
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
    REPLACING_RECOVERY_KEY,
}

/** Why this device cannot work with its recovery phrase right now. */
enum class PhraseProblem {
    /** The phrase could not be read from this device's storage. */
    UNREADABLE,

    /** The phrase was read, but the recovery key could not be made from it. */
    CANNOT_DERIVE,

    /** Whether the person confirmed the phrase could not be read. */
    CONFIRMATION_UNREADABLE,
}

/** What the Recovery key screen shows. */
sealed interface RecoveryKeyUiState {
    data object Loading : RecoveryKeyUiState

    /** The feature is off, the server cannot perform it, or this device cannot confirm with a passkey. */
    data object NotOffered : RecoveryKeyUiState

    /** The identity could not be read from [serverName]. */
    data class LoadFailed(
        val serverName: String,
    ) : RecoveryKeyUiState

    /**
     * This device has a phrase it cannot work with. The page offers only Try again: entering a phrase
     * here would replace the one that unlocks this device's journal.
     */
    data class PhraseUnavailable(
        val handle: String,
        val serverName: String,
        val problem: PhraseProblem,
    ) : RecoveryKeyUiState

    data class Ready(
        val handle: String,
        val serverName: String,
        val phrase: PhraseStatus,
        val key: KeyStatus,
        val action: RecoveryKeyAction? = null,
        /** The last request's failure, until the person dismisses it or a later request starts. */
        val error: IdentityRecoveryFailure? = null,
        /** The server's key was just replaced with one the recovery key approved. */
        val replacedServerKey: Boolean = false,
        /** The registered recovery key was just replaced with the one from this device's phrase. */
        val replacedRecoveryKey: Boolean = false,
    ) : RecoveryKeyUiState {
        val canTurnOn: Boolean get() = key == KeyStatus.OFF && phrase == PhraseStatus.CONFIRMED && action == null
        val canReplaceServerKey: Boolean get() = key == KeyStatus.ON && action == null
        val canReplaceRecoveryKey: Boolean
            get() = key == KeyStatus.ON_FROM_OTHER_PHRASE && phrase == PhraseStatus.CONFIRMED && action == null
    }
}

/**
 * Sets up the recovery key a person holds for their identity, uses it to replace the server's key,
 * and replaces it when the phrase it came from is lost. The key comes from the recovery phrase on
 * this device; only its public half and signatures reach the server.
 *
 * @param offered whether the page may do anything at all; see [recoveryKeyOffered]
 * @param loadPhrase reads the stored recovery phrase, or `null` when this device has none
 * @param isPhraseConfirmed whether the person typed the phrase back to confirm they saved it
 */
class RecoveryKeyViewModel(
    private val identity: AccountIdentityRepository,
    private val connectedServer: ConnectedServer,
    private val offered: Flow<Boolean>,
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
     * Reads the identity and this device's phrase again, such as after returning from saving the
     * phrase. A request under way is left to finish.
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

    /** Puts the recovery key from this device's phrase in place of one made from a phrase the person no longer has. */
    fun replaceRecoveryKey() {
        val current = _state.value as? RecoveryKeyUiState.Ready ?: return
        if (!current.canReplaceRecoveryKey) return
        runAction(current, RecoveryKeyAction.REPLACING_RECOVERY_KEY) { phrase -> identity.replaceRecoveryKey(phrase).map { } }
    }

    fun dismissError() {
        _state.update { state -> (state as? RecoveryKeyUiState.Ready)?.copy(error = null) ?: state }
    }

    private fun runAction(
        current: RecoveryKeyUiState.Ready,
        action: RecoveryKeyAction,
        request: suspend (List<String>) -> Result<Unit>,
    ) {
        val started = current.copy(action = action, error = null, replacedServerKey = false, replacedRecoveryKey = false)
        _state.value = started
        viewModelScope.launch {
            val phrase =
                when (val read = readPhrase()) {
                    is PhraseRead.Words -> read.words
                    PhraseRead.Missing, is PhraseRead.Failed -> {
                        _state.value = RecoveryKeyUiState.PhraseUnavailable(current.handle, current.serverName, PhraseProblem.UNREADABLE)
                        return@launch
                    }
                }
            request(phrase)
                .onSuccess {
                    val reloaded = load(previous = started.copy(action = null))
                    _state.value =
                        if (reloaded is RecoveryKeyUiState.Ready) {
                            reloaded.copy(
                                replacedServerKey = action == RecoveryKeyAction.REPLACING_SERVER_KEY,
                                replacedRecoveryKey = action == RecoveryKeyAction.REPLACING_RECOVERY_KEY,
                            )
                        } else {
                            reloaded
                        }
                }.onFailure { error ->
                    val failure =
                        (error as? IdentityRecoveryException)?.failure
                            ?: IdentityRecoveryFailure.REFUSED.also { Napier.e("A recovery key request failed unexpectedly", error) }
                    _state.value = started.copy(action = null, error = failure)
                }
        }
    }

    private suspend fun load(previous: RecoveryKeyUiState.Ready?): RecoveryKeyUiState {
        if (!offered.first()) return RecoveryKeyUiState.NotOffered
        val server = connectedServer.info.first()
        val serverName = server.displayName ?: server.host
        val status =
            identity.getIdentityStatus().getOrElse { error ->
                Napier.w("Could not load the identity for the recovery key", error)
                return RecoveryKeyUiState.LoadFailed(serverName)
            }
        val phrase =
            when (val read = readPhrase()) {
                is PhraseRead.Words -> read.words
                PhraseRead.Missing -> null
                is PhraseRead.Failed -> return RecoveryKeyUiState.PhraseUnavailable(status.handle, serverName, PhraseProblem.UNREADABLE)
            }
        val confirmed =
            phrase != null &&
                try {
                    isPhraseConfirmed()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Napier.w("Could not check whether the recovery phrase was confirmed", error)
                    return RecoveryKeyUiState.PhraseUnavailable(status.handle, serverName, PhraseProblem.CONFIRMATION_UNREADABLE)
                }
        val derived =
            phrase?.let { words ->
                identity
                    .derivePlcRecoveryDidKey(words.joinToString(" "))
                    .getOrElse { error ->
                        Napier.w("Could not derive the recovery key on this device", error)
                        return RecoveryKeyUiState.PhraseUnavailable(status.handle, serverName, PhraseProblem.CANNOT_DERIVE)
                    }.recoveryDidKey
            }
        val registered = status.plcRecoveryDidKey
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
            error = null,
            replacedServerKey = previous?.replacedServerKey ?: false,
            replacedRecoveryKey = previous?.replacedRecoveryKey ?: false,
        )
    }

    private suspend fun readPhrase(): PhraseRead =
        try {
            loadPhrase()?.let(PhraseRead::Words) ?: PhraseRead.Missing
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Napier.w("Could not read the recovery phrase", error)
            PhraseRead.Failed(error)
        }

    private sealed interface PhraseRead {
        data class Words(
            val words: List<String>,
        ) : PhraseRead

        data object Missing : PhraseRead

        data class Failed(
            val error: Exception,
        ) : PhraseRead
    }
}
