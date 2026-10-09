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

    @Test
    fun `viewport baseline suffixes are unique`() {
        val suffixes = ScreenshotViewportId.entries.map { it.baselineSuffix }
        assertEquals(suffixes.size, suffixes.distinct().size)
    }

    @Test
    fun `os integration variants cover the accessibility and window configurations`() {
        val viewports = osIntegrationVariants.map { it.viewport }

        assertTrue(viewports.any { it.rightToLeft }, "OS integration scenes must render right-to-left")
        assertTrue(viewports.any { it.fontScale >= 2f }, "OS integration scenes must render at 200% text")
        assertTrue(viewports.any { it.darkTheme }, "OS integration scenes must render in dark theme")
        assertTrue(ScreenshotViewportId.COMPACT_PHONE in viewports)
        assertTrue(ScreenshotViewportId.TABLET in viewports)
    }

    @Test
    fun `bubble variants add the bubble window to the os integration variants`() {
        assertEquals(
            osIntegrationVariants + ScreenshotSceneVariant(ScreenshotViewportId.BUBBLE),
            osIntegrationBubbleVariants,
        )
    }

    @Test
    fun `privacy settings renders in every os integration variant`() {
        val privacy = SharedScreenshotCatalog.scene(SharedScreenshotSceneId.PrivacySettings)
        assertTrue(privacy.variants.containsAll(osIntegrationVariants))
    }
}
