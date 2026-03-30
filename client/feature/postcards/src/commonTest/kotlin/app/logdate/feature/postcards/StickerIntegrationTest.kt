package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.CanvasEditorState
import app.logdate.feature.postcards.ui.StickerShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Tests for sticker element creation, shelf state, and URI map resolution.
 */
class StickerIntegrationTest {
    @Test
    fun addStickerElementProducesCorrectRef() {
        val stickerRef = Uuid.random()
        val element =
            CanvasElement.Sticker(
                id = Uuid.random(),
                stickerRef = stickerRef,
                transform = ElementTransform(x = 10f, y = 20f),
                zIndex = 0,
            )

        assertEquals(stickerRef, element.stickerRef)
        assertEquals(10f, element.transform.x)
        assertEquals(20f, element.transform.y)
    }

    @Test
    fun stickerUriMapResolvesKnownRef() {
        val ref = Uuid.random()
        val uriMap = mapOf(ref to "file:///stickers/cat.png")

        assertEquals("file:///stickers/cat.png", uriMap[ref])
    }

    @Test
    fun stickerUriMapReturnsNullForUnknownRef() {
        val uriMap = mapOf(Uuid.random() to "file:///stickers/cat.png")
        val unknownRef = Uuid.random()

        assertNull(uriMap[unknownRef])
    }

    @Test
    fun shelfStickersPopulateFromEntities() {
        val stickers =
            listOf(
                StickerShelfItem(Uuid.random(), "file:///sticker1.png", "Cat"),
                StickerShelfItem(Uuid.random(), "file:///sticker2.png", null),
            )

        val state =
            CanvasEditorState(
                document =
                    PostcardDocument(
                        id = Uuid.random(),
                        title = "Test",
                        createdAt = Clock.System.now(),
                        modifiedAt = Clock.System.now(),
                    ),
                shelfStickers = stickers,
            )

        assertEquals(2, state.shelfStickers.size)
        assertEquals("Cat", state.shelfStickers[0].label)
        assertNull(state.shelfStickers[1].label)
    }

    @Test
    fun stickerUriMapBuiltFromShelfStickers() {
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val stickers =
            listOf(
                StickerShelfItem(id1, "file:///sticker1.png", "Cat"),
                StickerShelfItem(id2, "file:///sticker2.png", "Dog"),
            )

        val uriMap = stickers.associate { it.id to it.imageUri }

        assertEquals("file:///sticker1.png", uriMap[id1])
        assertEquals("file:///sticker2.png", uriMap[id2])
    }

    @Test
    fun documentWithStickerElementsExtractsRefs() {
        val ref1 = Uuid.random()
        val ref2 = Uuid.random()
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
                elements =
                    listOf(
                        CanvasElement.Sticker(
                            id = Uuid.random(),
                            stickerRef = ref1,
                            zIndex = 0,
                        ),
                        CanvasElement.Photo(
                            id = Uuid.random(),
                            momentRef = Uuid.random(),
                            mediaUri = "content://photo",
                            zIndex = 1,
                        ),
                        CanvasElement.Sticker(
                            id = Uuid.random(),
                            stickerRef = ref2,
                            zIndex = 2,
                        ),
                    ),
            )

        val stickerRefs =
            doc.elements
                .filterIsInstance<CanvasElement.Sticker>()
                .map { it.stickerRef }

        assertEquals(2, stickerRefs.size)
        assertTrue(ref1 in stickerRefs)
        assertTrue(ref2 in stickerRefs)
    }
}
