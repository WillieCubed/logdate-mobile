package app.logdate.client.repository.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptDocumentTest {
    @Test
    fun plainText_joins_final_segments_in_timeline_order() {
        val document =
            TranscriptDocument(
                segments =
                    listOf(
                        segment(id = "later", text = "second thought", startMs = 1_000),
                        segment(id = "earlier", text = "First thought.", startMs = 0),
                    ),
            )

        assertEquals("First thought. second thought", document.plainText)
    }

    @Test
    fun upsertSegment_replaces_matching_segment_without_changing_its_identity() {
        val original =
            TranscriptDocument(
                revision = 4,
                segments =
                    listOf(
                        segment(id = "s1", text = "draft wrds", isFinal = false),
                    ),
            )

        val updated =
            original.upsertSegment(
                segment(id = "s1", text = "draft words", isFinal = true),
            )

        assertEquals(5, updated.revision)
        assertEquals(listOf("s1"), updated.segments.map { it.segmentId })
        assertEquals("draft words", updated.plainText)
        assertTrue(updated.segments.single().isFinal)
    }

    @Test
    fun upsertSegment_ignores_lower_priority_revision_for_same_segment() {
        val original =
            TranscriptDocument(
                segments =
                    listOf(
                        segment(
                            id = "s1",
                            text = "cloud final text",
                            source = TranscriptSource.CLOUD_REFINEMENT,
                            isFinal = true,
                        ),
                    ),
            )

        val updated =
            original.upsertSegment(
                segment(
                    id = "s1",
                    text = "local draft text",
                    source = TranscriptSource.LOCAL_LIVE,
                    isFinal = false,
                ),
            )

        assertEquals(original, updated)
        assertEquals("cloud final text", updated.plainText)
        assertTrue(updated.segments.single().isFinal)
    }

    @Test
    fun isFinal_is_true_only_when_document_and_segments_are_final() {
        val finalDocument =
            TranscriptDocument(
                status = TranscriptDocumentStatus.FINAL,
                segments =
                    listOf(
                        segment(id = "s1", isFinal = true),
                        segment(id = "s2", isFinal = true),
                    ),
            )

        val refiningDocument =
            finalDocument.copy(
                status = TranscriptDocumentStatus.REFINING,
                segments = finalDocument.segments.plus(segment(id = "s3", isFinal = false)),
            )

        assertTrue(finalDocument.isFinal)
        assertFalse(refiningDocument.isFinal)
    }

    @Test
    fun coordinator_keeps_local_draft_until_cloud_live_replaces_it() {
        val coordinator = TranscriptSessionCoordinator(language = "en-US")

        val local =
            coordinator.apply(
                TranscriptSessionUpdate.UpsertSegment(
                    segment = segment(id = "s1", text = "local guess", source = TranscriptSource.LOCAL_LIVE, isFinal = false),
                ),
            )
        val cloud =
            coordinator.apply(
                TranscriptSessionUpdate.UpsertSegment(
                    segment = segment(id = "s1", text = "cloud guess", source = TranscriptSource.CLOUD_LIVE, isFinal = false),
                ),
            )

        assertEquals("local guess", local.plainText)
        assertEquals("cloud guess", cloud.plainText)
        assertEquals(TranscriptDocumentStatus.LISTENING, cloud.status)
    }

    @Test
    fun coordinator_marks_refining_and_final_states_without_losing_segments() {
        val coordinator = TranscriptSessionCoordinator()
        coordinator.apply(
            TranscriptSessionUpdate.UpsertSegment(
                segment = segment(id = "s1", text = "rough text", source = TranscriptSource.LOCAL_LIVE, isFinal = true),
            ),
        )

        val refining = coordinator.apply(TranscriptSessionUpdate.MarkRefining)
        val final =
            coordinator.apply(
                TranscriptSessionUpdate.UpsertSegment(
                    segment =
                        segment(
                            id = "s1",
                            text = "refined text",
                            source = TranscriptSource.CLOUD_REFINEMENT,
                            isFinal = true,
                        ),
                ),
            )
        val closed = coordinator.apply(TranscriptSessionUpdate.MarkFinal)

        assertEquals(TranscriptDocumentStatus.REFINING, refining.status)
        assertEquals("refined text", final.plainText)
        assertTrue(closed.isFinal)
    }

    private fun segment(
        id: String,
        text: String = "hello",
        startMs: Long = 0,
        source: TranscriptSource = TranscriptSource.LOCAL_LIVE,
        isFinal: Boolean = true,
    ): TranscriptSegment =
        TranscriptSegment(
            segmentId = id,
            text = text,
            startMs = startMs,
            endMs = startMs + 500,
            source = source,
            isFinal = isFinal,
        )
}
