package app.logdate.server.routes.sync

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.cio.MultipartEvent
import io.ktor.http.cio.parseMultipart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/** [reason] is the client-facing message; it never echoes request content. */
internal class InvalidBackupMultipart(
    val reason: String = "Invalid backup multipart body",
) : IllegalArgumentException(reason)

private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024
private const val MAX_OVERHEAD_BYTES = 16L * 1024 * 1024
private const val CHUNK_BYTES = 64L * 1024
private const val MAX_MANIFEST_BYTES = 256L * 1024

/** File bytes earn streaming credit; all headers, fields and epilogues share a fixed memory bound. */
internal suspend fun withBackupFileHandoff(work: suspend () -> ParsedBackupMultipartFile): ParsedBackupMultipartFile {
    var owned: ParsedBackupMultipartFile? = null
    return try {
        withContext(Dispatchers.IO) { work().also { owned = it } }
    } catch (failure: Throwable) {
        owned?.path?.let { runCatching { deletePrivateBackupTempFile(it) } }
        throw failure
    }
}

/** The client sent nothing for longer than the idle timeout, so the upload gave up its transfer slot. */
internal class BackupUploadStalled : IOException("The backup upload sent nothing for too long")

private class BackupStorageFailure : IllegalStateException("BACKUP_STORAGE_UNAVAILABLE")

private inline fun <T> backupStorage(work: () -> T): T =
    try {
        work()
    } catch (_: IOException) {
        throw BackupStorageFailure()
    }

