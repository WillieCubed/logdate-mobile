package app.logdate.client.data.restore

import app.logdate.client.database.LogDateDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal fun verifyEncryptedScope(
    files: Map<String, File>,
    database: LogDateDatabase,
    sourceScope: RetainedJournalSourceScope,
    preferenceFile: File,
) {
    if (files["source_scope.json"]?.readText()?.let {
            Json
                .parseToJsonElement(it)
                .jsonObject["version"]
                ?.jsonPrimitive
                ?.intOrNull
        } in setOf(2, 3, 4)
    ) {
        check(
            app.logdate.client.database
                .readNativeJournalSnapshotScope(database) ==
                app.logdate.client.database.NativeJournalSnapshotScope(
                    checkNotNull(sourceScope.ownerId),
                    sourceScope.bound,
                    sourceScope.backendOrigin,
                    nativeSnapshotChecksum(preferenceFile),
                ),
        ) { "Encrypted source scope does not match native preferences" }
    }
}
