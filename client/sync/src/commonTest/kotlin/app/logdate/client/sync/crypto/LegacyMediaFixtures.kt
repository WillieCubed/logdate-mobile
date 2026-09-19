package app.logdate.client.sync.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom

/** Builds media in LDCE1, the single-shot format earlier builds uploaded and downloads still read. */
@OptIn(DelicateCryptographyApi::class)
fun legacyLdce1Encrypt(
    key: ByteArray,
    plaintext: ByteArray,
): ByteArray {
    val iv = CryptographyRandom.nextBytes(CLIENT_MEDIA_IV_SIZE_BYTES)
    val cipherText =
        CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()
            .encryptWithIvBlocking(iv, plaintext)
    return CLIENT_MEDIA_PREFIX_BYTES + iv + cipherText
}
