package app.logdate.client.data.notes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import app.logdate.client.data.journals.NativeDraftMediaQuarantine
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.RoomSyncTransactionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeMediaQuarantineTest {
    @Test
    fun `restored provider IDs are quarantined while private originals and timestamps survive`() =
        runTest {
            val file = Files.createTempFile("native-quarantine", ".db")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                val timestamp = Instant.fromEpochMilliseconds(1)
                val stale =
                    ImageNoteEntity(contentUri = "content://media/external/images/media/123", created = timestamp, lastUpdated = timestamp)
                val owned = stale.copy(uid = Uuid.random(), contentUri = "logdate-media://library/original.jpg")
                val shared = stale.copy(uid = Uuid.random(), contentUri = "/storage/emulated/0/DCIM/123.jpg")
                database.imageNoteDao().addNote(stale)
                database.imageNoteDao().addNote(owned)
                database.imageNoteDao().addNote(shared)
                val transcripts =
                    Proxy.newProxyInstance(
                        TranscriptionRepository::class.java.classLoader,
                        arrayOf(TranscriptionRepository::class.java),
                    ) { _, _, _ -> error("No audio expected") } as TranscriptionRepository
                val preferences =
                    object : DataStore<Preferences> {
                        override val data =
                            MutableStateFlow(
                                emptyPreferences(),
                            )

                        override suspend fun updateData(
                            transform: suspend (
                                Preferences,
                            ) -> Preferences,
                        ) = transform(data.value).also {
                            data.value =
                                it
                        }
                    }
                val repair =
                    NativeMediaQuarantine(
                        database,
                        NativeDraftMediaQuarantine(preferences, Json),
                        RoomSyncTransactionManager(database),
                        transcripts,
                    )
                repair.run { true }
                assertEquals(
                    stale.copy(contentUri = UnavailableMediaReferences.nativeRestore(stale.contentUri)),
                    database.imageNoteDao().getNoteOneOff(stale.uid),
                )
                assertEquals(owned, database.imageNoteDao().getNoteOneOff(owned.uid))
                assertEquals(
                    shared.copy(contentUri = UnavailableMediaReferences.nativeRestore(shared.contentUri)),
                    database.imageNoteDao().getNoteOneOff(shared.uid),
                )
                val rows = database.imageNoteDao().getAll()
                repair.run { true }
                assertEquals(rows.toSet(), database.imageNoteDao().getAll().toSet())
                assertFailsWith<IllegalStateException> { repair.run { false } }
            } finally {
                database.close()
                Files.deleteIfExists(file)
            }
        }
}
