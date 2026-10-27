package app.logdate.feature.editor.ui.editor

import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.shared.model.Location
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Original ink is owned by editor state so ordinary draft and save operations include it. */
data class HandwritingBlockUiState(
    override val id: Uuid = Uuid.random(),
    override val timestamp: Instant = Clock.System.now(),
    override val location: Location? = null,
    override val timeZoneId: String? = SystemCaptureTimeZone.currentTimeZoneId(),
    val page: HandwritingPage = HandwritingPage(),
    val noteLocation: app.logdate.client.repository.journals.NoteLocation? = null,
    val capturedOn: app.logdate.client.repository.journals.CaptureDevice? = null,
    val syncVersion: Long = 0,
) : EntryBlockUiState {
    override fun hasContent(): Boolean = page.strokes.isNotEmpty()

    override val wantsImmersiveLayout: Boolean = true
}
