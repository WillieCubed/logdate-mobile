package app.logdate.client.domain.export.archive

import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChecksumFileTest {
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun entry(
        path: String,
        sha: String,
    ) = HashLedger.Entry(ArchivePath.of(path), sha, bytes = 0)

    @Test
    fun `each line is the hash then two spaces then the path so shasum and sha256sum can check it`() {
        val text = ChecksumFile.render(listOf(entry("data/notes.json", abcSha)))

        assertEquals("$abcSha  data/notes.json\n", text)
    }

    @Test
    fun `lines are sorted by path and end with a newline`() {
        val text =
            ChecksumFile.render(
                listOf(
                    entry("media/photos/2026/b.jpg", emptySha),
                    entry("data/notes.json", abcSha),
                    entry("README.txt", emptySha),
                ),
            )

        assertEquals(
            "$emptySha  README.txt\n$abcSha  data/notes.json\n$emptySha  media/photos/2026/b.jpg\n",
            text,
        )
    }

    @Test
    fun `an empty list renders an empty file`() {
        assertEquals("", ChecksumFile.render(emptyList()))
    }

    @Test
    fun `a rendered file can be read back into path and hash pairs`() {
        val text = ChecksumFile.render(listOf(entry("a.txt", abcSha), entry("b/c.txt", emptySha)))

        assertEquals(mapOf("a.txt" to abcSha, "b/c.txt" to emptySha), ChecksumFile.parse(text))
    }

    @Test
    fun `a file with a malformed line cannot be read`() {
        assertNull(ChecksumFile.parse("not-a-hash  a.txt\n"))
        assertNull(ChecksumFile.parse("$abcSha a.txt\n"))
        assertNull(ChecksumFile.parse("$abcSha  ../escape.txt\n"))
    }

    @Test
    fun `hashing while writing reports the sha-256 and size and passes the bytes through`() {
        val ledger = HashLedger()
        val destination = Buffer()

        hashedWrite(destination, ArchivePath.of("data/notes.json"), ledger) { it.writeUtf8("abc") }

        val recorded = ledger.entries.single()
        assertEquals(abcSha, recorded.sha256)
        assertEquals(3, recorded.bytes)
        assertEquals("abc", destination.readUtf8())
    }

    @Test
    fun `a large write is hashed the same as the whole content at once`() {
        val ledger = HashLedger()
        val content = "0123456789abcdef".repeat(20_000)

        hashedWrite(Buffer(), ArchivePath.of("data/big.json"), ledger) { it.writeUtf8(content) }

        assertEquals(content.encodeUtf8().sha256().hex(), ledger.entries.single().sha256)
        assertEquals(content.length.toLong(), ledger.entries.single().bytes)
    }

    @Test
    fun `an empty file has the well known empty hash`() {
        val ledger = HashLedger()

        hashedWrite(Buffer(), ArchivePath.of("empty.txt"), ledger) { }

        assertEquals(emptySha, ledger.entries.single().sha256)
        assertEquals(0, ledger.entries.single().bytes)
    }

    @Test
    fun `the ledger lists files in the order they were written`() {
        val ledger = HashLedger()
        ledger.record(ArchivePath.of("b.txt"), emptySha, 0)
        ledger.record(ArchivePath.of("a.txt"), emptySha, 0)

        assertEquals(listOf("b.txt", "a.txt"), ledger.entries.map { it.path.value })
    }
}
