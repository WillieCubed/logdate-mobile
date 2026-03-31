@file:Suppress("ktlint:standard:filename")

package app.logdate.client.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Expands the FTS5 search index to cover journals, media captions, places,
 * rewinds, stickers, and postcards.
 *
 * Populates existing data from each table and creates CRUD triggers to keep
 * the FTS index in sync. All operations are guarded by an entries_fts
 * existence check.
 */
val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            val hasFtsTable =
                connection
                    .prepare(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='entries_fts'",
                    ).use { stmt ->
                        stmt.step()
                        stmt.getLong(0) > 0
                    }

            if (!hasFtsTable) return

            // ── Populate FTS with existing data ───────────────────────

            // Journals (title + description)
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT id, title || ' ' || COALESCE(description, ''), created, 'journal'
                FROM journals
                WHERE deletedAt IS NULL
                """.trimIndent(),
            )

            // Media captions
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT noteId, caption, 0, 'media_caption'
                FROM media_captions
                """.trimIndent(),
            )

            // Places (name + description)
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT id, name || ' ' || COALESCE(description, ''), 0, 'place'
                FROM places
                WHERE deleted_at IS NULL
                """.trimIndent(),
            )

            // Rewinds
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT uid, title, generationDate, 'rewind'
                FROM rewinds
                WHERE title IS NOT NULL
                """.trimIndent(),
            )

            // Stickers (only labeled)
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT id, label, created_at, 'sticker'
                FROM stickers
                WHERE label IS NOT NULL
                """.trimIndent(),
            )

            // Postcards
            connection.execSQL(
                """
                INSERT INTO entries_fts(uid, content, created, contentType)
                SELECT id, title, created_at, 'postcard'
                FROM postcards
                WHERE title IS NOT NULL
                """.trimIndent(),
            )

            // ── Journals triggers ─────────────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER journals_fts_insert
                AFTER INSERT ON journals
                WHEN NEW.deletedAt IS NULL
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.title || ' ' || COALESCE(NEW.description, ''), NEW.created, 'journal');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER journals_fts_content_update
                AFTER UPDATE ON journals
                WHEN NEW.deletedAt IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.title || ' ' || COALESCE(NEW.description, ''), NEW.created, 'journal');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER journals_fts_soft_delete
                AFTER UPDATE ON journals
                WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER journals_fts_delete
                AFTER DELETE ON journals
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
                END
                """.trimIndent(),
            )

            // ── Media captions triggers ───────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER media_captions_fts_insert
                AFTER INSERT ON media_captions
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.noteId, NEW.caption, 0, 'media_caption');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER media_captions_fts_update
                AFTER UPDATE ON media_captions
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'media_caption';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.noteId, NEW.caption, 0, 'media_caption');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER media_captions_fts_delete
                AFTER DELETE ON media_captions
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'media_caption';
                END
                """.trimIndent(),
            )

            // ── Places triggers ───────────────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER places_fts_insert
                AFTER INSERT ON places
                WHEN NEW.deleted_at IS NULL
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.name || ' ' || COALESCE(NEW.description, ''), 0, 'place');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER places_fts_content_update
                AFTER UPDATE ON places
                WHEN NEW.deleted_at IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.name || ' ' || COALESCE(NEW.description, ''), 0, 'place');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER places_fts_soft_delete
                AFTER UPDATE ON places
                WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER places_fts_delete
                AFTER DELETE ON places
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
                END
                """.trimIndent(),
            )

            // ── Rewinds triggers ──────────────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER rewinds_fts_insert
                AFTER INSERT ON rewinds
                WHEN NEW.title IS NOT NULL
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.uid, NEW.title, NEW.generationDate, 'rewind');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER rewinds_fts_update
                AFTER UPDATE ON rewinds
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'rewind';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    SELECT NEW.uid, NEW.title, NEW.generationDate, 'rewind'
                    WHERE NEW.title IS NOT NULL;
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER rewinds_fts_delete
                AFTER DELETE ON rewinds
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'rewind';
                END
                """.trimIndent(),
            )

            // ── Stickers triggers ─────────────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER stickers_fts_insert
                AFTER INSERT ON stickers
                WHEN NEW.label IS NOT NULL
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.label, NEW.created_at, 'sticker');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER stickers_fts_update
                AFTER UPDATE ON stickers
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'sticker';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    SELECT NEW.id, NEW.label, NEW.created_at, 'sticker'
                    WHERE NEW.label IS NOT NULL;
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER stickers_fts_delete
                AFTER DELETE ON stickers
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'sticker';
                END
                """.trimIndent(),
            )

            // ── Postcards triggers ────────────────────────────────────

            connection.execSQL(
                """
                CREATE TRIGGER postcards_fts_insert
                AFTER INSERT ON postcards
                WHEN NEW.title IS NOT NULL
                BEGIN
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    VALUES (NEW.id, NEW.title, NEW.created_at, 'postcard');
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER postcards_fts_update
                AFTER UPDATE ON postcards
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'postcard';
                    INSERT INTO entries_fts(uid, content, created, contentType)
                    SELECT NEW.id, NEW.title, NEW.created_at, 'postcard'
                    WHERE NEW.title IS NOT NULL;
                END
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE TRIGGER postcards_fts_delete
                AFTER DELETE ON postcards
                BEGIN
                    DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'postcard';
                END
                """.trimIndent(),
            )
        }
    }
