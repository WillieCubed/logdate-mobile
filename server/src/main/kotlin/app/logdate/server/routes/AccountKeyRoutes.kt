package app.logdate.server.routes

import app.logdate.server.accountkeys.AccountKeyMaterial
import app.logdate.server.accountkeys.AccountKeySaveResult
import app.logdate.server.accountkeys.AccountKeyVault
import app.logdate.server.accountkeys.AccountKeyVaultUnavailable
import app.logdate.server.auth.TokenService
import app.logdate.server.responses.error
import app.logdate.server.routes.docs.AccountKeyDocs
import app.logdate.server.routes.sync.extractUserId
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.Base64

internal const val ACCOUNT_KEYS_PATH = "/api/v1/account/keys"

@Serializable
internal data class AccountKeys(
    val identityKey: String,
    val mediaKey: String,
)

internal fun Route.accountKeyRoutes(
    tokenService: TokenService,
    vault: AccountKeyVault,
) {
    route("/account/keys") {
        getAccountKeysRoute(tokenService, vault)
        putAccountKeysRoute(tokenService, vault)
    }
}

private fun Route.getAccountKeysRoute(
    tokenService: TokenService,
    vault: AccountKeyVault,
) {
    get(AccountKeyDocs.get) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val accountId = extractUserId(call, tokenService) ?: return@get
        try {
            val material =
                vault.get(accountId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, error("ACCOUNT_KEYS_MISSING", "Account key unavailable"))
            call.respond(
                AccountKeys(
                    identityKey = Base64.getEncoder().encodeToString(material.identityKey),
                    mediaKey = Base64.getEncoder().encodeToString(material.mediaKey),
                ),
            )
        } catch (_: AccountKeyVaultUnavailable) {
            call.respond(HttpStatusCode.ServiceUnavailable, error("ACCOUNT_KEYS_UNAVAILABLE", "Account key service unavailable"))
        } catch (_: Exception) {
            Napier.e("Could not retrieve account key")
            call.respond(HttpStatusCode.InternalServerError, error("ACCOUNT_KEYS_FAILED", "Could not retrieve account key"))
        }
    }
}

private fun Route.putAccountKeysRoute(
    tokenService: TokenService,
    vault: AccountKeyVault,
) {
    put(AccountKeyDocs.put) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val accountId = extractUserId(call, tokenService) ?: return@put
        val request =
            try {
                call.receive<AccountKeys>()
            } catch (_: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, error("INVALID_ACCOUNT_KEYS", "Invalid account key request"))
            }
        val identityKey = decodeKey(request.identityKey)
        val mediaKey = decodeKey(request.mediaKey)
        if (identityKey == null || mediaKey == null) {
            return@put call.respond(HttpStatusCode.BadRequest, error("INVALID_ACCOUNT_KEYS", "Invalid account key request"))
        }
        try {
            when (vault.save(accountId, AccountKeyMaterial(identityKey, mediaKey))) {
                AccountKeySaveResult.CREATED -> {
                    call.response.headers.append(HttpHeaders.Location, ACCOUNT_KEYS_PATH)
                    call.respond(HttpStatusCode.Created)
                }
                AccountKeySaveResult.ALREADY_PRESENT -> call.respond(HttpStatusCode.OK)
                AccountKeySaveResult.CONFLICT ->
                    call.respond(HttpStatusCode.Conflict, error("ACCOUNT_KEY_CONFLICT", "This account already has a different key"))
            }
        } catch (_: AccountKeyVaultUnavailable) {
            call.respond(HttpStatusCode.ServiceUnavailable, error("ACCOUNT_KEYS_UNAVAILABLE", "Account key service unavailable"))
        } catch (_: Exception) {
            Napier.e("Could not save account key")
            call.respond(HttpStatusCode.InternalServerError, error("ACCOUNT_KEYS_FAILED", "Could not save account key"))
        }
    }
}

private fun decodeKey(value: String): ByteArray? {
    val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
    return decoded.takeIf { it.size == AccountKeyMaterial.KEY_LENGTH && Base64.getEncoder().encodeToString(it) == value }
}
