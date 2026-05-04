package app.logdate.server.billing

import com.apple.itunes.storekit.model.Environment
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload
import com.apple.itunes.storekit.verification.SignedDataVerifier
import io.github.aakira.napier.Napier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.io.InputStream

private const val APPLE_ROOT_CERT_RESOURCE_DIR = "apple-root-certs"

private val DEFAULT_APPLE_ROOT_CERTS =
    listOf(
        "$APPLE_ROOT_CERT_RESOURCE_DIR/AppleRootCA-G3.cer",
        "$APPLE_ROOT_CERT_RESOURCE_DIR/AppleRootCA-G2.cer",
        "$APPLE_ROOT_CERT_RESOURCE_DIR/AppleIncRootCertificate.cer",
    )

/**
 * Wire request body for the App Store Server Notifications V2 webhook. Apple sends a single
 * field [signedPayload] containing the JWS — every other piece of metadata lives inside
 * the verified payload.
 */
@Serializable
data class AppStoreSignedPayload(
    val signedPayload: String,
)

/**
 * Verifies a signed App Store notification and forwards the decoded payload to whatever
 * entitlement / billing reconciliation logic the deployment cares about. The dispatcher
 * intentionally does not know how the LogDate account ↔ App Store transaction mapping is
 * stored — provide an [onPayload] callback that consults whatever account-linking table is
 * authoritative.
 */
class AppStoreNotificationDispatcher(
    private val verifier: SignedDataVerifier,
    private val onPayload: suspend (ResponseBodyV2DecodedPayload) -> Unit = { },
) {
    suspend fun handle(signedPayload: String): Result<ResponseBodyV2DecodedPayload> =
        runCatching {
            val decoded = verifier.verifyAndDecodeNotification(signedPayload)
            onPayload(decoded)
            decoded
        }
}

/**
 * Builds an Apple [SignedDataVerifier] from the root CAs bundled at
 * `server/src/main/resources/apple-root-certs/`. Pass [appleAppId] from App Store Connect
 * once the production app record exists; until then [appleAppId] = null skips the optional
 * appAppleId check (the JWS signature is still validated against Apple's certificate
 * chain).
 */
fun createAppStoreSignedDataVerifier(
    bundleId: String,
    appleAppId: Long? = null,
    environment: Environment = Environment.PRODUCTION,
    enableOnlineChecks: Boolean = true,
    rootCertResourcePaths: List<String> = DEFAULT_APPLE_ROOT_CERTS,
): SignedDataVerifier {
    val classLoader = AppStoreNotificationDispatcher::class.java.classLoader
    val rootCerts: Set<InputStream> =
        rootCertResourcePaths
            .mapNotNull { classLoader.getResourceAsStream(it) }
            .toSet()
    require(rootCerts.isNotEmpty()) {
        "No Apple root CA certificates found on the classpath under $APPLE_ROOT_CERT_RESOURCE_DIR/. " +
            "Bundle the .cer files (downloaded from https://www.apple.com/certificateauthority/) " +
            "before constructing the verifier."
    }
    return SignedDataVerifier(
        rootCerts,
        bundleId,
        appleAppId,
        environment,
        enableOnlineChecks,
    )
}

/**
 * Mounts `POST /billing/appstore/notifications` on the receiver. Mount the receiver under
 * whatever base route the rest of the v1 API uses (typically `/api/v1`), so the public URL
 * reads `https://<host>/api/v1/billing/appstore/notifications` — that exact value is what
 * App Store Connect → App Information → Server URL needs.
 *
 * Apple expects a 2xx within ~5 seconds. The dispatcher runs synchronously, so heavy
 * reconciliation work in [AppStoreNotificationDispatcher.onPayload] should hand off to a
 * background queue rather than block the request.
 */
fun Route.appStoreNotificationsRoutes(dispatcher: AppStoreNotificationDispatcher) {
    route("/billing/appstore") {
        post("/notifications") {
            val body =
                runCatching { call.receive<AppStoreSignedPayload>() }
                    .getOrNull()
            if (body == null || body.signedPayload.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing signedPayload"))
                return@post
            }

            dispatcher.handle(body.signedPayload).fold(
                onSuccess = { decoded ->
                    Napier.i(
                        "App Store notification: type=${decoded.notificationType} " +
                            "subtype=${decoded.subtype} uuid=${decoded.notificationUUID}",
                    )
                    call.respond(HttpStatusCode.OK)
                },
                onFailure = { error ->
                    Napier.w("App Store notification verification failed: ${error.message}", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "verification failed"))
                },
            )
        }
    }
}
