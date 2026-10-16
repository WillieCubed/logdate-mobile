package app.logdate.client.device.identity

import android.content.Context
import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** Installation identity belongs to this container and never enters native backup. */
class AndroidInstallationIdProvider(
    private val context: Context,
    legacy: KeyValueStorage,
) : DeviceIdProvider {
    private val current =
        MutableStateFlow(
            runBlocking(Dispatchers.IO) {
                synchronized(lock) {
                    val file = identityFile()
                    if (file.exists()) return@synchronized read(file)
                    val initialized = File(context.noBackupFilesDir, ".logdate_device_initialized").exists()
                    val restored = File(context.noBackupFilesDir, ".logdate_native_restore_pending").exists()
                    val previous = if (initialized && !restored) runBlocking { legacy.getString("device.id") } else null
                    val id = previous?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()
                    write(id)
                    id
                }
            },
        )

    override fun getDeviceId(): StateFlow<Uuid> = current.asStateFlow()

    override suspend fun refreshDeviceId() =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val id = Uuid.random()
                write(id)
                current.value = id
            }
        }

    private fun read(file: File): Uuid {
        check(file.length() == 36L) { "Installation identity needs recovery" }
        return Uuid.parse(file.readText())
    }

    private fun write(id: Uuid) {
        val file = identityFile()
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use {
            it.write(id.toString().encodeToByteArray())
            it.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun identityFile() = File(context.noBackupFilesDir, ".logdate_installation_id")

    private companion object {
        val lock = Any()
    }
}
