package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import studio.hypertext.atproto.pds.PdsErrorResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Signs in requests made by the owner's own LogDate devices. Only a full-access LogDate token sent
 * as `Authorization: Bearer` is accepted. OAuth DPoP tokens, AT Protocol session tokens and linked
 * device tokens are refused, so third-party AT Protocol clients can't reach what it guards.
 */
class LogDateAccountAuth(
    private val tokenService: TokenService,
    private val accountRepository: AccountRepository,
    private val identityService: AtprotoIdentityService,
) {
    /**
     * The account [call]'s LogDate access token belongs to, with its AT Protocol identity ensured so
     * its `did` is set. Otherwise answers `401 AuthRequired`, the same for every kind of refused or
     * missing token, and returns null.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun requireLogDateAccount(call: ApplicationCall): Account? {
        val token =
            call.request
                .header(HttpHeaders.Authorization)
                ?.takeIf { it.startsWith(BEARER_PREFIX) }
                ?.removePrefix(BEARER_PREFIX)
                ?.trim()
        val accountId = token?.let(tokenService::validateAccessToken)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val account = accountId?.let { accountRepository.findById(it) }?.let { identityService.ensureIdentity(it) }
        if (account == null) {
            call.respond(HttpStatusCode.Unauthorized, PdsErrorResponse("AuthRequired", "A LogDate access token is required"))
        }
        return account
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
