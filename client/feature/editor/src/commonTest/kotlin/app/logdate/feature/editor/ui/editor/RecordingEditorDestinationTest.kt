package app.logdate.feature.editor.ui.editor

import androidx.navigation3.runtime.NavKey
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.editor.navigation.RecordingEditorDestination
import app.logdate.feature.editor.navigation.completeEditorVisit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RecordingEditorDestinationTest {
    private data object Home : NavKey

    @Test
    fun `saving recording retains another editors input and stale completion cannot close it`() {
        val recording = EntryEditorRoute()
        val other = EntryEditorRoute(initialText = "Accepted shared text")
        val stack = mutableListOf<NavKey>(Home, other, recording)
        stack.completeEditorVisit(recording, Home)
        assertEquals(listOf<NavKey>(Home, other), stack)
        stack.completeEditorVisit(recording, Home)
        assertEquals(listOf<NavKey>(Home, other), stack)
        stack.completeEditorVisit(other, Home)
        assertEquals(listOf<NavKey>(Home), stack)
    }

    @Test
    fun `notification returns to the recording visit while retaining another editors unsaved content`() {
        val draft = Uuid.random().toString()
        val recording = EntryEditorRoute()
        val other = EntryEditorRoute(initialText = "Shared words must survive")
        val stack = mutableListOf<NavKey>(recording, other)
        RecordingEditorDestination.bind(draft, recording)
        RecordingEditorDestination.open(stack, EntryEditorRoute(draftId = draft, resumeRecording = true))
        assertEquals(listOf<NavKey>(other, recording), stack)
        RecordingEditorDestination.open(stack, EntryEditorRoute(draftId = draft, resumeRecording = true))
        assertEquals(listOf<NavKey>(other, recording), stack)
    }

    @Test
    fun `missing presentation restores durable draft without claiming a live recording`() {
        val destination = EntryEditorRoute(draftId = Uuid.random().toString(), resumeRecording = true)
        val stack = mutableListOf<NavKey>()
        RecordingEditorDestination.open(stack, destination)
        assertEquals(listOf<NavKey>(destination), stack)
    }
}
