package app.logdate.mobile.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.Component
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * An interface for managing biometric authentication.
 *
 * This may be used to authenticate the user using biometrics, and provides a state flow that can be
 * observed to determine the current state of the app's biometric authentication.
 *
 * Implementations of this interface should handle the platform-specific APIs for biometric
 * authentication.
 */
interface BiometricGatekeeper {
    val authState: StateFlow<AppAuthState>

    /**
     * Performs biometric authentication.
     *
     * If the user has not enabled biometric authentication, the [authState] will be set to
     * [AppAuthState.NO_PROMPT_NEEDED] to indicate that no prompt is needed.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Making sure it's you",
        subtitle: String = "Continue using your fingerprint or face ID",
        cancelLabel: String = "Cancel",
        requireConfirmation: Boolean = false,
        requestEnrollmentIfNecessary: Boolean = true,
        description: String? = null,
    )

    /**
     * Request that the user enroll in biometric authentication.
     */
    fun requestEnrollment(activity: FragmentActivity)
    // TODO: Decouple this interface from Android-specific APIs
}


@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncComponentDependencies {
    fun getBiometricActivity(): FragmentActivity
}

@Component(dependencies = [SyncComponentDependencies::class])
interface BiometricGatekeeperComponent {
    fun inject(biometricActivity: FragmentActivity)
}

interface BiometricActivityProvider {
    fun provideBiometricActivity(): FragmentActivity
}

//fun getBiometricActivity(context: Context): FragmentActivity {
//    val hiltEntryPoint = EntryPointAccessors.fromApplication(
//        context, BiometricGatekeeperEntryPoint::class.java
//    )
//
//    return hiltEntryPoint.getBiometricGatekeeper()
//}

/**
 * An implementation of [BiometricGatekeeper] that uses the Android Biometric API.
 */
class AndroidBiometricGatekeeper @Inject constructor(
    // TODO: Load whether biometric authentication is enabled from the user datastore
) : BiometricGatekeeper {

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

    fun updateAuthState() {
    }

    /**
     * Authenticates the user using biometric authentication.
     *
     * If the user has not enabled biometric authentication, the [authState] will be set to
     * [AppAuthState.NO_PROMPT_NEEDED] to indicate that no prompt is needed.
     */
    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
    ) {
        // TODO: Do activity injection properly
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
                    requestEnrollment(activity)
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
    override fun requestEnrollment(activity: FragmentActivity) {
        val request = activity.registerForActivityResult(BiometricEnrollmentActivityContract()) {
            // TODO: Handle the result
        }
        request.launch(Unit)
    }
}

/**
 * A flag representing the current state of the app's biometric authentication.
 *
 * UI components can use this state to determine how to
 */
enum class AppAuthState {
    /**
     * The user has not enabled biometric authentication.
     */
    NO_PROMPT_NEEDED,

    /**
     * The user has enabled biometric authentication, but has not authenticated in the current session.
     */
    REQUIRE_PROMPT,

    /**
     * The user has recently authenticated with biometrics in the current session.
     */
    AUTHENTICATED,

    /**
     * The user has not enrolled in biometric authentication.
     */
    REQUEST_ENROLLMENT,

    /**
     * The user's device requires a security update to use biometric authentication.
     */
    UPDATE_REQUIRED,

    /**
     * The user's device does not support biometric authentication.
     */
    UNSUPPORTED,

    /**
     * An unknown error has occurred.
     *
     * The user should be prompted to restart.
     */
    UNKNOWN,
}

private class BiometricEnrollmentActivityContract : ActivityResultContract<Unit, Unit>() {

    companion object {
        const val REQUEST_CODE = 100
    }

    @RequiresApi(Build.VERSION_CODES.R)
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