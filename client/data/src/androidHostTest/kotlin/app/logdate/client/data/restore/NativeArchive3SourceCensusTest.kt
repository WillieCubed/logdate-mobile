package app.logdate.client.data.restore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeArchive3SourceCensusTest {
    @Test
    fun `content not represented by archive three blocks complete claim`() {
        val rows =
            mapOf(
                "journals" to 2L,
                "text_notes" to 1L,
                "user_places" to 1L,
                "location_logs" to 4L,
                "transcriptions" to 1L,
                "audio_tags" to 1L,
                "people" to 1L,
                "unknown_future_table" to 1L,
                "room_master_table" to 1L,
                "native_journal_source_scope" to 1L,
                "indexed_media" to 3L,
                "storage_metadata" to 1L,
            )
        assertEquals(
            setOf("transcriptions", "audio_tags", "people", "unknown_future_table", "indexed_media"),
            NativeArchive3SourceCensus.unsupportedPopulatedTables(rows),
        )
    }

    @Test
    fun `device indexes and installation bookkeeping do not block journal transfer`() {
        val rows =
            mapOf(
                "journals" to 2L,
                "indexed_media_images" to 90L,
                "indexed_media_videos" to 12L,
                "storage_metadata" to 3L,
                "user_devices" to 2L,
                "journal_notes" to 1L,
            )
        assertEquals(setOf("journal_notes"), NativeArchive3SourceCensus.unsupportedPopulatedTables(rows))
    }

    @Test
    fun `unreviewed source schema cannot claim a complete archive`() {
        assertEquals(true, NativeArchive3SourceCensus.reviewedRoomIdentity("d8b23ded9a1a82c49e39b8df9ef25e35"))
        assertEquals(false, NativeArchive3SourceCensus.reviewedRoomIdentity("a future schema"))
    }

    @Test
    fun `empty unsupported tables do not block a journal-only source`() {
        val rows = mapOf("journals" to 1L, "text_notes" to 1L, "transcriptions" to 0L, "audio_tags" to 0L)
        assertEquals(emptySet(), NativeArchive3SourceCensus.unsupportedPopulatedTables(rows))
    }

    @Test
    fun `unmapped settings and future preference keys block complete scope`() {
        val preferences =
            preferencesOf(
                stringPreferencesKey("display_name") to "A person",
                stringPreferencesKey("identity.canonical_owner_id") to "11111111-1111-1111-1111-111111111111",
                booleanPreferencesKey("quiet_save_preference_enabled") to false,
                stringPreferencesKey("future_personal_setting") to "value",
                stringPreferencesKey("identity.future_personal_setting") to "value",
                stringPreferencesKey("sync_future_personal_setting") to "value",
            )
        assertEquals(
            setOf(
                "quiet_save_preference_enabled",
                "future_personal_setting",
                "identity.future_personal_setting",
                "sync_future_personal_setting",
            ),
            NativeArchive3SourceCensus.unsupportedPreferences(preferences),
        )
    }
}
