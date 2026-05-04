package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.UsageCalculator
import app.logdate.shared.model.QuotaUsage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Mounts `GET /quota` returning the calling account's storage quota and current usage.
 *
 * The category breakdown is intentionally empty for now — the server tracks aggregate storage
 * bytes only, and the iOS client treats `categories = []` as "no per-category data, render
 * the totals only". When the database grows more granular tracking the response can fill in
 * `QuotaContentType` rows without an iOS contract change.
 */
fun Route.quotaRoutes(
    tokenService: TokenService,
    entitlementService: EntitlementService,
    usageCalculator: UsageCalculator,
) {
    route("/quota") {
        get {
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "missing or invalid Authorization header"))
                return@get
            }
            val token = authHeader.removePrefix("Bearer ").trim()
            val accountIdString =
                tokenService.validateAccessToken(token)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid token"))
            val accountId =
                runCatching { java.util.UUID.fromString(accountIdString) }
                    .getOrNull()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid subject"))

            val entitlement = entitlementService.resolve(accountId)
            val totalBytes = entitlement.limits.storageBytes ?: Long.MAX_VALUE
            val usedBytes = usageCalculator.storageBytes(accountId)
            call.respond(
                HttpStatusCode.OK,
                QuotaUsage(
                    totalBytes = totalBytes,
                    usedBytes = usedBytes,
                    categories = emptyList(),
                ),
            )
        }
    }
}
