package app.logdate.client.e2e

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeArchiveDraftRetryTest {
    @Test
    fun archiveRetryPreservesAnEditThroughTheProductionRepositories(): Unit =
        runBlocking {
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext.packageName
                    .endsWith(".debug"),
            )
            val koin = GlobalContext.get()
            val journals = koin.get<JournalRepository>()
            val restore = koin.get<RestoreUserDataUseCase>()
            val id = Uuid.random()
            val now = Clock.System.now()
            val incoming = ExportDraft(id = id.toString(), content = "Archived draft", createdAt = now - 2.days, updatedAt = now - 1.days)
            val archive =
                RestoreBundle(
                    metadataJson =
                        Json.encodeToString(
                            ExportMetadata(
                                version = ExportSchemaVersion.V1_2,
                                exportDate = now,
                                userId = "synthetic-archive-owner",
                                deviceId = "synthetic-source-installation",
                                appVersion = "1.0.0",
                                stats = ExportStats(0, 0, 1, 0),
                            ),
                        ),
                    journalsJson = "{\"journals\":[]}",
                    notesJson = "{\"notes\":[]}",
                    journalNotesJson = "{\"journal_notes\":[]}",
                    draftsJson = Json.encodeToString(mapOf("drafts" to listOf(incoming))),
                )
            try {
                assertEquals(1, restore.restore(archive).draftsImported)
                assertEquals(incoming.updatedAt, assertNotNull(journals.getDraftForRestore(id)).lastModifiedAt)
                val edited =
                    EditorDraft(
                        id = id,
                        blocks = listOf(SerializableTextBlock(Uuid.random(), now, content = "Edited on the replacement phone")),
                        createdAt = incoming.createdAt,
                        lastModifiedAt = now,
                    )
                journals.saveDraft(edited)
                val accepted = assertNotNull(journals.getDraftForRestore(id))
                assertEquals(0, restore.restore(archive).draftsImported)
                assertEquals(accepted, journals.getDraftForRestore(id))
                assertEquals(0, restore.restore(archive).draftsImported)
                assertEquals(accepted, journals.getDraftForRestore(id))
            } finally {
                journals.deleteDraft(id)
            }
        }
}
