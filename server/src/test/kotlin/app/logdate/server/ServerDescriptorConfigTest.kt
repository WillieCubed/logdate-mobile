package app.logdate.server

import app.logdate.server.accountkeys.AccountKeyVault
import app.logdate.server.accountkeys.InMemoryAccountKeyRepository
import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
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
    fun `account key vault is advertised only when its keyring is configured`() {
        val keyring =
            object : EncryptionKeyring {
                private val key = EncryptionKey("vault-key", ByteArray(32) { it.toByte() })

                override fun getActiveKey(): EncryptionKey = key

                override fun getKey(keyId: String): EncryptionKey? = key.takeIf { it.keyId == keyId }
            }
        val configured = AccountKeyVault(InMemoryAccountKeyRepository(), keyring)
        val unconfigured = AccountKeyVault(InMemoryAccountKeyRepository(), keyring = null)
        val identity = AtprotoIdentityConfig()

        fun descriptor(vault: AccountKeyVault) =
            ServerDescriptorConfig().toDescriptor(identity, "logdate.app", "LogDate", accountKeyVaultEnabled = vault.isAvailable)

        val advertised = descriptor(configured)
        val withheld = descriptor(unconfigured)

        assertTrue(advertised.hasProtocolFeature(ServerProtocolFeature.ACCOUNT_KEY_VAULT_V1))
        assertFalse(withheld.hasProtocolFeature(ServerProtocolFeature.ACCOUNT_KEY_VAULT_V1))
        assertFalse(descriptorFor(identity).hasProtocolFeature(ServerProtocolFeature.ACCOUNT_KEY_VAULT_V1))
        assertEquals(withheld.capabilities, advertised.capabilities)
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
