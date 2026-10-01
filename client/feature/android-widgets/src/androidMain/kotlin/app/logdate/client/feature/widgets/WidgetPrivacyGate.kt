package app.logdate.client.feature.widgets

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.AppSecurityLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Private widget setup and playback must honor the app's configured lock. */
suspend fun requireWidgetContentAccess(
    readSecurityLevel: suspend () -> AppSecurityLevel?,
    authenticate: suspend () -> Boolean,
): Boolean =
    when (runCatching { readSecurityLevel() }.getOrNull()) {
        AppSecurityLevel.NONE -> true
        AppSecurityLevel.BIOMETRIC -> runCatching { authenticate() }.getOrDefault(false)
        AppSecurityLevel.PASSWORD, null -> false
    }

internal suspend fun FragmentActivity.unlockWidgetContent(userStateRepository: UserStateRepository): Boolean =
    requireWidgetContentAccess(
        readSecurityLevel = { userStateRepository.userData.first().securityLevel },
        authenticate = { authenticateWidgetContent() },
    )

private suspend fun FragmentActivity.authenticateWidgetContent(): Boolean =
    suspendCancellableCoroutine { continuation ->
        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(getString(R.string.widget_unlock_title))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    ).build(),
            )
        }.onFailure { error ->
            Napier.w("Unable to authenticate widget access", error)
            if (continuation.isActive) continuation.resume(false)
        }
    }
