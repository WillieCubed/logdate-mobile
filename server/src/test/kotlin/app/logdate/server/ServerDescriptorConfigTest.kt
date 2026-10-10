package app.logdate.server

import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.HostedAccountDidMethod
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerProtocolFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerDescriptorConfigTest {
    @Test
    fun `journal merge protocol is advertised for first party and self hosted deployments`() {
        for (kind in DeploymentKind.entries) {
            val descriptor = ServerDescriptorConfig(deploymentKind = kind).toDescriptor(AtprotoIdentityConfig(), "logdate.app", "LogDate")
            assertTrue(descriptor.hasProtocolFeature("journalMergeV1"))
        }
    }

    @Test
    fun `diagnostic reports use an additive protocol flag without changing known capabilities`() {
        val identity = AtprotoIdentityConfig()
        val config = ServerDescriptorConfig(deploymentKind = DeploymentKind.FIRST_PARTY)
        val baseline = config.toDescriptor(identity, "logdate.app", "LogDate")
        val enabled = config.toDescriptor(identity, "logdate.app", "LogDate", diagnosticReportsEnabled = true)
        val selfHosted =
            ServerDescriptorConfig().toDescriptor(
                identity,
                "logdate.app",
                "LogDate",
                diagnosticReportsEnabled = true,
            )

        assertFalse(baseline.hasProtocolFeature(ServerProtocolFeature.DIAGNOSTIC_REPORTS_V1))
        assertTrue(enabled.hasProtocolFeature(ServerProtocolFeature.DIAGNOSTIC_REPORTS_V1))
        assertTrue(selfHosted.hasProtocolFeature(ServerProtocolFeature.DIAGNOSTIC_REPORTS_V1))
        assertEquals(baseline.capabilities, enabled.capabilities)
    }

    @Test
    fun `encrypted unlock is opt in and never advertises server custody`() {
        val identity = AtprotoIdentityConfig()
        val baseline = ServerDescriptorConfig().toDescriptor(identity, "logdate.app", "LogDate")
        val enabled = ServerDescriptorConfig(encryptedAccountKeysEnabled = true).toDescriptor(identity, "logdate.app", "LogDate")
        assertFalse(baseline.hasProtocolFeature(ServerProtocolFeature.ENCRYPTED_ACCOUNT_KEYS_V1))
        assertTrue(enabled.hasProtocolFeature(ServerProtocolFeature.ENCRYPTED_ACCOUNT_KEYS_V1))
        assertFalse(baseline.hasProtocolFeature(ServerProtocolFeature.ACCOUNT_KEY_VAULT_V1))
        assertFalse(enabled.hasProtocolFeature(ServerProtocolFeature.ACCOUNT_KEY_VAULT_V1))
        assertEquals(baseline.capabilities, enabled.capabilities)
    }

    @Test
    fun `first-party environment advertises LogDate Cloud defaults`() {
        val config = ServerDescriptorConfig.fromEnvironment(deploymentKind = "first_party")

        assertEquals(DeploymentKind.FIRST_PARTY, config.deploymentKind)
        assertEquals("LogDate Cloud", config.displayName)
        assertEquals("https://logdate.app/privacy", config.privacyPolicyUrl)
        assertEquals("https://logdate.app/terms", config.termsOfServiceUrl)
    }

    @Test
    fun `unset and self-hosted environments advertise server defaults`() {
        val unset = ServerDescriptorConfig.fromEnvironment()
        val explicit = ServerDescriptorConfig.fromEnvironment(deploymentKind = "self_hosted")

        listOf(unset, explicit).forEach { config ->
            assertEquals(DeploymentKind.SELF_HOSTED, config.deploymentKind)
            assertEquals("LogDate Server", config.displayName)
            assertNull(config.privacyPolicyUrl)
            assertNull(config.termsOfServiceUrl)
        }
    }

    @Test
    fun `PLC publishing is advertised only when hosted PLC operations are published`() {
        val publishing = descriptorFor(AtprotoIdentityConfig(publishHostedPlcOperations = true))
        val notPublishing = descriptorFor(AtprotoIdentityConfig(publishHostedPlcOperations = false))

        assertTrue(publishing.hasProtocolFeature(ServerProtocolFeature.ATPROTO_PLC_PUBLISHING_V1))
        assertFalse(notPublishing.hasProtocolFeature(ServerProtocolFeature.ATPROTO_PLC_PUBLISHING_V1))
    }

    @Test
    fun `PLC publishing is not advertised for did web accounts`() {
        val descriptor =
            descriptorFor(
                AtprotoIdentityConfig(
                    hostedAccountDidMethod = HostedAccountDidMethod.WEB,
                    publishHostedPlcOperations = true,
                ),
            )

        assertFalse(descriptor.hasProtocolFeature(ServerProtocolFeature.ATPROTO_PLC_PUBLISHING_V1))
        assertTrue(descriptor.hasProtocolFeature(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1))
    }

    private fun descriptorFor(identityConfig: AtprotoIdentityConfig) =
        ServerDescriptorConfig().toDescriptor(
            identityConfig = identityConfig,
            webAuthnRpId = "logdate.app",
            webAuthnRpName = "LogDate",
        )
}
