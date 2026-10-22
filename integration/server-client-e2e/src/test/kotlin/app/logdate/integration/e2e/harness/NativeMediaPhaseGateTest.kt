package app.logdate.integration.e2e.harness

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NativeMediaPhaseGateTest {
    @Test
    fun `both media gates preserve a worker across the bounded host and app handoff`() =
        runTest {
            for (prefix in listOf("media", "media-upload")) {
                val directory = Files.createTempDirectory("native-policy-handoff-gate-test")
                try {
                    val gate = NativeMediaPhaseGate(directory)
                    Files.createFile(directory.resolve("$prefix-gate-arm"))
                    val held = async { gate.beforeRequest("/api/v1/drafts/changes") }
                    runCurrent()
                    assertTrue(Files.isRegularFile(directory.resolve("$prefix-gate-held")))
                    advanceTimeBy(120_000)
                    runCurrent()
                    assertFalse(held.isCompleted)
                    Files.createFile(directory.resolve("$prefix-gate-release"))
                    held.await()
                } finally {
                    directory.toFile().deleteRecursively()
                }
            }
        }

    @Test
    fun `original upload gets an independent gate after the download gate was consumed`() =
        runTest {
            val directory = Files.createTempDirectory("native-upload-gate-test")
            try {
                val gate = NativeMediaPhaseGate(directory)
                Files.createFile(directory.resolve("media-gate-arm"))
                Files.createFile(directory.resolve("media-gate-held"))
                Files.createFile(directory.resolve("media-gate-release"))
                Files.createFile(directory.resolve("media-upload-gate-arm"))
                val held = async { gate.beforeRequest("/api/v1/drafts/changes") }
                runCurrent()
                assertTrue(Files.isRegularFile(directory.resolve("media-upload-gate-held")))
                assertFalse(held.isCompleted)
                Files.createFile(directory.resolve("media-upload-gate-release"))
                held.await()
                gate.beforeRequest("/api/v1/drafts/changes")
            } finally {
                directory.toFile().deleteRecursively()
            }
        }

    @Test
    fun `only the armed post-media request waits for explicit host release`() =
        runTest {
            val directory = Files.createTempDirectory("native-media-gate-test")
            try {
                val gate = NativeMediaPhaseGate(directory)
                gate.beforeRequest("/api/v1/drafts/changes")
                assertFalse(Files.exists(directory.resolve("media-gate-held")))
                Files.createFile(directory.resolve("media-gate-arm"))
                gate.beforeRequest("/api/v1/contents/changes")
                assertFalse(Files.exists(directory.resolve("media-gate-held")))
                val held = async { gate.beforeRequest("/api/v1/drafts/changes") }
                runCurrent()
                assertTrue(Files.isRegularFile(directory.resolve("media-gate-held")))
                assertFalse(held.isCompleted)
                Files.createFile(directory.resolve("media-gate-release"))
                held.await()
                gate.beforeRequest("/api/v1/drafts/changes")
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
}
