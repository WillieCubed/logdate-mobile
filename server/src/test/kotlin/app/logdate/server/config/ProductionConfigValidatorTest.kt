package app.logdate.server.config

import app.logdate.server.config.ProductionConfigValidator.InsecureProductionConfigException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests the [profileAwareBoolEnv] utility function for resolving boolean environment variables
 * with profile-specific defaults.
 */
class ProfileAwareBoolEnvTest {
    @Test
    fun `unset env picks productionDefault in production`() {
        val v =
            profileAwareBoolEnv(
                name = "SOME_FLAG",
                productionDefault = true,
                devDefault = false,
                readEnv = { null },
                profile = RuntimeProfile.PRODUCTION,
            )
        assertTrue(v)
    }

    @Test
    fun `unset env picks devDefault outside production`() {
        val v =
            profileAwareBoolEnv(
                name = "SOME_FLAG",
                productionDefault = true,
                devDefault = false,
                readEnv = { null },
                profile = RuntimeProfile.DEVELOPMENT,
            )
        assertTrue(!v)
    }

    @Test
    fun `explicit true wins in any profile`() {
        val v =
            profileAwareBoolEnv(
                name = "SOME_FLAG",
                productionDefault = false,
                devDefault = false,
                readEnv = { "true" },
                profile = RuntimeProfile.PRODUCTION,
            )
        assertTrue(v)
    }

    @Test
    fun `explicit false wins even in production`() {
        val v =
            profileAwareBoolEnv(
                name = "SOME_FLAG",
                productionDefault = true,
                devDefault = true,
                readEnv = { "false" },
                profile = RuntimeProfile.PRODUCTION,
            )
        assertTrue(!v)
    }
}

/**
 * Tests the security validation logic for server configurations.
 *
 * Ensures that the server correctly enforces strict security requirements when
 * running in a production profile, such as mandatory high-entropy secrets and
 * database passwords, while allowing more flexible configurations for development
 * and testing environments.
 */
class ProductionConfigValidatorTest {
    @Test
    fun `development profile skips validation`() {
        // No env vars set — should not throw, because only production enforces secrets.
        ProductionConfigValidator.validate(profile = RuntimeProfile.DEVELOPMENT, readEnv = { null })
    }

    @Test
    fun `test profile skips validation`() {
        ProductionConfigValidator.validate(profile = RuntimeProfile.TEST, readEnv = { null })
    }

