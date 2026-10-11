package app.logdate.server.identity

import app.logdate.server.config.AtprotoSigningKeyKek
import app.logdate.server.config.AtprotoSigningKeyKekDecryptOnly
import app.logdate.server.logging.KeyCustodyNotices
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
 * [AtprotoSigningKeyKek] once at startup, and logs the outcome as one of the [KeyCustodyNotices].
 * See server/docs/environment-variables.md for the order in which a deployment changes the two
 * secrets.
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
                when {
                    report.unreadable.isNotEmpty() -> Napier.e(KeyCustodyNotices.SIGNING_KEYS_UNREADABLE)
                    report.rewrapped > 0 -> Napier.i(KeyCustodyNotices.SIGNING_KEYS_REWRAPPED)
                    else -> Napier.i(KeyCustodyNotices.SIGNING_KEYS_CURRENT)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Napier.e(KeyCustodyNotices.SIGNING_KEYS_REWRAP_FAILED, exception)
            }
        }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
