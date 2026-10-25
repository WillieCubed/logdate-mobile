package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.logging.IdentityNotices
import app.logdate.server.routes.docs.IdentityDocs
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.host
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.coroutines.CancellationException

fun Route.identityRoutes(identityService: AtprotoIdentityService) {
    get("/.well-known/atproto-did", IdentityDocs.resolveAtprotoDid) {
        val account = call.hostAccount(identityService) ?: return@get

        call.respondText(
            text = requireNotNull(account.did),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.OK,
        )
    }

    get("/.well-known/did.json", IdentityDocs.getDidDocument) {
        val host = call.request.host().lowercase()
        if (host == identityService.config.normalizedHandleDomain) {
            call.respond(HttpStatusCode.OK, identityService.serverDocument())
            return@get
        }

        val account = call.hostAccount(identityService) ?: return@get
        if (account.did?.startsWith("did:web:") != true) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        call.respond(HttpStatusCode.OK, identityService.documentFor(account))
    }
}

/**
 * The account whose handle is the request host. Otherwise this answers the call and returns null:
 * 404 when no account owns the handle, and 500 when the lookup itself fails, so an outage never
 * reads as a missing handle.
 */
private suspend fun ApplicationCall.hostAccount(identityService: AtprotoIdentityService): Account? {
    val account =
        try {
            identityService.findPublicByHandle(request.host().lowercase())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Napier.e(IdentityNotices.HANDLE_RESOLUTION_FAILED, error)
            respond(HttpStatusCode.InternalServerError)
            return null
        }
    if (account == null) respond(HttpStatusCode.NotFound)
    return account
}
