package app.logdate.client.data.notes.drafts

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EntryDraftDecodingPrivacyTest {
    @Test
    fun `malformed retained drafts preserve bytes without exposing private input to any error boundary`() =
        runTest {
            val canary = "synthetic-private-draft-contents"
            val time = Instant.fromEpochMilliseconds(1234)
            val draft =
                EntryDraft(
                    Uuid.random(),
                    listOf(JournalNote.Text(content = canary, creationTimestamp = time, lastUpdated = time)),
                    time,
                    time,
                    pendingMedia = listOf(PendingMediaRecord(Uuid.random(), PendingMediaType.AUDIO, time)),
                )
            val valid = Json.encodeToString(draft)
            val payloads =
                listOf(
                    valid.dropLast(1) + ",\"futureSecret\":\"$canary\",\"broken\":}",
                    valid.replace("\"mediaType\":\"AUDIO\"", "\"mediaType\":\"$canary\""),
                    valid.replace(draft.id.toString(), canary),
                )
            val captured = mutableListOf<String>()
            val sink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        captured += message.orEmpty() + throwable?.stackTraceToString().orEmpty()
                    }
                }
            val violations = mutableSetOf<String>()
            Napier.base(sink)
            try {
                for (raw in payloads) {
                    val preferences = RecoveryPreferences()
                    val key = stringPreferencesKey(draft.id.toString())
                    preferences.edit { it[key] = raw }
                    val store = AndroidLocalEntryDraftStore(preferences)
                    val attempts =
                        listOf<suspend () -> Unit>(
                            { store.getDraft(draft.id) },
                            { store.getAllDrafts() },
                            { store.compareAndSetForRecovery(draft, draft.copy(markId = "repair")) { true } },
                        )
                    for (attempt in attempts) {
                        val failure = assertFailsWith<EntryDraftStorageException> { attempt() }
                        if (failure.cause != null) violations += "Original decoder cause retained"
                        if (canary in failure.stackTraceToString()) violations += "Private input escaped through thrown error"
                        if (preferences.data.first()[key] != raw) violations += "Retained bytes changed"
                    }
                }
                assertTrue(captured.isNotEmpty(), "The generic read failure should remain observable")
                if (captured.any { canary in it }) violations += "Private input escaped through logging"
                assertTrue(violations.isEmpty(), violations.joinToString())
            } finally {
                Napier.takeLogarithm(sink)
            }
        }
}
