package app.logdate.server.crypto

object PayloadPrefixes {
    val SERVER_MEDIA = "LDSM1".toByteArray(Charsets.UTF_8)
    val SERVER_BACKUP = "LDBK1".toByteArray(Charsets.UTF_8)
    val CLIENT_MEDIA = "LDCE1".toByteArray(Charsets.UTF_8)

    /** Client media sealed in independent chunks, so clients can encrypt it without holding the whole file. */
    val CLIENT_MEDIA_CHUNKED = "LDCE2".toByteArray(Charsets.UTF_8)
}

fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { this[it] == prefix[it] }
}
