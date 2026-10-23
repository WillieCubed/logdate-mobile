@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

internal fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

internal fun List<ByteArray>.flattenToNSData(): NSData {
    if (isEmpty()) {
        return ByteArray(0).toNSData()
    }

    val totalSize = sumOf { it.size }
    val flattened = ByteArray(totalSize)
    var offset = 0
    for (chunk in this) {
        chunk.copyInto(flattened, destinationOffset = offset)
        offset += chunk.size
    }
    return flattened.toNSData()
}

internal fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) {
        return ByteArray(0)
    }
    val buffer = ByteArray(length)
    buffer.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length.toULong())
    }
    return buffer
}