    @Test
    fun `production requires JWT_SECRET`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv = secureEnvOf("DATABASE_PASSWORD" to VALID_DB_PASSWORD),
                )
            }
        assertTrue(failure.message!!.contains("JWT_SECRET is required"))
    }

    @Test
    fun `production rejects short JWT_SECRET`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        secureEnvOf(
                            "JWT_SECRET" to "too-short",
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                        ),
                )
            }
        assertTrue(failure.message!!.contains("at least 32"))
    }

    @Test
    fun `production rejects placeholder JWT_SECRET of correct length`() {
        val placeholder = "your-secret-key-change-in-production" // 36 chars, passes length check
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        secureEnvOf(
                            "JWT_SECRET" to placeholder,
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                        ),
                )
            }
        assertTrue(failure.message!!.contains("known placeholder"))
    }

    @Test
    fun `production requires DATABASE_PASSWORD when DATABASE_URL has no credentials`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv = secureEnvOf("JWT_SECRET" to VALID_JWT_SECRET),
                )
            }
        assertTrue(failure.message!!.contains("DATABASE_PASSWORD is required"))
    }

    @Test
    fun `production accepts DATABASE_URL with embedded credentials`() {
        ProductionConfigValidator.validate(
            profile = RuntimeProfile.PRODUCTION,
            readEnv =
                secureEnvOf(
                    "JWT_SECRET" to VALID_JWT_SECRET,
                    "DATABASE_URL" to "jdbc:postgresql://user:pass@host:5432/db",
                ),
        )
    }

    @Test
    fun `production rejects default DATABASE_PASSWORD`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        secureEnvOf(
                            "JWT_SECRET" to VALID_JWT_SECRET,
                            "DATABASE_PASSWORD" to "logdate",
                        ),
                )
            }
        assertTrue(failure.message!!.contains("known default"))
    }

    @Test
    fun `production accepts a fully-secured configuration`() {
        ProductionConfigValidator.validate(
            profile = RuntimeProfile.PRODUCTION,
            readEnv =
                secureEnvOf(
                    "JWT_SECRET" to VALID_JWT_SECRET,
                    "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                ),
        )
    }

    @Test
    fun `reports every failure at once rather than bailing on first`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv = { null },
                )
            }
        val message = failure.message!!
        assertTrue(message.contains("JWT_SECRET is required"))
        assertTrue(message.contains("DATABASE_PASSWORD is required"))
        assertTrue(message.contains("WEBAUTHN_RP_ID is required"))
        assertTrue(message.contains("WEBAUTHN_ORIGIN is required"))
    }

    @Test
    fun `production requires WEBAUTHN_RP_ID`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        envOf(
                            "JWT_SECRET" to VALID_JWT_SECRET,
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                            "WEBAUTHN_ORIGIN" to "https://cloud.logdate.app",
                        ),
                )
            }
        assertTrue(failure.message!!.contains("WEBAUTHN_RP_ID is required"))
    }

    @Test
    fun `production requires WEBAUTHN_ORIGIN`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        envOf(
                            "JWT_SECRET" to VALID_JWT_SECRET,
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                            "WEBAUTHN_RP_ID" to "logdate.app",
                        ),
                )
            }
        assertTrue(failure.message!!.contains("WEBAUTHN_ORIGIN is required"))
    }

    @Test
    fun `production rejects http WEBAUTHN_ORIGIN`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        envOf(
                            "JWT_SECRET" to VALID_JWT_SECRET,
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                            "WEBAUTHN_RP_ID" to "logdate.app",
                            "WEBAUTHN_ORIGIN" to "http://cloud.logdate.app",
                        ),
                )
            }
        assertTrue(failure.message!!.contains("https://"))
    }

    @Test
    fun `production rejects RP ID that is not the apex of origin`() {
        val failure =
            assertFailsWith<InsecureProductionConfigException> {
                ProductionConfigValidator.validate(
                    profile = RuntimeProfile.PRODUCTION,
                    readEnv =
                        envOf(
                            "JWT_SECRET" to VALID_JWT_SECRET,
                            "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                            "WEBAUTHN_RP_ID" to "logdate.app",
                            "WEBAUTHN_ORIGIN" to "https://other-domain.example",
                        ),
                )
            }
        assertTrue(failure.message!!.contains("registrable apex"))
    }

    @Test
    fun `production accepts RP ID equal to origin host (staging pattern)`() {
        ProductionConfigValidator.validate(
            profile = RuntimeProfile.PRODUCTION,
            readEnv =
                envOf(
                    "JWT_SECRET" to VALID_JWT_SECRET,
                    "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                    "WEBAUTHN_RP_ID" to "cloud-staging.logdate.app",
                    "WEBAUTHN_ORIGIN" to "https://cloud-staging.logdate.app",
                ),
        )
    }

    @Test
    fun `production accepts RP ID as parent suffix of origin host (production pattern)`() {
        ProductionConfigValidator.validate(
            profile = RuntimeProfile.PRODUCTION,
            readEnv =
                envOf(
                    "JWT_SECRET" to VALID_JWT_SECRET,
                    "DATABASE_PASSWORD" to VALID_DB_PASSWORD,
                    "WEBAUTHN_RP_ID" to "logdate.app",
                    "WEBAUTHN_ORIGIN" to "https://cloud.logdate.app",
                ),
        )
    }

    private fun envOf(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { name -> map[name] }
    }

    /** Pre-populated with valid WebAuthn config so secret-focused tests don't get cross-checked. */
    private fun secureEnvOf(vararg pairs: Pair<String, String>): (String) -> String? =
        envOf(
            "WEBAUTHN_RP_ID" to "logdate.app",
            "WEBAUTHN_ORIGIN" to "https://cloud.logdate.app",
            *pairs,
        )

    companion object {
        private const val VALID_JWT_SECRET = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789+/=abc"
        private const val VALID_DB_PASSWORD = "a-real-secret-password"
    }
}
