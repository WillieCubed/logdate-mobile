package app.logdate.feature.editor.ui.handwriting

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Debug-only practice document; independent of the published journal-note schema. */
@Serializable
data class HandwritingPracticeEntry(
    val page: HandwritingPage = HandwritingPage(),
    val text: String? = null,
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun decode(content: String): HandwritingPracticeEntry = Json.decodeFromString(serializer(), content)
    }
}
