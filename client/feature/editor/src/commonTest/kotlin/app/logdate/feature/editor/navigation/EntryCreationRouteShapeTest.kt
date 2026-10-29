package app.logdate.feature.editor.navigation

import app.logdate.ui.creation.EntryCreationShape
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryCreationRouteShapeTest {
    @Test
    fun sourcedRouteKeepsTheSameShapeThroughCopyAndSavedStateRestoration() {
        EntryCreationShape.entries.forEach { sourceShape ->
            val route =
                EntryEditorRoute(
                    creationTransitionId = "source-$sourceShape",
                    creationTransitionShape = sourceShape,
                )
            val restored = Json.decodeFromString<EntryEditorRoute>(Json.encodeToString(route))
            assertEquals(route.creationTransitionId, restored.creationTransitionId)
            assertEquals(sourceShape, restored.creationTransitionShape)
            assertEquals(sourceShape, restored.copy(journalIds = listOf("journal")).creationTransitionShape)
        }
    }

    @Test
    fun legacyRoutesWithoutShapeMetadataKeepTheLargeDefault() {
        assertEquals(EntryCreationShape.Large, EntryEditorRoute().creationTransitionShape)
        val restored = Json.decodeFromString<EntryEditorRoute>("""{"creationTransitionId":"legacy"}""")
        assertEquals("legacy", restored.creationTransitionId)
        assertEquals(EntryCreationShape.Large, restored.creationTransitionShape)
    }
}
