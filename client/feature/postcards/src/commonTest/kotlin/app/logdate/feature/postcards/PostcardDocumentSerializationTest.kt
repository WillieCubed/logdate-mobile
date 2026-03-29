package app.logdate.feature.postcards.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PostcardDocumentSerializationTest {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    @Test
    fun roundTripFullDocument() {
        val document =
            PostcardDocument(
                id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                title = "Best Weekend",
                createdAt = Instant.fromEpochMilliseconds(1711324800000),
                modifiedAt = Instant.fromEpochMilliseconds(1711324800000),
                sourceMomentRef = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                background = CanvasBackground.SolidColor("#FFF5E6"),
                elements =
                    listOf(
                        CanvasElement.Photo(
                            id = Uuid.parse("110e8400-e29b-41d4-a716-446655440000"),
                            momentRef = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                            mediaUri = "content://media/external/images/123",
                            transform = ElementTransform(x = 100f, y = 200f, rotation = -5f, scaleX = 1.2f, scaleY = 1.2f),
                            zIndex = 0,
                            parallaxDepth = 0.3f,
                        ),
                        CanvasElement.Text(
                            id = Uuid.parse("220e8400-e29b-41d4-a716-446655440000"),
                            content = "best weekend",
                            fontFamily = "caveat",
                            color = "#333333",
                            fontSize = 24f,
                            transform = ElementTransform(x = 50f, y = 500f),
                            zIndex = 2,
                        ),
                        CanvasElement.Ink(
                            id = Uuid.parse("330e8400-e29b-41d4-a716-446655440000"),
                            tool = InkTool.PEN,
                            color = "#FF6B6B",
                            strokeWidth = 3f,
                            points =
                                listOf(
                                    InkPoint(0f, 0f, 0.5f),
                                    InkPoint(10f, 15f, 0.8f),
                                    InkPoint(20f, 10f, 1f),
                                ),
                            zIndex = 3,
                        ),
                        CanvasElement.Shape(
                            id = Uuid.parse("440e8400-e29b-41d4-a716-446655440000"),
                            shapeKind = ShapeKind.ARROW,
                            color = "#333333",
                            strokeWidth = 2f,
                            width = 100f,
                            height = 80f,
                            transform = ElementTransform(x = 200f, y = 300f),
                            zIndex = 4,
                        ),
                        CanvasElement.Sticker(
                            id = Uuid.parse("550e8400-e29b-41d4-a716-446655440001"),
                            stickerRef = Uuid.parse("770e8400-e29b-41d4-a716-446655440000"),
                            transform = ElementTransform(x = 300f, y = 100f, rotation = 12f, scaleX = 0.8f, scaleY = 0.8f),
                            zIndex = 5,
                        ),
                    ),
            )

        val encoded = json.encodeToString(PostcardDocument.serializer(), document)
        val decoded = json.decodeFromString(PostcardDocument.serializer(), encoded)

        assertEquals(document, decoded)
    }

    @Test
    fun roundTripEmptyDocument() {
        val document =
            PostcardDocument(
                id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                title = "Empty Canvas",
                createdAt = Instant.fromEpochMilliseconds(1711324800000),
                modifiedAt = Instant.fromEpochMilliseconds(1711324800000),
            )

        val encoded = json.encodeToString(PostcardDocument.serializer(), document)
        val decoded = json.decodeFromString(PostcardDocument.serializer(), encoded)

        assertEquals(document, decoded)
    }

    @Test
    fun roundTripGradientBackground() {
        val document =
            PostcardDocument(
                id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                title = "Gradient Test",
                createdAt = Instant.fromEpochMilliseconds(1711324800000),
                modifiedAt = Instant.fromEpochMilliseconds(1711324800000),
                background =
                    CanvasBackground.Gradient(
                        stops =
                            listOf(
                                GradientStop("#FF6B6B", 0f),
                                GradientStop("#FFF5E6", 0.5f),
                                GradientStop("#6B9FFF", 1f),
                            ),
                    ),
            )

        val encoded = json.encodeToString(PostcardDocument.serializer(), document)
        val decoded = json.decodeFromString(PostcardDocument.serializer(), encoded)

        assertEquals(document, decoded)
    }

    @Test
    fun roundTripAllInkTools() {
        for (tool in InkTool.entries) {
            val element =
                CanvasElement.Ink(
                    id = Uuid.parse("330e8400-e29b-41d4-a716-446655440000"),
                    tool = tool,
                    color = "#000000",
                    strokeWidth = 2f,
                    points = listOf(InkPoint(0f, 0f)),
                )
            val encoded = json.encodeToString(CanvasElement.serializer(), element)
            val decoded = json.decodeFromString(CanvasElement.serializer(), encoded)
            assertEquals(element, decoded)
        }
    }

    @Test
    fun roundTripAllShapeKinds() {
        for (kind in ShapeKind.entries) {
            val element =
                CanvasElement.Shape(
                    id = Uuid.parse("440e8400-e29b-41d4-a716-446655440000"),
                    shapeKind = kind,
                    color = "#000000",
                    strokeWidth = 1f,
                    width = 50f,
                    height = 50f,
                )
            val encoded = json.encodeToString(CanvasElement.serializer(), element)
            val decoded = json.decodeFromString(CanvasElement.serializer(), encoded)
            assertEquals(element, decoded)
        }
    }

    @Test
    fun polymorphicElementDeserialization() {
        val elements: List<CanvasElement> =
            listOf(
                CanvasElement.Photo(
                    id = Uuid.parse("110e8400-e29b-41d4-a716-446655440000"),
                    momentRef = Uuid.parse("660e8400-e29b-41d4-a716-446655440000"),
                    mediaUri = "content://test",
                ),
                CanvasElement.Text(
                    id = Uuid.parse("220e8400-e29b-41d4-a716-446655440000"),
                    content = "hello",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                ),
                CanvasElement.Sticker(
                    id = Uuid.parse("330e8400-e29b-41d4-a716-446655440000"),
                    stickerRef = Uuid.parse("770e8400-e29b-41d4-a716-446655440000"),
                ),
            )

        val document =
            PostcardDocument(
                id = Uuid.parse("550e8400-e29b-41d4-a716-446655440000"),
                title = "Mixed Elements",
                createdAt = Instant.fromEpochMilliseconds(1711324800000),
                modifiedAt = Instant.fromEpochMilliseconds(1711324800000),
                elements = elements,
            )

        val encoded = json.encodeToString(PostcardDocument.serializer(), document)
        val decoded = json.decodeFromString(PostcardDocument.serializer(), encoded)

        assertEquals(3, decoded.elements.size)
        assert(decoded.elements[0] is CanvasElement.Photo)
        assert(decoded.elements[1] is CanvasElement.Text)
        assert(decoded.elements[2] is CanvasElement.Sticker)
        assertEquals(document, decoded)
    }
}
