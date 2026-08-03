package app.logdate.server.routes

import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerInfoResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.serverInfoRoutes(serverDescriptor: ServerDescriptor) {
    get("/server/info", {
        publicOperation("getServerInfo", "Server", "Get server information", "Discover this deployment and its supported capabilities.")
        response { io.ktor.http.HttpStatusCode.OK to { body<ServerInfoResponse>() } }
    }) {
        call.respond(ServerInfoResponse(success = true, data = serverDescriptor))
    }
}
