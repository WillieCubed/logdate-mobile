package app.logdate.server.crypto

object PayloadPrefixes {
    val SERVER_MEDIA = "LDSM1".toByteArray(Charsets.UTF_8)
    val SERVER_BACKUP = "LDBK1".toByteArray(Charsets.UTF_8)
    val CLIENT_MEDIA = "LDCE1".toByteArray(Charsets.UTF_8)
}

fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { this[it] == prefix[it] }
}
