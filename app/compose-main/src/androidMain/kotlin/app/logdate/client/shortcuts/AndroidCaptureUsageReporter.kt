package app.logdate.client.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import app.logdate.client.capture.captureShortcutId
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutDescriptor
import app.logdate.feature.editor.navigation.EditorLaunchMode
import app.logdate.feature.editor.ui.editor.CaptureUsageReporter
import kotlin.uuid.Uuid

/**
 * Reports each saved entry to Android's shortcut ranking: the static capture shortcut matching each
 * kind of capture (writing, voice, photo), and the share target of each journal it was saved to.
 *
 * Android asks apps to report a shortcut whenever the person does what it does, whether or not they
 * used the shortcut, so launchers can predict which one to offer.
 */
class AndroidCaptureUsageReporter(
    private val context: Context,
) : CaptureUsageReporter {
    override fun entrySaved(
        modes: Set<EditorLaunchMode>,
        journalIds: List<Uuid>,
    ) {
        val captureIds = modes.mapNotNull(::captureShortcutId)
        val journalShortcutIds = journalIds.map { "${DynamicShortcutDescriptor.ShareToJournal.ID_PREFIX}:$it" }
        (captureIds + journalShortcutIds).forEach { id -> ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }
}
