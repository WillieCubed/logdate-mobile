package app.logdate.client.domain.timeline

import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

/**
 * In-memory [AudioTagRepository] for tests. By default it returns no tags for
 * any note, which mirrors the production behavior on devices where the
 * ambient sound model hasn't been downloaded yet — the use case still has to
 * work cleanly.
 *
 * Pass [tags] when a test needs to assert that the moment narrative reflects
 * the detected sounds.
 */
internal class FakeAudioTagRepository(
    private val tags: List<AudioTag> = emptyList(),
) : AudioTagRepository {
    override suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    ) = Unit

    override suspend fun getTagsForNote(noteId: Uuid): List<AudioTag> = tags.filter { it.noteId == noteId }

    override fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>> = flowOf(tags.filter { it.noteId == noteId })

    override suspend fun findNotesBySoundName(soundName: String): List<Uuid> =
        tags
            .filter { it.soundName.equals(soundName, ignoreCase = true) }
            .map { it.noteId }
            .distinct()
}
