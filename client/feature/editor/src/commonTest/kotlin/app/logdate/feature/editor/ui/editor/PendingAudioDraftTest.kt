package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.feature.editor.ui.editor.delegate.DefaultPendingAudioRecoverer
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PendingAudioDraftTest {
    private val repository = FakeEntryDraftRepository()
    private val manager =
        DraftManager(
            updateEntryDraft = UpdateEntryDraftUseCase(repository),
            createEntryDraft = CreateEntryDraftUseCase(repository),
            fetchEntryDraft = FetchEntryDraftUseCase(repository),
            deleteEntryDraft = DeleteEntryDraftUseCase(repository, FakeJournalNotesRepository()),
            deleteAllDraftsUseCase = DeleteAllDraftsUseCase(repository),
            cleanupExpiredDraftsUseCase = CleanupExpiredDraftsUseCase(repository),
        )
    private val recordedAt = Instant.parse("2026-01-02T03:04:05Z")

    @Test
    fun `missing transferred recording retains its metadata through autosave and later recovery`() =
        runTest {
            val text = TextBlockUiState(timestamp = recordedAt, content = "Before the recording")
            val audio =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Stopping("/staging/excluded.m4a", "/audio/committed.m4a"),
                    caption = "A retained caption",
                    transcription = "Words already transcribed",
                )
            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(text, audio))))
            val pendingBefore =
                repository
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
            var fallbackAvailable = false
            val recoverer =
                DefaultPendingAudioRecoverer(
                    AudioDurationResolver { path -> if (fallbackAvailable && path == "/audio/committed.m4a") 2500L else null },
                )
            val loaded = manager.loadDraft(id).getOrThrow().blocks
            val restored = loaded.last() as AudioBlockUiState
            val failed = recoverer.recover(restored.captureState as AudioCaptureState.Stopping)
            assertIs<AudioCaptureState.Failed>(failed)

            manager.autoSave(
                EditorState(blocks = listOf(loaded.first(), restored.copy(captureState = failed)), draftState = DraftState.Active(id)),
            )

            val pendingAfter =
                repository
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .pendingMedia
            assertEquals(listOf(pendingBefore), pendingAfter)
            repository.setPendingMedia(id, Json.decodeFromString(Json.encodeToString(pendingAfter)))
            val reopened = manager.loadDraft(id).getOrThrow().blocks
            assertEquals(listOf(text.id, audio.id), reopened.map { it.id })
            val retained = reopened.last() as AudioBlockUiState
            assertEquals(audio.caption, retained.caption)
            assertEquals(audio.transcription, retained.transcription)
            assertEquals(audio.timestamp, retained.timestamp)
            fallbackAvailable = true
            assertEquals(
                AudioCaptureState.Ready("/audio/committed.m4a", 2500),
                recoverer.recover(retained.captureState as AudioCaptureState.Stopping),
            )
        }

    @Test
    fun `pending recording stays between text blocks with identical timestamps`() =
        runTest {
            val first = TextBlockUiState(timestamp = recordedAt, content = "Before recording")
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Recording("/audio/recording.m4a"),
                )
            val last = TextBlockUiState(timestamp = recordedAt, content = "After recording")

            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(first, recording, last))))
            val pending =
                repository
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .pendingMedia
            repository.setPendingMedia(id, Json.decodeFromString(Json.encodeToString(pending)))
            val loaded = manager.loadDraft(id).getOrThrow()

            assertEquals(listOf(first.id, recording.id, last.id), loaded.blocks.map { it.id })
        }

    @Test
    fun `omitted blocks do not move recovered recordings past editable text`() =
        runTest {
            val readOnly = TextBlockUiState(timestamp = recordedAt, content = "Previously published")
            val empty = TextBlockUiState(timestamp = recordedAt)
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Stopping("/audio/recording.m4a"),
                )
            val text = TextBlockUiState(timestamp = recordedAt, content = "After recording")
            val id =
                assertNotNull(
                    manager.autoSave(
                        EditorState(
                            blocks = listOf(readOnly, empty, recording, text),
                            readOnlyBlocks = mapOf(readOnly.id to true),
                        ),
                    ),
                )

            assertEquals(
                listOf(recording.id, text.id),
                manager
                    .loadDraft(id)
                    .getOrThrow()
                    .blocks
                    .map { it.id },
            )
        }

    @Test
    fun `repeated autosaves retain the recording start timestamp`() =
        runTest {
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Recording("/audio/recording.m4a"),
                )
            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(recording))))
            assertEquals(
                recordedAt,
                repository
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .createdAt,
            )

            manager.autoSave(
                EditorState(
                    blocks = listOf(recording.copy(captureState = AudioCaptureState.Stopping("/audio/recording.m4a"))),
                    draftState = DraftState.Active(id),
                ),
            )

            assertEquals(
                recordedAt,
                repository
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single()
                    .createdAt,
            )
            assertEquals(
                recordedAt,
                manager
                    .loadDraft(id)
                    .getOrThrow()
                    .blocks
                    .single()
                    .timestamp,
            )
        }

    @Test
    fun `pending recording caption and transcription survive serialized recovery`() =
        runTest {
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Stopping("/audio/recording.m4a"),
                    caption = "At the station",
                    transcription = "The train is arriving.",
                )
            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(recording))))
            val saved = repository.getDraft(id).first().getOrThrow()
            val serialized = Json.encodeToString(saved.pendingMedia.single())
            val recoveredRecord = Json.decodeFromString<PendingMediaRecord>(serialized)
            repository.setPendingMedia(id, listOf(recoveredRecord))

            val loaded =
                manager
                    .loadDraft(id)
                    .getOrThrow()
                    .blocks
                    .single() as AudioBlockUiState

            assertEquals("At the station", loaded.caption)
            assertEquals("The train is arriving.", loaded.transcription)
            assertEquals(AudioCaptureState.Stopping("/audio/recording.m4a"), loaded.captureState)
        }

    @Test
    fun `replacement recording draft retains the previous take as a fallback`() =
        runTest {
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState =
                        AudioCaptureState.Recording(
                            filePath = "/audio/replacement.m4a",
                            fallbackFilePath = "/audio/previous.m4a",
                        ),
                )
            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(recording))))

            val loaded =
                manager
                    .loadDraft(id)
                    .getOrThrow()
                    .blocks
                    .single() as AudioBlockUiState

            assertEquals(
                AudioCaptureState.Stopping(
                    filePath = "/audio/replacement.m4a",
                    fallbackFilePath = "/audio/previous.m4a",
                ),
                loaded.captureState,
            )
        }

    @Test
    fun `ready recording caption and transcript survive draft note serialization`() =
        runTest {
            val recording =
                AudioBlockUiState(
                    timestamp = recordedAt,
                    captureState = AudioCaptureState.Ready("/audio/ready.m4a", 2500),
                    caption = "At the station",
                    transcription = "The train is arriving.",
                )
            val id = assertNotNull(manager.autoSave(EditorState(blocks = listOf(recording))))
            val saved = repository.getDraft(id).first().getOrThrow()
            val notes = Json.decodeFromString<List<JournalNote>>(Json.encodeToString(saved.notes))
            repository.updateDraft(id, notes)

            val loaded =
                manager
                    .loadDraft(id)
                    .getOrThrow()
                    .blocks
                    .single() as AudioBlockUiState

            assertEquals("At the station", loaded.caption)
            assertEquals("The train is arriving.", loaded.transcription)
            assertEquals(AudioCaptureState.Ready("/audio/ready.m4a", 2500), loaded.captureState)
        }

    @Test
    fun `legacy serialized pending recordings remain recoverable after notes`() =
        runTest {
            val legacy =
                Json.decodeFromString<PendingMediaRecord>(
                    """
                    {
                      "blockId": "c454d604-4ca2-48b4-a95f-c44e80c4723c",
                      "mediaType": "AUDIO",
                      "createdAt": "2026-01-02T03:04:05Z",
                      "filePath": "/audio/legacy.m4a"
                    }
                    """.trimIndent(),
                )
            val text =
                JournalNote.Text(
                    creationTimestamp = recordedAt,
                    lastUpdated = recordedAt,
                    content = "Existing legacy note",
                )
            val id =
                repository.createDraft(
                    uid = Uuid.random(),
                    notes = listOf(text),
                    pendingMedia = listOf(legacy),
                    selectedJournalIds = emptyList(),
                )
            val loaded = manager.loadDraft(id).getOrThrow().blocks

            assertEquals(listOf(text.uid, legacy.blockId), loaded.map { it.id })
            val audio = loaded.last() as AudioBlockUiState
            assertEquals(recordedAt, audio.timestamp)
            assertEquals("", audio.caption)
            assertEquals("", audio.transcription)
            assertEquals(AudioCaptureState.Stopping("/audio/legacy.m4a"), audio.captureState)
        }
}
