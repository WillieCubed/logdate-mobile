package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object DescribeRepoLexicon {
    public const val ID: String = "com.atproto.repo.describeRepo"
}

@Serializable
public data class DescribeRepoParams(
    /** The handle or DID of the repo. */
    val repo: String,
)

@Serializable
public data class DescribeRepoOutput(
    /** List of all the collections (NSIDs) for which this repo contains at least one record. */
    val collections: List<String>,
    val did: String,
    /** The complete DID document for this account. */
    val didDoc: JsonElement,
    val handle: String,
    /** Indicates if handle is currently valid (resolves bi-directionally) */
    val handleIsCorrect: Boolean,
)
