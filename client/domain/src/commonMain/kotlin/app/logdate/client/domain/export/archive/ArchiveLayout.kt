package app.logdate.client.domain.export.archive

/**
 * Where each file lives in a 2.0 archive.
 *
 * Fixed files have the same name in every archive so a person or a script can go straight to them.
 * Only media and the readable copies are named per item.
 */
object ArchiveLayout {
    val README: ArchivePath = ArchivePath.of("README.txt")
    val MANIFEST: ArchivePath = ArchivePath.of("manifest.json")
    val CHECKSUMS: ArchivePath = ArchivePath.of("SHA256SUMS")

    val JOURNALS: ArchivePath = ArchivePath.of("data/journals.json")
    val NOTES: ArchivePath = ArchivePath.of("data/notes.json")
    val DRAFTS: ArchivePath = ArchivePath.of("data/drafts.json")
    val PLACES: ArchivePath = ArchivePath.of("data/places.json")
    val PROFILE: ArchivePath = ArchivePath.of("data/profile.json")
    val LOCATION_HISTORY: ArchivePath = ArchivePath.of("data/location-history.jsonl")
    val MEDIA_INVENTORY: ArchivePath = ArchivePath.of("data/media.json")

    val SCHEMA_MANIFEST: ArchivePath = ArchivePath.of("schema/manifest.schema.json")
    val SCHEMA_JOURNALS: ArchivePath = ArchivePath.of("schema/journals.schema.json")
    val SCHEMA_NOTES: ArchivePath = ArchivePath.of("schema/notes.schema.json")
    val SCHEMA_DRAFTS: ArchivePath = ArchivePath.of("schema/drafts.schema.json")
    val SCHEMA_PLACES: ArchivePath = ArchivePath.of("schema/places.schema.json")
    val SCHEMA_PROFILE: ArchivePath = ArchivePath.of("schema/profile.schema.json")
    val SCHEMA_LOCATION_SAMPLE: ArchivePath = ArchivePath.of("schema/location-sample.schema.json")
    val SCHEMA_MEDIA: ArchivePath = ArchivePath.of("schema/media.schema.json")

    const val JSON_MEDIA_TYPE = "application/json"
    const val JSON_LINES_MEDIA_TYPE = "application/x-ndjson"
    const val TEXT_MEDIA_TYPE = "text/plain"
}
