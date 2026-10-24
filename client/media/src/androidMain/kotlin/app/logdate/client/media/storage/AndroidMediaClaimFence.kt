package app.logdate.client.media.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Cross-process media claim fence rooted in app-private, excluded-from-backup storage. */
class AndroidMediaClaimFence(
    private val noBackupFilesDir: File,
) : MediaClaimFence {
    private val directory = File(noBackupFilesDir, "media-reclamation")
    private val lockPath = File(directory, ".claims.lock").absolutePath
    private val processMutex = mutexes.computeIfAbsent(lockPath) { Mutex() }

    override suspend fun <T> withClaim(block: suspend () -> T): T = fenced(shared = true, block)

    override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = fenced(shared = false, block)

    private suspend fun <T> fenced(
        shared: Boolean,
        block: suspend () -> T,
    ): T {
        val current = currentCoroutineContext()[HeldFence]
        if (current?.path == lockPath) {
            check(shared && current.shared) { "Media reclamation cannot run inside a claim mutation" }
            return block()
        }
        return withContext(Dispatchers.IO) {
            processMutex.lock()
            try {
                check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
                    "Could not create media claim fence directory"
                }
                check(!Files.isSymbolicLink(directory.toPath())) { "Media claim fence directory is a symlink" }
                FileChannel
                    .open(
                        File(directory, ".claims.lock").toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { channel ->
                        acquireLock(channel, shared).use {
                            withContext(HeldFence(lockPath, shared)) { block() }
                        }
                    }
            } finally {
                processMutex.unlock()
            }
        }
    }

    private suspend fun acquireLock(
        channel: FileChannel,
        shared: Boolean,
    ): FileLock {
        while (true) {
            currentCoroutineContext().ensureActive()
            val acquired =
                try {
                    channel.tryLock(0L, Long.MAX_VALUE, shared)
                } catch (_: OverlappingFileLockException) {
                    null
                }
            if (acquired != null) return acquired
            delay(25)
        }
    }

    private class HeldFence(
        val path: String,
        val shared: Boolean,
    ) : AbstractCoroutineContextElement(HeldFence) {
        companion object : CoroutineContext.Key<HeldFence>
    }

    private companion object {
        val mutexes = ConcurrentHashMap<String, Mutex>()
    }
}
