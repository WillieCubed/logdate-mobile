package app.logdate.wear.sync.v2.removal

import app.logdate.client.device.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WatchRemovalWriteRaceTest {
    @Test
    fun `prepare waits for an in-flight capture and refuses a nonempty outbox`() =
        runTest {
            var marker: String? = null
            var pending = 0
            val secure = mockk<SecureStorage>()
            coEvery { secure.getString(any()) } answers { marker }
            coEvery { secure.putString(any(), any()) } answers { marker = secondArg() }
            val lock = WatchRemovalLock(secure)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val capture =
                async {
                    lock.write {
                        entered.complete(Unit)
                        release.await()
                        pending++
                    }
                }
            entered.await()
            val prepare = async { lock.prepare("link") { pending } }
            runCurrent()
            release.complete(Unit)
            capture.await()

            assertEquals(1, prepare.await())
            assertNull(lock.state())
            pending = 0
            assertEquals(0, lock.prepare("link") { pending })
            assertFailsWith<IllegalStateException> { lock.write { pending++ } }
            assertEquals(0, pending)
        }

    @Test
    fun `erasure waits for an existing capture then rejects every late capture`() =
        runTest {
            var marker: String? = null
            val secure = mockk<SecureStorage>()
            coEvery { secure.getString(any()) } answers { marker }
            coEvery { secure.putString(any(), any()) } answers { marker = secondArg() }
            val lock = WatchRemovalLock(secure)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val rows = mutableListOf<String>()
            val capture =
                async {
                    lock.write {
                        entered.complete(Unit)
                        release.await()
                        rows += "capture"
                    }
                }
            entered.await()
            val wipe =
                async {
                    lock.erase {
                        lock.mark()
                        rows.clear()
                        lock.finish()
                    }
                }
            runCurrent()
            release.complete(Unit)
            capture.await()
            wipe.await()
            assertEquals(emptyList(), rows)
            assertFailsWith<IllegalStateException> { lock.write { rows += "late" } }
            lock.ingest { rows += "late phone state" }
            assertEquals(emptyList(), rows)
        }
}
