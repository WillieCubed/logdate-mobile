package app.logdate.client.data.transcription

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.database.dao.TranscriptionDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
import app.logdate.client.database.entities.TranscriptionStatus
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.repository.transcription.TranscriptDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class OfflineFirstTranscriptionRepositoryTest {
    private val now = Instant.parse("2026-09-01T12:00:00Z")

    @Test
    fun `a rejected enqueue is persisted as failed instead of stranded pending`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val transcriptions = FakeTranscriptionDao()
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = transcriptions,
                    audioNoteDao = notes,
                    transcriptionManager = FakeTranscriptionManager(enqueueResult = false),
                    now = { now },
                )

            assertFalse(repository.requestTranscription(noteId))
            assertEquals(
                TranscriptionStatus.FAILED,
                transcriptions.getTranscriptionByNoteId(noteId)?.status,
            )
        }

    @Test
    fun `a stale in-progress transcription is requeued`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val transcriptions =
                FakeTranscriptionDao().apply {
                    insertTranscription(
                        transcription(
                            noteId = noteId,
                            status = TranscriptionStatus.IN_PROGRESS,
                            lastUpdated = now - 11.minutes,
                        ),
                    )
                }
            val manager = FakeTranscriptionManager(enqueueResult = true)
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = transcriptions,
                    audioNoteDao = notes,
                    transcriptionManager = manager,
                    now = { now },
                )

            assertTrue(repository.requestTranscription(noteId))
            assertEquals(1, manager.enqueueCount)
            assertEquals(
                TranscriptionStatus.PENDING,
                transcriptions.getTranscriptionByNoteId(noteId)?.status,
            )
        }

    @Test
    fun `an update that touches no row is reported as a persistence failure`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val transcriptions =
                FakeTranscriptionDao(updateRowCount = 0).apply {
                    insertTranscription(transcription(noteId, TranscriptionStatus.IN_PROGRESS, now))
                }
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = transcriptions,
                    audioNoteDao = notes,
                    transcriptionManager = FakeTranscriptionManager(true),
                    now = { now },
                )

            assertFalse(
                repository.updateTranscription(
                    noteId = noteId,
                    text = "words",
                    status = app.logdate.client.repository.transcription.TranscriptionStatus.COMPLETED,
                ),
            )
        }

    @Test
    fun `a scheduling failure that cannot be recorded is reported without escaping`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val transcriptions =
                FakeTranscriptionDao(throwOnUpdate = true).apply {
                    insertTranscription(transcription(noteId, TranscriptionStatus.PENDING, now))
                }
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = transcriptions,
                    audioNoteDao = notes,
                    transcriptionManager = FakeTranscriptionManager(false),
                    now = { now },
                )

            assertFalse(repository.requestTranscription(noteId))
        }

    @Test
    fun `a structured transcript insert failure is returned to the realtime retry path`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = FakeTranscriptionDao(throwOnInsert = true),
                    audioNoteDao = notes,
                    transcriptionManager = FakeTranscriptionManager(true),
                    now = { now },
                )

            assertFalse(
                repository.updateTranscriptDocument(
                    noteId = noteId,
                    document = TranscriptDocument.fromPlainText("words"),
                    status = app.logdate.client.repository.transcription.TranscriptionStatus.COMPLETED,
                ),
            )
        }

    @Test
    fun `a plain transcript insert failure is returned to the realtime retry path`() =
        runTest {
            val noteId = Uuid.random()
            val notes = FakeAudioNoteDao().apply { addNote(audioNote(noteId)) }
            val repository =
                OfflineFirstTranscriptionRepository(
                    transcriptionDao = FakeTranscriptionDao(throwOnInsert = true),
                    audioNoteDao = notes,
                    transcriptionManager = FakeTranscriptionManager(true),
                    now = { now },
                )

            assertFalse(
                repository.updateTranscription(
                    noteId = noteId,
                    text = "words",
                    status = app.logdate.client.repository.transcription.TranscriptionStatus.COMPLETED,
                ),
            )
        }

    private fun audioNote(noteId: Uuid) =
        AudioNoteEntity(
            uid = noteId,
            contentUri = "file:///recording.m4a",
            created = now,
            lastUpdated = now,
        )

    private fun transcription(
        noteId: Uuid,
        status: TranscriptionStatus,
        lastUpdated: Instant,
    ) = TranscriptionEntity(
        noteId = noteId,
        text = null,
        status = status,
        created = lastUpdated,
        lastUpdated = lastUpdated,
    )
}

