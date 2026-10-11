package app.logdate.server.identity

import app.logdate.server.config.AtprotoSigningKeyKek
import app.logdate.server.config.AtprotoSigningKeyKekDecryptOnly
import app.logdate.server.oauth.OAuthSigningKeyRepository
import io.github.aakira.napier.Napier
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

/**
 * While [AtprotoSigningKeyKekDecryptOnly] is set, re-encrypts every stored signing key under
 * [AtprotoSigningKeyKek] once at startup. See server/docs/environment-variables.md for the order
 * in which a deployment changes the two secrets.
 */
internal fun Application.installStoredKeyRewrap() {
    val decryptOnlySeed = AtprotoSigningKeyKekDecryptOnly.resolve() ?: return
    val signingKeys by inject<SigningKeyRepository>()
    val oauthSigningKeys by inject<OAuthSigningKeyRepository>()
    val rewrap =
        StoredKeyRewrap(
            stores = mapOf("signing_keys" to signingKeys, "oauth_signing_keys" to oauthSigningKeys),
            cipher = AtRestKeyCipher(AtprotoSigningKeyKek.resolve(), listOf(decryptOnlySeed)),
        )
    val job =
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val report = rewrap.run()
                Napier.i(
                    "Stored signing keys re-encrypted under ${AtprotoSigningKeyKek.envVar}: " +
                        "${report.rewrapped} re-encrypted, ${report.alreadyCurrent} already current.",
                )
                if (report.unreadable.isNotEmpty()) {
                    Napier.e(
                        "${report.unreadable.size} stored signing key(s) can't be decrypted with " +
                            "${AtprotoSigningKeyKek.envVar} or ${AtprotoSigningKeyKekDecryptOnly.ENV_VAR} and were left " +
                            "unchanged: ${report.unreadable.joinToString()}",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Napier.e("Re-encrypting stored signing keys failed", exception)
            }
        }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
