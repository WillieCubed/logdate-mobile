package studio.hypertext.atproto.lexicon.generated.com.atproto.identity

import kotlinx.serialization.Serializable

public object ResolveHandleLexicon {
    public const val ID: String = "com.atproto.identity.resolveHandle"
}

@Serializable
public data class ResolveHandleParams(
    /** The handle to resolve. */
    val handle: String,
)

@Serializable
public data class ResolveHandleOutput(
    val did: String,
)
