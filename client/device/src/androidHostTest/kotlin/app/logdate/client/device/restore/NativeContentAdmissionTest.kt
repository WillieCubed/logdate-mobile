package app.logdate.client.device.restore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeContentAdmissionTest {
    @Test
    fun `first local content cannot enter while restore ownership is being committed`() =
        runTest {
            val directory = Files.createTempDirectory("native-content-admission").toFile()
            try {
                val content = directory.resolve("media")
                val committing = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val commit =
                    async {
                        nativeRestoreDeliveryGate().withLock {
                            committing.complete(Unit)
                            release.await()
                            assertFalse(content.exists())
                        }
                    }
                committing.await()
                val admission = async { admitNativeContent(content) }
                runCurrent()
                assertFalse(admission.isCompleted)
                assertFalse(content.exists())
                release.complete(Unit)
                commit.await()
                admission.await()
                assertTrue(content.isDirectory)
            } finally {
                directory.deleteRecursively()
            }
        }
}
