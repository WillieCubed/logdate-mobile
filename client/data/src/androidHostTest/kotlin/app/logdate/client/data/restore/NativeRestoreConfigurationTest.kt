package app.logdate.client.data.restore

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeRestoreConfigurationTest {
    private val origin = "https://journal.example.com"
    private val descriptor =
        ServerDescriptor(
            origin,
            "$origin/api/v1",
            deploymentKind = DeploymentKind.SELF_HOSTED,
            displayName = "Synthetic",
            capabilities = listOf(ServerCapability.AUTH_PASSKEY),
            protocolFeatures = listOf(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1),
        )
    private val backend = stringPreferencesKey("backend_url")
    private val server = stringPreferencesKey("server_descriptor")

    @Test
    fun `empty configuration selects the known default server`() {
        assertEquals(
            DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL,
            nativeRestoreConfiguration(preferencesOf())?.getCurrentBackendUrl(),
        )
    }

    @Test
    fun `a valid custom server remains pinned to its advertised API`() {
        val config = nativeRestoreConfiguration(preferencesOf(backend to origin, server to Json.encodeToString(descriptor)))
        assertEquals("$origin/api/v1", config?.getCurrentApiBaseUrl())
    }

    @Test
    fun `corrupt custom configuration never falls back to the default server`() {
        assertNull(nativeRestoreConfiguration(preferencesOf(backend to origin)))
        assertNull(nativeRestoreConfiguration(preferencesOf(backend to "invalid")))
        assertNull(nativeRestoreConfiguration(preferencesOf(backend to origin, server to "invalid")))
        assertNull(
            nativeRestoreConfiguration(
                preferencesOf(
                    backend to origin,
                    server to Json.encodeToString(descriptor.copy(apiBaseUrl = "https://other.example/api/v1")),
                ),
            ),
        )
        assertNull(
            nativeRestoreConfiguration(
                preferencesOf(
                    backend to origin,
                    server to Json.encodeToString(descriptor.copy(serverOrigin = "https://other.example")),
                ),
            ),
        )
        assertNull(
            nativeRestoreConfiguration(
                preferencesOf(
                    backend to origin,
                    server to Json.encodeToString(descriptor.copy(protocolFeatures = emptyList())),
                ),
            ),
        )
    }
}
