package app.logdate.client.data.restore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
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
    fun `legacy journal links are safe only when active modern links contain them`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name("native-archive3-legacy-links-${System.nanoTime()}.db")
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE journal_notes (id TEXT NOT NULL, uid TEXT NOT NULL)")
                                db.execSQL(
                                    "CREATE TABLE journal_content_links " +
                                        "(journal_id TEXT NOT NULL, content_id TEXT NOT NULL, deletedAt INTEGER)",
                                )
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO journal_notes VALUES ('journal', 'note')")
            assertFalse(NativeArchive3SourceCensus.legacyJournalLinksCovered(database))
            database.execSQL("INSERT INTO journal_content_links VALUES ('journal', 'note', 1)")
            assertFalse(NativeArchive3SourceCensus.legacyJournalLinksCovered(database))
            database.execSQL("UPDATE journal_content_links SET deletedAt = NULL")
            assertTrue(NativeArchive3SourceCensus.legacyJournalLinksCovered(database))
        } finally {
            helper.close()
        }
    }

    @Test
    fun `deleted notes and journal links cannot be exported as active records`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name("native-archive3-deleted-source-${System.nanoTime()}.db")
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                for (table in listOf("text_notes", "image_notes", "audio_notes", "video_notes", "journal_content_links")) {
                                    db.execSQL("CREATE TABLE $table (deletedAt INTEGER)")
                                }
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO text_notes VALUES (NULL)")
            database.execSQL("INSERT INTO image_notes VALUES (123)")
            database.execSQL("INSERT INTO journal_content_links VALUES (456)")
            assertEquals(
                setOf("image_notes", "journal_content_links"),
                NativeArchive3SourceCensus.deletedSourceRows(database),
            )
        } finally {
            helper.close()
        }
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
