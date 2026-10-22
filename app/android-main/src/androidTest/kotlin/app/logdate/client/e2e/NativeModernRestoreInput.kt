package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.test.assertEquals

/** Only the managed callback driver's nested instrumentation enables these hidden fixture APIs. */
internal fun nativeModernRestoreInput(
    inputType: Class<*>,
    descriptor: ParcelFileDescriptor,
    destination: File,
    size: Long,
    mode: Long,
    transportFlags: Int = 0,
    contentVersion: String = "",
): Any =
    inputType
        .getDeclaredConstructor(
            ParcelFileDescriptor::class.java,
            java.lang.Long.TYPE,
            File::class.java,
            Integer.TYPE,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            Integer.TYPE,
            String::class.java,
        ).newInstance(descriptor, size, destination, BackupAgent.TYPE_FILE, mode, 1L, 1L, transportFlags, contentVersion)
        .also { input ->
            assertEquals(8, BackupAgent::class.java.getField("FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS").getInt(null))
            assertEquals(transportFlags, inputType.getMethod("getTransportFlags").invoke(input))
            assertEquals(contentVersion, inputType.getMethod("getContentVersion").invoke(input))
        }
