package app.logdate.client.permissions

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.aakira.napier.Napier

/**
 * Android [GoogleSignInManager] backed by Credential Manager + Google Identity Services
 * ([GetGoogleIdOption]). Mirrors [AndroidPasskeyManager]'s use of [CredentialManager].
 */
class AndroidGoogleSignInManager(
    private val context: Context,
) : GoogleSignInManager {
    private val credentialManager = CredentialManager.create(context)

    override suspend fun getGoogleIdToken(
        serverClientId: String,
        nonce: String?,
    ): Result<String> {
        if (serverClientId.isBlank()) {
            return Result.failure(GoogleSignInException("Google sign-in is not configured"))
        }
        return try {
            val googleIdOption =
                GetGoogleIdOption
                    .Builder()
                    .setServerClientId(serverClientId)
                    // Show all Google accounts, not only ones already authorized for this app, so a
                    // first-time user can pick an account.
                    .setFilterByAuthorizedAccounts(false)
                    .apply { nonce?.let { setNonce(it) } }
                    .build()

            val request =
                GetCredentialRequest
                    .Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            Result.success(idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleSignInException("Google sign-in was cancelled", e))
        } catch (e: NoCredentialException) {
            Result.failure(GoogleSignInException("No Google account is available on this device", e))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(GoogleSignInException("Received an invalid Google ID token", e))
        } catch (e: GetCredentialException) {
            Napier.w("Google credential request failed", e)
            Result.failure(GoogleSignInException("Google sign-in failed", e))
        } catch (e: Exception) {
            Napier.w("Unexpected Google sign-in failure", e)
            Result.failure(GoogleSignInException("Google sign-in failed", e))
        }
    }
}
