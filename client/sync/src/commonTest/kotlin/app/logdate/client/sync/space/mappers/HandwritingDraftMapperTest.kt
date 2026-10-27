package app.logdate.client.sync.space.mappers

import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import app.logdate.shared.model.SerializableHandwritingBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingDraftMapperTest {
    @Test
    fun originalDraftInkAndBlockContextSurvivePersonalSpaceRecords() {
        val at = Instant.parse("2026-10-12T07:41:09.512Z")
        val ink =
            HandwritingDocument(
                height = 1700f,
                strokes =
                    listOf(
                        HandwritingStroke(
                            Uuid.random(),
                            "pen",
                            "#263044",
                            3.5f,
                            listOf(HandwritingPoint(12.25f, 1400.5f, 0.2f), HandwritingPoint(400.75f, 1500.25f, 0.9f)),
                            x = 4.25f,
                            rotation = 0.2f,
                            scaleX = 1.1f,
                            zIndex = 2,
                        ),
                    ),
            )
        val block = SerializableHandwritingBlock(Uuid.random(), at, locationLat = -33.86882, locationLng = 151.209295, document = ink)
        val original =
            SpaceDraft(EditorDraft(blocks = listOf(block), selectedJournalIds = listOf(Uuid.random()), createdAt = at, lastModifiedAt = at))
        val mapper = DraftRecordMapper()
        val record = mapper.toRecord(original)
        assertValidRecord(mapper.collection, record)
        assertEquals(original, mapper.fromRecord(mapper.rkeyOf(original), record))
        assertEquals(emptyList(), mapper.blobCidsOf(original))
    }
}
