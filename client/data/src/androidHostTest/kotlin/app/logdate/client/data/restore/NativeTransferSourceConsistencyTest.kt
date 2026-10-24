package app.logdate.client.data.restore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeTransferSourceConsistencyTest {
    @Test
    fun `live preferences must still match the selected snapshot`() {
        val root = Files.createTempDirectory("native-transfer-preferences-").toFile()
        try {
            val pinned = root.resolve("pinned").apply { writeText("selected") }
            val live = root.resolve("live").apply { writeText("selected") }
            val checksum = nativeSnapshotChecksum(pinned)

            requirePinnedNativeTransferFile(live, checksum)
            live.writeText("changed")
            assertFailsWith<IllegalStateException> { requirePinnedNativeTransferFile(live, checksum) }
        } finally {
            check(root.deleteRecursively())
        }
    }
}
