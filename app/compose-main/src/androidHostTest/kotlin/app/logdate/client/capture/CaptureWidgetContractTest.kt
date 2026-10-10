package app.logdate.client.capture

import app.logdate.client.feature.widgets.CAPTURE_WIDGET_ACTION
import app.logdate.client.feature.widgets.CAPTURE_WIDGET_EXTRA_MODE
import app.logdate.client.feature.widgets.CaptureWidgetButton
import app.logdate.feature.editor.navigation.EditorLaunchMode
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureWidgetContractTest {
    @Test
    fun `the capture widget sends the public capture action`() {
        assertEquals(ACTION_CAPTURE, CAPTURE_WIDGET_ACTION)
        assertEquals(EXTRA_CAPTURE_MODE, CAPTURE_WIDGET_EXTRA_MODE)
    }

    @Test
    fun `each capture widget button opens the editor in its mode`() {
        val modes = CaptureWidgetButton.entries.associateWith { captureRoute(it.mode, journalId = null).launchMode }

        assertEquals(
            mapOf(
                CaptureWidgetButton.Write to EditorLaunchMode.Text,
                CaptureWidgetButton.Record to EditorLaunchMode.Voice,
                CaptureWidgetButton.Photo to EditorLaunchMode.Camera,
            ),
            modes,
        )
    }
}
