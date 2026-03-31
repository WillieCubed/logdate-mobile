package app.logdate.client.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.time.Clock

internal object SearchIndexBootstrapper {
    private const val CURRENT_SCHEMA_VERSION = 3L
    private const val METADATA_TABLE = "search_index_metadata"
    private const val BUMP_GENERATION_SQL = "UPDATE $METADATA_TABLE SET generation = generation + 1 WHERE id = 1;"

    fun bootstrap(connection: SQLiteConnection) {
        createMetadataTable(connection)
        createFtsTable(connection)
        createVocabularyTable(connection)
        recreateTriggers(connection)

        if (needsRebuild(connection)) {
            rebuildIndex(connection)
            optimizeIndex(connection)
            updateMetadata(connection)
        }
    }

    private fun createMetadataTable(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $METADATA_TABLE (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                schemaVersion INTEGER NOT NULL,
                generation INTEGER NOT NULL DEFAULT 0,
                lastRebuiltAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        runCatching {
            connection.execSQL(
                """
                ALTER TABLE $METADATA_TABLE
                ADD COLUMN generation INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
        connection.execSQL(
            """
            INSERT INTO $METADATA_TABLE(id, schemaVersion, generation, lastRebuiltAt)
            SELECT 1, 0, 0, 0
            WHERE NOT EXISTS(SELECT 1 FROM $METADATA_TABLE WHERE id = 1)
            """.trimIndent(),
        )
    }

    private fun createFtsTable(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(
                uid UNINDEXED,
                content,
                created UNINDEXED,
                contentType UNINDEXED,
                tokenize = 'porter unicode61'
            )
            """.trimIndent(),
        )
    }

    private fun createVocabularyTable(connection: SQLiteConnection) {
        runCatching {
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts_vocab
                USING fts5vocab(entries_fts, 'row')
                """.trimIndent(),
            )
        }
    }

    private fun recreateTriggers(connection: SQLiteConnection) {
        triggerNames.forEach { triggerName ->
            connection.execSQL("DROP TRIGGER IF EXISTS $triggerName")
        }
        triggerDefinitions.forEach(connection::execSQL)
    }

    private fun needsRebuild(connection: SQLiteConnection): Boolean {
        val schemaVersion =
            connection.prepare("SELECT schemaVersion FROM $METADATA_TABLE WHERE id = 1").use { stmt ->
                if (!stmt.step()) {
                    null
                } else {
                    stmt.getLong(0)
                }
            }

        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return true
        }

        val hasIndexedRows =
            connection.prepare("SELECT EXISTS(SELECT 1 FROM entries_fts LIMIT 1)").use { stmt ->
                stmt.step()
                stmt.getLong(0) == 1L
            }
        if (hasIndexedRows) {
            return false
        }

        return connection.prepare(sourceRowsExistSql).use { stmt ->
            stmt.step()
            stmt.getLong(0) == 1L
        }
    }

    private fun rebuildIndex(connection: SQLiteConnection) {
        connection.execSQL("DELETE FROM entries_fts")
        rebuildStatements.forEach(connection::execSQL)
    }

    private fun optimizeIndex(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO entries_fts(entries_fts) VALUES('optimize')")
    }

    private fun updateMetadata(connection: SQLiteConnection) {
        val rebuiltAt = Clock.System.now().toEpochMilliseconds()
        connection.execSQL(
            """
            UPDATE $METADATA_TABLE
            SET schemaVersion = $CURRENT_SCHEMA_VERSION,
                generation = generation + 1,
                lastRebuiltAt = $rebuiltAt
            WHERE id = 1
            """.trimIndent(),
        )
    }

    private fun trackedTrigger(definition: String): String {
        val trimmed = definition.trimIndent()
        val endIndex = trimmed.lastIndexOf("\nEND")
        check(endIndex >= 0) { "Invalid trigger definition: missing END" }

        return buildString {
            append(trimmed.substring(0, endIndex))
            append("\n    ")
            append(BUMP_GENERATION_SQL)
            append(trimmed.substring(endIndex))
        }
    }

    private val sourceRowsExistSql =
        """
        SELECT EXISTS(
            SELECT 1 FROM text_notes WHERE deletedAt IS NULL
            UNION ALL
            SELECT 1
            FROM transcriptions
            INNER JOIN audio_notes ON audio_notes.uid = transcriptions.noteId
            WHERE transcriptions.status = 'COMPLETED'
              AND transcriptions.text IS NOT NULL
              AND audio_notes.deletedAt IS NULL
            UNION ALL
            SELECT 1 FROM journals WHERE deletedAt IS NULL
            UNION ALL
            SELECT 1
            FROM media_captions
            LEFT JOIN image_notes ON image_notes.uid = media_captions.noteId
            LEFT JOIN video_notes ON video_notes.uid = media_captions.noteId
            WHERE COALESCE(image_notes.deletedAt, video_notes.deletedAt) IS NULL
              AND COALESCE(image_notes.uid, video_notes.uid) IS NOT NULL
            UNION ALL
            SELECT 1 FROM places WHERE deleted_at IS NULL
            UNION ALL
            SELECT 1 FROM rewinds WHERE title IS NOT NULL
            UNION ALL
            SELECT 1 FROM stickers WHERE label IS NOT NULL
            UNION ALL
            SELECT 1 FROM postcards WHERE title IS NOT NULL
        )
        """.trimIndent()

    private val rebuildStatements =
        listOf(
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT uid, content, created, 'text_note'
            FROM text_notes
            WHERE deletedAt IS NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT transcriptions.noteId, transcriptions.text, audio_notes.created, 'transcription'
            FROM transcriptions
            INNER JOIN audio_notes ON audio_notes.uid = transcriptions.noteId
            WHERE transcriptions.status = 'COMPLETED'
              AND transcriptions.text IS NOT NULL
              AND audio_notes.deletedAt IS NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT id, title || ' ' || COALESCE(description, ''), created, 'journal'
            FROM journals
            WHERE deletedAt IS NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT media_captions.noteId,
                   media_captions.caption,
                   COALESCE(image_notes.created, video_notes.created),
                   'media_caption'
            FROM media_captions
            LEFT JOIN image_notes ON image_notes.uid = media_captions.noteId
            LEFT JOIN video_notes ON video_notes.uid = media_captions.noteId
            WHERE COALESCE(image_notes.deletedAt, video_notes.deletedAt) IS NULL
              AND COALESCE(image_notes.uid, video_notes.uid) IS NOT NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT id, name || ' ' || COALESCE(description, ''), created, 'place'
            FROM places
            WHERE deleted_at IS NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT uid, title, generationDate, 'rewind'
            FROM rewinds
            WHERE title IS NOT NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT id, label, created_at, 'sticker'
            FROM stickers
            WHERE label IS NOT NULL
            """.trimIndent(),
            """
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT id, title, created_at, 'postcard'
            FROM postcards
            WHERE title IS NOT NULL
            """.trimIndent(),
        )

    private val triggerNames =
        listOf(
            "text_notes_fts_insert",
            "text_notes_fts_content_update",
            "text_notes_fts_soft_delete",
            "text_notes_fts_delete",
            "transcriptions_fts_insert",
            "transcriptions_fts_update",
            "transcriptions_fts_delete",
            "audio_notes_transcriptions_fts_soft_delete",
            "audio_notes_transcriptions_fts_restore",
            "audio_notes_transcriptions_fts_delete",
            "journals_fts_insert",
            "journals_fts_content_update",
            "journals_fts_soft_delete",
            "journals_fts_delete",
            "media_captions_fts_insert",
            "media_captions_fts_update",
            "media_captions_fts_delete",
            "image_notes_media_captions_fts_soft_delete",
            "image_notes_media_captions_fts_restore",
            "image_notes_media_captions_fts_delete",
            "video_notes_media_captions_fts_soft_delete",
            "video_notes_media_captions_fts_restore",
            "video_notes_media_captions_fts_delete",
            "places_fts_insert",
            "places_fts_content_update",
            "places_fts_soft_delete",
            "places_fts_delete",
            "rewinds_fts_insert",
            "rewinds_fts_update",
            "rewinds_fts_delete",
            "stickers_fts_insert",
            "stickers_fts_update",
            "stickers_fts_delete",
            "postcards_fts_insert",
            "postcards_fts_update",
            "postcards_fts_delete",
        )

    private val triggerDefinitions =
        listOf(
            trackedTrigger(
                """
            CREATE TRIGGER text_notes_fts_insert
            AFTER INSERT ON text_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.uid, NEW.content, NEW.created, 'text_note');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER text_notes_fts_content_update
            AFTER UPDATE ON text_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid;
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.uid, NEW.content, NEW.created, 'text_note');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER text_notes_fts_soft_delete
            AFTER UPDATE ON text_notes
            WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER text_notes_fts_delete
            AFTER DELETE ON text_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER transcriptions_fts_insert
            AFTER INSERT ON transcriptions
            BEGIN
                DELETE FROM entries_fts WHERE uid = NEW.noteId AND contentType = 'transcription';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.noteId, NEW.text, audio_notes.created, 'transcription'
                FROM audio_notes
                WHERE audio_notes.uid = NEW.noteId
                  AND audio_notes.deletedAt IS NULL
                  AND NEW.status = 'COMPLETED'
                  AND NEW.text IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER transcriptions_fts_update
            AFTER UPDATE ON transcriptions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'transcription';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.noteId, NEW.text, audio_notes.created, 'transcription'
                FROM audio_notes
                WHERE audio_notes.uid = NEW.noteId
                  AND audio_notes.deletedAt IS NULL
                  AND NEW.status = 'COMPLETED'
                  AND NEW.text IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER transcriptions_fts_delete
            AFTER DELETE ON transcriptions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'transcription';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER audio_notes_transcriptions_fts_soft_delete
            AFTER UPDATE ON audio_notes
            WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'transcription';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER audio_notes_transcriptions_fts_restore
            AFTER UPDATE ON audio_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = NEW.uid AND contentType = 'transcription';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT transcriptions.noteId, transcriptions.text, NEW.created, 'transcription'
                FROM transcriptions
                WHERE transcriptions.noteId = NEW.uid
                  AND transcriptions.status = 'COMPLETED'
                  AND transcriptions.text IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER audio_notes_transcriptions_fts_delete
            AFTER DELETE ON audio_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'transcription';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER journals_fts_insert
            AFTER INSERT ON journals
            WHEN NEW.deletedAt IS NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.title || ' ' || COALESCE(NEW.description, ''), NEW.created, 'journal');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER journals_fts_content_update
            AFTER UPDATE ON journals
            WHEN NEW.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.title || ' ' || COALESCE(NEW.description, ''), NEW.created, 'journal');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER journals_fts_soft_delete
            AFTER UPDATE ON journals
            WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER journals_fts_delete
            AFTER DELETE ON journals
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER media_captions_fts_insert
            AFTER INSERT ON media_captions
            BEGIN
                DELETE FROM entries_fts WHERE uid = NEW.noteId AND contentType = 'media_caption';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.noteId,
                       NEW.caption,
                       COALESCE(image_notes.created, video_notes.created),
                       'media_caption'
                FROM image_notes
                LEFT JOIN video_notes ON video_notes.uid = NEW.noteId
                WHERE image_notes.uid = NEW.noteId
                  AND image_notes.deletedAt IS NULL
                UNION ALL
                SELECT NEW.noteId,
                       NEW.caption,
                       video_notes.created,
                       'media_caption'
                FROM video_notes
                WHERE video_notes.uid = NEW.noteId
                  AND video_notes.deletedAt IS NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER media_captions_fts_update
            AFTER UPDATE ON media_captions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'media_caption';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.noteId,
                       NEW.caption,
                       COALESCE(image_notes.created, video_notes.created),
                       'media_caption'
                FROM image_notes
                LEFT JOIN video_notes ON video_notes.uid = NEW.noteId
                WHERE image_notes.uid = NEW.noteId
                  AND image_notes.deletedAt IS NULL
                UNION ALL
                SELECT NEW.noteId,
                       NEW.caption,
                       video_notes.created,
                       'media_caption'
                FROM video_notes
                WHERE video_notes.uid = NEW.noteId
                  AND video_notes.deletedAt IS NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER media_captions_fts_delete
            AFTER DELETE ON media_captions
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'media_caption';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER image_notes_media_captions_fts_soft_delete
            AFTER UPDATE ON image_notes
            WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER image_notes_media_captions_fts_restore
            AFTER UPDATE ON image_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = NEW.uid AND contentType = 'media_caption';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT media_captions.noteId, media_captions.caption, NEW.created, 'media_caption'
                FROM media_captions
                WHERE media_captions.noteId = NEW.uid;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER image_notes_media_captions_fts_delete
            AFTER DELETE ON image_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER video_notes_media_captions_fts_soft_delete
            AFTER UPDATE ON video_notes
            WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER video_notes_media_captions_fts_restore
            AFTER UPDATE ON video_notes
            WHEN NEW.deletedAt IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = NEW.uid AND contentType = 'media_caption';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT media_captions.noteId, media_captions.caption, NEW.created, 'media_caption'
                FROM media_captions
                WHERE media_captions.noteId = NEW.uid;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER video_notes_media_captions_fts_delete
            AFTER DELETE ON video_notes
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER places_fts_insert
            AFTER INSERT ON places
            WHEN NEW.deleted_at IS NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.name || ' ' || COALESCE(NEW.description, ''), NEW.created, 'place');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER places_fts_content_update
            AFTER UPDATE ON places
            WHEN NEW.deleted_at IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.name || ' ' || COALESCE(NEW.description, ''), NEW.created, 'place');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER places_fts_soft_delete
            AFTER UPDATE ON places
            WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER places_fts_delete
            AFTER DELETE ON places
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER rewinds_fts_insert
            AFTER INSERT ON rewinds
            WHEN NEW.title IS NOT NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.uid, NEW.title, NEW.generationDate, 'rewind');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER rewinds_fts_update
            AFTER UPDATE ON rewinds
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'rewind';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.uid, NEW.title, NEW.generationDate, 'rewind'
                WHERE NEW.title IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER rewinds_fts_delete
            AFTER DELETE ON rewinds
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'rewind';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER stickers_fts_insert
            AFTER INSERT ON stickers
            WHEN NEW.label IS NOT NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.label, NEW.created_at, 'sticker');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER stickers_fts_update
            AFTER UPDATE ON stickers
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'sticker';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.id, NEW.label, NEW.created_at, 'sticker'
                WHERE NEW.label IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER stickers_fts_delete
            AFTER DELETE ON stickers
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'sticker';
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER postcards_fts_insert
            AFTER INSERT ON postcards
            WHEN NEW.title IS NOT NULL
            BEGIN
                INSERT INTO entries_fts(uid, content, created, contentType)
                VALUES (NEW.id, NEW.title, NEW.created_at, 'postcard');
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER postcards_fts_update
            AFTER UPDATE ON postcards
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'postcard';
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT NEW.id, NEW.title, NEW.created_at, 'postcard'
                WHERE NEW.title IS NOT NULL;
            END
            """,
            ),
            trackedTrigger(
                """
            CREATE TRIGGER postcards_fts_delete
            AFTER DELETE ON postcards
            BEGIN
                DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'postcard';
            END
            """,
            ),
        )
}
