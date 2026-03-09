package app.logdate.server.routes

import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerInfoResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.serverInfoRoutes(serverDescriptor: ServerDescriptor) {
    get("/server/info") {
        call.respond(ServerInfoResponse(success = true, data = serverDescriptor))
    }
}
