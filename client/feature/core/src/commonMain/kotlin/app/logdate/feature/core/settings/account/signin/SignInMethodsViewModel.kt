package app.logdate.feature.core.settings.account.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.permissions.PasskeyErrorCodes
import app.logdate.client.permissions.PasskeyException
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.PasskeyInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What the sign-in methods screen shows. */
sealed interface SignInMethodsUiState {
    data object Loading : SignInMethodsUiState

    data class Loaded(
        val passkeys: List<PasskeyRow>,
        val linkedProviders: List<LinkedProviderRow>,
        /** Whether this device can create a passkey. Where it can't, the action is not shown. */
        val canAddPasskey: Boolean,
        val isAddingPasskey: Boolean = false,
        val removingCredentialId: String? = null,
    ) : SignInMethodsUiState

    data class Failed(
        val reason: SignInMethodsLoadError,
    ) : SignInMethodsUiState
}

/**
 * One passkey on the account.
 *
 * @property deviceName the device it was created on, or `null` when the server never learned one
 * @property canRemove whether removing it would still leave a way to sign in
 */
data class PasskeyRow(
    val credentialId: String,
    val deviceName: String?,
    val addedOn: LocalDate?,
    val lastUsedOn: LocalDate?,
    val canRemove: Boolean,
)

/** A non-passkey way to sign in, such as a linked Google account. */
data class LinkedProviderRow(
    val kind: LinkedSignInProvider.Kind,
    val email: String?,
    val lastSignInOn: LocalDate?,
)

enum class SignInMethodsLoadError {
    NOT_SIGNED_IN,
    OFFLINE,
    SERVER,
}

enum class AddPasskeyFailure {
    /** The platform refused because a passkey for this account already lives on this device. */
    ALREADY_ON_THIS_DEVICE,
    NOT_SIGNED_IN,
    OFFLINE,

    /** The platform could not create a passkey (unsupported, locked, or its own error). */
    PLATFORM,
    SERVER,
}

enum class RemovePasskeyFailure {
    LAST_SIGN_IN_METHOD,
    OFFLINE,
    SERVER,
}

/** Results announced once, as a snackbar, rather than kept in state. */
sealed interface SignInMethodsEvent {
    data class PasskeyAdded(
        val deviceName: String?,
    ) : SignInMethodsEvent

    data object PasskeyRemoved : SignInMethodsEvent

    data class AddPasskeyFailed(
        val reason: AddPasskeyFailure,
    ) : SignInMethodsEvent

    data class RemovePasskeyFailed(
        val reason: RemovePasskeyFailure,
    ) : SignInMethodsEvent
}

/**
 * Lists the ways a person can sign in to their account and lets them add or remove passkeys.
 *
 * @param defaultPasskeyName the name the server gives a passkey when the client sends none. Older
 *   passkeys all carry it, so it says nothing about which device they belong to and is shown as
 *   an unnamed passkey instead.
 * @param connectedRpId the connected server's passkey relying party ID, or `null` when unknown
 * @param passkeysWorkWith whether this device's platform can create passkeys for a relying party
 */
