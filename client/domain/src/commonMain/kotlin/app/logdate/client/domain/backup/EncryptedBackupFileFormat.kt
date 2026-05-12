package app.logdate.client.domain.backup

import app.logdate.shared.model.backup.BackupManifest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

internal object EncryptedBackupFileFormat {
    private val magic = byteArrayOf('L'.code.toByte(), 'D'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1

    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun writeHeader(
        sink: BufferedSink,
        manifest: BackupManifest,
    ) {
        val manifestJson = json.encodeToString(manifest)
        val manifestBytes = manifestJson.encodeToByteArray()
        require(manifestBytes.isNotEmpty()) { "Backup manifest cannot be empty." }
        sink.write(magic)
        sink.writeByte(VERSION.toInt())
        sink.writeInt(manifestBytes.size)
        sink.write(manifestBytes)
    }

    fun readHeader(source: BufferedSource): Header {
        val actualMagic = source.readByteArray(magic.size.toLong())
        require(actualMagic.contentEquals(magic)) { "Invalid encrypted backup file." }
        val version = source.readByte()
        require(version == VERSION) { "Unsupported encrypted backup version: $version." }
        val manifestSize = source.readInt()
        require(manifestSize > 0) { "Invalid encrypted backup manifest." }
        val manifestJson = source.readUtf8(manifestSize.toLong())
        json.decodeFromString<BackupManifest>(manifestJson)
        return Header(manifestJson)
    }

    data class Header(
        val manifestJson: String,
    ) {
        val manifest: BackupManifest = json.decodeFromString(manifestJson)
    }
}
