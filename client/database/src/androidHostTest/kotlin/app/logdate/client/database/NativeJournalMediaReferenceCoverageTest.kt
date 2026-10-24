package app.logdate.client.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeJournalMediaReferenceCoverageTest {
    @Test
    fun `snapshot selection covers persisted user-visible media and excludes deleted notes and covers`() {
        val columns =
            setOf(
                "image_notes.contentUri",
                "video_notes.contentUri",
                "audio_notes.contentUri",
                "journals.coverImageUri",
                "events.cover_image_uri",
                "rewind_image_content.uri",
                "rewind_video_content.uri",
                "people.photo_uri",
                "stickers.image_uri",
            )
        val queried = mutableMapOf<String, String>()
        val references =
            collectNativeJournalMediaReferences { sql ->
                val match = checkNotNull(Regex("SELECT (\\w+) FROM (\\w+)").find(sql))
                val column = "${match.groupValues[2]}.${match.groupValues[1]}"
                queried[column] = sql
                listOf("logdate-media://library/$column.jpg")
            }

        assertEquals(columns, queried.keys)
        assertTrue(queried.keys.none { it.startsWith("indexed_media_") || it.startsWith("media_images.") })
        assertEquals(columns.mapTo(mutableSetOf()) { "logdate-media://library/$it.jpg" }, references)
        listOf("image_notes", "video_notes", "audio_notes", "journals", "events", "people").forEach { table ->
            val sql = checkNotNull(queried.entries.firstOrNull { it.key.startsWith("$table.") }?.value)
            assertTrue("WHERE ${if (table in setOf("events", "people")) "deleted_at" else "deletedAt"} IS NULL" in sql)
        }
        assertTrue("transcriptions.mediaUri" !in queried)
    }
}
