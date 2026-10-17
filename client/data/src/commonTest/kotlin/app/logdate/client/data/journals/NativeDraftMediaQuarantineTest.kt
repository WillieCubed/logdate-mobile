package app.logdate.client.data.journals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeDraftMediaQuarantineTest {
    @Test
    fun `signed-out quarantine repairs every retained owner without changing receipts or timestamps`() =
        runTest {
            val storage =
                object : DataStore<Preferences> {
                    override val data = MutableStateFlow(emptyPreferences())

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        transform(data.value).also {
                            data.value =
                                it
                        }
                }
            val values = DataStoreKeyValueStorage(storage)
            val a = DraftStorageScope("a", "https://a.example")
            val b = DraftStorageScope("b", "https://b.example")
            var selected: DraftStorageScope? = a
            val repository = LocalFirstDraftRepository(values, Json, currentScope = { selected })
            val source = "content://media/external/video/media/123"
            val missingPrivate = "file:///data/user/0/studio.hypertext.logdate/files/media/objects/draft-missing.mp4"
            val block = SerializableVideoBlock(Uuid.random(), Instant.fromEpochMilliseconds(1), uri = source, caption = "Retained")
            val missingBlock = SerializableVideoBlock(Uuid.random(), Instant.fromEpochMilliseconds(2), uri = missingPrivate)
            val draft = EditorDraft(blocks = listOf(block, missingBlock))
            repository.saveDraftFromSync(draft)
            val key = "editor_drafts_scope_${a.ownerId.length}:${a.ownerId}:${a.serverOrigin.length}:${a.serverOrigin}"
            values.putStringOrThrow(key, ArchiveDraftStorageState(listOf(draft), setOf("retained-receipt")).encode(Json))
            selected = b
            repository.saveDraftFromSync(draft)
            selected = null
            repository.saveDraftFromSync(draft)
            val damagedKey = "editor_drafts_scope_7:damaged:17:https://b.example"
            val damaged =
                Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EditorDraft.serializer()), listOf(draft)).dropLast(1) +
                    ",{\"id\":\"partial"
            values.putStringOrThrow(damagedKey, damaged)
            val before = storage.data.first()
            val repair = NativeDraftMediaQuarantine(storage, Json)
            assertFailsWith<IllegalStateException> { repair.run({ false }) }
            assertEquals(before, storage.data.first())
            repair.run({ true }) { reference ->
                when (reference) {
                    source, missingPrivate -> UnavailableMediaReferences.nativeRestore(reference)
                    else -> null
                }
            }
            val expected =
                draft.copy(
                    blocks =
                        listOf(
                            block.copy(uri = UnavailableMediaReferences.nativeRestore(source)),
                            missingBlock.copy(uri = UnavailableMediaReferences.nativeRestore(missingPrivate)),
                        ),
                )
            for (scope in listOf(null, a, b)) {
                selected = scope
                assertEquals(listOf(expected), repository.getAllDraftsForSync())
            }
            assertEquals(setOf("retained-receipt"), ArchiveDraftStorageState.decode(Json, values.getStringOrThrow(key)).appliedRecoveries)
            assertEquals(damaged, values.getStringOrThrow("editor_drafts_native_unparsed_$damagedKey"))
            val salvage = ArchiveDraftStorageState.decode(Json, values.getStringOrThrow(damagedKey))
            assertEquals(listOf(expected), salvage.drafts)
            val repaired = storage.data.first()
            repair.run({ true }) { reference ->
                when (reference) {
                    source, missingPrivate -> UnavailableMediaReferences.nativeRestore(reference)
                    else -> null
                }
            }
            assertEquals(repaired, storage.data.first())
        }
}
