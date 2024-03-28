package app.logdate.feature.editor.ui

import android.location.Location
import android.net.Uri
import app.logdate.core.data.notes.JournalNote
import kotlinx.datetime.Clock

data class NewEntryContent(
    var textContent: String = "",
    var mediaAttachments: List<Uri> = listOf(),
    var location: Location? = null,
)

fun NewEntryContent.toNewTextNote() = JournalNote.Text(
    content = textContent,
    uid = "0", // Ensures that downstream code handles this as a new note
    creationTimestamp = Clock.System.now(),
    lastUpdated = Clock.System.now(),
)