internal suspend fun ApplicationCall.streamBackupMultipart(idleTimeout: Duration): ParsedBackupMultipartFile =
    withBackupFileHandoff {
        val stalled = AtomicBoolean(false)
        try {
            try {
                parseBackupMultipartBody(idleTimeout, stalled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                throw InvalidBackupMultipart()
            } catch (invalid: InvalidBackupMultipart) {
                throw invalid
            } catch (_: IllegalArgumentException) {
                throw InvalidBackupMultipart()
            }
        } catch (failure: Exception) {
            // A stalled pump closes the channel the multipart parser reads, and the parser's failure is what
            // the scope rethrows, so the flag is the only record of why the upload ended.
            if (stalled.get()) throw BackupUploadStalled()
            throw failure
        }
    }

private suspend fun ApplicationCall.parseBackupMultipartBody(
    idleTimeout: Duration,
    stalled: AtomicBoolean,
): ParsedBackupMultipartFile =
    coroutineScope {
        val contentType =
            request.headers[HttpHeaders.ContentType]
                ?.takeIf { it.trim().startsWith("multipart/form-data", ignoreCase = true) }
                ?: throw InvalidBackupMultipart("Expected multipart/form-data body")
        val contentLength =
            request.headers[HttpHeaders.ContentLength]?.let {
                it.toLongOrNull()?.takeIf { size -> size in 1..(MAX_BACKUP_BYTES + MAX_OVERHEAD_BYTES) }
                    ?: throw InvalidBackupMultipart()
            }
        val input = receiveChannel()
        val guarded = ByteChannel(autoFlush = true)
        val credit = AtomicLong()
        val pump = launchGuardedPump(input, guarded, credit, idleTimeout, stalled)
        val events = parseMultipart(guarded, contentType, contentLength, MAX_BACKUP_BYTES)
        val reader = BackupMultipartReader(credit)
        var accepted = false
        try {
            reader.readAll(events, pump)
            val result = reader.complete()
            accepted = true
            result
        } finally {
            events.cancel()
            pump.cancel()
            guarded.cancel(null)
            if (!accepted) {
                input.cancel(null)
                reader.path?.let(::deletePrivateBackupTempFile)
            }
        }
    }

/**
 * Forwards the request body while bounding how far non-file bytes may run ahead of the file [credit].
 * A client that sends nothing for [idleTimeout] ends the upload, and [stalled] says why.
 */
private fun CoroutineScope.launchGuardedPump(
    input: ByteReadChannel,
    guarded: ByteChannel,
    credit: AtomicLong,
    idleTimeout: Duration,
    stalled: AtomicBoolean,
): Job =
    launch {
        try {
            var forwarded = 0L
            val buffer = ByteArray(CHUNK_BYTES.toInt())
            while (true) {
                val count =
                    withTimeoutOrNull(idleTimeout) { input.readAvailable(buffer, 0, buffer.size) }
                        ?: run {
                            stalled.set(true)
                            throw BackupUploadStalled()
                        }
                if (count < 0) break
                val chunk = buffer.copyOf(count)
                forwarded += chunk.size
                if (forwarded > MAX_BACKUP_BYTES + MAX_OVERHEAD_BYTES || forwarded - credit.get() > MAX_OVERHEAD_BYTES) {
                    throw InvalidBackupMultipart("Backup upload is larger than 2 GiB")
                }
                guarded.writeFully(chunk)
                guarded.flush()
            }
            guarded.flushAndClose()
        } catch (failure: Exception) {
            guarded.cancel(failure)
            if (failure is CancellationException) throw failure
        }
    }

private class BackupMultipartReader(
    private val credit: AtomicLong,
) {
    var path: Path? = null
        private set
    private var size = 0L
    private val fields = mutableMapOf<String, String>()
    private val names = mutableSetOf<String>()

    suspend fun readAll(
        events: ReceiveChannel<MultipartEvent>,
        pump: Job,
    ) {
        while (true) {
            val received = events.receiveCatching()
            received.exceptionOrNull()?.let {
                if (it is CancellationException) throw it
                throw generateSequence(it) { failure -> failure.cause }.filterIsInstance<InvalidBackupMultipart>().firstOrNull()
                    ?: InvalidBackupMultipart()
            }
            val event = received.getOrNull() ?: break
            try {
                read(event)
            } catch (failure: Exception) {
                // release() drains a part body; cancel first so rejection never drains an unbounded request.
                events.cancel()
                pump.cancel()
                if (event is MultipartEvent.MultipartPart) event.body.cancel(failure)
                throw failure
            } finally {
                event.release()
            }
        }
    }

    fun complete(): ParsedBackupMultipartFile {
        val device = fields["deviceId"]?.trim()?.takeIf { it.isNotBlank() } ?: throw missingField("deviceId")
        val manifest = fields["manifest"]?.takeIf { it.isNotBlank() } ?: throw missingField("manifest")
        val stored = path ?: throw missingField("data")
        val result = stored.takeIf { size > 0 } ?: throw InvalidBackupMultipart("Backup payload must not be empty")
        return ParsedBackupMultipartFile(device, manifest, result, size)
    }

    private suspend fun read(event: MultipartEvent) {
        when (event) {
            is MultipartEvent.MultipartPart -> readPart(event)
            is MultipartEvent.Preamble -> if (event.body.readByteArray().size > 8192) throw InvalidBackupMultipart()
            is MultipartEvent.Epilogue -> if (event.body.readByteArray().size > 8192) throw InvalidBackupMultipart()
        }
    }

    private suspend fun readPart(part: MultipartEvent.MultipartPart) {
        val headers = part.headers.await()
        val disposition =
            try {
                ContentDisposition.parse(headers[HttpHeaders.ContentDisposition]?.toString() ?: "")
            } catch (_: Exception) {
                throw InvalidBackupMultipart()
            }
        val name = disposition.parameter("name") ?: throw InvalidBackupMultipart()
        if (name !in setOf("deviceId", "manifest", "data") || !names.add(name)) throw InvalidBackupMultipart()
        if (name == "data") {
            if (disposition.parameter("filename") == null) throw InvalidBackupMultipart()
            writeDataPart(part.body)
        } else {
            fields[name] = readTextField(name, part.body)
        }
    }

    private suspend fun writeDataPart(body: ByteReadChannel) {
        val output = backupStorage { createPrivateBackupTempFile("logdate-backup-upload-", ".bin") }
        path = output
        val target = backupStorage { Files.newOutputStream(output).buffered() }
        try {
            while (true) {
                val chunk = body.readRemaining(CHUNK_BYTES).readByteArray()
                if (chunk.isEmpty()) break
                size += chunk.size
                if (size > MAX_BACKUP_BYTES) throw InvalidBackupMultipart("Backup payload is larger than 2 GiB")
                backupStorage { target.write(chunk) }
                credit.addAndGet(chunk.size.toLong())
            }
        } finally {
            backupStorage { target.close() }
        }
    }

    private suspend fun readTextField(
        name: String,
        body: ByteReadChannel,
    ): String {
        val limit = if (name == "deviceId") 256L else MAX_MANIFEST_BYTES
        val bytes = body.readRemaining(limit + 1).readByteArray()
        if (bytes.size > limit) throw InvalidBackupMultipart()
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            throw InvalidBackupMultipart()
        }
    }
}

private fun missingField(name: String) = InvalidBackupMultipart("Missing required multipart field: $name")
