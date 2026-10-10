package app.logdate.client.handoff

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.feature.editor.handoff.DraftRefreshResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDraftRefresherTest {
    @Test
    fun `a draft sync that ran counts as fetched`() {
        assertEquals(DraftRefreshResult.Refreshed, SyncResult(success = true, downloadedItems = 1).toRefreshResult())
    }

    @Test
    fun `a draft sync stopped before reaching the server means backup is unavailable here`() {
        assertEquals(DraftRefreshResult.Unavailable, SyncResult(success = false).toRefreshResult())
    }

    @Test
    fun `a failed draft sync keeps its errors as the reason`() {
        val result =
            SyncResult(
                success = false,
                errors =
                    listOf(
                        SyncError(SyncErrorType.NETWORK_ERROR, "The server could not be reached"),
                        SyncError(SyncErrorType.SERVER_ERROR, "Drafts are temporarily unavailable"),
                    ),
            )

        assertEquals(
            DraftRefreshResult.Failed("The server could not be reached; Drafts are temporarily unavailable"),
            result.toRefreshResult(),
        )
    }
}
