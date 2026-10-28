package app.logdate.client.e2e

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.testing.onboarding.DEBUG_SKIP_ONBOARDING_EXTRA
import app.logdate.ui.media.MediaDeviceSelectorTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class EditorCameraCreationControlsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @Test(timeout = 180_000)
    fun physicalCameraCaptureAndReviewStayClearOfCreationChoicesAndTransparentBars() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val device = UiDevice.getInstance(instrumentation)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousMode = runBlocking { preferences.observeEntryStartMode().first() }
        val overlays = device.executeShellCommand("cmd overlay list")
        val previousNavigation =
            checkNotNull(
                overlays.lineSequence().firstOrNull {
                    it.startsWith("[x]") && it.contains("com.android.internal.systemui.navbar.")
                },
            ).substringAfter("[x] ").trim()
        val output =
            File(
                checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),
                "editor-camera-creation",
            )
        output.mkdirs()
        try {
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            device.executeShellCommand("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural")
            val intent = Intent(context, MainActivity::class.java).putExtra(DEBUG_SKIP_ONBOARDING_EXTRA, true)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                try {
                    val create =
                        hasText("Create Entry") or hasContentDescription("Create new entry") or hasContentDescription("Add a memory")
                    compose.waitUntil(20_000) { compose.onAllNodes(create).fetchSemanticsNodes().isNotEmpty() }
                    tap(device, compose.onAllNodes(create).onFirst())
                    compose.waitUntil(20_000) {
                        compose.onAllNodes(hasContentDescription("Camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    tap(device, compose.onNodeWithContentDescription("Camera").assertIsEnabled())
                    compose.waitUntil(20_000) {
                        compose.onAllNodes(hasContentDescription("Take Photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    check(device.takeScreenshot(File(output, "camera-before-stream.png")))
                    compose.waitUntil(30_000) {
                        var streaming = false
                        scenario.onActivity { streaming = previewStreaming(it.window.decorView) }
                        streaming
                    }
                    val focused =
                        compose
                            .onAllNodes(
                                SemanticsMatcher("focused camera block") {
                                    it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("focused_block_")
                                },
                            ).fetchSemanticsNodes()
                            .single()
                            .config[SemanticsProperties.TestTag]
                    val route = compose.onNodeWithTag(MediaDeviceSelectorTags.chip("Camera")).assertIsDisplayed().assertIsEnabled()
                    val backBounds = compose.onNodeWithContentDescription("Return to entry").fetchSemanticsNode().boundsInRoot
                    assertTrue(route.fetchSemanticsNode().boundsInRoot.top >= backBounds.bottom, "Camera route must clear Return to entry")
                    tap(device, route)
                    compose.onNodeWithTag(MediaDeviceSelectorTags.sheet("Camera")).assertIsDisplayed()
                    device.pressBack()
                    compose.waitUntil(10_000) {
                        compose.onAllNodesWithTag(MediaDeviceSelectorTags.sheet("Camera")).fetchSemanticsNodes().isEmpty()
                    }
                    val shutter = compose.onNodeWithContentDescription("Take Photo").assertIsDisplayed().assertIsEnabled()
                    val shutterBounds = shutter.fetchSemanticsNode().boundsInRoot
                    val chooser = compose.onNodeWithTag("entry_creation_actions").fetchSemanticsNode().boundsInRoot
                    assertTrue(shutterBounds.bottom <= chooser.top, "Shutter must remain above the separate creation surface")
                    check(device.takeScreenshot(File(output, "mobile-camera-shutter.png")))
                    val windowSizes =
                        ManagedEditorWindowCaptures(device, compose, scenario, output) {
                            compose.waitUntil(30_000) {
                                var streaming = false
                                scenario.onActivity { streaming = previewStreaming(it.window.decorView) }
                                streaming
                            }
                        }
                    windowSizes.captureCameraSizes()
                    tap(device, compose.onNodeWithContentDescription("Take Photo"))
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("Use Photo").fetchSemanticsNodes().isNotEmpty() }
                    windowSizes.awaitReviewImage()
                    var safeBottom = 0
                    var windowHeight = 0
                    scenario.onActivity {
                        val window = it.window
                        assertEquals(0, Color.alpha(window.statusBarColor))
                        assertEquals(0, Color.alpha(window.navigationBarColor))
                        assertFalse(window.isNavigationBarContrastEnforced)
                        assertFalse(window.isStatusBarContrastEnforced)
                        val icons = WindowInsetsControllerCompat(window, window.decorView)
                        assertFalse(icons.isAppearanceLightStatusBars, "Camera status icons must be light over its dark preview")
                        assertFalse(icons.isAppearanceLightNavigationBars)
                        safeBottom =
                            WindowInsetsCompat
                                .toWindowInsetsCompat(window.decorView.rootWindowInsets)
                                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                                .bottom
                        windowHeight = window.decorView.height
                    }
                    for (label in listOf("Retake", "Use Photo")) {
                        val action = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled()
                        val bounds = action.fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.bottom <= windowHeight - safeBottom, "$label must clear navigation UI")
                        val dock = compose.onNodeWithTag("entry_creation_actions").fetchSemanticsNode().boundsInRoot
                        if (dock.height > 0f) assertTrue(bounds.bottom <= dock.top, "$label must clear an exiting creation surface")
                    }
                    check(device.takeScreenshot(File(output, "mobile-camera-review.png")))
                    windowSizes.captureReviewSizes()
                    tap(device, compose.onNodeWithText("Use Photo"))
                    compose.waitUntil(
                        15_000,
                    ) { compose.onAllNodesWithContentDescription("Captured photo").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag(focused).assertIsDisplayed()
                    compose.onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
                    tap(device, compose.onNodeWithContentDescription("Return to entry"))
                    compose.onNodeWithTag("entry_add_memory").assertIsDisplayed()
                    var gestureBottom = 0
                    for (navigation in listOf("gestural", "threebutton")) {
                        device.executeShellCommand(
                            "cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$navigation",
                        )
                        compose.waitForIdle()
                        compose.waitUntil(10_000) {
                            var actualBottom = 0
                            scenario.onActivity {
                                actualBottom =
                                    WindowInsetsCompat
                                        .toWindowInsetsCompat(it.window.decorView.rootWindowInsets)
                                        .getInsets(WindowInsetsCompat.Type.navigationBars())
                                        .bottom
                            }
                            if (navigation == "threebutton") actualBottom > gestureBottom else actualBottom > 0
                        }
                        var bottomInset = 0
                        var topInset = 0
                        scenario.onActivity {
                            assertFalse(it.window.isNavigationBarContrastEnforced)
                            bottomInset =
                                WindowInsetsCompat
                                    .toWindowInsetsCompat(it.window.decorView.rootWindowInsets)
                                    .getInsets(WindowInsetsCompat.Type.navigationBars())
                                    .bottom
                        }
                        if (navigation == "gestural") gestureBottom = bottomInset
                        val screenshot = File(output, "mobile-$navigation-transparent-bars.png")
                        check(device.takeScreenshot(screenshot))
                        val bitmap = checkNotNull(BitmapFactory.decodeFile(screenshot.absolutePath))
                        try {
                            assertEquals(
                                bitmap.getPixel(bitmap.width / 8, bitmap.height - bottomInset - 4),
                                bitmap.getPixel(bitmap.width / 8, bitmap.height - bottomInset / 2),
                                "App background must continue behind the $navigation navigation area without a scrim",
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } finally {
                    device.takeScreenshot(File(output, "camera-terminal.png"))
                    scenario.onActivity { activity ->
                        val rows = mutableListOf<String>()

                        fun visit(
                            view: View,
                            depth: Int,
                        ) {
                            if (rows.size >= 200) return
                            val state = if (view is PreviewView) " preview=${view.previewStreamState.value}" else ""
                            rows += "${" ".repeat(depth)}${view.javaClass.simpleName} ${view.width}x${view.height}$state"
                            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index), depth + 1)
                        }
                        visit(activity.window.decorView, 0)
                        File(output, "camera-terminal-views.txt").writeText(rows.joinToString("\n"))
                    }
                }
            }
        } finally {
            device.executeShellCommand("cmd overlay enable-exclusive --category $previousNavigation")
            runBlocking { preferences.setEntryStartMode(previousMode) }
        }
    }

    private fun previewStreaming(view: View): Boolean =
        when (view) {
            is PreviewView -> view.previewStreamState.value == PreviewView.StreamState.STREAMING
            is ViewGroup -> (0 until view.childCount).any { previewStreaming(view.getChildAt(it)) }
            else -> false
        }

    private fun tap(
        device: UiDevice,
        node: SemanticsNodeInteraction,
    ) {
        compose.waitForIdle()
        device.waitForIdle(2_000)
        node.assertIsDisplayed().assertIsEnabled()
        val center = node.fetchSemanticsNode().boundsInRoot.center
        check(device.click(center.x.roundToInt(), center.y.roundToInt()))
    }
}
