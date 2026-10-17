package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object EntryLexicon {
    public const val ID: String = "studio.hypertext.logdate.entry"
}

@Serializable
public data class Entry(
    val content: String? = null,
    val createdAt: Long,
    val deviceId: String? = null,
    val durationMs: Long? = null,
    val id: String? = null,
    val lastUpdated: Long,
    val type: String,
)
