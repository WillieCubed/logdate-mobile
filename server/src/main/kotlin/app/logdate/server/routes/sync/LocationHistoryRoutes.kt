package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.responses.error
import app.logdate.server.routes.docs.LocationHistoryDocs
import app.logdate.server.sync.LocationHistoryConflictException
import app.logdate.server.sync.LocationHistoryRepository
import app.logdate.shared.model.sync.LocationHistoryBatchRequest
import app.logdate.shared.model.sync.LocationHistoryBatchResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val MAX_LOCATION_BATCH_BYTES = 7_000_000L
private val locationHistoryJson = Json { ignoreUnknownKeys = true }

internal fun Route.locationHistoryRoutes(
    tokenService: TokenService?,
    repository: LocationHistoryRepository,
) {
    route("/location-history") {
        put(LocationHistoryDocs.upload) {
            val userId = extractUserId(call, tokenService) ?: return@put
            try {
                val body = call.receiveChannel().readRemaining(MAX_LOCATION_BATCH_BYTES + 1).readByteArray()
                if (body.size > MAX_LOCATION_BATCH_BYTES) {
                    return@put call.respond(HttpStatusCode.PayloadTooLarge, error("VALIDATION_ERROR", "Batch is too large"))
                }
                val request = locationHistoryJson.decodeFromString<LocationHistoryBatchRequest>(body.decodeToString())
                call.respond(LocationHistoryBatchResponse(repository.upload(userId, request.records)))
            } catch (_: LocationHistoryConflictException) {
                call.respond(HttpStatusCode.Conflict, error("VERSION_CONFLICT", "Pull changes before retrying this batch"))
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "Invalid location history batch"))
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "Invalid location history batch"))
            }
        }
        get(LocationHistoryDocs.changes) {
            val userId = extractUserId(call, tokenService) ?: return@get
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            if (since < 0 ||
                limit !in 1..100 ||
                (call.request.queryParameters["since"] != null && call.request.queryParameters["since"]?.toLongOrNull() == null) ||
                (call.request.queryParameters["limit"] != null && call.request.queryParameters["limit"]?.toIntOrNull() == null)
            ) {
                return@get call.respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "Invalid page parameters"))
            }
            call.respond(repository.changes(userId, since, limit))
        }
    }
}
