package app.logdate.client.data.audio

import app.logdate.client.database.dao.AudioTagDao
import app.logdate.client.database.entities.AudioTagEntity
import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Implementation of [AudioTagRepository] backed by the local Room database.
 */
class OfflineFirstAudioTagRepository(
    private val audioTagDao: AudioTagDao,
) : AudioTagRepository {
    override suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    ) {
        val now = Clock.System.now()
        val entities =
            tags.map { tag ->
                AudioTagEntity(
                    noteId = tag.noteId,
                    soundName = tag.soundName,
                    confidence = tag.confidence,
                    startMs = tag.startMs,
                    durationMs = tag.durationMs,
                    created = now,
                )
            }
        audioTagDao.replaceTagsForNote(noteId, entities)
    }

    override suspend fun getTagsForNote(noteId: Uuid): List<AudioTag> = audioTagDao.getTagsForNote(noteId).map { it.toDomain() }

    override fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>> =
        audioTagDao
            .observeTagsForNote(noteId)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun findNotesBySoundName(soundName: String): List<Uuid> = audioTagDao.findNotesBySoundName(soundName)

    private fun AudioTagEntity.toDomain(): AudioTag =
        AudioTag(
            noteId = noteId,
            soundName = soundName,
            confidence = confidence,
            startMs = startMs,
            durationMs = durationMs,
        )
}
