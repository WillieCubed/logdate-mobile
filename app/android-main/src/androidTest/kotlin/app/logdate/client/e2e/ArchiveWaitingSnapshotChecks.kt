package app.logdate.client.e2e

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.restore.RecoveryMediaRole
import app.logdate.client.domain.restore.UnavailableMediaAsset
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.core.restore.ArchiveMediaRecoveryWorker
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Settle legitimate recovery before the backup baseline; never waive later checkpoint changes. */
internal suspend fun verifyRetainedArchiveWaitingState(
    context: Context,
    state: JSONObject,
    notes: JournalNotesRepository,
    journals: JournalRepository,
    settleSourceWork: Boolean,
    verifyEdits: suspend (JSONObject) -> Unit,
) {
    val recoveryId = Uuid.parse(state.getString("recoveryId")).toString()
    val worker =
        TestListenableWorkerBuilder<ArchiveMediaRecoveryWorker>(context)
            .setInputData(Data.Builder().putString(RestoreWorker.RECOVERY_ID_KEY, recoveryId).build())
            .build()
    assertIs<ListenableWorker.Result.Success>(worker.doWork())
    if (settleSourceWork) awaitArchiveSourceWork(context, recoveryId)
    val record = JSONObject(context.filesDir.resolve("archive_recovery/records/$recoveryId.json").readText())
    val json = Json { ignoreUnknownKeys = true }
    val retained = json.decodeFromString<RestoreSummary>(record.getJSONObject("summary").toString())
    assertEquals(2, retained.unavailableOriginals)
    assertEquals(0, retained.unavailablePreviews)
    assertFalse(retained.recoveringMedia)
    assertEquals(2, retained.mediaRecoveryChecked)
    assertEquals(2, retained.mediaRecoveryTotal)
    val pending = json.decodeFromString<List<UnavailableMediaAsset>>(record.getJSONArray("pending").toString())
    assertEquals(2, pending.size)
    assertTrue(pending.all { it.role == RecoveryMediaRole.ORIGINAL })
    val photo = assertIs<JournalNote.Image>(notes.getNoteById(Uuid.parse(state.getString("note"))))
    val draft = assertNotNull(journals.getDraftForRestore(Uuid.parse(state.getString("draft"))))
    val video = draft.blocks.filterIsInstance<SerializableVideoBlock>().single()
    val references = setOf(photo.mediaRef, assertNotNull(video.uri))
    assertEquals(2, references.size)
    assertTrue(references.all { it.startsWith("logdate-unavailable://archive/$recoveryId/") })
    assertEquals(references, pending.map { it.reference }.toSet())
    verifyEdits(state)
}

private suspend fun awaitArchiveSourceWork(
    context: Context,
    recoveryId: String,
) {
    val manager = WorkManager.getInstance(context)
    withTimeout(30_000) {
        manager.getWorkInfosForUniqueWorkFlow("archive_media_recovery_$recoveryId").first { work ->
            check(work.none { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }) {
                "Source archive media work failed or was cancelled before the backup baseline"
            }
            work.isNotEmpty() && work.all { it.state == WorkInfo.State.SUCCEEDED }
        }
    }
}
