package app.logdate.feature.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * An implementation of [BiometricGatekeeper] that uses the Android Biometric API.
 */
class AndroidBiometricGatekeeper(
    // TODO: Load whether biometric authentication is enabled from the user datastore
//    private val activity: FragmentActivity,
) : BiometricGatekeeper {
    private var activityRef = WeakReference<FragmentActivity>(null)

    private val activity: FragmentActivity
        get() =
            activityRef.get()
                ?: throw IllegalStateException("Activity reference must be initialized using setActivity(FragmentActivity).")

    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

    override val authState: StateFlow<AppAuthState> = _authState

    /**
     * Authenticates the user using biometric authentication.
     *
     * If the user has not enabled biometric authentication, the [authState] will be set to
     * [AppAuthState.NO_PROMPT_NEEDED] to indicate that no prompt is needed.
     */
    override fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
        onResult: (AppAuthState) -> Unit,
    ) {
        val biometricManager = BiometricManager.from(activity)
        val executor = ContextCompat.getMainExecutor(activity)
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Napier.e("Biometric authentication error: $errorCode, $errString", tag = TAG)
                    _authState.value = AppAuthState.REQUIRE_PROMPT
                    onResult(AppAuthState.REQUIRE_PROMPT)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _authState.value = AppAuthState.AUTHENTICATED
                    onResult(AppAuthState.AUTHENTICATED)
                }
            }
        when (
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Napier.d("App can authenticate using biometrics.", tag = TAG)
                BiometricPrompt(activity, executor, callback).authenticate(
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .apply {
                            if (description != null) {
                                setDescription(description)
                            }
                        }.setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        ).setConfirmationRequired(requireConfirmation)
                        // TODO: Only apply negative button text if device credential authentication is allowed
//                        .setNegativeButtonText(cancelLabel)
                        .build(),
                )
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Napier.e(
                    "Biometric features are currently unavailable.",
                    tag = TAG,
                )
                _authState.value = AppAuthState.UNSUPPORTED
                onResult(AppAuthState.UNSUPPORTED)
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                _authState.value = AppAuthState.REQUEST_ENROLLMENT
                onResult(AppAuthState.REQUEST_ENROLLMENT)
                if (requestEnrollmentIfNecessary) {
                    requestEnrollment()
                }
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            -> {
                Napier.d(
                    "Biometric authentication is not supported on this device.",
                    tag = TAG,
                )
                _authState.value = AppAuthState.UNSUPPORTED
                onResult(AppAuthState.UNSUPPORTED)
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                _authState.value = AppAuthState.UNKNOWN
                onResult(AppAuthState.UNKNOWN)
            }
        }
    }

    /**
     * Requests that the user enroll in biometric authentication.
     *
     * This will launch the system biometric enrollment activity.
     */
    override fun requestEnrollment() {
        val request =
            activity.registerForActivityResult(BiometricEnrollmentActivityContract()) {
                // TODO: Handle the result
            }
        request.launch(Unit)
    }

    /**
     * Sets the [FragmentActivity] that will be used to launch the biometric prompt.
     *
     * This must be called before calling [authenticate] or [requestEnrollment].
     */
    fun setActivity(fragmentActivity: FragmentActivity) {
        activityRef.clear()
        activityRef = WeakReference(fragmentActivity)
    }

    private companion object {
        const val TAG = "BiometricGatekeeper"
    }
}

private class BiometricEnrollmentActivityContract : ActivityResultContract<Unit, Unit>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ) = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        Napier.d("Biometric enrollment result: $resultCode", tag = "BiometricEnrollment")
    }
}
