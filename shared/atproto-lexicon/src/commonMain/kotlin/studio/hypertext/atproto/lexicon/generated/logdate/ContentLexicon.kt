package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object ContentLexicon {
    public const val ID: String = "studio.hypertext.logdate.content"
}

@Serializable
public data class Content(
    val content: String?,
    val createdAt: Long,
    val deviceId: String?,
    val durationMs: Long?,
    val id: String?,
    val lastUpdated: Long,
    val mediaUri: String?,
    val type: String,
)
