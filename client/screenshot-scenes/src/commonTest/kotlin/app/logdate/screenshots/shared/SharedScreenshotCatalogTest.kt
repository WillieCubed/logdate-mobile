package app.logdate.screenshots.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [SharedScreenshotCatalog], ensuring the integrity and uniqueness of
 * defined screenshot scenes and their variants.
 *
 * These tests prevent collisions in scene identifiers and baseline names, which are
 * critical for automated screenshot testing and comparison.
 */
class SharedScreenshotCatalogTest {
    @Test
    fun `scene ids are unique`() {
        val sceneIds = SharedScreenshotCatalog.allScenes.map { it.id }
        assertEquals(sceneIds.size, sceneIds.distinct().size)
    }

    @Test
    fun `every scene has at least one variant`() {
        assertTrue(SharedScreenshotCatalog.allScenes.all { it.variants.isNotEmpty() })
    }

    @Test
    fun `baseline names are unique`() {
        val baselineNames =
            SharedScreenshotCatalog.allScenes.flatMap { scene ->
                scene.variants.map { variant -> screenshotBaselineName(scene, variant) }
            }

        assertEquals(baselineNames.size, baselineNames.distinct().size)
    }
}
