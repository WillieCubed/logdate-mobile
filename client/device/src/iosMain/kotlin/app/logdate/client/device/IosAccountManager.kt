package app.logdate.client.device

import app.logdate.client.device.identity.KeychainWrapper
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * iOS [PlatformAccountManager] backed by the app keychain wrapper.
 */
class IosAccountManager(
    private val keychain: KeychainWrapper,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PlatformAccountManager {
    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val storedAccount = account.toStoredAccount(backendUrl)
                saveAccounts(loadAccounts().filterNot { it.key == storedAccount.key } + storedAccount)
                saveTokens(storedAccount.key, TokenPair(accessToken, refreshToken))
                Napier.i("Added LogDate account to iOS keychain: ${account.username}")
            }
        }

    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val storedAccount = account.toStoredAccount(backendUrl)
                val accounts = loadAccounts()
                require(accounts.any { it.key == storedAccount.key }) { "Account not found" }
                saveAccounts(accounts.map { if (it.key == storedAccount.key) storedAccount else it })
                Napier.i("Updated LogDate account in iOS keychain: ${account.username}")
            }
        }

    override suspend fun updateTokens(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                saveTokens(accountKey(username, backendUrl), TokenPair(accessToken, refreshToken))
                Napier.d("Updated iOS keychain tokens for account: $username")
            }
        }

    override suspend fun removeAccount(
        username: String,
        backendUrl: String,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val key = accountKey(username, backendUrl)
                saveAccounts(loadAccounts().filterNot { it.key == key })
                keychain.remove(tokensKey(key))
                Napier.i("Removed LogDate account from iOS keychain: $username")
            }
        }

    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> =
        withContext(Dispatchers.Default) {
            runCatching {
                loadAccounts().map {
                    PlatformAccountInfo(
                        username = it.username,
                        displayName = it.displayName,
                        userId = it.userId,
                        backendUrl = it.backendUrl,
                    )
                }
            }
        }

    override suspend fun getTokens(
        username: String,
        backendUrl: String,
    ): Result<TokenPair?> =
        withContext(Dispatchers.Default) {
            runCatching {
                val encoded = keychain.getString(tokensKey(accountKey(username, backendUrl))) ?: return@runCatching null
                json.decodeFromString(TokenPair.serializer(), encoded)
            }
        }

    override suspend fun clearAllTokens(): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                loadAccounts().forEach { keychain.remove(tokensKey(it.key)) }
                Napier.i("Cleared LogDate account tokens from iOS keychain")
            }
        }

    private fun loadAccounts(): List<StoredAccount> {
        val encoded = keychain.getString(ACCOUNTS_KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredAccount.serializer()), encoded)
        }.getOrElse {
            Napier.e("Failed to decode iOS keychain account index", it)
            emptyList()
        }
    }

    private suspend fun saveAccounts(accounts: List<StoredAccount>) {
        val encoded = json.encodeToString(ListSerializer(StoredAccount.serializer()), accounts)
        check(keychain.set(encoded, ACCOUNTS_KEY)) { "Unable to save account index to iOS keychain" }
    }

    private suspend fun saveTokens(
        key: String,
        tokens: TokenPair,
    ) {
        val encoded = json.encodeToString(TokenPair.serializer(), tokens)
        check(keychain.set(encoded, tokensKey(key))) { "Unable to save account tokens to iOS keychain" }
    }

    private fun LogDateAccount.toStoredAccount(backendUrl: String): StoredAccount =
        StoredAccount(
            key = accountKey(username, backendUrl),
            username = username,
            displayName = displayName,
            userId = id.toString(),
            backendUrl = backendUrl,
        )

    private fun accountKey(
        username: String,
        backendUrl: String,
    ): String = "$username@$backendUrl"

    private fun tokensKey(accountKey: String): String = "account_tokens_$accountKey"

    @Serializable
    private data class StoredAccount(
        val key: String,
        val username: String,
        val displayName: String,
        val userId: String?,
        val backendUrl: String?,
    )

    private companion object {
        const val ACCOUNTS_KEY = "accounts"
    }
}
