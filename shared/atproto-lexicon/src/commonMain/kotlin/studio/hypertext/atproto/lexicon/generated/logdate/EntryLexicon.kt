package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object EntryLexicon {
    public const val ID: String = "studio.hypertext.logdate.entry"
}

@Serializable
public data class Entry(
    val content: String?,
    val createdAt: Long,
    val deviceId: String?,
    val durationMs: Long?,
    val id: String?,
    val lastUpdated: Long,
    val type: String,
)
