package app.logdate.client.permissions

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Android implementation of [RestoreCredentialManager] using the AndroidX Credentials library.
 *
 * Uses [CreateRestoreCredentialRequest] and [GetRestoreCredentialOption] from the restore
 * credentials API (androidx.credentials 1.5+). The restore key is backed up to the user's
 * encrypted Android cloud backup and can be silently redeemed on a restored device.
 */
class AndroidRestoreCredentialManager(
    private val context: Context,
) : RestoreCredentialManager {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> =
        try {
            val request = CreateRestoreCredentialRequest(buildRestoreRegistrationJson(options))
            val response = credentialManager.createCredential(context, request) as CreateRestoreCredentialResponse
            Napier.i("Restore key created successfully")
            Result.success(response.responseJson)
        } catch (e: E2eeUnavailableException) {
            Napier.w("E2EE backup unavailable — restore key creation skipped", e)
            Result.failure(RestoreCredentialError.BackupUnavailable(e))
        } catch (e: Exception) {
            Napier.e("Failed to create restore key", e)
            Result.failure(RestoreCredentialError.Unknown("Failed to create restore key", e))
        }

    override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> =
        try {
            val option = GetRestoreCredentialOption(buildRestoreAuthenticationJson(options))
            val request = GetCredentialRequest(listOf(option))
            val response = credentialManager.getCredential(context, request)
            when (val credential = response.credential) {
                is RestoreCredential -> {
                    Napier.i("Restore credential retrieved successfully")
                    Result.success(credential.authenticationResponseJson)
                }
                else -> {
                    Napier.w("Unexpected restore credential type: ${credential::class.simpleName}")
                    Result.failure(RestoreCredentialError.Unknown("Unexpected restore credential type"))
                }
            }
        } catch (e: NoCredentialException) {
            Napier.d("No restore credential on device — proceeding with normal sign-in flow")
            Result.failure(RestoreCredentialError.NoCredential(e))
        } catch (e: GetCredentialException) {
            when {
                e.message?.contains("CANCELLED", ignoreCase = true) == true ->
                    Result.failure(RestoreCredentialError.Cancelled(e))
                else -> {
                    Napier.e("Failed to retrieve restore credential", e)
                    Result.failure(RestoreCredentialError.Unknown("Failed to retrieve restore credential", e))
                }
            }
        } catch (e: Exception) {
            Napier.e("Unexpected error retrieving restore credential", e)
            Result.failure(RestoreCredentialError.Unknown("Unexpected error retrieving restore credential", e))
        }

    override suspend fun clearRestoreCredential(): Result<Unit> =
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL))
            Napier.i("Restore credential cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to clear restore credential (non-fatal)", e)
            Result.failure(RestoreCredentialError.Unknown("Failed to clear restore credential", e))
        }
}

private fun buildRestoreRegistrationJson(options: PasskeyRegistrationOptions): String =
    buildJsonObject {
        putJsonObject("rp") {
            put("id", options.rpId)
            put("name", options.rpName)
        }
        putJsonObject("user") {
            put("id", options.user.id)
            put("name", options.user.name)
            put("displayName", options.user.displayName)
        }
        put("challenge", options.challenge)
        putJsonArray("pubKeyCredParams") {
            add(
                buildJsonObject {
                    put("type", "public-key")
                    put("alg", -7)
                },
            )
            add(
                buildJsonObject {
                    put("type", "public-key")
                    put("alg", -257)
                },
            )
        }
        put("timeout", options.timeout)
        putJsonArray("excludeCredentials") {
            options.excludeCredentials.forEach { add(JsonPrimitive(it)) }
        }
        putJsonObject("authenticatorSelection") {
            put("requireResidentKey", false)
            put("residentKey", "preferred")
            put("userVerification", "preferred")
        }
        put("attestation", "none")
    }.toString()

private fun buildRestoreAuthenticationJson(options: PasskeyAuthenticationOptions): String =
    buildJsonObject {
        put("challenge", options.challenge)
        put("timeout", options.timeout)
        put("rpId", options.rpId)
        putJsonArray("allowCredentials") {
            options.allowCredentials.forEach { add(JsonPrimitive(it)) }
        }
        put("userVerification", "preferred")
    }.toString()
