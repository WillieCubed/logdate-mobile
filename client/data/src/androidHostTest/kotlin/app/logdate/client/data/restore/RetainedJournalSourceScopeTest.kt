package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
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
    fun `writer produced v2 receipt admits only its decoded exact source preferences`(): Unit =
        runBlocking {
            val root =
                kotlin.io.path
                    .createTempDirectory("native-v2-scope-")
                    .toFile()
            val job = SupervisorJob()
            try {
                val prefs = File(root, "logdate.preferences_pb")
                val writer = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { prefs })
                val snapshot =
                    writer.updateData {
                        mutablePreferencesOf(
                            stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                            stringPreferencesKey("identity.canonical_owner_bound") to "true",
                            stringPreferencesKey("backend_url") to "HTTPS://Cloud.Example/",
                        )
                    }
                val database = File(root, "logdate").apply { writeText("immutable encrypted DB fixture") }
                val receipt = File(root, "native_source_scope.json")
                writeNativeJournalSourceReceipt(receipt, snapshot, prefs, mapOf("database/logdate" to database))
                val scratch = File(root, "scratch").apply { mkdirs() }

                val admitted =
                    requireNotNull(
                        readRetainedJournalSourceScope(prefs, receipt, scratch, mapOf("database/logdate" to database)),
                    )

                assertEquals("347c6231-19f4-49fa-bd1d-58ed34b9be03", admitted.ownerId)
                assertEquals("https://cloud.example", admitted.backendOrigin)
                assertTrue(admitted.bound)
                database.appendText("changed")
                kotlin.test.assertFailsWith<IllegalStateException> {
                    readRetainedJournalSourceScope(prefs, receipt, scratch, mapOf("database/logdate" to database))
                }
            } finally {
                job.cancelAndJoin()
                check(root.deleteRecursively())
            }
        }

    @Test
    fun `pending adoption scope is refused while completed historical adoption remains valid`() {
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                stringPreferencesKey("backend_url") to "https://cloud.example",
                stringPreferencesKey("identity.pending_history_owner_adoption.v1") to "durable unfinished adoption",
            )
        assertNull(RetainedJournalSourceScope.from(preferences))
        preferences.remove(stringPreferencesKey("identity.pending_history_owner_adoption.v1"))
        preferences[stringPreferencesKey("identity.completed_history_owner_adoption.v1")] = "completed prior-owner history"
        assertEquals("https://cloud.example", requireNotNull(RetainedJournalSourceScope.from(preferences)).backendOrigin)
    }

    @Test
    fun `completed native source refuses account and backend switch and accepts only committed adoption`() {
        val oldOwner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
        val newOwner = "c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9"
        val source = RetainedJournalSourceScope(oldOwner, false, "https://cloud.example")
        val adopted = RetainedJournalSourceScope(newOwner, true, "https://cloud.example")
        val preferences = mutablePreferencesOf()
        assertTrue(source.matchesCurrentOrAdopted(source, preferences))
        assertFalse(source.matchesCurrentOrAdopted(adopted, preferences))
        preferences[stringPreferencesKey("identity.completed_history_owner_adoption.v1")] =
            """{"oldOwner":"$oldOwner","newOwner":"$newOwner","origin":"HTTPS://CLOUD.EXAMPLE/","device":"source-device"}"""
        assertTrue(source.matchesCurrentOrAdopted(adopted, preferences))
        assertFalse(source.copy(bound = true).matchesCurrentOrAdopted(adopted, preferences))
        assertFalse(source.matchesCurrentOrAdopted(adopted.copy(backendOrigin = "https://other.example"), preferences))
        assertFalse(source.matchesCurrentOrAdopted(adopted.copy(ownerId = "4bd791a7-c550-4003-b6ba-21ac54661a45"), preferences))
        assertFalse(source.matchesCurrentOrAdopted(adopted.copy(bound = false), preferences))
        preferences[stringPreferencesKey("identity.completed_history_owner_adoption.v1")] =
            """{"oldOwner":"$oldOwner","newOwner":"$newOwner","origin":"https://other.example","device":"source-device"}"""
        assertFalse(source.matchesCurrentOrAdopted(adopted, preferences))
    }

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
