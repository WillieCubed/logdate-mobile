package app.logdate.client.sync

import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPauseTest {
    @Test
    fun `a sync asked for while paused waits until the pause ends`() =
        runTest {
            val session = fakeSessionStorage(authenticated = false)
            val manager = testDefaultSyncManager(sessionStorage = session, syncMetadataService = fakeSyncMetadataService(session))
            val release = CompletableDeferred<Unit>()
            val paused = CompletableDeferred<Unit>()

            launch {
                manager.whilePaused {
                    paused.complete(Unit)
                    release.await()
                }
            }
            paused.await()
            val upload = async { manager.uploadPendingChanges() }
            repeat(5) { yield() }

            assertFalse(upload.isCompleted, "An upload must not run while sync is paused")

            release.complete(Unit)
            upload.await()
            assertTrue(upload.isCompleted)
        }
}
