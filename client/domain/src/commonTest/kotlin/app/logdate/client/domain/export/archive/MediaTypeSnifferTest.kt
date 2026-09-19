package app.logdate.client.domain.export.archive

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTypeSnifferTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String) = text.encodeToByteArray()

    private fun ftyp(brand: String) = byteArrayOf(0, 0, 0, 0x18) + ascii("ftyp$brand") + ByteArray(12)

    private fun assertSniffs(
        expectedMime: String,
        expectedExtension: String,
        header: ByteArray,
        hintExtension: String? = null,
    ) {
        val type = MediaTypeSniffer.sniff(header, hintExtension)

        assertEquals(expectedMime, type.mimeType)
        assertEquals(expectedExtension, type.extension)
    }

    @Test
    fun `still image formats are recognised by their magic bytes`() {
        assertSniffs("image/jpeg", "jpg", bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16))
        assertSniffs("image/png", "png", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16))
        assertSniffs("image/gif", "gif", ascii("GIF89a") + ByteArray(16))
        assertSniffs("image/webp", "webp", ascii("RIFF") + ByteArray(4) + ascii("WEBP") + ByteArray(8))
        assertSniffs("image/heic", "heic", ftyp("heic"))
        assertSniffs("image/heic", "heic", ftyp("mif1"))
        assertSniffs("image/avif", "avif", ftyp("avif"))
    }

    @Test
    fun `video formats are recognised by their container brand`() {
        assertSniffs("video/mp4", "mp4", ftyp("isom"))
        assertSniffs("video/mp4", "mp4", ftyp("mp42"))
        assertSniffs("video/quicktime", "mov", ftyp("qt  "))
        assertSniffs("video/3gpp", "3gp", ftyp("3gp4"))
        assertSniffs("video/webm", "webm", bytes(0x1A, 0x45, 0xDF, 0xA3) + ByteArray(16))
    }

    @Test
    fun `audio formats are recognised`() {
        assertSniffs("audio/mp4", "m4a", ftyp("M4A "))
        assertSniffs("audio/mpeg", "mp3", ascii("ID3") + ByteArray(16))
        assertSniffs("audio/mpeg", "mp3", bytes(0xFF, 0xFB, 0x90, 0x00) + ByteArray(16))
        assertSniffs("audio/aac", "aac", bytes(0xFF, 0xF1, 0x50, 0x80) + ByteArray(16))
        assertSniffs("audio/wav", "wav", ascii("RIFF") + ByteArray(4) + ascii("WAVE") + ByteArray(8))
        assertSniffs("audio/ogg", "ogg", ascii("OggS") + ByteArray(16))
        assertSniffs("audio/flac", "flac", ascii("fLaC") + ByteArray(16))
        assertSniffs("audio/x-caf", "caf", ascii("caff") + ByteArray(16))
        assertSniffs("audio/amr", "amr", ascii("#!AMR\n") + ByteArray(16))
    }

    @Test
    fun `an mp4 container containing an audio note is filed as m4a`() {
        val audioNote = MediaTypeSniffer.sniff(ftyp("isom"), hintExtension = "m4a", kind = MediaKind.AUDIO)
        val video = MediaTypeSniffer.sniff(ftyp("isom"), hintExtension = "mp4", kind = MediaKind.VIDEO)

        assertEquals("audio/mp4" to "m4a", audioNote.mimeType to audioNote.extension)
        assertEquals("video/mp4" to "mp4", video.mimeType to video.extension)
    }

    @Test
    fun `the bytes win over a misleading extension`() {
        assertSniffs("image/png", "png", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16), hintExtension = "jpg")
    }

    @Test
    fun `an unrecognised header falls back to the extension the file already had`() {
        assertSniffs("image/jpeg", "jpg", ByteArray(32), hintExtension = "JPEG")
        assertSniffs("audio/mp4", "m4a", ByteArray(32), hintExtension = "m4a")
    }

    @Test
    fun `a file that is neither recognised nor hinted is stored as opaque bytes`() {
        assertSniffs("application/octet-stream", "bin", ByteArray(32))
        assertSniffs("application/octet-stream", "bin", ByteArray(0), hintExtension = "zzz")
    }

    @Test
    fun `a header too short to contain a signature does not crash`() {
        assertSniffs("application/octet-stream", "bin", bytes(0xFF))
    }
}
