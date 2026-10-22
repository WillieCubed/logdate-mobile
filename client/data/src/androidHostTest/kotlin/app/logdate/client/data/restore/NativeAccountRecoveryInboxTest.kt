package app.logdate.client.data.restore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeAccountRecoveryInboxTest {
    private val first =
        NativeAccountRecoveryRequest(
            "2f18404e-e9f5-434d-8b03-2f04f09ac523",
            "19b6ef01-19ec-4b65-8c82-c49b1ea25862",
            "a".repeat(64),
        )

    @Test
    fun `pending recovery survives process recreation without retaining a credential`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            assertTrue(NativeAccountRecoveryInbox(directory).retain(first))
            assertEquals(first, NativeAccountRecoveryInbox(directory).pending())
            assertEquals(2, directory.listFiles()!!.size)
            val stored = directory.listFiles()!!.joinToString { it.readText() }
            assertFalse(stored.contains("accessToken"))
            assertFalse(stored.contains("refreshToken"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a pending delivery cannot be retargeted after account or server configuration changes`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            assertFalse(inbox.retain(first.copy(scope = "b".repeat(64))))
            assertEquals(first, NativeAccountRecoveryInbox(directory).pending())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `completion from an older delivery cannot erase a newer restore`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            val next =
                first.copy(
                    delivery = "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    generation = "a63ab31e-7f38-4d71-9925-ed033d658661",
                )
            assertTrue(inbox.retain(next))
            assertFalse(inbox.acknowledge(first))
            assertEquals(next, NativeAccountRecoveryInbox(directory).pending())
            assertTrue(inbox.acknowledge(next))
            assertNull(NativeAccountRecoveryInbox(directory).pending())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `an authenticated target survives restart and cannot become a different account`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            val owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
            assertTrue(inbox.acceptTarget(first, owner))
            val restarted = NativeAccountRecoveryInbox(directory)
            assertEquals(owner, restarted.acceptedTarget(first))
            assertTrue(restarted.acceptTarget(first, owner))
            assertFalse(restarted.acceptTarget(first, "a63ab31e-7f38-4d71-9925-ed033d658661"))
            assertEquals(owner, restarted.acceptedTarget(first))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a verified fresh delivery repairs corrupted custody while preserving the old bytes`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            java.io.File(directory, "native-account-recovery.json").writeText("truncated")
            assertFails { inbox.retain(first) }
            val next = first.copy(delivery = "347c6231-19f4-49fa-bd1d-58ed34b9be03", generation = "a63ab31e-7f38-4d71-9925-ed033d658661")
            assertTrue(inbox.retainFreshDelivery(next))
            assertEquals(next, NativeAccountRecoveryInbox(directory).pending())
            assertTrue(directory.listFiles()!!.any { it.readText() == "truncated" })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `unreadable custody cannot be replaced by an unverified retry`() {
        val directory = Files.createTempDirectory("native-account-recovery").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            val file = java.io.File(directory, "native-account-recovery.json")
            file.writeText("truncated")
            assertFails { NativeAccountRecoveryInbox(directory).retain(first) }
            assertEquals("truncated", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `target receipt failure cannot accept an account`() {
        val directory = Files.createTempDirectory("native-account-target-failure").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            val temporary = java.io.File(directory, "native-account-target-${first.generation}-${first.delivery}.json.part")
            assertTrue(temporary.mkdir())
            assertFails { inbox.acceptTarget(first, "347c6231-19f4-49fa-bd1d-58ed34b9be03") }
            assertNull(inbox.acceptedTarget(first))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `inbox write failure preserves the earlier durable target receipt`() {
        val directory = Files.createTempDirectory("native-account-target-failure").toFile()
        try {
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(first))
            val temporary = java.io.File(directory, "native-account-recovery.json.part")
            assertTrue(temporary.mkdir())
            val owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
            assertFails { inbox.acceptTarget(first, owner) }
            assertEquals(owner, NativeAccountRecoveryInbox(directory).acceptedTarget(first))
            assertTrue(inbox.retain(first))
            assertFalse(inbox.acceptTarget(first, "a63ab31e-7f38-4d71-9925-ed033d658661"))
            assertEquals(owner, NativeAccountRecoveryInbox(directory).acceptedTarget(first))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a new verified delivery preserves and repairs a directory obstructing the inbox`() {
        val directory = Files.createTempDirectory("native-account-directory-custody").toFile()
        try {
            val obstruction = java.io.File(directory, "native-account-recovery.json")
            assertTrue(obstruction.mkdir())
            java.io.File(obstruction, "retained").writeText("original bytes")
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retainFreshDelivery(first))
            assertEquals(first, inbox.pending())
            assertTrue(
                directory.listFiles()!!.any {
                    it.isDirectory && java.io.File(it, "retained").readText() == "original bytes"
                },
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
