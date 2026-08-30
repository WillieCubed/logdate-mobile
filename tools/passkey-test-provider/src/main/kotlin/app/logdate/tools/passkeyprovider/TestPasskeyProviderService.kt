package app.logdate.tools.passkeyprovider

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.provider.BeginGetCredentialOption
import android.os.OutcomeReceiver
import org.json.JSONObject

/**
 * Credential Manager entry point for the emulator-only test authenticator.
 *
 * The system calls the `onBegin*` methods to ask what this provider can offer; each returned entry
 * carries a [PendingIntent] into [CeremonyActivity], which performs the actual WebAuthn ceremony
 * once the user picks it.
 */
class TestPasskeyProviderService : CredentialProviderService() {

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onResult(BeginCreateCredentialResponse())
            return
        }
        val userName = runCatching {
            JSONObject(request.requestJson).getJSONObject("user").getString("name")
        }.getOrDefault("LogDate test user")

        val response =
            BeginCreateCredentialResponse.Builder()
                .addCreateEntry(
                    CreateEntry(
                        accountName = "$userName (test authenticator)",
                        pendingIntent = ceremonyIntent(CeremonyActivity.ACTION_CREATE, request.callingAppInfo),
                    ),
                )
                .build()
        callback.onResult(response)
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val store = CredentialStore(applicationContext)
        val builder = BeginGetCredentialResponse.Builder()

        request.beginGetCredentialOptions.forEach { option ->
            if (option !is BeginGetPublicKeyCredentialOption) return@forEach
            val rpId = runCatching { JSONObject(option.requestJson).getString("rpId") }.getOrNull() ?: return@forEach
            store.forRpId(rpId).forEach { credential ->
                builder.addCredentialEntry(
                    publicKeyEntry(credential, option, request.callingAppInfo),
                )
            }
        }
        callback.onResult(builder.build())
    }

    private fun publicKeyEntry(
        credential: StoredCredential,
        option: BeginGetCredentialOption,
        callingAppInfo: CallingAppInfo?,
    ): PublicKeyCredentialEntry =
        PublicKeyCredentialEntry.Builder(
            context = applicationContext,
            username = credential.userName,
            pendingIntent = ceremonyIntent(
                action = CeremonyActivity.ACTION_GET,
                callingAppInfo = callingAppInfo,
                credentialId = credential.credentialId,
            ),
            beginGetPublicKeyCredentialOption = option as BeginGetPublicKeyCredentialOption,
        )
            .setDisplayName("LogDate test authenticator")
            .build()

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    /**
     * Each entry needs its own PendingIntent. The request code keeps them distinct so the system
     * does not collapse several entries onto one intent.
     */
    private fun ceremonyIntent(
        action: String,
        callingAppInfo: CallingAppInfo?,
        credentialId: String? = null,
    ): PendingIntent {
        val intent =
            Intent(applicationContext, CeremonyActivity::class.java)
                .setAction(action)
                .putExtra(CeremonyActivity.EXTRA_CREDENTIAL_ID, credentialId)
                .putExtra(CeremonyActivity.EXTRA_CALLING_PACKAGE, callingAppInfo?.packageName)
        return PendingIntent.getActivity(
            applicationContext,
            requestCode++,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        var requestCode = 1
    }
}
