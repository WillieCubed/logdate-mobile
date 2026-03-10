package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object JournalLexicon {
    public const val ID: String = "studio.hypertext.logdate.journal"
}

@Serializable
public data class Journal(
    val createdAt: Long,
    val description: String,
    val deviceId: String?,
    val id: String?,
    val lastUpdated: Long,
    val title: String,
)
