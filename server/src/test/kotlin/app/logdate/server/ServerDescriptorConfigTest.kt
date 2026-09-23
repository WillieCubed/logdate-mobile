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
