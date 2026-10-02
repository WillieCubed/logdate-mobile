package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ACCOUNT_KEYS_PATH
import app.logdate.server.routes.AccountKeys
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for `AccountKeyRoutes.kt`, the server-held copy of an account's encryption keys. */
internal object AccountKeyDocs {
    private val example =
        AccountKeys(
            identityKey = "MTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTE=",
            mediaKey = "REREREREREREREREREREREREREREREREREREREREREQ=",
        )

    private val unavailable =
        ErrorCase(
            "ACCOUNT_KEYS_UNAVAILABLE",
            "This server has no key-encryption keyring configured, so it cannot keep account keys. Check for " +
                "`accountKeyVaultV1` in **Describe this server** before calling.",
            "Account key service unavailable",
        )

    private fun ResponsesConfig.failures(failedMessage: String) {
        syncError(
            HttpStatusCode.InternalServerError,
            ErrorCase(
                "ACCOUNT_KEYS_FAILED",
                "The stored keys could not be read, decrypted or written. Retry later.",
                failedMessage,
            ),
            SyncExamples.serverMisconfigured,
        )
        syncError(HttpStatusCode.ServiceUnavailable, unavailable)
    }

    val get: RouteConfig.() -> Unit = {
        bearerOperation(
            "getAccountKeys",
            ApiTags.DEVICES,
            "Recover account keys",
            """
            Returns the account's identity and media keys so a newly signed-in device can read the account's
            encrypted journal data. The server stores them encrypted under its own keyring, bound to the account,
            and only a token for that account can read them.

            The response is marked `Cache-Control: no-store`.
            """,
        )
        response {
            ok("The account's keys, each 32 bytes in standard base64.", example)
            syncUnauthorized()
            syncError(
                HttpStatusCode.NotFound,
                ErrorCase(
                    "ACCOUNT_KEYS_MISSING",
                    "No device has stored keys for this account yet. Generate them on this device and store them.",
                    "Account key unavailable",
                ),
            )
            failures("Could not retrieve account key")
        }
    }

    val put: RouteConfig.() -> Unit = {
        bearerOperation(
            "storeAccountKeys",
            ApiTags.DEVICES,
            "Store account keys",
            """
            Stores the account's identity and media keys the first time a device creates them. The keys never
            change afterwards: sending the same keys again answers `200`, and sending different keys answers
            `409` so a second device cannot silently replace the keys the account's data is encrypted with.

            The response is marked `Cache-Control: no-store`.
            """,
        )
        request { jsonBody(example, "The account's identity and media keys, each 32 bytes in standard base64.") }
        response {
            code(HttpStatusCode.Created) {
                description = "The keys are stored. The response has no body."
                header<String>("Location") { description = "Path of the stored keys, `$ACCOUNT_KEYS_PATH`." }
            }
            code(HttpStatusCode.OK) { description = "These exact keys were already stored; nothing changed. The response has no body." }
            syncError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "INVALID_ACCOUNT_KEYS",
                    "The body is not JSON, or a key is not exactly 32 bytes in canonical standard base64.",
                    "Invalid account key request",
                ),
            )
            syncUnauthorized()
            syncError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "ACCOUNT_KEY_CONFLICT",
                    "The account already has different keys. Recover them with **Recover account keys** instead.",
                    "This account already has a different key",
                ),
            )
            failures("Could not save account key")
        }
    }
}
