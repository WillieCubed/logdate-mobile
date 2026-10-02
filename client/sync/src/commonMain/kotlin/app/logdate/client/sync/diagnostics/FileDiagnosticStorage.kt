package app.logdate.client.sync.diagnostics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.uuid.Uuid

/** Platform DI supplies an app-private, backup-excluded directory and protection callback. */
class FileDiagnosticStorage(
    private val directory: Path,
    private val protect: (Path) -> Unit,
) : DiagnosticStorage {
    private val mutex = Mutex()
    private val history = Path(directory, "history.json")

    override suspend fun read(): String? =
        mutex.withLock {
            removeAbandonedWrites()
            if (!SystemFileSystem.exists(history)) return@withLock null
            SystemFileSystem.source(history).use { source ->
                val buffer = Buffer()
                while (buffer.size <= MAX_BYTES) {
                    val count = source.readAtMostTo(buffer, MAX_BYTES + 1 - buffer.size)
                    if (count == -1L) break
                    check(count > 0) { "Diagnostic storage unavailable" }
                }
                require(buffer.size <= MAX_BYTES) { "Diagnostic history exceeds limit" }
                buffer.readString()
            }
        }

    override suspend fun write(value: String) {
        require(value.length <= MAX_BYTES) { "Diagnostic history exceeds limit" }
        val bytes = value.encodeToByteArray()
        require(bytes.size <= MAX_BYTES) { "Diagnostic history exceeds limit" }
        mutex.withLock {
            SystemFileSystem.createDirectories(directory)
            protect(directory)
            val temporary = Path(directory, "history-${Uuid.random()}.pending")
            try {
                SystemFileSystem.sink(temporary).use { raw ->
                    protect(temporary)
                    raw.buffered().use { it.write(bytes) }
                }
                SystemFileSystem.atomicMove(temporary, history)
            } finally {
                if (SystemFileSystem.exists(temporary)) SystemFileSystem.delete(temporary)
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            removeAbandonedWrites()
            if (SystemFileSystem.exists(history)) SystemFileSystem.delete(history)
        }
    }

    private fun removeAbandonedWrites() {
        if (!SystemFileSystem.exists(directory)) return
        SystemFileSystem.list(directory).forEach { path ->
            if (PENDING_NAME.matches(path.name)) SystemFileSystem.delete(path)
        }
    }

    private companion object {
        val PENDING_NAME = Regex("history-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pending")
        const val MAX_BYTES = 10L * 1024 * 1024
    }
}
