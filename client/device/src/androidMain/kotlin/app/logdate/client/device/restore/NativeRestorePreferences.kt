package app.logdate.client.device.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.LegacyCloudCredentialMigration
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal suspend fun resetRestoredInstallationState(
    preferences: DataStore<Preferences>,
    sourceDeviceId: String?,
) {
    val deviceIdKey = stringPreferencesKey("device.id")
    preferences.edit { restored ->
        val clean = LegacyCloudCredentialMigration.withoutCredentials(restored)
        restored
            .asMap()
            .keys
            .filter { it !in clean.asMap() }
            .forEach { restored.remove(it) }
        if (sourceDeviceId != null && restored[deviceIdKey] == sourceDeviceId) restored.remove(deviceIdKey)
    }
}

/** Remembers the transferred identity so a duplicate completion cannot clear a newly generated id. */
internal class NativeInstallationResetMarker(
    private val directory: File,
) {
    fun sourceDeviceId(currentDeviceId: String?): String? {
        val primaryValue = readSourceDeviceId(primaryFile)
        val backupValue = readSourceDeviceId(backupFile)
        if (primaryValue.valid || backupValue.valid) {
            val sourceDeviceId = if (primaryValue.valid) primaryValue.value else backupValue.value
            if (!primaryValue.valid || primaryValue.value != sourceDeviceId) writeAtomically(primaryFile, encode(sourceDeviceId))
            if (!backupValue.valid || backupValue.value != sourceDeviceId) writeAtomically(backupFile, encode(sourceDeviceId))
            return sourceDeviceId
        }
        check(!primaryFile.exists() && !backupFile.exists()) { "Native installation reset markers could not be recovered" }
        val encoded = encode(currentDeviceId)
        writeAtomically(primaryFile, encoded)
        check(
            readSourceDeviceId(primaryFile) == MarkerValue(true, currentDeviceId),
        ) { "Native installation reset marker could not be verified" }
        writeAtomically(backupFile, encoded)
        check(
            readSourceDeviceId(backupFile) == MarkerValue(true, currentDeviceId),
        ) { "Native installation reset backup could not be verified" }
        return currentDeviceId
    }

    fun clear() {
        check(!primaryFile.exists() || primaryFile.delete()) { "Native installation reset marker could not be cleared" }
        check(!backupFile.exists() || backupFile.delete()) { "Native installation reset backup could not be cleared" }
    }

    private fun encode(currentDeviceId: String?): String =
        currentDeviceId?.let {
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(it.encodeToByteArray())
        } ?: "-"

    private fun readSourceDeviceId(file: File): MarkerValue =
        runCatching {
            file.readText().takeIf { it.length <= MAX_MARKER_LENGTH }?.let { encoded ->
                MarkerValue(
                    valid = true,
                    value =
                        if (encoded == "-") {
                            null
                        } else {
                            val decoded =
                                java.util.Base64
                                    .getUrlDecoder()
                                    .decode(encoded)
                                    .decodeToString()
                            check(encode(decoded) == encoded)
                            decoded
                        },
                )
            }
        }.getOrNull() ?: MarkerValue(valid = false, value = null)

    private fun writeAtomically(
        file: File,
        value: String,
    ) {
        directory.mkdirs()
        val temporary = File(directory, "${file.name}.part")
        FileOutputStream(temporary).use { stream ->
            stream.write(value.encodeToByteArray())
            stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private val primaryFile get() = File(directory, ".logdate_installation_reset_source")
    private val backupFile get() = File(directory, ".logdate_installation_reset_source_backup")

    private data class MarkerValue(
        val valid: Boolean,
        val value: String?,
    )

    private companion object {
        const val MAX_MARKER_LENGTH = 512
    }
}
