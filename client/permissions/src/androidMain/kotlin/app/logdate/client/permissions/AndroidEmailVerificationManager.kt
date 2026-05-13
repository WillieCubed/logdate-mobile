package app.logdate.client.permissions

import android.content.Context
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.logdate.client.networking.EmailVerificationApiClientContract
import app.logdate.client.networking.EmailVerificationCompletion
import app.logdate.shared.model.BeginEmailVerificationResponse
import app.logdate.shared.model.CompleteEmailVerificationRequest
import io.github.aakira.napier.Napier

/**
 * Drives the Android Credential Manager Digital Credentials flow for verifying
 * a user's email via Google's `UserInfoCredential` issuer.
 *
 * 1. Asks the server for a server-bound nonce + transactionId.
 * 2. Hands an OpenID4VP request to [CredentialManager.getCredential] using
 *    [GetDigitalCredentialOption], which surfaces the system wallet picker.
 * 3. Forwards the returned [DigitalCredential.credentialJson] to the server's
 *    /complete endpoint, which cryptographically verifies the SD-JWT VC and
 *    attaches the email to the account.
 *
 * Every documented outcome (cancel, no credential, untrusted issuer, etc.)
 * maps to a value of [EmailVerificationOutcome]; nothing throws.
 */
@OptIn(ExperimentalDigitalCredentialApi::class)
class AndroidEmailVerificationManager(
    private val context: Context,
    private val api: EmailVerificationApiClientContract,
) : EmailVerificationManager {
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    override val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    override suspend fun verifyEmail(accessToken: String): EmailVerificationOutcome {
        if (!isSupported) return EmailVerificationOutcome.Unsupported

        val challenge =
            api.begin(accessToken).getOrElse {
                Napier.w("email-verification: begin failed", it)
                return EmailVerificationOutcome.Failed("begin_failed:${it.message ?: "unknown"}")
            }

        val credentialJson =
            try {
                val response =
                    credentialManager.getCredential(
                        context = context,
                        request =
                            GetCredentialRequest(
                                listOf(GetDigitalCredentialOption(requestJson = buildOpenId4VpRequest(challenge))),
                            ),
                    )
                (response.credential as? DigitalCredential)?.credentialJson
                    ?: return EmailVerificationOutcome.Failed("unexpected_credential_type")
            } catch (e: GetCredentialCancellationException) {
                return EmailVerificationOutcome.UserCancelled
            } catch (e: NoCredentialException) {
                return EmailVerificationOutcome.NoCredentialAvailable
            } catch (e: GetCredentialException) {
                Napier.w("email-verification: credential manager rejected request (${e.type})", e)
                return EmailVerificationOutcome.Failed("dc_error:${e.type}")
            }

        val completion =
            api
                .complete(
                    accessToken = accessToken,
                    request =
                        CompleteEmailVerificationRequest(
                            transactionId = challenge.transactionId,
                            credentialJson = credentialJson,
                        ),
                ).getOrElse {
                    Napier.w("email-verification: complete failed", it)
                    return EmailVerificationOutcome.Failed("complete_failed:${it.message ?: "unknown"}")
                }

        return when (completion) {
            is EmailVerificationCompletion.Success ->
                EmailVerificationOutcome.Success(completion.email, completion.verifiedAt)
            is EmailVerificationCompletion.Conflict ->
                EmailVerificationOutcome.Conflict(completion.message)
            is EmailVerificationCompletion.Failed ->
                EmailVerificationOutcome.Failed(completion.reason)
        }
    }

    /**
     * Builds the OpenID4VP DCQL query for a Google `UserInfoCredential` per the
     * Android Digital Credentials email-verification doc. The shape is fixed —
     * Google rejects any deviation in protocol / format / vct.
     */
    private fun buildOpenId4VpRequest(challenge: BeginEmailVerificationResponse): String =
        """
        {
          "requests": [
            {
              "protocol": "openid4vp-v1-unsigned",
              "data": {
                "response_type": "vp_token",
                "response_mode": "dc_api",
                "nonce": "${challenge.nonce}",
                "client_id": "${challenge.audience}",
                "dcql_query": {
                  "credentials": [
                    {
                      "id": "user_info_query",
                      "format": "dc+sd-jwt",
                      "meta": { "vct_values": ["UserInfoCredential"] },
                      "claims": [
                        {"path": ["email"]},
                        {"path": ["email_verified"]},
                        {"path": ["name"]},
                        {"path": ["given_name"]},
                        {"path": ["family_name"]},
                        {"path": ["picture"]},
                        {"path": ["hd"]}
                      ]
                    }
                  ]
                }
              }
            }
          ]
        }
        """.trimIndent()
}
