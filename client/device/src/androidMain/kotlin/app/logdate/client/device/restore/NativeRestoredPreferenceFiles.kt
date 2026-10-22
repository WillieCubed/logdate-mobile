package app.logdate.client.device.restore

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Retains the platform-delivered bytes before another process can cache or edit preferences. */
class NativeRestoredPreferenceFiles(
    private val context: Context,
    private val replay: ((File, ByteArray) -> Unit)? = null,
) {
    fun begin(names: Set<String>) =
        locked {
            names.forEach { captureFor(it).begin() }
        }

    fun observe(
        destination: File,
        names: Set<String>,
    ): String? =
        locked {
            val name = matchingName(destination, names) ?: return@locked null
            captureFor(name).observe()
            name
        }

    fun capture(
        destination: File,
        names: Set<String>,
    ) = locked {
        val name = matchingName(destination, names) ?: return@locked
        captureFor(name).capture()
    }

    internal fun restore(
        destination: File,
        names: Set<String>,
        size: Long,
        incoming: () -> ByteArray,
        copy: (ByteArray) -> Unit,
        duplicate: () -> Unit,
    ): Boolean {
        if (names.isEmpty()) return false
        if (matchingName(destination, names) == null) return false
        return locked {
            val name = matchingName(destination, names) ?: return@locked false
            if (size !in 0..MAX_BYTES.toLong()) throw IOException("Native preference payload has an invalid size")
            captureFor(name).restore(
                incoming = {
                    incoming().also { if (it.size.toLong() != size) throw IOException("Native preference input is incomplete") }
                },
                copy = { bytes ->
                    copy(bytes)
                    val source = sourceFile(name)
                    if (!source.isFile || source.length() != size || !source.readBytes().contentEquals(bytes)) {
                        throw IOException("Android did not retain the complete native preference file")
                    }
                },
                duplicate = duplicate,
            )
            true
        }
    }

    fun finish(
        names: Set<String>,
        deliveredNames: Set<String>,
    ) = locked {
        names.forEach { name ->
            captureFor(name).finish(name in deliveredNames)
        }
    }

    internal fun recordFinalDelivery(names: Set<String>): Set<String> {
        if (names.isEmpty()) return emptySet()
        return locked {
            val journal = deliveryJournal(generation())
            val decision = journal.read()
            if (decision != null) {
                if (decision.names != names) throw IOException("Native preference delivery enrollment changed")
                decision.delivered
            } else {
                val delivered = names.filterTo(mutableSetOf()) { captureFor(it).deliveryDecision() }
                journal.record(names, delivered).delivered
            }
        }
    }

    internal fun canResumeFinalization(names: Set<String>): Boolean {
        if (names.isEmpty()) return false
        return locked {
            val detector = PostRestoreDetector(context)
            val generation = detector.retainedPreferenceSessionId() ?: return@locked false
            if (!detector.canResumeNativeRestoreFinalization(generation)) return@locked false
            val decision = deliveryJournal(generation).read()
            if (decision != null && decision.names != names) throw IOException("Native preference delivery enrollment changed")
            true
        }
    }

    fun read(
        name: String,
        expectedGeneration: String? = PostRestoreDetector(context).retainedPreferenceSessionId(),
    ): ByteArray? {
        if (expectedGeneration == null) return null
        return locked { captureFor(name, expectedGeneration).read() }
    }

    private fun captureFor(
        name: String,
        expectedGeneration: String = generation(),
    ) = NativePreferenceCapture(
        expectedGeneration,
        object : NativePreferenceSnapshotStore {
            override fun read(): NativePreferenceSnapshot? {
                val file = file(name, expectedGeneration)
                if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
                return readSnapshot(file)
            }

            override fun write(snapshot: NativePreferenceSnapshot) = this@NativeRestoredPreferenceFiles.write(name, snapshot)
        },
        {
            val source = sourceFile(name)
            check(expectedGeneration == generation()) { "A previous delivery cannot read the current preference file" }
            require(source.length() <= MAX_BYTES)
            source.readBytes()
        },
        { PostRestoreDetector(context).hasCompletedNativeRestoreDelivery() },
        {
            if (PostRestoreDetector(context).retainedPreferenceSessionId() != expectedGeneration) {
                null
            } else {
                deliveryJournal(expectedGeneration).read()?.takeIf { name in it.names }?.let { name in it.delivered }
            }
        },
        { bytes -> repairDestination(name, expectedGeneration, bytes) },
    )

    private fun repairDestination(
        name: String,
        expectedGeneration: String,
        bytes: ByteArray,
    ) {
        check(expectedGeneration == generation()) { "A previous delivery cannot replace the current preference file" }
        check(!PostRestoreDetector(context).hasCompletedNativeRestoreDelivery()) { "A completed delivery cannot replay preferences" }
        val restore = replay ?: throw IOException("Native preference replay requires the framework callback")
        val destination = sourceFile(name)
        restore(destination, bytes)
        if (!destination.isFile || destination.length() != bytes.size.toLong() || !destination.readBytes().contentEquals(bytes)) {
            throw IOException("Android did not accept the complete native preference replay")
        }
    }

    private fun generation() = requireNotNull(PostRestoreDetector(context).retainedPreferenceSessionId())

    private fun sourceFile(name: String) = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")

    private fun matchingName(
        destination: File,
        names: Set<String>,
    ) = names.singleOrNull {
        destination.canonicalFile ==
            sourceFile(it).canonicalFile
    }

    private fun readSnapshot(file: AtomicFile): NativePreferenceSnapshot =
        file.openRead().use { input ->
            require(input.channel.size() <= MAX_STORED_BYTES)
            Json.decodeFromString(input.readBytes().decodeToString())
        }

    private fun write(
        name: String,
        snapshot: NativePreferenceSnapshot,
    ) {
        val bytes = Json.encodeToString(snapshot).encodeToByteArray()
        require(bytes.size <= MAX_STORED_BYTES)
        val file = file(name, snapshot.generation)
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
            if (readSnapshot(file) != snapshot) throw IOException("Restored preferences were not retained")
        } catch (failure: Throwable) {
            runCatching { file.failWrite(output) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun file(
        name: String,
        generation: String,
    ): AtomicFile {
        require(name.matches(Regex("[a-z][a-z0-9_]{0,99}")))
        return AtomicFile(File(generationDirectory(generation), "$name.json"))
    }

    private fun deliveryJournal(generation: String) =
        NativePreferenceDeliveryJournal(File(generationDirectory(generation), ".delivery.json"), generation)

    private fun generationDirectory(generation: String): File {
        require(
            kotlin.uuid.Uuid
                .parse(generation)
                .toString() == generation,
        )
        val retained = File(directory(), generation)
        if (!retained.isDirectory && !retained.mkdirs()) throw IOException("Restored preference generation is unavailable")
        return retained
    }

    private fun directory(): File {
        val directory = File(context.noBackupFilesDir, "native-restored-preferences")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Restored preferences directory is unavailable")
        return directory
    }

    private fun <T> locked(block: () -> T): T =
        synchronized(processLock) {
            RandomAccessFile(File(directory(), "snapshots.lock"), "rw").use { lock -> lock.channel.lock().use { block() } }
        }

    private companion object {
        val processLock = Any()
        const val MAX_BYTES = 1_048_576
        const val MAX_STORED_BYTES = 1_500_000
    }
}
