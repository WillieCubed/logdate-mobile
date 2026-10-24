package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.data.notes.drafts.LocalEntryDraftStore
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.media.storage.MediaOwnerCensus
import kotlinx.coroutines.flow.first

/** Reads Room and local draft owners together; failures intentionally abort collection. */
class AndroidMediaOwnerCensus(
    private val database: LogDateDatabase,
    private val mainPreferences: DataStore<Preferences>,
    private val entryDrafts: LocalEntryDraftStore,
) : MediaOwnerCensus {
    override suspend fun references(): Set<String> =
        buildSet {
            addAll(
                collectAndroidMediaOwnerReferences { sql ->
                    database.openHelper.readableDatabase.query(sql).use { cursor ->
                        buildList { while (cursor.moveToNext()) if (!cursor.isNull(0)) add(cursor.getString(0)) }
                    }
                },
            )
            addAll(
                nativeTransferDraftMediaReferences(
                    mainPreferences.data.first(),
                    entryDrafts.getAllDrafts(),
                ),
            )
        }
}
