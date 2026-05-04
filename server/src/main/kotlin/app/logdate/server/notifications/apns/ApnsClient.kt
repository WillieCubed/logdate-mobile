package app.logdate.server.notifications.apns

import io.github.aakira.napier.Napier
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * APNs HTTP/2 environment. Defaults to [PRODUCTION] — the SANDBOX host accepts only sandbox
 * Apple IDs and is unreachable for App Store builds.
 */
enum class ApnsEnvironment(
    val host: String,
) {
    PRODUCTION("https://api.push.apple.com"),
    SANDBOX("https://api.sandbox.push.apple.com"),
}

/**
 * Result of a single APNs send. [token] is the device token that was attempted; [accepted]
 * is true on Apple's 200 OK; [reason] mirrors Apple's `reason` field for failures
 * ([token's `BadDeviceToken`, `Unregistered`, `ExpiredProviderToken`, etc.).
 */
data class ApnsSendResult(
    val token: String,
    val accepted: Boolean,
    val statusCode: Int,
    val reason: String? = null,
)

/**
 * Minimal APNs HTTP/2 client. Uses the JDK's built-in HTTP/2 implementation plus
 * [ApnsJwtSigner] for the rotating provider token. The client doesn't bundle retry logic
 * or token-feedback handling — callers are expected to react to [ApnsSendResult.reason] by
 * unregistering tokens that come back as `BadDeviceToken` or `Unregistered`.
 */
class ApnsClient(
    private val signer: ApnsJwtSigner,
    private val topic: String,
    private val environment: ApnsEnvironment = ApnsEnvironment.PRODUCTION,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
) {
    suspend fun send(
        deviceToken: String,
        body: String,
    ): ApnsSendResult {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${environment.host}/3/device/$deviceToken"))
                .timeout(Duration.ofSeconds(10))
                .header("authorization", "bearer ${signer.token()}")
                .header("apns-topic", topic)
                .header("apns-push-type", "alert")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return runCatching { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }
            .map { response ->
                val accepted = response.statusCode() == 200
                if (!accepted) {
                    Napier.w(
                        "APNs send rejected for token=${deviceToken.take(8)}…: " +
                            "status=${response.statusCode()} body=${response.body().take(200)}",
                    )
                }
                ApnsSendResult(
                    token = deviceToken,
                    accepted = accepted,
                    statusCode = response.statusCode(),
                    reason = if (!accepted) extractReason(response.body()) else null,
                )
            }.getOrElse { error ->
                Napier.w("APNs send failed for token=${deviceToken.take(8)}…", error)
                ApnsSendResult(token = deviceToken, accepted = false, statusCode = -1, reason = error.message)
            }
    }

    private fun extractReason(body: String): String? {
        val match = Regex("""\"reason\"\s*:\s*\"([^\"]+)\"""").find(body)
        return match?.groupValues?.getOrNull(1)
    }
}
