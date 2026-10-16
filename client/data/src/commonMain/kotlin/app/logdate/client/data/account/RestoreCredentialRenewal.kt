package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.io.encoding.Base64

/** Remembers a consumed restore key until the signed-in installation registers its replacement. */
class RestoreCredentialRenewal(
    private val storage: SecureStorage,
    private val apiFactory: ((LogDateConfigRepository) -> PasskeyApiClientContract)? = null,
) {
    suspend fun mark(
        origin: String,
        owner: String,
    ) = storage.putString(key(origin), owner)

    suspend fun isPending(
        origin: String,
        owner: String,
    ): Boolean = storage.getString(key(origin)) == owner

    suspend fun complete(origin: String) = storage.putString(key(origin), "resolved")

    internal suspend fun renew(
        config: LogDateConfigRepository,
        session: UserSession,
        credentials: RestoreCredentialManager,
        platform: PlatformAccountManager,
        isCurrent: () -> Boolean,
    ) {
        val origin = config.getCurrentBackendUrl()
        if (!isPending(origin, session.accountId)) return
        val canonicalOrigin = origin.trimEnd('/')
        require(config.getCurrentApiBaseUrl().startsWith("$canonicalOrigin/"))
        require(
            config
                .getCurrentServerDescriptor()
                ?.serverOrigin
                ?.trimEnd('/')
                ?.let { it == canonicalOrigin } != false,
        )
        val api = apiFactory?.invoke(config) ?: return

        fun checkCurrent() {
            check(isCurrent()) { "Restore key renewal account changed" }
        }
        checkCurrent()
        val account = api.getAccountInfo(session.accessToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        check(account.id.toString() == session.accountId)
        platform.updateTokens(account.username, origin, session.accessToken, session.refreshToken).onFailure {
            currentCoroutineContext().ensureActive()
            checkCurrent()
            platform.addAccount(account, session.accessToken, session.refreshToken, origin).getOrThrow()
        }
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val options = api.beginRestoreKeyRegistration(session.accessToken).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        val credential = credentials.createRestoreKey(options).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        api.completeRestoreKeyRegistration(session.accessToken, credential, options.challenge).getOrThrow()
        currentCoroutineContext().ensureActive()
        checkCurrent()
        complete(origin)
    }

    private fun key(origin: String): String = "restore_key_renewal_${Base64.UrlSafe.encode(origin.encodeToByteArray())}"
}
