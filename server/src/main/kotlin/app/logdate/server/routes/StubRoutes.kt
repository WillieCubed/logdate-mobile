package app.logdate.server.routes

import app.logdate.server.responses.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Stub implementations for all routes that are not yet implemented.
 * These return "Not Implemented" responses to allow compilation to succeed.
 */

fun Route.authRoutes() {
    route("/auth") {
        post("/login") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Login not implemented yet")
            )
        }
        post("/logout") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Logout not implemented yet")
            )
        }
        post("/refresh") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Token refresh not implemented yet")
            )
        }
    }
}

fun Route.passkeyRoutes() {
    route("/passkeys") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Passkey listing not implemented yet")
            )
        }
        post("/register/begin") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Passkey registration not implemented yet")
            )
        }
        post("/register/complete") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Passkey registration not implemented yet")
            )
        }
        post("/authenticate/begin") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Passkey authentication not implemented yet")
            )
        }
        post("/authenticate/complete") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Passkey authentication not implemented yet")
            )
        }
    }
}

fun Route.journalRoutes() {
    route("/journals") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Journal listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Journal creation not implemented yet")
            )
        }
        get("/{id}") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Journal details not implemented yet")
            )
        }
    }
}

fun Route.notesRoutes() {
    route("/notes") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Notes listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Note creation not implemented yet")
            )
        }
    }
}

fun Route.draftRoutes() {
    route("/drafts") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Drafts listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Draft creation not implemented yet")
            )
        }
    }
}

fun Route.mediaRoutes() {
    route("/media") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Media listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Media upload not implemented yet")
            )
        }
    }
}

fun Route.syncRoutes() {
    route("/sync") {
        get("/status") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Sync status not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Sync operation not implemented yet")
            )
        }
    }
}

fun Route.aiRoutes() {
    route("/ai") {
        post("/summarize") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "AI summarization not implemented yet")
            )
        }
    }
}

fun Route.deviceRoutes() {
    route("/devices") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Device listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Device registration not implemented yet")
            )
        }
    }
}

fun Route.rewindRoutes() {
    route("/rewind") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Rewind listing not implemented yet")
            )
        }
        post("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Rewind creation not implemented yet")
            )
        }
    }
}

fun Route.timelineRoutes() {
    route("/timeline") {
        get("/") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Timeline listing not implemented yet")
            )
        }
        get("/{date}") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Timeline for date not implemented yet")
            )
        }
    }
}
