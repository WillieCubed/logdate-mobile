package app.logdate.server.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the Android Digital Asset Links statement served at
 * `/.well-known/assetlinks.json`.
 *
 * Android Credential Manager fetches `https://<rpId>/.well-known/assetlinks.json` and only lets the
 * app act as that relying party when the app's signing certificate is listed here. Self-hosted and
 * staging deployments use a server host that *is* the WebAuthn relying party (e.g. the staging RP id
 * `cloud-staging.logdate.app` is this server), so the server must publish its own asset links. The
 * production apex `logdate.app` is a separate web property and serves its own copy.
 */
data class AssetLinksConfig(
    val packageName: String,
    val sha256CertFingerprints: List<String>,
) {
    companion object {
        const val DEFAULT_PACKAGE_NAME = "co.reasonabletech.logdate"

        fun fromEnvironment(
            packageName: String? = System.getenv("ANDROID_PACKAGE_NAME"),
            certFingerprints: String? = System.getenv("ANDROID_CERT_FINGERPRINTS"),
        ): AssetLinksConfig =
            AssetLinksConfig(
                packageName = packageName?.trim()?.ifBlank { null } ?: DEFAULT_PACKAGE_NAME,
                sha256CertFingerprints = parseFingerprints(certFingerprints),
            )

        /**
         * `ANDROID_CERT_FINGERPRINTS` is a comma-separated list of colon-hex SHA-256 signing-cert
         * fingerprints (one per debug/upload/Play-App-Signing certificate). Blank entries are
         * dropped so a stray comma doesn't emit an empty fingerprint.
         */
        private fun parseFingerprints(raw: String?): List<String> =
            raw
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
    }
}

@Serializable
data class AssetLinkStatement(
    val relation: List<String>,
    val target: AssetLinkTarget,
)

@Serializable
data class AssetLinkTarget(
    val namespace: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("sha256_cert_fingerprints") val sha256CertFingerprints: List<String>,
)

/**
 * Serves the Android Digital Asset Links file authorizing the LogDate app to use passkeys
 * (`delegate_permission/common.get_login_creds`) and handle app links
 * (`delegate_permission/common.handle_all_urls`) for this host.
 */
fun Route.assetLinksRoutes(config: AssetLinksConfig) {
    get("/.well-known/assetlinks.json", {}) {
        call.respond(
            listOf(
                AssetLinkStatement(
                    relation =
                        listOf(
                            "delegate_permission/common.get_login_creds",
                            "delegate_permission/common.handle_all_urls",
                        ),
                    target =
                        AssetLinkTarget(
                            namespace = "android_app",
                            packageName = config.packageName,
                            sha256CertFingerprints = config.sha256CertFingerprints,
                        ),
                ),
            ),
        )
    }
}
