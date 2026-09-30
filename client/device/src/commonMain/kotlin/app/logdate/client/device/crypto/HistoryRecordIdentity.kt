package app.logdate.client.device.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

/** Legacy activity IDs include movement details; only the encrypted payload may retain them. */
fun activityHistoryRecordId(id: String): String {
    val digest =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hashBlocking(id.encodeToByteArray())
    return "activity:" + digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
