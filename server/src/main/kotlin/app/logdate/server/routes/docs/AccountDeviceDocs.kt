package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import app.logdate.shared.model.RegisterDeviceRequest
import app.logdate.shared.model.RegisteredDevice
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal object AccountDeviceDocs {
    private val example = RegisteredDevice("00000000-0000-0000-0000-000000000001", "Test device", "MACOS", "0.1.0", 1000, 2000)

    val list: RouteConfig.() -> Unit = {
        bearerOperation(
            "listAccountDevices",
            ApiTags.DEVICES,
            "List registered account devices",
            "Returns device metadata belonging to the authenticated account. Platform is explicitly reported by the client; names do not determine device type.",
        )
        response {
            ok("Registered installations, ordered by most recent activity.", listOf(example))
            syncUnauthorized()
            syncServerError()
        }
    }

    val register: RouteConfig.() -> Unit = {
        bearerOperation(
            "registerAccountDevice",
            ApiTags.DEVICES,
            "Register or update this account installation",
            "Idempotently registers a stable installation UUID within the authenticated account. Repeated requests update metadata and preserve the creation timestamp. This metadata does not grant or revoke access.",
        )
        request {
            pathParameter<String>("id") { description = "The installation's stable UUID." }
            jsonBody(RegisterDeviceRequest("Test device", "MACOS", "0.1.0"), "User-visible name, explicit platform and app version.")
        }
        response {
            ok("The registered installation.", example)
            syncUnauthorized()
            syncError(HttpStatusCode.BadRequest, ErrorCase("INVALID_DEVICE", "Invalid UUID or metadata.", "Device information is invalid"))
            syncServerError()
        }
    }
}
