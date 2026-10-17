package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object ListRecordsLexicon {
    public const val ID: String = "com.atproto.repo.listRecords"
}

@Serializable
public data class ListRecordsParams(
    /** The NSID of the record type. */
    val collection: String,
    val cursor: String? = null,
    /** The number of records to return. */
    val limit: Long? = null,
    /** The handle or DID of the repo. */
    val repo: String,
    /** Flag to reverse the order of the returned records. */
    val reverse: Boolean? = null,
)

@Serializable
public data class ListRecordsOutput(
    val cursor: String? = null,
    val records: List<ListRecordsRecord>,
)

@Serializable
public data class ListRecordsRecord(
    val cid: String,
    val uri: String,
    val value: JsonElement,
)
