package app.logdate.client.domain.export.archive

/** An IANA media type and the file extension it is stored under. */
data class MediaType(
    val mimeType: String,
    val extension: String,
) {
    companion object {
        val OPAQUE = MediaType("application/octet-stream", "bin")
    }
}

/**
 * Works out what a media file is from its first bytes.
 *
 * The device gives a media file whatever name it had, which may be a bare content id with no
 * extension, or an extension that does not match the bytes. The archive records the type the bytes
 * show, and falls back to the file's own extension only when the bytes are not recognised.
 */
object MediaTypeSniffer {
    /** Enough of a file's start to recognise every format handled here. */
    const val HEADER_SIZE = 32

    private const val FTYP_OFFSET = 4
    private const val BRAND_OFFSET = 8
    private const val BRAND_END = 12

    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
    private val AVIF_BRANDS = setOf("avif", "avis")
    private val M4A_BRANDS = setOf("M4A ", "M4B ", "M4P ")

    private val JPEG = MediaType("image/jpeg", "jpg")
    private val PNG = MediaType("image/png", "png")
    private val GIF = MediaType("image/gif", "gif")
    private val WEBP = MediaType("image/webp", "webp")
    private val HEIC = MediaType("image/heic", "heic")
    private val AVIF = MediaType("image/avif", "avif")
    private val MP4 = MediaType("video/mp4", "mp4")
    private val QUICKTIME = MediaType("video/quicktime", "mov")
    private val THREE_GPP = MediaType("video/3gpp", "3gp")
    private val THREE_GPP2 = MediaType("video/3gpp2", "3g2")
    private val WEBM = MediaType("video/webm", "webm")
    private val M4A = MediaType("audio/mp4", "m4a")
    private val MP3 = MediaType("audio/mpeg", "mp3")
    private val AAC = MediaType("audio/aac", "aac")
    private val WAV = MediaType("audio/wav", "wav")
    private val OGG = MediaType("audio/ogg", "ogg")
    private val FLAC = MediaType("audio/flac", "flac")
    private val CAF = MediaType("audio/x-caf", "caf")
    private val AMR = MediaType("audio/amr", "amr")

    private val BY_EXTENSION: Map<String, MediaType> =
        mapOf(
            "jpg" to JPEG,
            "jpeg" to JPEG,
            "png" to PNG,
            "gif" to GIF,
            "webp" to WEBP,
            "heic" to HEIC,
            "heif" to HEIC,
            "avif" to AVIF,
            "mp4" to MP4,
            "m4v" to MP4,
            "mov" to QUICKTIME,
            "3gp" to THREE_GPP,
            "3g2" to THREE_GPP2,
            "webm" to WEBM,
            "m4a" to M4A,
            "mp3" to MP3,
            "aac" to AAC,
            "wav" to WAV,
            "ogg" to OGG,
            "oga" to OGG,
            "flac" to FLAC,
            "caf" to CAF,
            "amr" to AMR,
        )

    /**
     * @param header the first bytes of the file, at least [HEADER_SIZE] when the file is that long
     * @param hintExtension the extension the file had on the device, used only when [header] is
     *   not recognised
     * @param kind the kind of note the file belongs to. An MPEG-4 container holding an audio note is
     *   filed as `m4a` rather than `mp4`.
     */
    fun sniff(
        header: ByteArray,
        hintExtension: String? = null,
        kind: MediaKind? = null,
    ): MediaType {
        val recognised = fromMagicBytes(header)
        if (recognised == null) return hintExtension?.lowercase()?.let(BY_EXTENSION::get) ?: MediaType.OPAQUE
        return if (kind == MediaKind.AUDIO && recognised == MP4) M4A else recognised
    }

    private fun fromMagicBytes(header: ByteArray): MediaType? =
        fromFrame(header)
            ?: fromContainerBrand(header)
            ?: fromRiff(header)
            ?: fromStreamHeader(header)

    private fun fromFrame(header: ByteArray): MediaType? {
        if (header.size < 2 || header[0].toInt() and 0xFF != 0xFF) return null
        val second = header[1].toInt() and 0xFF
        if (second == 0xD8 && header.size >= 3 && header[2].toInt() and 0xFF == 0xFF) return JPEG
        if (second and 0xE0 != 0xE0) return null
        val layer = (second shr 1) and 0x03
        return if (layer == 0) AAC else MP3
    }

    private fun fromContainerBrand(header: ByteArray): MediaType? {
        if (header.size < BRAND_END || !header.startsWith("ftyp", FTYP_OFFSET)) return null
        val brand = header.decodeToString(BRAND_OFFSET, BRAND_END)
        return when {
            brand in HEIC_BRANDS -> HEIC
            brand in AVIF_BRANDS -> AVIF
            brand == "qt  " -> QUICKTIME
            brand.startsWith("3gp") -> THREE_GPP
            brand.startsWith("3g2") -> THREE_GPP2
            brand in M4A_BRANDS -> M4A
            else -> MP4
        }
    }

    private fun fromRiff(header: ByteArray): MediaType? {
        if (header.size < BRAND_END || !header.startsWith("RIFF", 0)) return null
        return when (header.decodeToString(BRAND_OFFSET, BRAND_END)) {
            "WEBP" -> WEBP
            "WAVE" -> WAV
            else -> null
        }
    }

    private fun fromStreamHeader(header: ByteArray): MediaType? =
        when {
            header.startsWith(PNG_SIGNATURE) -> PNG
            header.startsWith("GIF8", 0) -> GIF
            header.startsWith("ID3", 0) -> MP3
            header.startsWith("OggS", 0) -> OGG
            header.startsWith("fLaC", 0) -> FLAC
            header.startsWith("caff", 0) -> CAF
            header.startsWith("#!AMR", 0) -> AMR
            header.startsWith(MATROSKA_SIGNATURE) -> WEBM
            else -> null
        }

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val MATROSKA_SIGNATURE = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

    private fun ByteArray.startsWith(
        text: String,
        offset: Int,
    ): Boolean = startsWith(text.encodeToByteArray(), offset)

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        offset: Int = 0,
    ): Boolean {
        if (size < offset + prefix.size) return false
        return prefix.indices.all { this[offset + it] == prefix[it] }
    }
}
