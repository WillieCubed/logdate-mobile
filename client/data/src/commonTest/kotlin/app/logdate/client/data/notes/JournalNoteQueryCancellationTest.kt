package app.logdate.client.data.notes

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.data.fakes.FakeImageNoteDao
import app.logdate.client.data.fakes.FakeJournalContentDao
import app.logdate.client.data.fakes.FakeMediaCaptionDao
import app.logdate.client.data.fakes.FakeTextNoteDao
import app.logdate.client.data.fakes.FakeVideoNoteDao
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class JournalNoteQueryCancellationTest {
    @Test
    fun `interrupted or failed queries never report an existing note as absent`() =
        runTest {
            for (failure in listOf(CancellationException("Worker replaced"), IllegalStateException("Storage unavailable"))) {
                for (kind in 0..3) {
                    val text =
                        object : TextNoteDao by FakeTextNoteDao() {
                            override suspend fun findNoteOneOff(uid: Uuid): TextNoteEntity? {
                                if (kind == 0) throw failure
                                return null
                            }
                        }
                    val image =
                        object : ImageNoteDao by FakeImageNoteDao() {
                            override suspend fun findNoteOneOff(uid: Uuid): ImageNoteEntity? {
                                if (kind == 1) throw failure
                                return null
                            }
                        }
                    val audio =
                        object : AudioNoteDao by FakeAudioNoteDao() {
                            override suspend fun findNoteOneOff(uid: Uuid): AudioNoteEntity? {
                                if (kind == 2) throw failure
                                return null
                            }
                        }
                    val video =
                        object : VideoNoteDao by FakeVideoNoteDao() {
                            override suspend fun findNoteOneOff(uid: Uuid): VideoNoteEntity? = throw failure
                        }
                    val queries =
                        JournalNoteQueries(
                            text,
                            image,
                            audio,
                            video,
                            FakeJournalContentDao(),
                            FakeMediaCaptionDao(),
                            EmptyNotePlaceResolver,
                            null,
                        )
                    assertSame(failure, assertFails { queries.getNoteById(Uuid.random()) })
                }
            }
        }
}
