package app.logdate.client.media

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.media.storage.AndroidCanonicalMediaStore
import app.logdate.client.media.storage.AndroidMediaClaimFence
import app.logdate.client.media.storage.AndroidMediaPublicationTickets
import app.logdate.client.media.storage.MediaClaimFence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AndroidCanonicalMediaClaimOrderPlatformTest {
    @Test
    fun `ordinary publication cannot hold publication lock while recovery owns claim`() =
        runBlocking {
            check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            check(context.packageName.endsWith(".debug"))
            val root = File(context.cacheDir, "canonical-claim-order-${Uuid.random()}").apply { check(mkdirs()) }
            try {
                val files = File(root, "files").apply { mkdirs() }
                File(files, "media").mkdirs()
                val claim = AndroidMediaClaimFence(File(root, "private").apply { mkdirs() })
                val ordinaryAttempt = CompletableDeferred<Unit>()
                val recoveryClaimed = CompletableDeferred<Unit>()
                val observed = ObservedFence(claim, ordinaryAttempt)
                val tickets = AndroidMediaPublicationTickets(File(root, "private"), observed)
                val store = AndroidCanonicalMediaStore(files, publicationTickets = tickets)
                val recoveredBytes = "retained recovery original".encodeToByteArray()
                val ordinaryBytes = "concurrent ordinary original".encodeToByteArray()
                val paths =
                    withTimeout(5_000) {
                        val recovery =
                            async(CoroutineName("recovery")) {
                                claim.withClaim {
                                    recoveryClaimed.complete(Unit)
                                    ordinaryAttempt.await()
                                    store.store(recoveredBytes.inputStream(), "image/jpeg", recoveredBytes.size.toLong())
                                }
                            }
                        recoveryClaimed.await()
                        val ordinary =
                            async(CoroutineName("ordinary")) {
                                store.store(ordinaryBytes.inputStream(), "image/jpeg", ordinaryBytes.size.toLong())
                            }
                        recovery.await() to ordinary.await()
                    }
                assertContentEquals(recoveredBytes, File(URI(paths.first)).readBytes())
                assertContentEquals(ordinaryBytes, File(URI(paths.second)).readBytes())
                assertEquals(setOf(paths.first, paths.second), tickets.pending().mapNotNull { it.reference }.toSet())
            } finally {
                check(root.deleteRecursively())
            }
        }

    private class ObservedFence(
        private val delegate: MediaClaimFence,
        private val ordinaryAttempt: CompletableDeferred<Unit>,
    ) : MediaClaimFence {
        override suspend fun <T> withClaim(block: suspend () -> T): T {
            if (currentCoroutineContext()[CoroutineName]?.name == "ordinary") ordinaryAttempt.complete(Unit)
            return delegate.withClaim(block)
        }

        override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = delegate.withExclusiveReclamation(block)
    }
}
