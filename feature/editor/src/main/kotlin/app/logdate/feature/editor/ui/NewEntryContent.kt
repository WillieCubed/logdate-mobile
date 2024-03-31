package app.logdate.feature.editor.ui

import android.location.Location
import android.net.Uri
import app.logdate.core.data.notes.JournalNote
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class NewEntryContent(
    var textContent: String = "",
    var mediaAttachments: List<Uri> = listOf(),
    var location: Location? = null,
    var creationTimestamp: Instant = Clock.System.now(),
)

fun NewEntryContent.toNewTextNote() = JournalNote.Text(
    content = textContent,
    uid = "0", // Ensures that downstream code handles this as a new note
    creationTimestamp = creationTimestamp,
    lastUpdated = creationTimestamp,
)