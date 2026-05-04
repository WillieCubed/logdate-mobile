package app.logdate.feature.core

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication

/**
 * iOS implementation of [BiometricGatekeeper] backed by `LocalAuthentication`.
 *
 * Uses [LAPolicyDeviceOwnerAuthentication] so the system prompt automatically falls back
 * to the device passcode when biometrics fail or are unavailable.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBiometricGatekeeper : BiometricGatekeeper {
    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

    override val authState: StateFlow<AppAuthState> = _authState.asStateFlow()

    override fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
        onResult: (AppAuthState) -> Unit,
    ) {
        val context = LAContext()
        context.localizedCancelTitle = cancelLabel

        val canEvaluate =
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val ok = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, errorPtr.ptr)
                if (!ok) {
                    val error = errorPtr.value
                    val state = mapPolicyAvailabilityError(error)
                    Napier.w(
                        "LAContext cannot evaluate policy: code=${error?.code} domain=${error?.domain}",
                        tag = TAG,
                    )
                    _authState.value = state
                    onResult(state)
                }
                ok
            }
        if (!canEvaluate) return

        val reason = description ?: subtitle
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { success, error ->
            val state =
                if (success) {
                    AppAuthState.AUTHENTICATED
                } else {
                    Napier.i(
                        "LAContext authentication failed: code=${error?.code} domain=${error?.domain}",
                        tag = TAG,
                    )
                    mapAuthenticationError(error)
                }
            _authState.value = state
            onResult(state)
        }
    }

    override fun requestEnrollment() {
        // No-op on iOS: there is no public API to enroll biometrics. With
        // LAPolicyDeviceOwnerAuthentication, missing biometric enrollment is handled
        // automatically by falling back to the device passcode.
    }

    private fun mapPolicyAvailabilityError(error: NSError?): AppAuthState =
        when (error?.code) {
            LAErrorPasscodeNotSet, LAErrorBiometryNotAvailable -> AppAuthState.UNSUPPORTED
            LAErrorBiometryNotEnrolled -> AppAuthState.REQUEST_ENROLLMENT
            null -> AppAuthState.UNKNOWN
            else -> AppAuthState.UNKNOWN
        }

    private fun mapAuthenticationError(error: NSError?): AppAuthState =
        when (error?.code) {
            LAErrorUserCancel,
            LAErrorAppCancel,
            LAErrorSystemCancel,
            LAErrorUserFallback,
            LAErrorAuthenticationFailed,
            -> AppAuthState.REQUIRE_PROMPT
            LAErrorPasscodeNotSet, LAErrorBiometryNotAvailable -> AppAuthState.UNSUPPORTED
            LAErrorBiometryNotEnrolled -> AppAuthState.REQUEST_ENROLLMENT
            else -> AppAuthState.UNKNOWN
        }

    private companion object {
        const val TAG = "IosBiometricGatekeeper"
    }
}
