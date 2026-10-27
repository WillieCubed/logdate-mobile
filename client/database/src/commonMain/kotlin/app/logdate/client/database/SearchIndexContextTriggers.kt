package app.logdate.client.database

internal val searchIndexContextTriggers =
    listOf(
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER places_fts_soft_delete
        AFTER UPDATE ON places
        WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER places_fts_delete
        AFTER DELETE ON places
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'place';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER rewinds_fts_delete
        AFTER DELETE ON rewinds
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'rewind';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER stickers_fts_delete
        AFTER DELETE ON stickers
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'sticker';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER postcards_fts_delete
        AFTER DELETE ON postcards
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'postcard';
        END
        """,
        ),
        // Each audio_tag row becomes a searchable ambient_sound entry on
        // its parent note's uid. Multiple tags per note share the same
        // (uid, contentType) — FTS5 doesn't enforce uniqueness, so the
        // sound text accumulates across rows and a single search query
        // ranks the note by how many of its detected sounds match.
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_tags_fts_insert
        AFTER INSERT ON audio_tags
        BEGIN
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT NEW.noteId, NEW.soundName, audio_notes.created, 'ambient_sound'
            FROM audio_notes
            WHERE audio_notes.uid = NEW.noteId
              AND audio_notes.deletedAt IS NULL;
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_tags_fts_update
        AFTER UPDATE ON audio_tags
        BEGIN
            DELETE FROM entries_fts
            WHERE uid = OLD.noteId
              AND contentType = 'ambient_sound'
              AND content = OLD.soundName;
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT NEW.noteId, NEW.soundName, audio_notes.created, 'ambient_sound'
            FROM audio_notes
            WHERE audio_notes.uid = NEW.noteId
              AND audio_notes.deletedAt IS NULL;
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_tags_fts_delete
        AFTER DELETE ON audio_tags
        BEGIN
            DELETE FROM entries_fts
            WHERE uid = OLD.noteId
              AND contentType = 'ambient_sound'
              AND content = OLD.soundName;
        END
        """,
        ),
        // Cascade FTS cleanup with the parent audio note. The Room
        // foreign-key cascade fires the audio_tags_fts_delete trigger
        // for each tag row, but soft-delete (deletedAt) leaves the rows
        // in place — that path is handled here.
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_notes_audio_tags_fts_soft_delete
        AFTER UPDATE ON audio_notes
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'ambient_sound';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_notes_audio_tags_fts_restore
        AFTER UPDATE ON audio_notes
        WHEN NEW.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = NEW.uid AND contentType = 'ambient_sound';
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT audio_tags.noteId, audio_tags.soundName, NEW.created, 'ambient_sound'
            FROM audio_tags
            WHERE audio_tags.noteId = NEW.uid;
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_notes_audio_tags_fts_delete
        AFTER DELETE ON audio_notes
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'ambient_sound';
        END
        """,
        ),
    )
