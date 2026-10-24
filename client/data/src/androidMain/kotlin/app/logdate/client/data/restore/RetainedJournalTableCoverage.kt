package app.logdate.client.data.restore

/** Explicit disposition for every table in the current Room schema. Unknown non-empty tables block completion. */
internal object RetainedJournalTableCoverage {
    private val merged =
        setOf(
            "journals",
            "text_notes",
            "image_notes",
            "video_notes",
            "audio_notes",
            "transcriptions",
            "transcription_segments",
            "journal_notes",
            "journal_content_links",
        )

    /** These are installation-local indexes/metadata or device identity, reconstructed for the new install. */
    private val reconstructed =
        setOf(
            "indexed_media_images",
            "indexed_media_videos",
            "search_index_metadata",
            "storage_metadata",
            "user_devices",
            "room_master_table",
            "android_metadata",
        )

    /** Known user/business data is deliberately retained until a typed handler is implemented. */
    private val retained =
        setOf(
            "location_logs",
            "history_records",
            "history_cursors",
            "location_activity",
            "media_images",
            "journal_merges",
            "rewinds",
            "rewind_text_content",
            "rewind_image_content",
            "rewind_video_content",
            "rewind_generation_requests",
            "sync_cursors",
            "pending_uploads",
            "sync_download_inbox",
            "sync_download_checkpoints",
            "peer_delivery",
            "sync_space_records",
            "places",
            "user_places",
            "media_captions",
            "media_exif_metadata",
            "health_snapshots",
            "postcards",
            "stickers",
            "events",
            "event_note_links",
            "audio_tags",
            "rewind_prompt_responses",
            "people",
            "inferred_person_clusters",
            "inferred_person_evidence",
            "person_links",
            "person_resolution_decisions",
            "marked_moments",
        )

    data class Assessment(
        val unsupportedNonEmptyTables: Set<String>,
        val unknownNonEmptyTables: Set<String>,
    ) {
        val complete: Boolean get() = unsupportedNonEmptyTables.isEmpty() && unknownNonEmptyTables.isEmpty()
    }

    fun assess(tableCounts: Map<String, Long>): Assessment {
        require(tableCounts.values.all { it >= 0 })
        val unsupported = tableCounts.filter { (table, count) -> count > 0 && table in retained }.keys
        val unknown =
            tableCounts
                .filter { (table, count) ->
                    count > 0 && table !in merged && table !in reconstructed && table !in retained
                }.keys
        return Assessment(unsupported, unknown)
    }

    fun canComplete(
        tableCounts: Map<String, Long>,
        checkpointsApplied: Boolean,
        unresolvedRows: Int,
        mediaPending: Int,
        draftsCovered: Boolean,
    ): Boolean {
        require(unresolvedRows >= 0 && mediaPending >= 0)
        return assess(tableCounts).complete && checkpointsApplied && unresolvedRows == 0 && mediaPending == 0 && draftsCovered
    }
}
