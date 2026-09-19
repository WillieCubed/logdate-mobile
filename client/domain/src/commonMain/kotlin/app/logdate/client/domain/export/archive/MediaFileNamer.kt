package app.logdate.client.domain.export.archive

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Names media files the way a person would sort them: by when they were captured, in the local time
 * of the zone they were captured in, filed by kind and year. A file name never includes an id, a hash or
 * the name the device gave it.
 */
class MediaFileNamer(
    private val allocator: ArchivePathAllocator,
) {
    fun nameFor(
        kind: MediaKind,
        capturedAt: Instant,
        zone: TimeZone,
        extension: String,
    ): ArchivePath {
        val local = capturedAt.toLocalDateTime(zone)
        val stem =
            "${local.year.pad(4)}-${local.month.number.pad(2)}-${local.day.pad(2)}_" +
                "${local.hour.pad(2)}-${local.minute.pad(2)}-${local.second.pad(2)}"
        return allocator.allocate("media/${kind.folder}/${local.year}", stem, extension.lowercase())
    }

    private fun Int.pad(width: Int) = toString().padStart(width, '0')
}
