package app.logdate.feature.journals.ui.detail

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.storage.WaveformStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeJournalNotesRepository(
    initialNotes: List<JournalNote> = emptyList(),
) : JournalNotesRepository {
    private val notesFlow = MutableStateFlow(initialNotes)

    var lastRemovedId: Uuid? = null

    override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = notesFlow

    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = notesFlow

    override suspend fun getNoteById(noteId: Uuid): JournalNote? {
        return notesFlow.value.firstOrNull { it.uid == noteId }
    }

    override suspend fun create(note: JournalNote): Uuid {
        notesFlow.value = notesFlow.value + note
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notesFlow.value = notesFlow.value.filterNot { it.uid == note.uid }
    }

    override suspend fun removeById(noteId: Uuid) {
        lastRemovedId = noteId
        notesFlow.value = notesFlow.value.filterNot { it.uid == noteId }
    }

    override suspend fun create(note: JournalNote, journalId: Uuid) {
        create(note)
    }

    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {
        // No-op for tests.
    }

    fun setNotes(notes: List<JournalNote>) {
        notesFlow.value = notes
    }
}

class FakeAudioDurationResolver(
    private val durationMs: Long = 0L,
) : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? = durationMs
}

class FakeAudioPlaybackManager : AudioPlaybackManager, AudioPlaybackStatusProvider {
    private val statusFlow = MutableStateFlow(AudioPlaybackStatus())

    var startCalls = 0
    var pauseCalls = 0
    var stopCalls = 0
    var releaseCalls = 0
    var lastSeek: Float? = null
    var lastUri: String? = null
    var lastMetadata: AudioPlaybackMetadata? = null
    var onProgressUpdated: ((Float) -> Unit)? = null
    var onPlaybackCompleted: (() -> Unit)? = null

    override val playbackStatus: StateFlow<AudioPlaybackStatus> = statusFlow

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        startCalls += 1
        lastUri = uri
        lastMetadata = metadata
        this.onProgressUpdated = onProgressUpdated
        this.onPlaybackCompleted = onPlaybackCompleted
        statusFlow.value = statusFlow.value.copy(isPlaying = true)
    }

    override fun pausePlayback() {
        pauseCalls += 1
        statusFlow.value = statusFlow.value.copy(isPlaying = false)
    }

    override fun stopPlayback() {
        stopCalls += 1
    }

    override fun seekTo(position: Float) {
        lastSeek = position
    }

    override fun release() {
        releaseCalls += 1
    }

    fun updateStatus(status: AudioPlaybackStatus) {
        statusFlow.value = status
    }
}

class FakeAmplitudeExtractor(
    private val amplitudes: List<Float> = listOf(0.1f, 0.2f, 0.3f),
) : AmplitudeExtractor {
    override suspend fun extractAmplitudes(uri: String, targetSampleCount: Int): List<Float> {
        return amplitudes
    }
}

class FakeWaveformStorage : WaveformStorage {
    override suspend fun save(audioUri: String, amplitudes: List<Float>) = Unit

    override suspend fun load(audioUri: String): List<Float>? = null

    override suspend fun exists(audioUri: String): Boolean = false

    override suspend fun delete(audioUri: String) = Unit
}
