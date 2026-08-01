package app.logdate.server

import app.logdate.shared.model.DeploymentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
