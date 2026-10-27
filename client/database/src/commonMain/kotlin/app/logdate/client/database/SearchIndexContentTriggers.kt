package app.logdate.client.database

internal val searchIndexContentTriggers =
    listOf(
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER text_notes_fts_insert
        AFTER INSERT ON text_notes
        WHEN NEW.deletedAt IS NULL AND NEW.content_format = 'text'
        BEGIN
            INSERT INTO entries_fts(uid, content, created, contentType)
            VALUES (NEW.uid, NEW.content, NEW.created, 'text_note');
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER text_notes_fts_content_update
        AFTER UPDATE ON text_notes
        WHEN NEW.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid;
            INSERT INTO entries_fts(uid, content, created, contentType)
            SELECT NEW.uid, NEW.content, NEW.created, 'text_note' WHERE NEW.content_format = 'text';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER text_notes_fts_soft_delete
        AFTER UPDATE ON text_notes
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid;
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER text_notes_fts_delete
        AFTER DELETE ON text_notes
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid;
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER transcriptions_fts_delete
        AFTER DELETE ON transcriptions
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'transcription';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_notes_transcriptions_fts_soft_delete
        AFTER UPDATE ON audio_notes
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'transcription';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER audio_notes_transcriptions_fts_delete
        AFTER DELETE ON audio_notes
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'transcription';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER journals_fts_soft_delete
        AFTER UPDATE ON journals
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER journals_fts_delete
        AFTER DELETE ON journals
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.id AND contentType = 'journal';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER media_captions_fts_delete
        AFTER DELETE ON media_captions
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.noteId AND contentType = 'media_caption';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER image_notes_media_captions_fts_soft_delete
        AFTER UPDATE ON image_notes
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER image_notes_media_captions_fts_delete
        AFTER DELETE ON image_notes
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
        END
        """,
        ),
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER video_notes_media_captions_fts_soft_delete
        AFTER UPDATE ON video_notes
        WHEN NEW.deletedAt IS NOT NULL AND OLD.deletedAt IS NULL
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
        END
        """,
        ),
        trackedSearchIndexTrigger(
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
        trackedSearchIndexTrigger(
            """
        CREATE TRIGGER video_notes_media_captions_fts_delete
        AFTER DELETE ON video_notes
        BEGIN
            DELETE FROM entries_fts WHERE uid = OLD.uid AND contentType = 'media_caption';
        END
        """,
        ),
    )

internal fun trackedSearchIndexTrigger(definition: String): String {
    val trimmed = definition.trimIndent()
    val endIndex = trimmed.lastIndexOf("\nEND")
    check(endIndex >= 0) { "Invalid trigger definition: missing END" }

    return buildString {
        append(trimmed.substring(0, endIndex))
        append("\n    ")
        append("UPDATE search_index_metadata SET generation = generation + 1 WHERE id = 1;")
        append(trimmed.substring(endIndex))
    }
}
