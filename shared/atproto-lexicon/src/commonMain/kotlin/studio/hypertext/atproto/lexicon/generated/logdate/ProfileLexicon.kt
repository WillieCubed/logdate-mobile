package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object ProfileLexicon {
    public const val ID: String = "studio.hypertext.logdate.profile"
}

@Serializable
public data class Profile(
    val avatarCid: String? = null,
    val bio: String? = null,
    val createdAt: Long,
    val did: String? = null,
    val displayName: String,
    val handle: String? = null,
    val lastUpdated: Long,
)
