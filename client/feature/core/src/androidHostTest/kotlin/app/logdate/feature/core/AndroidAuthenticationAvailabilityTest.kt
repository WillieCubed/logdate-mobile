package app.logdate.feature.core

import androidx.biometric.BiometricManager
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidAuthenticationAvailabilityTest {
    private val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private val combined = BiometricManager.Authenticators.BIOMETRIC_STRONG or credential

    @Test
    fun `available combined authentication does not require a second availability query`() {
        val queries = mutableListOf<Int>()
        val result =
            resolveAndroidAuthenticationAvailability {
                queries += it
                BiometricManager.BIOMETRIC_SUCCESS
            }
        assertEquals(AndroidAuthenticationAvailability.Ready(combined), result)
        assertEquals(listOf(combined), queries)
    }

    @Test
    fun `an enrolled device credential recovers every unavailable biometric result`() {
        unavailableResults.forEach { failure ->
            val result =
                resolveAndroidAuthenticationAvailability {
                    if (it == credential) BiometricManager.BIOMETRIC_SUCCESS else failure
                }
            assertEquals(AndroidAuthenticationAvailability.Ready(credential), result, failure.toString())
        }
    }

    @Test
    fun `missing destination credential offers enrollment even when the sensor is unavailable`() {
        unavailableResults.forEach { failure ->
            val result =
                resolveAndroidAuthenticationAvailability {
                    if (it == credential) BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED else failure
                }
            assertEquals(
                AndroidAuthenticationAvailability.Unavailable(AppAuthState.REQUEST_ENROLLMENT),
                result,
                failure.toString(),
            )
        }
    }

    @Test
    fun `unavailable device authentication retains a locked actionable state`() {
        mapOf(
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED to AppAuthState.REQUEST_ENROLLMENT,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED to AppAuthState.UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE to AppAuthState.UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE to AppAuthState.UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED to AppAuthState.UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN to AppAuthState.UNKNOWN,
            999 to AppAuthState.UNKNOWN,
        ).forEach { (failure, state) ->
            assertEquals(
                AndroidAuthenticationAvailability.Unavailable(state),
                resolveAndroidAuthenticationAvailability { failure },
            )
        }
    }

    private val unavailableResults =
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        )
}
