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
    fun sceneIds_are_unique() {
        val sceneIds = SharedScreenshotCatalog.allScenes.map { it.id }
        assertEquals(sceneIds.size, sceneIds.distinct().size)
    }

    @Test
    fun every_scene_has_at_least_one_variant() {
        assertTrue(SharedScreenshotCatalog.allScenes.all { it.variants.isNotEmpty() })
    }

    @Test
    fun baseline_names_are_unique() {
        val baselineNames =
            SharedScreenshotCatalog.allScenes.flatMap { scene ->
                scene.variants.map { variant -> screenshotBaselineName(scene, variant) }
            }

        assertEquals(baselineNames.size, baselineNames.distinct().size)
    }
}
