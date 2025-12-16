package app.logdate.client.device.identity.di

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.DesktopDeviceIdProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.DeviceRepository
import app.logdate.client.device.identity.data.InMemoryDeviceRepository
import app.logdate.client.device.models.DevicePlatform
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.net.InetAddress
import java.util.Properties

/**
 * Desktop implementation of device identity module.
 */
actual val deviceIdentityModule: Module = module {
    // Device ID provider
    single<DeviceIdProvider> {
        DesktopDeviceIdProvider(
            storage = get<KeyValueStorage>(named("deviceKeyValueStorage"))
        )
    }
    
    
    // Repository for device information
    single<DeviceRepository> {
        InMemoryDeviceRepository()
    }
    
    // Unified device manager
    single<DefaultDeviceManager> {
        // Create desktop-specific platform info provider
        val platformInfoProvider = {
            val systemProps = Properties()
            systemProps.putAll(System.getProperties())
            
            mapOf(
                "os.name" to systemProps.getProperty("os.name", "Unknown"),
                "os.version" to systemProps.getProperty("os.version", "Unknown"),
                "os.arch" to systemProps.getProperty("os.arch", "Unknown"),
                "java.version" to systemProps.getProperty("java.version", "Unknown"),
                "user.name" to systemProps.getProperty("user.name", "Unknown"),
                "hostname" to try { InetAddress.getLocalHost().hostName } catch (e: Exception) { "Unknown" }
            )
        }
        
        // Get a reasonable device name
        val deviceName = try {
            val hostname = InetAddress.getLocalHost().hostName
            val osName = System.getProperty("os.name")?.split(" ")?.firstOrNull() ?: "Desktop"
            "$osName ($hostname)"
        } catch (e: Exception) {
            "Desktop"
        }
        
        // Determine the correct platform based on OS
        val platform = when {
            System.getProperty("os.name").lowercase().contains("mac") -> DevicePlatform.MACOS
            System.getProperty("os.name").lowercase().contains("win") -> DevicePlatform.WINDOWS
            System.getProperty("os.name").lowercase().contains("linux") -> DevicePlatform.LINUX
            else -> DevicePlatform.UNKNOWN
        }
        
        DefaultDeviceManager(
            deviceIdProvider = get(),
            deviceRepository = get(),
            initialDeviceName = deviceName,
            platform = platform,
            appVersion = "1.0.0", // This should be provided from build config in a real app
        )
    }
}