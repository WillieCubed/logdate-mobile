package app.logdate.server.routes

import app.logdate.server.identity.AtprotoIdentityService
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.host
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route

fun Route.identityRoutes(identityService: AtprotoIdentityService) {
    get("/.well-known/atproto-did", {}) {
        val host = call.request.host().lowercase()
        val account =
            identityService.findByHandle(host)
                ?: return@get call.respond(HttpStatusCode.NotFound)

        call.respondText(
            text = requireNotNull(account.did),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.OK,
        )
    }

    get("/.well-known/did.json", {}) {
        val host = call.request.host().lowercase()
        if (host == identityService.config.normalizedHandleDomain) {
            call.respond(HttpStatusCode.OK, identityService.serverDocument())
            return@get
        }

        val account = identityService.findByHandle(host) ?: return@get call.respond(HttpStatusCode.NotFound)
        if (account.did?.startsWith("did:web:") != true) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        call.respond(HttpStatusCode.OK, identityService.documentFor(account))
    }
}
