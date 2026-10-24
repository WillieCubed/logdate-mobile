package app.logdate.client.data.restore

/** All tables that can retain a local media reference, including soft-deleted rows. */
fun collectAndroidMediaOwnerReferences(query: (String) -> List<String?>): Set<String> =
    buildSet {
        listOf(
            "SELECT contentUri FROM image_notes",
            "SELECT contentUri FROM video_notes",
            "SELECT contentUri FROM audio_notes",
            "SELECT coverImageUri FROM journals",
            "SELECT cover_image_uri FROM events",
            "SELECT uri FROM rewind_image_content",
            "SELECT uri FROM rewind_video_content",
            "SELECT photo_uri FROM people",
            "SELECT image_uri FROM stickers",
            "SELECT source_photo_uri FROM stickers",
            "SELECT mediaUri FROM transcriptions",
            "SELECT contentUri FROM storage_metadata",
            "SELECT uri FROM indexed_media_images",
            "SELECT uri FROM indexed_media_videos",
            "SELECT uri FROM media_images",
        ).forEach { sql -> query(sql).filterNotNull().forEach(::add) }
    }
