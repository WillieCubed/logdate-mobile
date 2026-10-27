package app.logdate.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class HandwritingDocumentTest {
    @Test
    fun originalGeometryAndPressureSurviveSerialization() {
        val document =
            HandwritingDocument(
                height = 1920f,
                strokes =
                    listOf(
                        HandwritingStroke(
                            id = Uuid.random(),
                            tool = "highlighter",
                            color = "#7C42F5",
                            width = 17.5f,
                            points = listOf(HandwritingPoint(4.25f, 1200.5f, 0.23f), HandwritingPoint(8.5f, 1300.75f, 0.87f)),
                            x = 12f,
                            y = 3f,
                            rotation = 2f,
                            scaleX = 0.8f,
                            zIndex = 4,
                        ),
                    ),
            )
        assertEquals(document, HandwritingDocument.decode(document.encode()))
    }

    @Test
    fun futureVersionsAreRejectedWithoutDiscardingInk() {
        assertFailsWith<IllegalArgumentException> {
            HandwritingDocument.decode("""{"version":2,"strokes":[]}""")
        }
    }

    @Test
    fun richDraftIncludesOriginalInkAlongsideText() {
        val time = kotlin.time.Instant.fromEpochMilliseconds(1000)
        val ink =
            HandwritingDocument(
                strokes = listOf(HandwritingStroke(Uuid.random(), "pen", "#263044", 3.5f, listOf(HandwritingPoint(2.5f, 4.75f, 0.3f)))),
            )
        val draft =
            EditorDraft(
                blocks =
                    listOf(
                        SerializableTextBlock(Uuid.random(), time, content = "Beside the ink"),
                        SerializableHandwritingBlock(Uuid.random(), time, document = ink),
                    ),
            )
        val json = kotlinx.serialization.json.Json
        assertEquals(draft, json.decodeFromString(EditorDraft.serializer(), json.encodeToString(EditorDraft.serializer(), draft)))
    }

    @Test
    fun unsupportedToolsAndInvalidColorsAreRejectedAtIngestion() {
        val stroke = HandwritingStroke(Uuid.random(), "pen", "#263044", 3.5f, listOf(HandwritingPoint(2f, 4f, 0.3f)))
        val valid = HandwritingDocument(strokes = listOf(stroke)).encode()
        assertFailsWith<IllegalArgumentException> { HandwritingDocument.decode(valid.replace("pen", "future-tool")) }
        assertFailsWith<IllegalArgumentException> { HandwritingDocument.decode(valid.replace("#263044", "invalid-color")) }
    }
}
