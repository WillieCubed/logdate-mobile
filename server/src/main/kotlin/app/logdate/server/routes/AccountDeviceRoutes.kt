@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.database.toKotlinUuid
import app.logdate.server.devices.AccountDeviceRepository
import app.logdate.server.responses.error
import app.logdate.server.routes.docs.AccountDeviceDocs
import app.logdate.server.routes.sync.extractUserId
import app.logdate.shared.model.RegisterDeviceRequest
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal fun Route.accountDeviceRoutes(
    tokenService: TokenService,
    repository: AccountDeviceRepository,
) {
    route("/devices") {
        get(AccountDeviceDocs.list) {
            val owner = extractUserId(call, tokenService)?.toKotlinUuid() ?: return@get
            call.response.headers.append("Cache-Control", "no-store")
            call.respond(repository.list(owner))
        }
        put("/{id}", AccountDeviceDocs.register) {
            val owner = extractUserId(call, tokenService)?.toKotlinUuid() ?: return@put
            call.response.headers.append("Cache-Control", "no-store")
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val request = call.receive<RegisterDeviceRequest>()
            val name = request.name.trim()
            if (id == null ||
                name.isBlank() ||
                name.length > 120 ||
                name.any(Char::isISOControl) ||
                request.platform !in supportedPlatforms ||
                request.appVersion.length > 64 ||
                request.appVersion.any(Char::isISOControl)
            ) {
                call.respond(HttpStatusCode.BadRequest, error("INVALID_DEVICE", "Device information is invalid"))
                return@put
            }
            call.respond(repository.register(owner, id, request.copy(name = name), Clock.System.now().toEpochMilliseconds()))
        }
    }
}

private val supportedPlatforms = setOf("ANDROID", "IOS", "MACOS", "WINDOWS", "LINUX", "WEB", "UNKNOWN")
