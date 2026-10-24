package app.logdate.client.data.restore

import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.db.SupportSQLiteDatabase

/** A conservative inventory of user rows before claiming a complete Android-to-Apple export. */
internal object NativeArchive3SourceCensus {
    // Source columns were reviewed against the exported Room 58 schema before claiming complete scope.
    private const val REVIEWED_ROOM_IDENTITY = "d8b23ded9a1a82c49e39b8df9ef25e35"
    private val represented =
        setOf(
            "journals",
            "text_notes",
            "image_notes",
            "audio_notes",
            "video_notes",
            "journal_content_links",
            "media_captions",
            "user_places",
            "location_logs",
        )
    private val regenerable =
        setOf(
            "room_master_table",
            "android_metadata",
            "sqlite_sequence",
            "native_journal_source_scope",
            "search_index_metadata",
            "media_exif_metadata",
            "indexed_media_images",
            "indexed_media_videos",
            "storage_metadata",
            "user_devices",
        )

    fun unsupportedPopulatedTables(rows: Map<String, Long>): Set<String> =
        rows
            .filter { (name, count) -> count > 0 && name !in represented && name !in regenerable }
            .keys

    fun inspect(database: SupportSQLiteDatabase): Set<String> {
        database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { identity ->
            check(identity.moveToFirst() && reviewedRoomIdentity(identity.getString(0))) {
                "Native source schema has not been reviewed for complete transfer"
            }
        }
        val rows = mutableMapOf<String, Long>()
        database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { tables ->
            while (tables.moveToNext()) {
                val name = tables.getString(0)
                check(name.matches(Regex("[A-Za-z_][A-Za-z_0-9]*"))) { "Unrecognized native source table" }
                database.query("SELECT COUNT(*) FROM \"$name\"").use { count ->
                    check(count.moveToFirst())
                    rows[name] = count.getLong(0)
                }
            }
        }
        val unsupported = unsupportedPopulatedTables(rows).toMutableSet()
        unsupported += deletedSourceRows(database)
        if (hasUnrepresentedJournalLinks(database)) unsupported += "journalContentWithoutNote"
        if (hasUnrepresentedMediaCaptions(database)) unsupported += "mediaCaptionWithoutMediaNote"
        if ("journal_notes" in unsupported && legacyJournalLinksCovered(database)) unsupported.remove("journal_notes")
        return unsupported
    }

    fun deletedSourceRows(database: SupportSQLiteDatabase): Set<String> =
        setOf("text_notes", "image_notes", "audio_notes", "video_notes", "journal_content_links").filterTo(mutableSetOf()) { table ->
            database.query("SELECT 1 FROM $table WHERE deletedAt IS NOT NULL LIMIT 1").use { it.moveToFirst() }
        }

    fun hasUnrepresentedJournalLinks(database: SupportSQLiteDatabase): Boolean =
        database
            .query(
                """
                SELECT 1 FROM journal_content_links AS link
                WHERE NOT EXISTS (SELECT 1 FROM journals AS journal WHERE journal.id = link.journal_id)
                   OR (
                       NOT EXISTS (SELECT 1 FROM text_notes AS note WHERE note.uid = link.content_id)
                       AND NOT EXISTS (SELECT 1 FROM image_notes AS note WHERE note.uid = link.content_id)
                       AND NOT EXISTS (SELECT 1 FROM audio_notes AS note WHERE note.uid = link.content_id)
                       AND NOT EXISTS (SELECT 1 FROM video_notes AS note WHERE note.uid = link.content_id)
                   ) LIMIT 1
                """.trimIndent(),
            ).use { it.moveToFirst() }

    fun hasUnrepresentedMediaCaptions(database: SupportSQLiteDatabase): Boolean =
        database
            .query(
                """
                SELECT 1 FROM media_captions AS caption
                WHERE NOT EXISTS (SELECT 1 FROM image_notes AS note WHERE note.uid = caption.noteId)
                  AND NOT EXISTS (SELECT 1 FROM audio_notes AS note WHERE note.uid = caption.noteId)
                  AND NOT EXISTS (SELECT 1 FROM video_notes AS note WHERE note.uid = caption.noteId)
                LIMIT 1
                """.trimIndent(),
            ).use { it.moveToFirst() }

    fun reviewedRoomIdentity(identity: String?): Boolean = identity == REVIEWED_ROOM_IDENTITY

    fun legacyJournalLinksCovered(database: SupportSQLiteDatabase): Boolean =
        database
            .query(
                """
                SELECT 1 FROM journal_notes AS legacy
                WHERE NOT EXISTS (
                    SELECT 1 FROM journal_content_links AS current
                    WHERE current.journal_id = legacy.id
                      AND current.content_id = legacy.uid
                      AND current.deletedAt IS NULL
                ) LIMIT 1
                """.trimIndent(),
            ).use { !it.moveToFirst() }

    /** Only profile fields are currently represented in the Apple auxiliary payload. */
    fun unsupportedPreferences(preferences: Preferences): Set<String> {
        val profile =
            setOf(
                "display_name",
                "birthday",
                "bio",
                "original_bio",
                "profile_created_at",
                "profile_last_updated_at",
                "profile_photo_uri",
            )
        val operational =
            setOf(
                "backend_url",
                "is_onboarded",
                "onboarded_timestamp",
                "backfilled_account_ids",
                "entry_drafts",
                "identity.canonical_owner_id",
                "identity.canonical_owner_bound",
                "identity.completed_history_owner_adoption.v1",
                "identity.native_restore_commit",
                "identity.pending_history_owner_adoption.v1",
            )
        return preferences.asMap().keys.map(Preferences.Key<*>::name).filterTo(mutableSetOf()) { name ->
            name !in profile &&
                name !in operational &&
                !name.startsWith("editor_drafts") &&
                !name.matches(
                    Regex(
                        "sync_(first_enqueue_done_|inflight_|last_error_|legacy_audit_v1_|local_backfill_v[12]_|media_ref_|resume_build_v1_|retry_|route_v1_|association_backfill_v1_).*",
                    ),
                )
        }
    }
}
