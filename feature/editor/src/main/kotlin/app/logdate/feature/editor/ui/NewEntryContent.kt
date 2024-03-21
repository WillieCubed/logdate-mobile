package app.logdate.feature.editor.ui

import android.location.Location
import android.net.Uri

data class NewEntryContent(
    var textContent: String = "",
    var mediaAttachments: List<Uri> = listOf(),
    var location: Location? = null,
)