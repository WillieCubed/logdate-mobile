package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.entitlements.Entitlement
import app.logdate.server.entitlements.EntitlementFeature
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.EntitlementStatus
import app.logdate.server.entitlements.EntitlementTier
import app.logdate.server.transcription.CloudTranscriptionSessionProvider
import app.logdate.server.transcription.CloudTranscriptionSessionUnavailableException
import app.logdate.server.transcription.DisabledCloudTranscriptionSessionProvider
import app.logdate.shared.model.transcription.CloudTranscriptionClientSecret
import app.logdate.shared.model.transcription.CloudTranscriptionMode
import app.logdate.shared.model.transcription.CloudTranscriptionSessionRequest
import app.logdate.shared.model.transcription.CloudTranscriptionSessionResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Mounts LogDate Cloud transcription session endpoints.
 *
 * The route intentionally creates only LogDate-owned session identifiers and
 * relative stream paths. Downstream ASR providers remain a server-side detail,
 * keeping provider credentials and model choices off every client platform.
 */
fun Route.transcriptionRoutes(
    tokenService: TokenService,
    entitlementService: EntitlementService,
    sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    sessionProvider: CloudTranscriptionSessionProvider = DisabledCloudTranscriptionSessionProvider(),
) {
    route("/transcription") {
        post("/sessions") {
            val accountId =
                call.resolveBearerAccountId(tokenService)
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "missing or invalid Authorization header"),
                    )
            val entitlement = entitlementService.resolve(accountId)
            val request = call.receive<CloudTranscriptionSessionRequest>()
            val requiredFeature =
                when (request.mode) {
                    CloudTranscriptionMode.REALTIME -> EntitlementFeature.CLOUD_TRANSCRIPTION_REALTIME
                    CloudTranscriptionMode.REFINEMENT -> EntitlementFeature.CLOUD_TRANSCRIPT_REFINEMENT
                }

            if (!entitlement.allowsCloudTranscription(requiredFeature)) {
                call.respond(
                    HttpStatusCode.PaymentRequired,
                    mapOf("error" to "cloud transcription requires an active LogDate Cloud subscription"),
                )
                return@post
            }

            val sessionId = sessionIdFactory()
            val lease =
                try {
                    sessionProvider.reserveSession(
                        accountId = accountId,
                        request = request,
                        sessionId = sessionId,
                    )
                } catch (e: CloudTranscriptionSessionUnavailableException) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to (e.message ?: "cloud transcription is unavailable")),
                    )
                    return@post
                }

            call.respond(
                HttpStatusCode.Created,
                CloudTranscriptionSessionResponse(
                    sessionId = lease.sessionId,
                    noteId = request.noteId,
                    language = request.language,
                    mode = request.mode,
                    streamPath = lease.streamPath,
                    inputFormat = lease.inputFormat,
                    realtimeUrl = lease.realtimeUrl,
                    clientSecret =
                        lease.clientSecretValue?.takeIf { lease.clientSecretExpiresAtEpochSeconds != null }?.let { secret ->
                            CloudTranscriptionClientSecret(
                                value = secret,
                                expiresAtEpochSeconds = requireNotNull(lease.clientSecretExpiresAtEpochSeconds),
                            )
                        },
                    modelId = lease.modelId,
                ),
            )
        }
    }
}

private fun ApplicationCall.resolveBearerAccountId(tokenService: TokenService): UUID? {
    val authHeader = request.headers["Authorization"]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
    val accountIdString = tokenService.validateAccessToken(authHeader.removePrefix("Bearer ").trim()) ?: return null
    return runCatching { UUID.fromString(accountIdString) }.getOrNull()
}

private fun Entitlement.allowsCloudTranscription(feature: EntitlementFeature): Boolean =
    status.allowsCloudTranscription() &&
        (hasFeature(feature) || tier == EntitlementTier.UNLIMITED || status == EntitlementStatus.SELF_HOST)

private fun EntitlementStatus.allowsCloudTranscription(): Boolean =
    when (this) {
        EntitlementStatus.ACTIVE,
        EntitlementStatus.PAST_DUE,
        EntitlementStatus.GRACE,
        EntitlementStatus.SELF_HOST,
        -> true
        EntitlementStatus.CANCELLED -> false
    }
