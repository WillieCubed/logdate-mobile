package app.logdate.feature.core

import android.content.Intent
import android.provider.Settings
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
 *
 * Whether app lock is on is not this class's business: `AppViewModel` reads the security level
 * from the datastore and decides when a prompt is required. This performs the challenge.
 */
class AndroidBiometricGatekeeper : BiometricGatekeeper {
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
        // Launched as a plain intent rather than through registerForActivityResult: this runs
        // from authenticate(), long after the activity has STARTED, and registering a launcher
        // that late throws. The result is not needed either -- when the user comes back,
        // authenticate() re-runs canAuthenticate() and sees whatever they actually enrolled.
        val intent =
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            }
        runCatching { activity.startActivity(intent) }
            .onFailure { Napier.e("Could not open biometric enrollment", it, tag = TAG) }
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
