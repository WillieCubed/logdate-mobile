package app.logdate.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.AppAuthState
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** Populated privacy scenes; screenshots include the initial view and the reachable action. */
@OptIn(ExperimentalTestApi::class)
class LockScreenVisualAcceptanceTest {
    @Test
    fun populatedJournalRemainsPrivateAcrossLockLayouts() {
        listOf(
            Scene("phone", 411, 891),
            Scene("landscape-200", 640, 320, fontScale = 2f),
            Scene("tablet", 1280, 800),
            Scene("book-200", 840, 900, fontScale = 2f, posture = FoldablePosture.Book),
            Scene("tabletop-200", 900, 600, fontScale = 2f, posture = FoldablePosture.Tabletop),
        ).forEach { scene ->
            runDesktopComposeUiTest(width = scene.width, height = scene.height) {
                var unlocks = 0
                setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scene.fontScale)) {
                        provideFoldableLayoutInfo(scene.foldableLayout()) {
                            LogDateTheme(dynamicColor = false) {
                                LockableContent(
                                    isLocked = true,
                                    displayName = "Alexandria Elizabeth",
                                    onUsePasscode = { unlocks++ },
                                    authState = AppAuthState.REQUEST_ENROLLMENT,
                                ) {
                                    Column {
                                        Text("Private restored entry")
                                        OutlinedTextField("Unsubmitted private draft", {})
                                        Button(onClick = {}) { Text("Delete private entry") }
                                    }
                                }
                            }
                        }
                    }
                }
                onNodeWithText("Private restored entry").assertDoesNotExist()
                onNodeWithText("Unsubmitted private draft").assertDoesNotExist()
                save("${scene.name}-initial", onRoot().captureToImage())
                val action = onNodeWithText("Set up device security")
                action.performScrollTo().assertIsDisplayed()
                val bounds = action.fetchSemanticsNode().boundsInRoot
                if (scene.posture == FoldablePosture.Book) {
                    check(bounds.right <= scene.width / 2f - 8f) { "Unlock action overlaps book hinge" }
                }
                if (scene.posture == FoldablePosture.Tabletop) {
                    check(bounds.bottom <= scene.height / 2f - 8f) { "Unlock action overlaps tabletop hinge" }
                }
                save("${scene.name}-action", onRoot().captureToImage())
                action.performClick()
                runOnIdle { assertEquals(1, unlocks) }
            }
        }
    }

    private data class Scene(
        val name: String,
        val width: Int,
        val height: Int,
        val fontScale: Float = 1f,
        val posture: FoldablePosture = FoldablePosture.Standard,
    ) {
        fun foldableLayout(): FoldableLayoutInfo {
            if (posture == FoldablePosture.Standard) return FoldableLayoutInfo()
            val vertical = posture == FoldablePosture.Book
            val left = if (vertical) width / 2 - 8 else 0
            val top = if (vertical) 0 else height / 2 - 8
            val right = if (vertical) width / 2 + 8 else width
            val bottom = if (vertical) height else height / 2 + 8
            return FoldableLayoutInfo(
                isFoldable = true,
                posture = posture,
                hinge =
                    FoldableHingeInfo(
                        orientation = if (vertical) FoldableHingeOrientation.Vertical else FoldableHingeOrientation.Horizontal,
                        state = FoldableHingeState.HalfOpened,
                        occlusionType = FoldableOcclusionType.Full,
                        bounds =
                            FoldableHingeBounds(
                                left.dp,
                                top.dp,
                                right.dp,
                                bottom.dp,
                                (right - left).dp,
                                (bottom - top).dp,
                            ),
                        isSeparating = true,
                    ),
            )
        }
    }

    private fun save(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = File(System.getenv("LOGDATE_LOCK_SCREENSHOT_DIR") ?: "/tmp/logdate-lock-renders")
        directory.mkdirs()
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        ImageIO.write(output, "png", File(directory, "$name.png"))
    }
}
