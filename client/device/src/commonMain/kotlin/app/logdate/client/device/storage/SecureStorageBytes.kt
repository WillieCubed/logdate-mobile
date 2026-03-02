package app.logdate.client.device.storage

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun SecureStorage.getBytes(key: String): ByteArray? {
    val encoded = getString(key)
    if (encoded.isNullOrBlank()) return null
    return Base64.decode(encoded)
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun SecureStorage.putBytes(
    key: String,
    value: ByteArray,
) {
    putString(key, Base64.encode(value))
}
