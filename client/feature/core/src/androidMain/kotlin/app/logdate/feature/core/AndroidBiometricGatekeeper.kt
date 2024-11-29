package app.logdate.feature.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
        get() = activityRef.get()
            ?: throw IllegalStateException("Activity reference must be initialized using setActivity(FragmentActivity).")

    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

    override val authState: StateFlow<AppAuthState> = _authState

    private val biometricRequestCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Log.e("BiometricGatekeeper", "Biometric authentication error: $errorCode, $errString")
            _authState.value = AppAuthState.UNKNOWN
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            _authState.value = AppAuthState.AUTHENTICATED
        }

    }

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
    ) {
        val biometricManager = BiometricManager.from(activity)
        val executor = ContextCompat.getMainExecutor(activity)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("BiometricGatekeeper", "App can authenticate using biometrics.")
                BiometricPrompt(activity, executor, biometricRequestCallback).authenticate(
                    BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
                        .apply {
                            if (description != null) {
                                setDescription(description)
                            }
                        }
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .setConfirmationRequired(requireConfirmation)
                        // TODO: Only apply negative button text if device credential authentication is allowed
//                        .setNegativeButtonText(cancelLabel)
                        .build())
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Log.e(
                "BiometricGatekeeper", "Biometric features are currently unavailable."
            )

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                _authState.value = AppAuthState.REQUEST_ENROLLMENT
                if (requestEnrollmentIfNecessary) {
                    requestEnrollment()
                }
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
                -> {
                Log.d(
                    "BiometricGatekeeper",
                    "Biometric authentication is not supported on this device."
                )
                _authState.value = AppAuthState.UNSUPPORTED
            }

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                _authState.value = AppAuthState.UNKNOWN
            }
        }
    }

    /**
     * Requests that the user enroll in biometric authentication.
     *
     * This will launch the system biometric enrollment activity.
     */
    override fun requestEnrollment() {
        val request = activity.registerForActivityResult(BiometricEnrollmentActivityContract()) {
            // TODO: Handle the result
        }
        request.launch(Unit)
    }

    /**
     * Sets the [FragmentActivity] that will be used to launch the biometric prompt.
     *
     * This must be called before calling [authenticate] or [requestEnrollment].
     */
    fun setActivity(
        fragmentActivity: FragmentActivity,
    ) {
        activityRef.clear()
        activityRef = WeakReference(fragmentActivity)
    }
}

private class BiometricEnrollmentActivityContract : ActivityResultContract<Unit, Unit>() {

    override fun createIntent(context: Context, input: Unit) =
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?) {
        Log.d("BiometricEnrollmentActivityContract", "Biometric enrollment result: $resultCode")
    }
}