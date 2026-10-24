package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.media.ConditionalMediaSaver
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeLateMediaTargetsTest {
    @Test
    fun `entry draft scope is rechecked inside main preferences transaction`() =
        runTest {
            val changedScopes =
                listOf(
                    "identity.canonical_owner_id" to OTHER,
                    "backend_url" to "https://other.example",
                    "identity.canonical_owner_bound" to "false",
                )
            for (changed in changedScopes) {
                val f = Fixture()
                val target = f.targets().read().single()
                f.preferences.beforeUpdate = {
                    f.preferences.value =
                        f.preferences.value
                            .toMutablePreferences()
                            .apply { this[stringPreferencesKey(changed.first)] = changed.second }
                }
                assertFailsWith<IllegalStateException> { target.commit(mapOf(UNAVAILABLE to ORIGINAL)) }
                assertEquals(0, f.commits)
            }
        }

    @Test
    fun `entry draft recovery commits unchanged snapshot without changing edit base or timestamps`() =
        runTest {
            val f = Fixture()
            assertTrue(
                f
                    .targets()
                    .read()
                    .single()
                    .commit(mapOf(UNAVAILABLE to ORIGINAL)),
            )
            assertEquals(f.draft.withLateMedia(mapOf(UNAVAILABLE to ORIGINAL)), f.saved)
            assertEquals(f.draft.createdAt, f.saved?.createdAt)
            assertEquals(f.draft.updatedAt, f.saved?.updatedAt)
            assertEquals(f.draft.editBase?.scope, f.saved?.editBase?.scope)
        }

    @Test
    fun `editor draft repairs video and thumbnail together while preserving text metadata and selection`() {
        val draft =
            EditorDraft(
                blocks =
                    listOf(
                        SerializableVideoBlock(
                            Uuid.random(),
                            TIME,
                            uri = UNAVAILABLE,
                            thumbnailUri = UNAVAILABLE,
                            caption = "kept caption",
                        ),
                    ),
                selectedJournalIds = listOf(Uuid.random()),
                createdAt = TIME,
                lastModifiedAt = TIME,
            )
        val next = draft.withLateMedia(mapOf(UNAVAILABLE to ORIGINAL))
        assertEquals(
            draft.copy(blocks = listOf((draft.blocks.single() as SerializableVideoBlock).copy(uri = ORIGINAL, thumbnailUri = ORIGINAL))),
            next,
        )
        assertEquals(setOf(UNAVAILABLE), draft.lateMediaReferences())
    }

    @Test
    fun `note hydration changes only matching attachment and preserves capture and transcript fields`() {
        val note =
            JournalNote.Audio(
                mediaRef = UNAVAILABLE,
                creationTimestamp = TIME,
                lastUpdated = TIME,
                durationMs = 1234,
                caption = "caption",
                transcription = "words",
                timeZoneId = "UTC",
            )
        assertEquals(note.copy(mediaRef = ORIGINAL), note.withLateMedia(mapOf(UNAVAILABLE to ORIGINAL)))
        assertEquals(note, note.withLateMedia(mapOf("content://unrelated" to ORIGINAL)))
    }

    @Test
    fun `native original import requires conditional publication and preserves the exact live predicate`() =
        runTest {
            val asset = RetainedManagedMediaSource.Asset(File("unused-fixture-source"), "original.png", "image/png")
            val unsupported = lateMediaRepository<MediaManager> { _, _ -> error("Unconditional media import is forbidden") }
            assertFailsWith<IllegalStateException> { importNativeLateMediaOriginal(unsupported, asset) { true } }
            var allowed = false
            var publications = 0
            val canCommit = { allowed }
            val media =
                Proxy.newProxyInstance(
                    MediaManager::class.java.classLoader,
                    arrayOf(MediaManager::class.java, ConditionalMediaSaver::class.java),
                ) { _, method, args ->
                    val arguments = checkNotNull(args)
                    check(method.name == "saveMediaFromFile" && arguments.size == 5)
                    assertTrue(arguments[3] === canCommit)
                    check((arguments[3] as Function0<*>).invoke() == true)
                    publications++
                    ORIGINAL
                } as MediaManager
            assertFailsWith<IllegalStateException> { importNativeLateMediaOriginal(media, asset, canCommit) }
            assertEquals(0, publications)
            allowed = true
            assertEquals(ORIGINAL, importNativeLateMediaOriginal(media, asset, canCommit))
            assertEquals(1, publications)
        }

    @Test
    fun `late media source scope refuses account switch before reading or publishing targets`() =
        runTest {
            val f = Fixture()
            assertFailsWith<IllegalStateException> { f.targets(RetainedJournalSourceScope(OTHER, true, ORIGIN)).read() }
            assertFailsWith<IllegalStateException> { f.targets(RetainedJournalSourceScope(OWNER, true, "https://other.example")).read() }
            assertEquals(0, f.commits)
            assertEquals(null, f.saved)
        }

    private class Fixture {
        val preferences = ScopePreferences()
        val scope = JournalMergeScope(OWNER, ORIGIN)
        val note = JournalNote.Image(creationTimestamp = TIME, lastUpdated = TIME, mediaRef = UNAVAILABLE, caption = "kept")
        val draft = EntryDraft(Uuid.random(), listOf(note), TIME, TIME, editBase = EntryEditBase(note, scope))
        var commits = 0
        var saved: EntryDraft? = null
        val journals =
            lateMediaRepository<JournalRepository> { name, _ ->
                when (name) {
                    "getJournalRecoveryScope" -> scope
                    "getDraftRecoveryScope" -> DraftRecoveryScope(OWNER, ORIGIN)
                    "getAllDraftsForSync" -> emptyList<EditorDraft>()
                    "getAllJournalsObserved" -> flowOf(emptyList<app.logdate.shared.model.Journal>())
                    else -> error("Unexpected journal call: $name")
                }
            }
        val notes =
            lateMediaRepository<JournalNotesRepository> { name, _ ->
                when (name) {
                    "getNoteRecoveryScope" -> scope
                    "getAllNotesObserved" -> flowOf(emptyList<JournalNote>())
                    else -> error("Unexpected notes call: $name")
                }
            }
        val entryDrafts =
            object : EntryDraftRecoveryRepository {
                override suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft> = listOf(draft)

                override suspend fun compareAndSetDraftForRecovery(
                    expected: EntryDraft,
                    replacement: EntryDraft,
                    canCommit: () -> Boolean,
                ): Boolean {
                    assertEquals(draft, expected)
                    assertTrue(canCommit())
                    saved = replacement
                    commits++
                    return true
                }

                override suspend fun compareAndSetDraftForScopedRecovery(
                    expected: EntryDraft,
                    replacement: EntryDraft,
                    canCommit: () -> Boolean,
                    scopeFence: suspend (commit: suspend () -> Boolean) -> Boolean,
                ): Boolean = scopeFence { compareAndSetDraftForRecovery(expected, replacement, canCommit) }
            }

        fun targets(sourceScope: RetainedJournalSourceScope = RetainedJournalSourceScope(OWNER, true, ORIGIN)) =
            NativeLateMediaTargets(
                NativeLateMediaRepositories(
                    journals,
                    notes,
                    entryDrafts,
                    preferences,
                    lateMediaRepository {
                        name,
                        _,
                        ->
                        error("Unexpected media call: $name")
                    },
                    app.logdate.client.media.storage.StoredMediaReferences.Unchanged,
                ),
                scope,
                RetainedJournalSourceScope(OWNER, true, ORIGIN),
                sourceScope,
            ) { true }
    }

    private class ScopePreferences : DataStore<Preferences> {
        var value: Preferences =
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to OWNER,
                stringPreferencesKey("identity.canonical_owner_bound") to "true",
                stringPreferencesKey("backend_url") to ORIGIN,
            )
        var beforeUpdate: () -> Unit = {}
        override val data: Flow<Preferences> get() = flow { emit(value) }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeUpdate()
            return transform(value).also { value = it }
        }
    }

    private companion object {
        const val OWNER = "11111111-1111-1111-1111-111111111111"
        const val OTHER = "22222222-2222-2222-2222-222222222222"
        const val ORIGIN = "https://cloud.example"
        const val ORIGINAL =
            "logdate-media://library/objects/sha256/aa/" + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png"
        val UNAVAILABLE = UnavailableMediaReferences.nativeRestore(ORIGINAL)
        val TIME = Instant.parse("2026-10-23T00:00:00Z")
    }
}

private inline fun <reified T> lateMediaRepository(crossinline call: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        call(method.name, args ?: emptyArray())
    } as T
