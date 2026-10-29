package app.logdate.ui.creation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryCreationShapeMetadataTest {
    @Test
    fun nestedAndThrowingScopesRestoreIdAndShapeTogether() {
        val launcher = EntryCreationLauncher()
        assertNull(launcher.currentSourceId)
        assertEquals(EntryCreationShape.Large, launcher.currentSourceShape)
        assertFailsWith<IllegalStateException> {
            launcher.withSource("compact", EntryCreationShape.Round) {
                assertEquals("compact", launcher.currentSourceId)
                assertEquals(EntryCreationShape.Round, launcher.currentSourceShape)
                launcher.withSource("pull", EntryCreationShape.Large) {
                    assertEquals("pull", launcher.currentSourceId)
                    assertEquals(EntryCreationShape.Large, launcher.currentSourceShape)
                }
                assertEquals("compact", launcher.currentSourceId)
                assertEquals(EntryCreationShape.Round, launcher.currentSourceShape)
                assertFailsWith<IllegalArgumentException> {
                    launcher.withSource("rail", EntryCreationShape.Large) {
                        assertEquals("rail", launcher.currentSourceId)
                        assertEquals(EntryCreationShape.Large, launcher.currentSourceShape)
                        throw IllegalArgumentException("Rejected nested navigation")
                    }
                }
                assertEquals("compact", launcher.currentSourceId)
                assertEquals(EntryCreationShape.Round, launcher.currentSourceShape)
                error("Rejected outer navigation")
            }
        }
        assertNull(launcher.currentSourceId)
        assertEquals(EntryCreationShape.Large, launcher.currentSourceShape)
    }

    @Test
    fun sourceLaunchForwardsItsShapeOnlyForTheSynchronousCallback() {
        val source = EntryCreationSource(initialId = "compact")
        val launcher = EntryCreationLauncher()
        var observed: Pair<String?, EntryCreationShape>? = null
        assertTrue(
            source.launch(source.id, launcher, EntryCreationShape.Round) {
                observed = launcher.currentSourceId to launcher.currentSourceShape
            },
        )
        assertEquals(Pair<String?, EntryCreationShape>("compact", EntryCreationShape.Round), observed)
        assertNull(launcher.currentSourceId)
        assertEquals(EntryCreationShape.Large, launcher.currentSourceShape)
    }
}
