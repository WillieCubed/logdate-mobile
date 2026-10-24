package app.logdate.client.data.restore

import android.app.Application
import android.content.ContextWrapper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.data.journals.ArchiveDraftStorageState
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeDraftOnlyTransferPreparationTest {
    @Test
    fun `drafts created before the journal open use the normal encrypted snapshot path`() {
        Fixture().use { fixture ->
            var opens = 0

            prepareNativeDraftOnlyDatabase(
                fixture.context,
                {
                    opens++
                    fixture.database.apply {
                        parentFile!!.mkdirs()
                        writeText("encrypted journal created")
                    }
                },
                hasEntryDrafts = { true },
            )

            assertEquals(1, opens)
            assertTrue(fixture.database.isFile)
        }
    }

    @Test
    fun `an empty installation is not initialized merely because Android requested backup`() {
        Fixture().use { fixture ->
            prepareNativeDraftOnlyDatabase(fixture.context, { error("unexpected database initialization") })

            assertFalse(fixture.database.exists())
        }
    }

    @Test
    fun `an empty installation emits no Apple payload and creates no journal`() {
        Fixture().use { fixture ->
            val emitted = mutableListOf<File>()
            runBlocking {
                NativeIosTransferSender(fixture.context, NativeJournalTransferSnapshot(fixture.context))
                    .send(9, emitted::add)
            }

            assertTrue(emitted.isEmpty())
            assertFalse(fixture.database.exists())
            assertFalse(File(fixture.context.filesDir, "logdate-os-transfer").exists())
        }
    }

    @Test
    fun `database-free settings content is not silently skipped`() {
        Fixture().use { fixture ->
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("theme_preference")] = "dark"
                }
            }
            val emitted = mutableListOf<File>()
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    NativeIosTransferSender(fixture.context, NativeJournalTransferSnapshot(fixture.context))
                        .send(9, emitted::add)
                }
            }

            assertTrue(emitted.isEmpty())
            assertFalse(fixture.database.exists())
        }
    }

    @Test
    fun `database-free draft deletion receipt is not silently skipped`() {
        Fixture().use { fixture ->
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("editor_drafts_anonymous")] =
                        ArchiveDraftStorageState(localDeletionIds = setOf(Uuid.random().toString())).encode(Json)
                }
            }
            val emitted = mutableListOf<File>()
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    NativeIosTransferSender(fixture.context, NativeJournalTransferSnapshot(fixture.context))
                        .send(9, emitted::add)
                }
            }

            assertTrue(emitted.isEmpty())
            assertFalse(fixture.database.exists())
        }
    }

    @Test
    fun `profile-only database-free source requires journal preparation for Apple transfer`() {
        Fixture().use { fixture ->
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("display_name")] = "Alex"
                }
            }

            val preferences = runBlocking { createAndroidMainDataStore(fixture.context).data.first() }
            assertTrue(NativeArchive3SourceCensus.hasPreDatabaseIosContent(preferences))
            prepareNativeDraftOnlyDatabase(
                fixture.context,
                {
                    fixture.database.apply {
                        parentFile!!.mkdirs()
                        writeText("test journal")
                    }
                },
                hasEntryDrafts = { NativeArchive3SourceCensus.hasPreDatabaseIosContent(preferences) },
            )
            assertTrue(fixture.database.isFile)
        }
    }

    @Test
    fun `canonical owner without a journal is also retained for Apple transfer`() {
        Fixture().use { fixture ->
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("identity.canonical_owner_id")] = Uuid.random().toString()
                }
            }

            val preferences = runBlocking { createAndroidMainDataStore(fixture.context).data.first() }
            assertTrue(NativeArchive3SourceCensus.hasPreDatabaseIosContent(preferences))
        }
    }

    @Test
    fun `an editor draft in main preferences also gains an encrypted source journal`() {
        Fixture().use { fixture ->
            val time = Instant.fromEpochMilliseconds(1_700_000_000_000)
            val draft =
                EditorDraft(
                    blocks =
                        listOf(
                            SerializableImageBlock(
                                Uuid.random(),
                                time,
                                uri = "file:/data/user/0/studio.hypertext.logdate/files/user_media/draft.png",
                            ),
                        ),
                )
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("editor_drafts_anonymous")] = Json.encodeToString(listOf(draft))
                }
            }

            prepareNativeDraftOnlyDatabase(
                fixture.context,
                {
                    fixture.database.apply {
                        parentFile!!.mkdirs()
                        writeText("encrypted journal created")
                    }
                },
                hasEntryDrafts = { false },
            )

            assertTrue(fixture.database.isFile)
        }
    }

    @Test
    fun `a text only editor draft in main preferences also gains a source journal`() {
        Fixture().use { fixture ->
            val draft =
                EditorDraft(
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                Uuid.random(),
                                Instant.fromEpochMilliseconds(1_700_000_000_000),
                                content = "Unfinished journal",
                            ),
                        ),
                )
            runBlocking {
                createAndroidMainDataStore(fixture.context).edit { preferences ->
                    preferences[stringPreferencesKey("editor_drafts_anonymous")] = Json.encodeToString(listOf(draft))
                }
            }

            prepareNativeDraftOnlyDatabase(
                fixture.context,
                {
                    fixture.database.apply {
                        parentFile!!.mkdirs()
                        writeText("encrypted journal created")
                    }
                },
                hasEntryDrafts = { false },
            )

            assertTrue(fixture.database.isFile)
        }
    }

    @Test
    fun `draft created during a database-free backup invalidates that backup`() {
        Fixture().use { fixture ->
            val snapshot = NativeJournalTransferSnapshot(fixture.context)
            snapshot.prepare()
            val draftFile = File(fixture.context.filesDir, "datastore/entry_drafts.preferences_pb")
            check(draftFile.parentFile!!.mkdirs() || draftFile.parentFile!!.isDirectory)
            draftFile.writeText("draft saved after source selection")

            assertFailsWith<IllegalStateException> { snapshot.finish() }
        }
    }

    @Test
    fun `journal created during a database-free backup invalidates that backup`() {
        Fixture().use { fixture ->
            val snapshot = NativeJournalTransferSnapshot(fixture.context)
            snapshot.prepare()
            check(fixture.database.parentFile!!.mkdirs() || fixture.database.parentFile!!.isDirectory)
            fixture.database.writeText("journal created during traversal")

            assertFailsWith<IllegalStateException> { snapshot.finish() }
        }
    }

    @Test
    fun `an interrupted restore cannot gain a new empty database during backup`() {
        Fixture().use { fixture ->
            PostRestoreDetector(fixture.context).beginNativeRestoreSession()

            val error =
                assertFailsWith<IllegalStateException> {
                    prepareNativeDraftOnlyDatabase(
                        fixture.context,
                        { error("unexpected database initialization") },
                        hasEntryDrafts = { true },
                    )
                }
            assertTrue(error.message.orEmpty().contains("Native restore must finish"))
            assertFalse(fixture.database.exists())
        }
    }

    private class Fixture : AutoCloseable {
        private val root = Files.createTempDirectory("native-draft-only-").toFile()
        private val files = File(root, "files").apply { mkdirs() }
        private val noBackup = File(root, "no-backup").apply { mkdirs() }
        val database = File(root, "database/logdate")
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                override fun getFilesDir() = files

                override fun getNoBackupFilesDir() = noBackup

                override fun getApplicationContext() = this

                override fun getDatabasePath(name: String) = File(root, "database/$name")
            }

        override fun close() {
            check(root.deleteRecursively())
        }
    }
}
