package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingContentSyncTest {
    @Test
    fun originalInkUsesItsOwnWireTypeAndSurvivesDownload() =
        runTest {
            val api = MockCloudApiClientForContent()
            val source = DefaultCloudContentDataSource(api)
            val time = Instant.fromEpochMilliseconds(1000)
            val note =
                JournalNote.Handwriting(
                    uid = Uuid.random(),
                    creationTimestamp = time,
                    lastUpdated = time,
                    document =
                        HandwritingDocument(
                            strokes =
                                listOf(
                                    HandwritingStroke(
                                        Uuid.random(),
                                        "pen",
                                        "#263044",
                                        3.5f,
                                        listOf(HandwritingPoint(2.5f, 4.25f, 0.2f), HandwritingPoint(50.75f, 80.5f, 0.9f)),
                                    ),
                                ),
                        ),
                )
            source.uploadNote("test-token", note).getOrThrow()
            val upload = api.uploadCalls.single().second
            assertEquals("HANDWRITING", upload.type)
            assertEquals(note.document, HandwritingDocument.decode(requireNotNull(upload.content)))
            api.contentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = note.uid.toString(),
                                    type = upload.type,
                                    content = upload.content,
                                    createdAt = 1000,
                                    lastUpdated = 1000,
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 1000,
                    ),
                )
            val restored =
                source
                    .getContentChanges(
                        "test-token",
                        Instant.fromEpochMilliseconds(0),
                    ).getOrThrow()
                    .changes
                    .single() as JournalNote.Handwriting
            assertEquals(note.uid, restored.uid)
            assertEquals(note.document, restored.document)
        }

    @Test
    fun unreadableInkDoesNotBlockOtherDownloadedNotes() =
        runTest {
            val api = MockCloudApiClientForContent()
            val id = Uuid.random()
            api.contentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = id.toString(),
                                    type = "HANDWRITING",
                                    content = """{"version":2}""",
                                    createdAt = 1,
                                    lastUpdated = 1,
                                    serverVersion = 3,
                                ),
                                ContentChange(
                                    id = Uuid.random().toString(),
                                    type = "TEXT",
                                    content = "Readable neighbor",
                                    createdAt = 1,
                                    lastUpdated = 1,
                                    serverVersion = 4,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 4,
                    ),
                )
            val result = DefaultCloudContentDataSource(api).getContentChanges("test-token", Instant.fromEpochMilliseconds(0)).getOrThrow()
            assertEquals(id.toString(), result.failures.single().entityId)
            assertEquals(3L, result.failures.single().serverVersion)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.UNSUPPORTED_FORMAT, result.failures.single().reason)
            assertEquals("Readable neighbor", (result.changes.single() as JournalNote.Text).content)
        }
}
