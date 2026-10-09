package app.logdate.server.database

import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoBlock

/** Bounded copies of immutable blocks; repository heads always come from durable storage. */
internal class RepoBlockReadCache(
    private val maxBytes: Int = 8 * 1024 * 1024,
) {
    private val lock = Any()
    private val blocks = LinkedHashMap<Cid, ByteArray>(16, 0.75f, true)
    private var byteCount = 0
    private var epoch = 0L

    suspend fun readMany(
        cids: List<Cid>,
        load: suspend (List<Cid>) -> Result<Map<Cid, RepoBlock>>,
    ): Result<List<RepoBlock?>> {
        val (generation, cached) =
            synchronized(lock) {
                epoch to
                    cids
                        .distinct()
                        .mapNotNull { cid -> blocks[cid]?.let { cid to it.copyOf() } }
                        .toMap()
                        .toMutableMap()
            }
        val missing = cids.distinct().filterNot(cached::containsKey)
        if (missing.isEmpty()) return Result.success(cids.map { cid -> cached[cid]?.let { RepoBlock(cid, it.copyOf()) } })
        return load(missing).map { loaded ->
            missing.forEach { cid ->
                loaded[cid]?.let { block ->
                    remember(block, generation)
                    cached[cid] = block.bytes.copyOf()
                }
            }
            cids.map { cid -> cached[cid]?.let { RepoBlock(cid, it.copyOf()) } }
        }
    }

    suspend fun read(
        cid: Cid,
        load: suspend () -> Result<RepoBlock?>,
    ): Result<RepoBlock?> {
        val generation =
            synchronized(lock) {
                blocks[cid]?.let { return Result.success(RepoBlock(cid, it.copyOf())) }
                epoch
            }
        return load().map { block ->
            block?.let {
                remember(it, generation)
                RepoBlock(it.cid, it.bytes.copyOf())
            }
        }
    }

    fun generation(): Long = synchronized(lock) { epoch }

    fun remember(
        block: RepoBlock,
        expectedGeneration: Long,
    ) {
        synchronized(lock) {
            if (epoch != expectedGeneration || block.bytes.size > maxBytes) return
            blocks.remove(block.cid)?.let { byteCount -= it.size }
            blocks[block.cid] = block.bytes.copyOf()
            byteCount += block.bytes.size
            val oldest = blocks.entries.iterator()
            while (byteCount > maxBytes && oldest.hasNext()) {
                byteCount -= oldest.next().value.size
                oldest.remove()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            blocks.clear()
            byteCount = 0
            epoch++
        }
    }
}
