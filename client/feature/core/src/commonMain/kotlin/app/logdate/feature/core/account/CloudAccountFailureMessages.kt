package app.logdate.feature.core.account

import app.logdate.client.domain.account.AuthenticateWithPasskeyUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.networking.DiscoveryFailure
import app.logdate.client.networking.ServerDiscoveryException
import app.logdate.feature.core.settings.ui.ServerPreset

/**
 * What to tell the person when the server they chose could not be set up: what failed and what to
 * do next, never the exception's own text, which names hosts and status codes.
 */
internal fun serverSelectionFailureMessage(
    error: Throwable,
    server: ServerPreset,
): String {
    val cloud = server == ServerPreset.PRODUCTION
    val kind =
        when (error) {
            is ServerDiscoveryException -> error.kind
            // A custom server that is reachable but lacks the account protocol is refused before
            // it is saved; anything else from the custom checks means it could not be reached.
            is IllegalArgumentException -> return "That server needs an update before LogDate can use it. Ask whoever runs it to update it."
            else -> DiscoveryFailure.UNREACHABLE
        }
    return when (kind) {
        DiscoveryFailure.UNREACHABLE ->
            if (cloud) {
                "Can’t reach LogDate Cloud. Check your internet connection, then try again."
            } else {
                "Can’t reach that server. Check the address and your internet connection, then try again."
            }
        DiscoveryFailure.NOT_RESPONDING ->
            if (cloud) {
                "LogDate Cloud isn’t responding right now. Try again in a few minutes."
            } else {
                "That server isn’t responding right now. Try again in a few minutes."
            }
        DiscoveryFailure.UNREADABLE ->
            if (cloud) {
                "LogDate Cloud answered with something this version of LogDate can’t read. Update LogDate, then try again."
            } else {
                "That address didn’t answer like a LogDate server. Check the address, then try again."
            }
    }
}

/** What to tell the person when signing in did not finish. */
internal fun signInFailureMessage(error: AuthenticateWithPasskeyUseCase.AuthenticationError): String =
    when (error) {
        AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyNotSupported ->
            "Passkeys are not supported on this device."
        AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyCancelled ->
            "Authentication was cancelled. Please try again."
        AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyFailed ->
            "Failed to authenticate with passkey. Please try again."
        AuthenticateWithPasskeyUseCase.AuthenticationError.NoCredentialsFound ->
            "No passkey found for this account. Please check your username or create a new account."
        AuthenticateWithPasskeyUseCase.AuthenticationError.AccountNotFound ->
            "Account not found. Please check your username or create a new account."
        AuthenticateWithPasskeyUseCase.AuthenticationError.NetworkError ->
            "Can’t reach the server. Check your internet connection, then try again."
        AuthenticateWithPasskeyUseCase.AuthenticationError.ServerUnavailable ->
            "The server isn’t responding right now. Try again in a few minutes."
        AuthenticateWithPasskeyUseCase.AuthenticationError.LocalDataNeedsAdoption ->
            "This device has entries that aren't in an account yet."
        is AuthenticateWithPasskeyUseCase.AuthenticationError.Unknown ->
            // The exception text names servers, key hashes and file paths. It is already in
            // the log; the user gets something they can act on.
            "Something went wrong. Please try again."
    }

/** What to tell the person when creating an account did not finish. */
internal fun accountCreationFailureMessage(error: CreatePasskeyAccountUseCase.CreateAccountError): String =
    when (error) {
        CreatePasskeyAccountUseCase.CreateAccountError.UsernameTaken ->
            "Username is already taken. Please choose a different one."
        CreatePasskeyAccountUseCase.CreateAccountError.UsernameInvalid ->
            "Username is invalid. Use only letters, numbers, and underscores."
        CreatePasskeyAccountUseCase.CreateAccountError.DisplayNameInvalid ->
            "Display name is invalid. Please enter a valid name."
        CreatePasskeyAccountUseCase.CreateAccountError.PasskeyNotSupported ->
            "Passkeys are not supported on this device."
        CreatePasskeyAccountUseCase.CreateAccountError.PasskeyCancelled ->
            "Passkey creation was cancelled. Please try again."
        CreatePasskeyAccountUseCase.CreateAccountError.PasskeyFailed ->
            "Failed to create passkey. Please check your device security settings."
        CreatePasskeyAccountUseCase.CreateAccountError.NetworkError ->
            "Can’t reach the server. Check your internet connection, then try again."
        CreatePasskeyAccountUseCase.CreateAccountError.RateLimited ->
            "Too many attempts. Please wait a moment before trying again."
        CreatePasskeyAccountUseCase.CreateAccountError.ServerError ->
            "The server isn’t responding right now. Try again in a few minutes."
        is CreatePasskeyAccountUseCase.CreateAccountError.Unknown ->
            // The exception text names servers, key hashes and file paths. It is already in
            // the log; the user gets something they can act on.
            "Something went wrong. Please try again."
    }