private class FakeTranscriptionManager(
    private val enqueueResult: Boolean,
) : TranscriptionManager {
    var enqueueCount = 0

    override suspend fun enqueueTranscription(
        noteId: Uuid,
        audioUri: String,
    ): Boolean {
        enqueueCount += 1
        return enqueueResult
    }

    override suspend fun cancelTranscription(noteId: Uuid): Boolean = true

    override suspend fun cancelAllTranscriptions(): Int = 0
}

private class FakeTranscriptionDao(
    private val updateRowCount: Int = 1,
    private val throwOnInsert: Boolean = false,
    private val throwOnUpdate: Boolean = false,
) : TranscriptionDao {
    private val values = linkedMapOf<Uuid, TranscriptionEntity>()
    private val flows = mutableMapOf<Uuid, MutableStateFlow<TranscriptionEntity?>>()

    override suspend fun insertTranscription(transcription: TranscriptionEntity): Long {
        if (throwOnInsert) error("insert unavailable")
        values[transcription.noteId] = transcription
        flows.getOrPut(transcription.noteId) { MutableStateFlow(null) }.value = transcription
        return 1
    }

    override suspend fun insertSegments(segments: List<TranscriptionSegmentEntity>) = Unit

    override suspend fun updateTranscription(transcription: TranscriptionEntity): Int {
        if (throwOnUpdate) error("update unavailable")
        if (updateRowCount > 0) {
            values[transcription.noteId] = transcription
            flows.getOrPut(transcription.noteId) { MutableStateFlow(null) }.value = transcription
        }
        return updateRowCount
    }

    override suspend fun getTranscriptionById(id: Uuid): TranscriptionEntity? = values.values.firstOrNull { it.id == id }

    override suspend fun getTranscriptionByNoteId(noteId: Uuid): TranscriptionEntity? = values[noteId]

    override suspend fun getSegmentsByNoteId(noteId: Uuid): List<TranscriptionSegmentEntity> = emptyList()

    override fun observeTranscriptionByNoteId(noteId: Uuid): Flow<TranscriptionEntity?> =
        flows.getOrPut(noteId) { MutableStateFlow(values[noteId]) }

    override suspend fun getAllTranscriptions(): List<TranscriptionEntity> = values.values.toList()

    override suspend fun getTranscriptionsByStatus(status: TranscriptionStatus): List<TranscriptionEntity> =
        values.values.filter { it.status == status }

    override suspend fun getActiveTranscriptions(): List<TranscriptionEntity> =
        values.values.filter { it.status == TranscriptionStatus.PENDING || it.status == TranscriptionStatus.IN_PROGRESS }

    override suspend fun updateTranscriptionStatus(
        id: Uuid,
        status: TranscriptionStatus,
        errorMessage: String?,
        timestamp: Instant,
    ): Int = updateById(id) { it.copy(status = status, errorMessage = errorMessage, lastUpdated = timestamp) }

    override suspend fun updateTranscriptionText(
        id: Uuid,
        text: String,
        timestamp: Instant,
    ): Int = updateById(id) { it.copy(text = text, lastUpdated = timestamp) }

    override suspend fun deleteTranscription(id: Uuid): Int {
        val key = values.entries.firstOrNull { it.value.id == id }?.key ?: return 0
        values.remove(key)
        return 1
    }

    override suspend fun deleteTranscriptionByNoteId(noteId: Uuid): Int = if (values.remove(noteId) != null) 1 else 0

    override suspend fun deleteSegmentsByNoteId(noteId: Uuid): Int = 0

    private fun updateById(
        id: Uuid,
        transform: (TranscriptionEntity) -> TranscriptionEntity,
    ): Int {
        val entry = values.entries.firstOrNull { it.value.id == id } ?: return 0
        values[entry.key] = transform(entry.value)
        return 1
    }
}
