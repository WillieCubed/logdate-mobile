package app.logdate.server.atproto.firehose

import app.logdate.server.auth.AccountRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoCommitChanges
import studio.hypertext.atproto.repo.RepoExport
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import java.util.concurrent.ConcurrentHashMap

/** One repository in a `com.atproto.sync.listRepos` page. */
data class PublicRepoSummary(
    val did: AtprotoDid,
    val head: RepoHead,
)

/** A page of [PublicRepoSummary]; [cursor] is the DID to continue after, or `null` at the end. */
data class PublicRepoPage(
    val repos: List<PublicRepoSummary>,
    val cursor: String?,
)

/**
 * Everything the server shows to people who are not an account's owner, and the only place it
 * decides what that is.
 *
 * Reading a repository's records and blobs and asking for its latest commit need only that the
 * account's repository is one the server shows ([PublicRepoGate]). The record methods further limit
 * the answer to public collections: a record in any other collection is reported as missing and a
 * listing of one is empty, so a caller cannot tell a private collection from one that does not
 * exist. Exporting the whole repository needs more: the record tree must hold nothing private,
 * because a tree names every record's collection and key.
 *
 * Reads go straight to the block store through a read-only engine. They never pass through the
 * adapters that present a person's journal as records, so no private record can be reached from here.
 */
class PublicRepoAccess(
    private val gate: PublicRepoGate,
    private val policy: PublicRepoPolicy,
    private val blocks: RepoBlockStore,
    private val accounts: AccountRepository,
) {
    private val reader = DefaultRepoEngine(blocks)
    private val treeChecks = ConcurrentHashMap<AtprotoDid, Pair<Cid, Boolean>>()
    private val blobIndexes = ConcurrentHashMap<AtprotoDid, Pair<Cid, Set<Cid>>>()

    /** The standing of [did]'s account, or `null` when the server shows nothing about it. */
    suspend fun standing(did: AtprotoDid): RepoStanding? = gate.standing(did)

    /** The head of [did]'s repository whatever the account's standing, for the status of an account that is not shown. */
    suspend fun headOf(did: AtprotoDid): RepoHead? = reader.loadHead(did).getOrThrow()

    /** The head of [did]'s repository when the server may show it, otherwise `null`. */
    suspend fun headIfShown(did: AtprotoDid): RepoHead? = if (gate.mayShow(did)) reader.loadHead(did).getOrThrow() else null

    /**
     * The record at [collection] and [recordKey], or `null` when it does not exist, is in a
     * collection that is not public, or the repository is not shown. When [cid] is given, only that
     * exact version matches.
     */
    suspend fun record(
        did: AtprotoDid,
        collection: Nsid,
        recordKey: RecordKey,
        cid: String?,
    ): RepoRecord? {
        if (!policy.isPublic(collection) || headIfShown(did) == null) return null
        val record = reader.getRecord(RepoRecordId(did, collection, recordKey)).getOrThrow()
        return record?.takeIf { cid == null || it.cid == cid }
    }

    /** One page of [collection], empty when it is not public or the repository is not shown. */
    suspend fun records(
        did: AtprotoDid,
        collection: Nsid,
        limit: Int,
        cursor: String?,
        reverse: Boolean,
    ): RepoListPage {
        if (!policy.isPublic(collection) || headIfShown(did) == null) return RepoListPage(emptyList())
        return reader.listRecords(did, collection, limit, cursor, reverse).getOrThrow()
    }

    /**
     * The repository as a CAR export, or `null` when it is not shown or holds a record that is not
     * public. The result of the tree check is remembered for as long as the head stays the same.
     */
    suspend fun export(
        did: AtprotoDid,
        since: Tid?,
    ): RepoExport? {
        val head = headIfShown(did) ?: return null
        if (!treeIsPublic(did, head)) return null
        return reader.export(did, since).getOrThrow()
    }

    /**
     * True when a public record of [did]'s repository refers to the blob [cid]. A blob that no
     * public record refers to stays private to its owner, even when it was uploaded to the same
     * account.
     */
    suspend fun referencesBlob(
        did: AtprotoDid,
        cid: Cid,
    ): Boolean {
        val head = headIfShown(did) ?: return false
        val cached = blobIndexes[did]?.takeIf { it.first == head.commitCid }?.second
        val referenced = cached ?: loadBlobReferences(did, head).also { blobIndexes[did] = head.commitCid to it }
        return cid in referenced
    }

    /**
     * One page of the repositories the server shows, ordered by DID, starting after [cursor]. An
     * account that has not written anything yet has no repository and is left out.
     */
    suspend fun listRepos(
        cursor: String?,
        limit: Int,
    ): PublicRepoPage {
        val repos = mutableListOf<PublicRepoSummary>()
        var after = cursor
        while (repos.size < limit) {
            val page = accounts.listPersonalSpaceAccountsAfter(after, limit)
            if (page.isEmpty()) return PublicRepoPage(repos, cursor = null)
            for (account in page) {
                val did = AtprotoDid.parse(checkNotNull(account.did)).getOrNull() ?: continue
                after = did.toString()
                val head = headIfShown(did) ?: continue
                repos += PublicRepoSummary(did, head)
                if (repos.size == limit) return PublicRepoPage(repos, cursor = after)
            }
            if (page.size < limit) return PublicRepoPage(repos, cursor = null)
        }
        return PublicRepoPage(repos, cursor = after)
    }

    private suspend fun treeIsPublic(
        did: AtprotoDid,
        head: RepoHead,
    ): Boolean {
        treeChecks[did]?.takeIf { it.first == head.commitCid }?.let { return it.second }
        val pure = policy.arePublic(RepoCommitChanges.collectionsAt(blocks, head.root).getOrThrow())
        treeChecks[did] = head.commitCid to pure
        return pure
    }

    private suspend fun loadBlobReferences(
        did: AtprotoDid,
        head: RepoHead,
    ): Set<Cid> {
        val collections = RepoCommitChanges.collectionsAt(blocks, head.root).getOrThrow().filter(policy::isPublic)
        val references = linkedSetOf<Cid>()
        collections.forEach { collection ->
            var cursor: String? = null
            do {
                val page = reader.listRecords(did, collection, limit = PAGE_SIZE, cursor = cursor, reverse = false).getOrThrow()
                page.records.forEach { record -> collectBlobCids(record.value, references) }
                cursor = page.cursor
            } while (cursor != null)
        }
        return references
    }

    private fun collectBlobCids(
        element: JsonElement,
        into: MutableSet<Cid>,
    ) {
        when (element) {
            is JsonObject -> {
                val link = (element["ref"] as? JsonObject)?.get("\$link") as? JsonPrimitive
                if (element["\$type"]?.jsonPrimitive?.content == "blob" && link != null) {
                    Cid.parse(link.content).getOrNull()?.let(into::add)
                }
                element.values.forEach { collectBlobCids(it, into) }
            }
            is JsonArray -> element.forEach { collectBlobCids(it, into) }
            is JsonPrimitive -> Unit
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
