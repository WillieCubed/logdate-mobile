package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.shared.model.PasskeyInfo
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Mounts `GET /passkeys`, listing the calling account's passkeys.
 *
 * The client already knows which credential IDs an account has -- they arrive with the account --
 * but not what any of them *are*. Without this, a settings screen showing the list has nothing to
 * distinguish one credential from another, which matters because that screen is where somebody
 * decides which one to revoke.
 *
 * Only active passkeys are returned; a deactivated credential is not something to offer for
 * revocation. Nothing secret is exposed: the stored public key and signature counter stay on the
 * server, and the response carries only what a person needs to recognise their own device.
 */
@OptIn(ExperimentalUuidApi::class)
fun Route.passkeyRoutes(
    tokenService: TokenService,
    passkeyRepository: PasskeyRepository,
) {
    route("/passkeys") {
        get({
            bearerOperation(
                "listPasskeys",
                "Passkeys",
                "List passkeys",
                "Return the active passkeys registered to the authenticated account.",
            )
            response {
                HttpStatusCode.OK to { body<List<PasskeyInfo>>() }
                HttpStatusCode.Unauthorized to { body<MessageErrorResponse>() }
            }
        }) {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "missing or invalid Authorization header"),
                )
            }

            val token = authHeader.removePrefix("Bearer ").trim()
            val accountIdString =
                tokenService.validateAccessToken(token)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid token"))

            val accountId =
                runCatching { Uuid.parse(accountIdString) }
                    .getOrNull()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid subject"))

            call.respond(HttpStatusCode.OK, passkeyRepository.getPasskeysForUser(accountId))
        }
    }
}
