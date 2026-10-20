package app.logdate.feature.core.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.device.restore.NativeBackupQuotaFailure
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ArchiveSettingsVisualAcceptanceTest {
    @Test
    fun archiveActionsRemainReachableInPopulatedDataAndBackupPages() {
        listOf(Variant("phone", 411, 891), Variant("tablet", 1280, 800), Variant("phone-200", 411, 891, 2f)).forEach { variant ->
            listOf("data", "backup").forEach { page ->
                runDesktopComposeUiTest(width = variant.width, height = variant.height) {
                    var exports = 0
                    setContent {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                            LogDateTheme(dynamicColor = false) {
                                if (page == "data") {
                                    ArchiveSettingsDataFixture { exports++ }
                                } else {
                                    SyncSettingsContent(
                                        onBack = {},
                                        syncStatus = null,
                                        isAuthenticated = false,
                                        onSyncNow = {},
                                        onNavigateToSignIn = {},
                                        quotaUsage = StorageQuotaUi(5_000_000_000, 0, 0f, formattedTotal = "5 GB", formattedUsed = "0 GB"),
                                        isQuotaAvailable = false,
                                        snackbarHostState = SnackbarHostState(),
                                        nativeBackupStatus = NativeBackupStatus.Available(NativeBackupQuotaFailure(30_000_000, 25_000_000)),
                                        onExportArchive = { exports++ },
                                    )
                                }
                            }
                        }
                    }
                    save("$page-${variant.name}-initial", onRoot().captureToImage())
                    val label = if (page == "data") "Export data" else "Export archive"
                    onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
                    onNodeWithText(label).assertIsDisplayed().performClick()
                    runOnIdle { assertEquals(1, exports) }
                    save("$page-${variant.name}-export", onRoot().captureToImage())
                    onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Cloud backup"))
                    save("$page-${variant.name}-coverage", onRoot().captureToImage())
                }
            }
        }
    }

    private data class Variant(
        val name: String,
        val width: Int,
        val height: Int,
        val fontScale: Float = 1f,
    )

    private fun save(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = File(System.getenv("LOGDATE_SETTINGS_FOLLOWUP_RENDER_DIR") ?: "/tmp/logdate-settings-followup-renders")
        directory.mkdirs()
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        ImageIO.write(output, "png", File(directory, "$name.png"))
    }
}
