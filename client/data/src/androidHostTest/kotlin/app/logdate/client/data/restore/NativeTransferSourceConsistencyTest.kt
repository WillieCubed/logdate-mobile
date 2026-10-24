package app.logdate.client.data.restore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeTransferSourceConsistencyTest {
    @Test
    fun `a live preference change does not invalidate the selected snapshot`() {
        val root = Files.createTempDirectory("native-transfer-preferences-").toFile()
        try {
            val pinned = root.resolve("pinned").apply { writeText("selected") }
            val live = root.resolve("live").apply { writeText("selected") }
            val checksum = nativeSnapshotChecksum(pinned)

            requirePinnedNativeTransferFile(pinned, checksum)
            live.writeText("changed")
            requirePinnedNativeTransferFile(pinned, checksum)

            pinned.writeText("tampered")
            assertFailsWith<IllegalStateException> { requirePinnedNativeTransferFile(pinned, checksum) }
        } finally {
            check(root.deleteRecursively())
        }
    }
}
