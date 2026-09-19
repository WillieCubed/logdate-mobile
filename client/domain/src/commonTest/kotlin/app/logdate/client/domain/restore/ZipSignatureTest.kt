package app.logdate.client.domain.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZipSignatureTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `a file that starts with a local file header is a zip`() {
        assertTrue(ZipSignature.isZip(bytes('P'.code, 'K'.code, 3, 4)))
    }

    @Test
    fun `an empty archive is a zip`() {
        assertTrue(ZipSignature.isZip(bytes('P'.code, 'K'.code, 5, 6)))
    }

    @Test
    fun `a spanned archive marker is a zip`() {
        assertTrue(ZipSignature.isZip(bytes('P'.code, 'K'.code, 7, 8)))
    }

    @Test
    fun `other files and files too short to tell are not a zip`() {
        assertFalse(ZipSignature.isZip("%PDF".encodeToByteArray()))
        assertFalse(ZipSignature.isZip(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertFalse(ZipSignature.isZip(bytes('P'.code, 'K'.code)))
        assertFalse(ZipSignature.isZip(ByteArray(0)))
    }

    @Test
    fun `the header length is four bytes`() {
        assertEquals(4, ZipSignature.LENGTH)
    }
}
