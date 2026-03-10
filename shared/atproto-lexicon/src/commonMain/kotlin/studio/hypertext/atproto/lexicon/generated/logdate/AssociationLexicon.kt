package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object AssociationLexicon {
    public const val ID: String = "studio.hypertext.logdate.association"
}

@Serializable
public data class Association(
    val contentId: String,
    val createdAt: Long,
    val deviceId: String?,
    val journalId: String,
)
