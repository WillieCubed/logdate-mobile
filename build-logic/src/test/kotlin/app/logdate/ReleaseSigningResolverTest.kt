package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [ReleaseSigningResolver]: which source wins for each signing value, and that a release is
 * never signed with a partial key.
 */
class ReleaseSigningResolverTest {
    private val fullEnvironment =
        mapOf(
            "LOGDATE_RELEASE_STORE_FILE" to "/ci/upload.jks",
            "LOGDATE_RELEASE_STORE_PASSWORD" to "store-secret",
            "LOGDATE_RELEASE_KEY_ALIAS" to "upload",
            "LOGDATE_RELEASE_KEY_PASSWORD" to "key-secret",
        )

    private fun resolve(
        environment: Map<String, String> = emptyMap(),
        properties: Map<String, String> = emptyMap(),
        envFile: Map<String, String> = emptyMap(),
    ) = ReleaseSigningResolver.resolve(environment, properties::get, envFile)

    @Test
    fun `environment variables resolve a full key`() {
        assertEquals(
            ReleaseSigning("/ci/upload.jks", "store-secret", "upload", "key-secret"),
            resolve(environment = fullEnvironment),
        )
    }

    @Test
    fun `gradle properties resolve a full key`() {
        val properties =
            mapOf(
                "logdate.release.storeFile" to "/home/.gradle/upload.jks",
                "logdate.release.storePassword" to "p1",
                "logdate.release.keyAlias" to "a1",
                "logdate.release.keyPassword" to "p2",
            )

        assertEquals(ReleaseSigning("/home/.gradle/upload.jks", "p1", "a1", "p2"), resolve(properties = properties))
    }

    @Test
    fun `the env file written by the keystore script resolves a full key`() {
        assertEquals(
            ReleaseSigning("/ci/upload.jks", "store-secret", "upload", "key-secret"),
            resolve(envFile = fullEnvironment),
        )
    }

    @Test
    fun `environment beats properties and properties beat the env file for each value`() {
        val resolved =
            resolve(
                environment = mapOf("LOGDATE_RELEASE_STORE_FILE" to "/env.jks"),
                properties = mapOf("logdate.release.storeFile" to "/prop.jks", "logdate.release.storePassword" to "prop-pass"),
                envFile = fullEnvironment,
            )

        assertEquals(ReleaseSigning("/env.jks", "prop-pass", "upload", "key-secret"), resolved)
    }

    @Test
    fun `a partial key resolves to nothing`() {
        assertNull(resolve(environment = fullEnvironment - "LOGDATE_RELEASE_KEY_PASSWORD"))
    }

    @Test
    fun `blank values resolve to nothing`() {
        assertNull(resolve(environment = fullEnvironment + ("LOGDATE_RELEASE_KEY_ALIAS" to "  ")))
    }

    @Test
    fun `nothing configured resolves to nothing`() {
        assertNull(resolve())
    }

    @Test
    fun `env file lines are parsed with comments blanks and quotes handled`() {
        val parsed =
            ReleaseSigningResolver.parseEnvFile(
                listOf(
                    "# written by create-signing-keystore.sh",
                    "",
                    "LOGDATE_RELEASE_STORE_FILE=\"/keys/upload.jks\"",
                    "  LOGDATE_RELEASE_KEY_ALIAS = 'upload' ",
                    "LOGDATE_RELEASE_KEY_PASSWORD=pa=ss",
                ),
            )

        assertEquals("/keys/upload.jks", parsed["LOGDATE_RELEASE_STORE_FILE"])
        assertEquals("upload", parsed["LOGDATE_RELEASE_KEY_ALIAS"])
        assertEquals("pa=ss", parsed["LOGDATE_RELEASE_KEY_PASSWORD"])
    }

    @Test
    fun `an env file line with no value parses as empty`() {
        assertEquals("", ReleaseSigningResolver.parseEnvFile(listOf("LOGDATE_RELEASE_KEY_ALIAS"))["LOGDATE_RELEASE_KEY_ALIAS"])
    }
}
