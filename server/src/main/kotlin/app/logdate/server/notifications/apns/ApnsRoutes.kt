package app.logdate.server.notifications.apns

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ApnsTokenRegistration(
    val deviceId: String,
    val token: String,
)

/**
 * Mounts the APNs device-token endpoints. The receiver must already have an authentication
 * gate that resolves the calling user — wire [resolveUserId] to whatever the rest of the
 * v1 API uses (the existing auth code reads `Authorization: Bearer <jwt>` and extracts the
 * subject claim). Returning null from [resolveUserId] makes both endpoints respond 401.
 *
 * The routes are intentionally small:
 *  * `POST /devices/apns` registers (or refreshes) a token for the caller's
 *    `(userId, deviceId)` tuple.
 *  * `DELETE /devices/apns/{deviceId}` clears the token, e.g. on sign-out.
 */
fun Route.apnsRoutes(
    registry: ApnsTokenRegistry,
    resolveUserId: suspend (io.ktor.server.application.ApplicationCall) -> String?,
) {
    route("/devices/apns") {
        post {
            val userId = resolveUserId(call) ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val body =
                runCatching { call.receive<ApnsTokenRegistration>() }
                    .getOrNull()
            if (body == null || body.deviceId.isBlank() || body.token.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing deviceId or token"))
                return@post
            }
            registry.register(userId, body.deviceId, body.token)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{deviceId}") {
            val userId = resolveUserId(call) ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val deviceId = call.parameters["deviceId"]
            if (deviceId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing deviceId"))
                return@delete
            }
            registry.unregister(userId, deviceId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
