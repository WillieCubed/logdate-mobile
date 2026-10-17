package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object ContentLexicon {
    public const val ID: String = "studio.hypertext.logdate.content"
}

@Serializable
public data class Content(
    val content: String? = null,
    val createdAt: Long,
    val deviceId: String? = null,
    val durationMs: Long? = null,
    val id: String? = null,
    val lastUpdated: Long,
    val mediaUri: String? = null,
    val type: String,
)
