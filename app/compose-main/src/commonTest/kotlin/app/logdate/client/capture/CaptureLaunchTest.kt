package app.logdate.client.capture

import app.logdate.feature.editor.navigation.EditorLaunchMode
import app.logdate.feature.editor.navigation.EntryEditorRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureLaunchTest {
    @Test
    fun `every capture mode opens the editor in that mode`() {
        assertEquals(EditorLaunchMode.Text, captureRoute(mode = "text", journalId = null).launchMode)
        assertEquals(EditorLaunchMode.Voice, captureRoute(mode = "voice", journalId = null).launchMode)
        assertEquals(EditorLaunchMode.Camera, captureRoute(mode = "camera", journalId = null).launchMode)
        assertEquals(EditorLaunchMode.Video, captureRoute(mode = "video", journalId = null).launchMode)
    }

    @Test
    fun `modes are read regardless of case and surrounding space`() {
        assertEquals(EditorLaunchMode.Voice, captureRoute(mode = " Voice ", journalId = null).launchMode)
    }

    @Test
    fun `a missing or unknown mode falls back to text instead of failing`() {
        assertEquals(EntryEditorRoute(), captureRoute(mode = null, journalId = null))
        assertEquals(EntryEditorRoute(), captureRoute(mode = "hologram", journalId = null))
    }

    @Test
    fun `a valid journal id pre-selects that journal`() {
        val journalId = "6f1c1b4e-2f5a-4c2e-9b7a-1d2e3f4a5b6c"
        assertEquals(listOf(journalId), captureRoute(mode = "voice", journalId = journalId).journalIds)
    }

    @Test
    fun `an invalid journal id is ignored`() {
        assertEquals(emptyList(), captureRoute(mode = "voice", journalId = "not-a-uuid").journalIds)
    }

    @Test
    fun `new entry widget extras map onto launch modes`() {
        assertEquals(
            EntryEditorRoute(launchMode = EditorLaunchMode.Camera),
            widgetCaptureRoute(photoUri = null, record = false, camera = true),
        )
        assertEquals(
            EntryEditorRoute(launchMode = EditorLaunchMode.Voice),
            widgetCaptureRoute(photoUri = null, record = true, camera = false),
        )
        assertEquals(EntryEditorRoute(), widgetCaptureRoute(photoUri = null, record = false, camera = false))
    }

    @Test
    fun `widget photo prompts keep the photo and the chosen input`() {
        val photo = "content://media/external/images/media/42"
        assertEquals(EntryEditorRoute(promptPhotoUri = photo), widgetCaptureRoute(photoUri = photo, record = false, camera = false))
        assertEquals(
            EntryEditorRoute(launchMode = EditorLaunchMode.Voice, promptPhotoUri = photo),
            widgetCaptureRoute(photoUri = photo, record = true, camera = false),
        )
    }

    @Test
    fun `each capture mode reports the static shortcut that starts it`() {
        assertEquals("new_note", captureShortcutId(EditorLaunchMode.Text))
        assertEquals("record_voice", captureShortcutId(EditorLaunchMode.Voice))
        assertEquals("take_photo", captureShortcutId(EditorLaunchMode.Camera))
        assertEquals(null, captureShortcutId(EditorLaunchMode.Video))
    }

    @Test
    fun `a capture can start the entry with text, such as an unsaved notification reply`() {
        assertEquals(
            EntryEditorRoute(initialText = "Dinner on the steps"),
            captureRoute(mode = "text", journalId = null, text = "Dinner on the steps"),
        )
        assertEquals(EntryEditorRoute(), captureRoute(mode = "text", journalId = null, text = "  "))
    }
}
