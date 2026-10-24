package app.logdate.client.database

import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Lives inside the authenticated encrypted export; it never relabels live multi-server rows. */
data class NativeJournalSnapshotScope(
    val ownerId: String,
    val ownerBound: Boolean,
    val backendOrigin: String,
    val preferencesSha256: String,
)

internal fun writeNativeJournalSnapshotScope(
    database: SQLiteDatabase,
    scope: NativeJournalSnapshotScope,
) {
    database.execSQL(
        "CREATE TABLE IF NOT EXISTS native_journal.native_journal_source_scope " +
            "(id INTEGER PRIMARY KEY CHECK(id = 1), version INTEGER NOT NULL CHECK(version = 1), " +
            "ownerId TEXT NOT NULL, ownerBound INTEGER NOT NULL CHECK(ownerBound IN (0, 1)), " +
            "backendOrigin TEXT NOT NULL, preferencesSha256 TEXT NOT NULL)",
    )
    database.execSQL("DELETE FROM native_journal.native_journal_source_scope")
    database.execSQL(
        "INSERT INTO native_journal.native_journal_source_scope " +
            "(id, version, ownerId, ownerBound, backendOrigin, preferencesSha256) VALUES (1, 1, ?, ?, ?, ?)",
        arrayOf<Any>(scope.ownerId, if (scope.ownerBound) 1 else 0, scope.backendOrigin, scope.preferencesSha256),
    )
}

internal fun readNativeJournalSnapshotScope(database: SQLiteDatabase): NativeJournalSnapshotScope =
    database.rawQuery(READ_SCOPE, emptyArray<String>()).use {
        check(it.moveToFirst())
        NativeJournalSnapshotScope(it.getString(0), it.getInt(1) == 1, it.getString(2), it.getString(3))
            .also { _ -> check(!it.moveToNext()) }
    }

fun readNativeJournalSnapshotScope(database: LogDateDatabase): NativeJournalSnapshotScope =
    database.openHelper.readableDatabase.query(READ_SCOPE).use {
        check(it.moveToFirst())
        NativeJournalSnapshotScope(it.getString(0), it.getInt(1) == 1, it.getString(2), it.getString(3))
            .also { _ -> check(!it.moveToNext()) }
    }

private const val READ_SCOPE =
    "SELECT ownerId, ownerBound, backendOrigin, preferencesSha256 FROM native_journal_source_scope WHERE id = 1 AND version = 1"
