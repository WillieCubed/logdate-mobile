@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.e2e

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.feature.rewind.ui.detail.RewindMemoryFrame
import app.logdate.feature.rewind.ui.detail.RewindMemoryLoadState
import app.logdate.feature.rewind.ui.detail.RewindRichMemoryText
import app.logdate.feature.rewind.ui.detail.TextNotePanel
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic memory fixtures; use a repository Gradle Managed Device task. */
class RewindMemoryPresentationE2ETest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun memoryFormattingUsesTheSupportedMarkdownRenderer() {
        compose.setContent {
            LogDateTheme {
                TextNotePanel(MARKDOWN, "Tuesday", Color(0xFF172431), null, Modifier.fillMaxSize(), memoryId = "formatted")
            }
        }
        compose.onNodeWithText("A useful day", substring = true).assertIsDisplayed()
        compose.onNodeWithText("## A useful day", substring = true).assertDoesNotExist()
        compose.onNodeWithText("**important**", substring = true).assertDoesNotExist()
        compose.onNodeWithText("important", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Transit notes", substring = true).assertIsDisplayed()
        capture("rewind-formatted-memory")
    }

    @Test fun loadingContentAndFailureKeepTheActualMemoryContainerAndIdentity() {
        val state = mutableStateOf<RewindMemoryLoadState>(RewindMemoryLoadState.Loading)
        var openedLinks = 0
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedLinks++
                }
            }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                LogDateTheme {
                    RewindMemoryFrame("stable-memory", state.value, Modifier.fillMaxSize(), errorMessage = "This memory is unavailable.") {
                        RewindRichMemoryText(
                            "## Your memory\n\nA **formatted** memory remains in the same block.\n\n[Memory link](https://example.invalid/memory)",
                        )
                    }
                }
            }
        }
        val initial = compose.onNodeWithTag("rewind_memory_stable-memory").fetchSemanticsNode()
        val initialBounds = initial.boundsInRoot

        fun tapMemoryLink() {
            val link = compose.onNodeWithText("Memory link", useUnmergedTree = true).fetchSemanticsNode()
            val firstLetter = Offset(link.boundsInRoot.left + 8f, link.boundsInRoot.center.y)
            compose.onNodeWithTag("rewind_memory_stable-memory").performTouchInput { click(firstLetter - initialBounds.topLeft) }
        }
        tapMemoryLink()
        compose.runOnIdle { assertEquals(0, openedLinks) }
        capture("rewind-memory-loading")
        compose.runOnIdle { state.value = RewindMemoryLoadState.Ready }
        val ready = compose.onNodeWithTag("rewind_memory_stable-memory").fetchSemanticsNode()
        assertEquals(initial.id, ready.id)
        assertEquals(initialBounds, ready.boundsInRoot)
        compose.onNodeWithText("Your memory").assertIsDisplayed()
        capture("rewind-memory-ready", minimumContentPixels = 1000)
        tapMemoryLink()
        compose.runOnIdle { assertEquals(1, openedLinks) }
        compose.runOnIdle { state.value = RewindMemoryLoadState.Unavailable }
        val failed = compose.onNodeWithTag("rewind_memory_stable-memory").fetchSemanticsNode()
        assertEquals(initial.id, failed.id)
        assertEquals(initialBounds, failed.boundsInRoot)
        compose.onNodeWithText("This memory is unavailable.").assertIsDisplayed()
        tapMemoryLink()
        compose.runOnIdle { assertEquals(1, openedLinks) }
        capture("rewind-memory-unavailable")
    }

    private fun capture(
        name: String,
        minimumContentPixels: Int = 50,
    ) {
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: error("Memory screenshots require a Gradle Managed Device output directory")
        compose.waitForIdle()
        val file = File(directory, "$name.png")
        file.parentFile?.mkdirs()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        compose.waitUntil(10_000) {
            if (!device.takeScreenshot(file)) return@waitUntil false
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@waitUntil false
            // UiDevice can report success before the Compose frame reaches the
            // display. Reject black captures instead of accepting stale pixels.
            var visible = 0
            for (y in bitmap.height / 5 until bitmap.height * 4 / 5 step 2) {
                for (x in 0 until bitmap.width step 2) {
                    if ((bitmap.getPixel(x, y) and 0x00ffffff) != 0) visible++
                }
            }
            bitmap.recycle()
            visible >= minimumContentPixels
        }
    }

    private companion object {
        val MARKDOWN =
            """
            ## A useful day

            An **important** note with *emphasis*.

            - Walked to the park
            - Saved a memory

            [Transit notes](https://example.invalid/notes)

            ```text
            Keep this code literal.
            ```
            """.trimIndent()
    }
}
