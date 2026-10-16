package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.UnavailableMediaAsset
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
internal data class ArchiveRecoveryScope(
    val ownerId: String,
    val serverOrigin: String,
)

@Serializable
internal data class ArchiveRecoveryRecord(
    val id: String,
    val scope: ArchiveRecoveryScope,
    val options: ImportOptions,
    val sourceLabel: String,
    val pending: List<UnavailableMediaAsset> = emptyList(),
    val metadataApplied: Boolean = false,
    val summary: RestoreSummary? = null,
    val sourceGeneration: String? = null,
    val sourceWorkId: String? = null,
    val createdAt: Long = 0,
    val pausedByUser: Boolean = false,
    val requiresSource: Boolean = false,
)

/** Records stay small enough for native cloud backup; source archives travel only through direct transfer. */
internal class ArchiveRecoveryStore(
    private val directory: File,
    private val staging: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val records = directory.resolve("records")
    private val sources = directory.resolve("sources")

    fun read(id: String): ArchiveRecoveryRecord? {
        val file = recordFile(id)
        return if (file.exists()) {
            val record = json.decodeFromString<ArchiveRecoveryRecord>(file.readText())
            if (records.resolve("$id.paused").exists()) record.copy(pausedByUser = true) else record
        } else {
            null
        }
    }

    fun pending(scope: ArchiveRecoveryScope): List<ArchiveRecoveryRecord> =
        records
            .listFiles()
            .orEmpty()
            .filter { it.extension == "json" }
            .mapNotNull { file ->
                runCatching { read(file.nameWithoutExtension) }
                    .onFailure {
                        io.github.aakira.napier.Napier
                            .w("Unreadable recovery record retained; other recovery continues")
                    }.getOrNull()
            }.filter { it.scope == scope && (!it.metadataApplied || it.pending.isNotEmpty() || it.requiresSource) }
            .sortedBy { it.createdAt }

    private fun sourceFile(
        id: String,
        generation: String?,
    ): File =
        sources.resolve(
            "${Uuid.parse(id)}${generation?.let { ".${Uuid.parse(it)}" }.orEmpty()}.zip",
        )

    fun source(id: String): File = sourceFile(id, read(id)?.sourceGeneration)

    fun begin(
        id: String,
        scope: ArchiveRecoveryScope,
        source: File,
        options: ImportOptions,
        label: String,
    ): ArchiveRecoveryRecord {
        read(id)?.let { existing ->
            check(existing.scope == scope) { "Archive recovery scope changed" }
            return existing.copy(options = options).also(::save)
        }
        val generation = Uuid.random().toString()
        atomicWrite(sourceFile(id, generation)) { output -> source.inputStream().use { it.copyTo(output) } }
        return ArchiveRecoveryRecord(
            id,
            scope,
            options,
            label,
            sourceGeneration = generation,
            sourceWorkId = id,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            pausedByUser = records.resolve("$id.paused").exists(),
        ).also(::save)
    }

    fun replaceSource(
        id: String,
        source: File,
        workId: String? = null,
    ) {
        val record = requireNotNull(read(id))
        val previousSource = source(id)
        val generation = Uuid.random().toString()
        atomicWrite(sourceFile(id, generation)) { output -> source.inputStream().use { it.copyTo(output) } }
        save(
            record.copy(
                sourceGeneration = generation,
                sourceWorkId = workId,
                requiresSource = false,
                metadataApplied = record.metadataApplied && !record.requiresSource,
                pending = record.pending.map { it.copy(archivePath = null, recoveredReference = null) },
            ),
        )
        previousSource.delete()
    }

    fun recordPending(
        id: String,
        asset: UnavailableMediaAsset,
    ) {
        val record = requireNotNull(read(id))
        val pending =
            record.pending.filterNot { it.entityId == asset.entityId && it.blockId == asset.blockId && it.role == asset.role } + asset
        save(record.copy(pending = pending))
    }

    fun replacePending(
        id: String,
        pending: List<UnavailableMediaAsset>,
    ) {
        val record = requireNotNull(read(id))
        save(record.copy(pending = pending))
    }

    fun finish(
        id: String,
        summary: RestoreSummary,
    ) {
        val record = requireNotNull(read(id))
        save(record.copy(metadataApplied = true, summary = summary, requiresSource = summary.needsAnotherArchive))
        if (record.pending.isEmpty() && !summary.needsAnotherArchive) source(id).delete()
    }

    fun setPaused(
        id: String,
        paused: Boolean,
    ) = synchronized(recordLock) {
        val marker = records.resolve("${Uuid.parse(id)}.paused")
        if (paused) atomicWrite(marker) { it.write(1) } else check(!marker.exists() || marker.delete())
        read(id)?.let { writeRecord(it.copy(pausedByUser = paused)) }
    }

    fun requireSource(id: String) =
        synchronized(recordLock) {
            read(id)?.let { writeRecord(it.copy(requiresSource = true)) }
        }

    private fun recordFile(id: String): File = records.resolve("${Uuid.parse(id)}.json")

    private fun save(record: ArchiveRecoveryRecord) =
        synchronized(recordLock) {
            val paused = records.resolve("${record.id}.paused").exists() || (read(record.id)?.pausedByUser ?: record.pausedByUser)
            writeRecord(record.copy(pausedByUser = paused))
        }

    private fun writeRecord(record: ArchiveRecoveryRecord) {
        val bytes = json.encodeToString(record).encodeToByteArray()
        atomicWrite(recordFile(record.id)) { it.write(bytes) }
    }

    private companion object {
        val recordLock = Any()
    }

    private fun atomicWrite(
        destination: File,
        write: (FileOutputStream) -> Unit,
    ) {
        check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
        check(staging.mkdirs() || staging.isDirectory)
        val temporary = File.createTempFile("archive-recovery-", ".part", staging)
        try {
            FileOutputStream(temporary).use { output ->
                write(output)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
