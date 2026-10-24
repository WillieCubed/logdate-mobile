package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerPasskeyConfig
import app.logdate.shared.model.ServerProtocolFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetainedJournalSourceScopeTest {
    @Test
    fun `same bound owner and normalized origin are required`() {
        val first = scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", "true", "https://Cloud.Example/")
        val same = scope("347C6231-19F4-49FA-BD1D-58ED34B9BE03", "true", "https://cloud.example")
        val otherOwner = scope("c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9", "true", "https://cloud.example")
        val otherOrigin = scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", "true", "https://other.example")

        assertTrue(requireNotNull(first).matchesCurrent(requireNotNull(same)))
        assertFalse(requireNotNull(first).matchesCurrent(requireNotNull(otherOwner)))
        assertFalse(requireNotNull(first).matchesCurrent(requireNotNull(otherOrigin)))
    }

    @Test
    fun `missing source owner and unbound source cannot be inferred into a bound account`() {
        val missing = scope(null, null, "https://cloud.example")
        val unbound = scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", null, "https://cloud.example")
        val current = requireNotNull(scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", "true", "https://cloud.example"))
        val sameLocal = requireNotNull(scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", null, "https://cloud.example"))

        assertFalse(requireNotNull(missing).matchesCurrent(current))
        assertFalse(requireNotNull(unbound).matchesCurrent(current))
        assertTrue(requireNotNull(unbound).matchesCurrent(sameLocal))
    }

    @Test
    fun `validated old to new owner adoption checks repository against live owner`() {
        val source = requireNotNull(scope("347c6231-19f4-49fa-bd1d-58ed34b9be03", "false", "https://cloud.example"))
        val current = requireNotNull(scope("c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9", "true", "https://cloud.example"))

        assertFalse(source.matchesCurrent(current))
        assertTrue(current.matchesRepositoryScope(current.ownerId, "https://cloud.example/"))
        assertFalse(current.matchesRepositoryScope(source.ownerId, "https://cloud.example"))
        assertFalse(current.matchesRepositoryScope(current.ownerId, "https://other.example"))
    }

    @Test
    fun `credentialed or non-origin URLs are rejected`() {
        assertNull(normalizeBackendOrigin("https://user:pass@cloud.example"))
        assertNull(normalizeBackendOrigin("https://cloud.example/private"))
        assertNull(normalizeBackendOrigin("ftp://cloud.example"))
        assertEquals("https://cloud.example", normalizeBackendOrigin("https://CLOUD.EXAMPLE:443/"))
        assertEquals("http://10.0.2.2:8765", normalizeBackendOrigin("http://10.0.2.2:8765/"))
    }

    @Test
    fun `captured default origin supports local offline owner without modifying retained preferences`() =
        runBlocking {
            val root =
                kotlin.io.path
                    .createTempDirectory("retained-scope-test")
                    .toFile()
            try {
                val prefsFile = File(root, "logdate.preferences_pb")
                val writerJob = SupervisorJob()
                val writerScope = CoroutineScope(writerJob + Dispatchers.IO)
                PreferenceDataStoreFactory.create(scope = writerScope, produceFile = { prefsFile }).updateData { current ->
                    current.toMutablePreferences().apply {
                        this[stringPreferencesKey("identity.canonical_owner_id")] = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
                    }
                }
                writerJob.cancelAndJoin()
                val originalBytes = prefsFile.readBytes()
                val anchor =
                    File(root, "source_scope.json").apply {
                        writeText(
                            Json.encodeToString(
                                RetainedSourceScopeAnchor(
                                    version = 1,
                                    readState = "captured",
                                    canonicalOwnerId = "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                                    backendOrigin = "https://offline.example",
                                    originProvenance = "default-at-capture",
                                ),
                            ),
                        )
                    }
                val scratch = File(root, "scratch").apply { mkdirs() }

                val scope = requireNotNull(readRetainedJournalSourceScope(prefsFile, anchor, scratch))

                assertEquals("347c6231-19f4-49fa-bd1d-58ed34b9be03", scope.ownerId)
                assertFalse(scope.bound)
                assertEquals("https://offline.example", scope.backendOrigin)
                assertTrue(originalBytes.contentEquals(prefsFile.readBytes()))
                assertTrue(scratch.listFiles().orEmpty().isEmpty())
            } finally {
                root.deleteRecursively()
            }
        }

    private fun scope(
        owner: String?,
        bound: String?,
        origin: String,
    ) = RetainedJournalSourceScope.from(
        mutablePreferencesOf().apply {
            this[stringPreferencesKey("backend_url")] = origin
            this[stringPreferencesKey("server_descriptor")] =
                Json.encodeToString(
                    ServerDescriptor(
                        serverOrigin = origin.trimEnd('/'),
                        apiBaseUrl = "${origin.trimEnd('/')}/api/v1",
                        deploymentKind = DeploymentKind.SELF_HOSTED,
                        displayName = "Test",
                        passkey = ServerPasskeyConfig("cloud.example", "Test"),
                        capabilities = listOf(ServerCapability.AUTH_PASSKEY),
                        protocolFeatures = listOf(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1),
                    ),
                )
            if (owner != null) this[stringPreferencesKey("identity.canonical_owner_id")] = owner
            if (bound != null) this[stringPreferencesKey("identity.canonical_owner_bound")] = bound
        },
    )
}
