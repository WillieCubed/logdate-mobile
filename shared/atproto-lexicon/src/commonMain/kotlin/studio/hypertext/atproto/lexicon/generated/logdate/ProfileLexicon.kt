package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object ProfileLexicon {
    public const val ID: String = "studio.hypertext.logdate.profile"
}

@Serializable
public data class Profile(
    val avatarCid: String?,
    val bio: String?,
    val createdAt: Long,
    val did: String?,
    val displayName: String,
    val handle: String?,
    val lastUpdated: Long,
)