class SignInMethodsViewModel(
    private val accountRepository: PasskeyAccountRepository,
    private val passkeyManager: PasskeyManager,
    private val defaultPasskeyName: () -> String?,
    private val connectedRpId: () -> String? = { null },
    private val passkeysWorkWith: (rpId: String) -> Boolean = { true },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow<SignInMethodsUiState>(SignInMethodsUiState.Loading)
    val state: StateFlow<SignInMethodsUiState> = _state.asStateFlow()

    private val _events = Channel<SignInMethodsEvent>(Channel.BUFFERED)
    val events: Flow<SignInMethodsEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** Reloads the list. A reload already under way is left to finish. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                if (_state.value !is SignInMethodsUiState.Loaded) {
                    _state.value = SignInMethodsUiState.Loading
                }
                _state.value = load()
            }
    }

    fun addPasskey() {
        val loaded = _state.value as? SignInMethodsUiState.Loaded ?: return
        if (loaded.isAddingPasskey || !loaded.canAddPasskey) return
        _state.value = loaded.copy(isAddingPasskey = true)

        viewModelScope.launch {
            accountRepository
                .addPasskey()
                .onSuccess { passkey ->
                    _events.send(SignInMethodsEvent.PasskeyAdded(passkey.displayName()))
                }.onFailure { error ->
                    addPasskeyFailure(error)?.let { _events.send(SignInMethodsEvent.AddPasskeyFailed(it)) }
                }
            _state.value = load().withAddingPasskey(false)
        }
    }

    fun removePasskey(credentialId: String) {
        val loaded = _state.value as? SignInMethodsUiState.Loaded ?: return
        if (loaded.removingCredentialId != null) return
        _state.value = loaded.copy(removingCredentialId = credentialId)

        viewModelScope.launch {
            accountRepository
                .deletePasskey(credentialId)
                .onSuccess { _events.send(SignInMethodsEvent.PasskeyRemoved) }
                .onFailure { error ->
                    Napier.w("Failed to remove passkey", error)
                    _events.send(SignInMethodsEvent.RemovePasskeyFailed(removePasskeyFailure(error)))
                }
            _state.value = load()
        }
    }

    private suspend fun load(): SignInMethodsUiState {
        val (passkeysResult, providersResult) =
            coroutineScope {
                val passkeys = async { accountRepository.listPasskeys() }
                val providers = async { accountRepository.listLinkedSignInProviders() }
                passkeys.await() to providers.await()
            }

        val passkeys = passkeysResult.getOrElse { return failedToLoad(it) }
        // Passkeys are the main thing here; a server that can't list linked accounts shouldn't hide them.
        val providers =
            providersResult
                .onFailure { Napier.w("Could not load linked sign-in providers", it) }
                .getOrDefault(emptyList())
        val platformSupportsPasskeys =
            runCatching { passkeyManager.getCapabilities().isSupported }
                .onFailure { Napier.w("Could not read this device's passkey support", it) }
                .getOrDefault(false)
        val canAddPasskey = platformSupportsPasskeys && connectedRpId()?.let(passkeysWorkWith) != false

        val signInMethodCount = passkeys.size + providers.size
        return SignInMethodsUiState.Loaded(
            passkeys =
                passkeys
                    .sortedBy { it.createdAt }
                    .map { passkey ->
                        PasskeyRow(
                            credentialId = passkey.credentialId,
                            deviceName = passkey.displayName(),
                            addedOn = passkey.createdAt.toLocalDate(),
                            lastUsedOn = passkey.lastUsedAt?.toLocalDate(),
                            canRemove = signInMethodCount > 1,
                        )
                    },
            linkedProviders =
                providers.map { provider ->
                    LinkedProviderRow(
                        kind = provider.kind,
                        email = provider.email,
                        lastSignInOn = provider.lastSignInAt?.toLocalDate(),
                    )
                },
            canAddPasskey = canAddPasskey,
        )
    }

    private fun failedToLoad(error: Throwable): SignInMethodsUiState.Failed {
        Napier.w("Failed to load sign-in methods", error)
        val reason =
            when {
                error is NotSignedInException -> SignInMethodsLoadError.NOT_SIGNED_IN
                error.isNetworkError() -> SignInMethodsLoadError.OFFLINE
                else -> SignInMethodsLoadError.SERVER
            }
        return SignInMethodsUiState.Failed(reason)
    }

    private fun PasskeyInfo.displayName(): String? = nickname?.trim()?.takeIf { it.isNotEmpty() && it != defaultPasskeyName() }

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date
}

private fun SignInMethodsUiState.withAddingPasskey(adding: Boolean): SignInMethodsUiState =
    if (this is SignInMethodsUiState.Loaded) copy(isAddingPasskey = adding) else this

/** Maps an add-passkey failure to what the person is told, or `null` when they cancelled. */
private fun addPasskeyFailure(error: Throwable): AddPasskeyFailure? {
    if (error is PasskeyException) {
        return when (error.errorCode) {
            PasskeyErrorCodes.USER_CANCELLED -> null
            PasskeyErrorCodes.INVALID_STATE, PasskeyErrorCodes.CONSTRAINT_ERROR -> AddPasskeyFailure.ALREADY_ON_THIS_DEVICE
            else -> AddPasskeyFailure.PLATFORM.also { Napier.w("The platform could not create a passkey", error) }
        }
    }
    Napier.w("Failed to add a passkey", error)
    return when {
        error is NotSignedInException -> AddPasskeyFailure.NOT_SIGNED_IN
        error.isNetworkError() -> AddPasskeyFailure.OFFLINE
        else -> AddPasskeyFailure.SERVER
    }
}

private fun removePasskeyFailure(error: Throwable): RemovePasskeyFailure =
    when {
        (error as? PasskeyApiException)?.errorCode == PasskeyApiErrorCodes.LAST_SIGNIN_FACTOR -> RemovePasskeyFailure.LAST_SIGN_IN_METHOD
        error.isNetworkError() -> RemovePasskeyFailure.OFFLINE
        else -> RemovePasskeyFailure.SERVER
    }

private fun Throwable.isNetworkError(): Boolean = (this as? PasskeyApiException)?.errorCode == PasskeyApiErrorCodes.NETWORK_ERROR
