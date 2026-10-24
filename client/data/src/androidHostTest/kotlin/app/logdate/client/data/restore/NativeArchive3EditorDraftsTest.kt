package app.logdate.client.data.restore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.journals.ArchiveDraftStorageState
import app.logdate.shared.model.EditorDraft
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class NativeArchive3EditorDraftsTest {
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val origin = "https://logdate.app"

    @Test
    fun `selected and anonymous copies of one draft are included once`() {
        val draft = EditorDraft(id = Uuid.parse("22222222-2222-2222-2222-222222222222"))
        val scope = "editor_drafts_scope_${owner.length}:$owner:${origin.length}:$origin"
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey(scope) to ArchiveDraftStorageState(drafts = listOf(draft)).encode(Json),
                stringPreferencesKey("editor_drafts_anonymous") to ArchiveDraftStorageState(drafts = listOf(draft)).encode(Json),
            )
        assertEquals(listOf(draft), NativeArchive3EditorDrafts.read(preferences, owner, true, origin))
    }

    @Test
    fun `foreign owner draft is never put into this transfer`() {
        val foreign = EditorDraft(id = Uuid.parse("33333333-3333-3333-3333-333333333333"))
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey("editor_drafts_scope_foreign") to ArchiveDraftStorageState(drafts = listOf(foreign)).encode(Json),
            )
        assertFailsWith<IllegalStateException> { NativeArchive3EditorDrafts.read(preferences, owner, true, origin) }
    }

    @Test
    fun `draft custody receipts cannot be dropped silently`() {
        val preferences =
            mutablePreferencesOf(
                stringPreferencesKey("editor_drafts_anonymous") to
                    ArchiveDraftStorageState(localDeletionIds = setOf("22222222-2222-2222-2222-222222222222")).encode(Json),
            )
        assertFailsWith<IllegalStateException> { NativeArchive3EditorDrafts.read(preferences, owner, false, origin) }
    }

    @Test
    fun `legacy editor draft remains in the source census`() {
        val draft = EditorDraft(id = Uuid.parse("44444444-4444-4444-4444-444444444444"))
        val preferences = mutablePreferencesOf(stringPreferencesKey("entry_drafts") to Json.encodeToString(listOf(draft)))
        assertEquals(listOf(draft), NativeArchive3EditorDrafts.read(preferences, owner, false, origin))
    }
}
