package app.logdate.client.data.restore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/** Contains delivery identity and a security-configuration digest, never a credential or token. */
@Serializable
internal data class NativeAccountRecoveryRequest(
    val delivery: String,
    val generation: String,
    val scope: String,
    val owner: String? = null,
    val binding: String? = null,
) {
    init {
        Uuid.parse(delivery)
        Uuid.parse(generation)
        require(scope.matches(Regex("[a-f0-9]{64}")))
    }
}

internal class NativeAccountRecoveryInbox(
    directory: File,
) {
    private val file = File(directory, "native-account-recovery.json")
    private val lock = locks.getOrPut(file.canonicalPath) { Any() }

    @Serializable
    private data class Record(
        val request: NativeAccountRecoveryRequest,
        val target: String? = null,
    )

    fun acceptedTarget(request: NativeAccountRecoveryRequest): String? =
        synchronized(lock) {
            val current = read()?.takeIf { it.request == request } ?: return@synchronized null
            val receipt = read(targetFile(request))
            check(receipt != null || current.target != null || (request.binding == "true" && request.owner != null))
            check(receipt == null || receipt.request == request)
            check(current.target == null || receipt?.target == null || current.target == receipt.target)
            receipt?.target ?: current.target
        }

    fun acceptTarget(
        request: NativeAccountRecoveryRequest,
        target: String,
    ): Boolean =
        synchronized(lock) {
            Uuid.parse(target)
            val current = read() ?: return@synchronized false
            if (current.request != request) return@synchronized false
            val accepted = acceptedTarget(request)
            if (accepted != null && accepted != target) return@synchronized false
            val pinned = current.copy(target = target)
            write(pinned, targetFile(request))
            write(pinned)
            true
        }

    /** The caller verifies the original scope and generation before using OS-owned job extras. */
    fun restoreScheduledRequest(
        request: NativeAccountRecoveryRequest,
        committedTarget: String? = null,
    ): Boolean =
        synchronized(lock) {
            val existing = runCatching { read() }
            val current = existing.getOrNull()
            if (current != null && current.request != request) return@synchronized false
            val receipt = runCatching { read(targetFile(request)) }.getOrNull()
            if (receipt != null && receipt.request != request) return@synchronized false
            if (receipt?.target != null && committedTarget != null && receipt.target != committedTarget) return@synchronized false
            val confirmedTarget = current?.target ?: committedTarget
            if (current?.target != null && committedTarget != null && current.target != committedTarget) return@synchronized false
            if (receipt?.target != null && confirmedTarget != null && receipt.target != confirmedTarget) return@synchronized false
            if (receipt == null && confirmedTarget == null) return@synchronized false
            val restored = Record(request, receipt?.target ?: confirmedTarget)
            if (receipt == null) {
                preserveUnreadable(targetFile(request))
                write(restored, targetFile(request))
            }
            if (current != restored) {
                preserveUnreadable(file)
                write(restored)
            }
            true
        }

    fun pending(): NativeAccountRecoveryRequest? = synchronized(lock) { read()?.request }

    /** Only a newly verified platform delivery may replace unreadable custody from a prior delivery. */
    fun retainFreshDelivery(request: NativeAccountRecoveryRequest): Boolean =
        synchronized(lock) {
            val readable = runCatching { read() }
            if (readable.isSuccess) {
                retain(request)
            } else {
                preserveUnreadable(file)
                retain(request)
            }
        }

    fun retain(request: NativeAccountRecoveryRequest): Boolean =
        synchronized(lock) {
            val previous = read()
            if (previous?.request == request) {
                val receipt = read(targetFile(request))
                if (receipt == null) {
                    check(previous.target != null || (request.binding == "true" && request.owner != null))
                    write(previous, targetFile(request))
                } else {
                    check(receipt.request == request)
                    check(previous.target == null || receipt.target == previous.target)
                }
                return@synchronized true
            }
            if (previous?.request?.generation == request.generation) return@synchronized false
            val receipt = read(targetFile(request))
            check(receipt == null || receipt.request == request)
            val record = receipt ?: Record(request)
            if (receipt == null) write(record, targetFile(request))
            write(record)
            true
        }

    fun acknowledge(request: NativeAccountRecoveryRequest): Boolean =
        synchronized(lock) {
            if (read()?.request != request) return@synchronized false
            check(file.delete())
            true
        }

    private fun targetFile(request: NativeAccountRecoveryRequest) =
        File(file.parentFile, "native-account-target-${request.generation}-${request.delivery}.json")

    private fun preserveUnreadable(path: File) {
        if (!path.exists()) return
        Files.move(
            path.toPath(),
            File(path.parentFile, "${path.name}.unreadable-${Uuid.random()}").toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun write(
        record: Record,
        file: File = this.file,
    ) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use { output ->
            output.write(Json.encodeToString(record).encodeToByteArray())
            output.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        check(read(file) == record)
    }

    private fun read(file: File = this.file): Record? {
        if (!file.exists()) return null
        check(file.length() <= 4096) { "Native account recovery custody is unreadable" }
        return Json.decodeFromString<Record>(file.readText())
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}
