package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.AccountKeyEnvelope
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.noContent
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal object AccountKeyEnvelopeDocs {
    val get: RouteConfig.() -> Unit = {
        bearerOperation(
            "getAccountKeyEnvelope",
            ApiTags.DEVICES,
            "Retrieve an encrypted account key envelope",
            "Returns client-encrypted ciphertext for an active passkey belonging to the signed-in account. Unlock secrets and journal keys remain on the client.",
        )
        request { pathParameter<String>("credentialId") { description = "The account's active base64url WebAuthn credential ID." } }
        response {
            ok("Opaque LDKE1 ciphertext, encoded as canonical base64.", AccountKeyEnvelope("encrypted-ciphertext"))
            syncUnauthorized()
            syncError(
                HttpStatusCode.NotFound,
                ErrorCase("NOT_FOUND", "No envelope for this active account credential.", "Envelope unavailable"),
            )
            syncError(HttpStatusCode.ServiceUnavailable, ErrorCase("UNAVAILABLE", "This feature is disabled.", "Secure unlock unavailable"))
            syncServerError()
        }
    }
    val put: RouteConfig.() -> Unit = {
        bearerOperation(
            "putAccountKeyEnvelope",
            ApiTags.DEVICES,
            "Store an encrypted account key envelope",
            "Stores only a client-encrypted LDKE1 envelope. Identical retries succeed; different ciphertext cannot overwrite an existing envelope. The credential must be active and belong to the signed-in account.",
        )
        request {
            pathParameter<String>("credentialId") { description = "The account's active base64url WebAuthn credential ID." }
            jsonBody(
                AccountKeyEnvelope("encrypted-ciphertext"),
                "Canonical base64 of the 97-byte client-encrypted envelope. Never send plaintext keys or a passkey unlock secret.",
            )
        }
        response {
            noContent("The envelope is stored, including identical retries.")
            syncUnauthorized()
            syncError(HttpStatusCode.NotFound, ErrorCase("NOT_FOUND", "Credential unavailable for this account.", "Envelope unavailable"))
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase("INVALID_ENVELOPE", "The envelope format is invalid.", "Invalid encrypted envelope"),
            )
            syncError(
                HttpStatusCode.Conflict,
                ErrorCase("ENVELOPE_EXISTS", "Different ciphertext already exists.", "An envelope already exists for this credential"),
            )
            syncError(HttpStatusCode.ServiceUnavailable, ErrorCase("UNAVAILABLE", "This feature is disabled.", "Secure unlock unavailable"))
            syncServerError()
        }
    }
}
