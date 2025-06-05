# `:client:sensor`

**Device sensor access and system monitoring**

## Overview

Provides cross-platform access to device sensors and system information such as battery status, network conditions, and device motion. This module offers a consistent API for monitoring device state across Android, iOS, and desktop platforms.

## Architecture

```
Sensor Module
├── Sensor Interfaces
├── Sensor Data Models
└── Platform-Specific Implementations
```

## Key Components

### Core Components

- `BatteryInfoProvider.kt` - Battery status monitoring interface
- `BatteryState.kt` - Battery state data model
- `NetworkSaverModeProvider.kt` - Network data saver mode interface
- `NetworkSaverState.kt` - Network data saver mode state model
- `GyroSensorProvider.kt` - Gyroscope sensor interface
- `GyroOffset.kt` - Gyroscope data model

## Features

### Battery Monitoring

- **Battery Level**: Real-time battery level readings
- **Charging Status**: Monitor charging state changes
- **Power Save Mode**: Track power saving mode (Low Power Mode on iOS)
- **Resource Cleanup**: Proper release of battery monitoring resources

### Network Monitoring

- **Data Saver Mode**: Track system data saver mode (Android)
- **Connection Type**: Monitor connection type (WiFi, cellular, etc.)
- **Event Streaming**: Reactive data flow with Kotlin Flow

### Motion Sensors

- **Gyroscope Data**: Real-time gyroscope readings where available
- **Sensor Lifecycle**: Proper resource management
- **Event Streaming**: Reactive data flow with Flow
- **Error Handling**: Graceful handling of sensor unavailability

## Platform Support

| Feature                | Android | iOS     | Desktop |
|------------------------|---------|---------|---------|
| Battery Level          | ✅      | ✅      | ⚠️ Stub  |
| Charging Status        | ✅      | ✅      | ⚠️ Stub  |
| Power Save Mode        | ✅      | ✅      | ⚠️ Stub  |
| Data Saver Mode        | ✅      | ⚠️ N/A   | ⚠️ Stub  |
| Network Connection Type| ✅      | ⚠️ Partial| ⚠️ Stub  |
| Gyroscope              | ✅      | ✅      | ⚠️ Stub  |

_⚠️ Note: On Desktop, stub implementations provide a consistent API but no actual sensor data. iOS has no direct equivalent to Android's Data Saver Mode._

### Platform Integration

- **Android Integration**: Native Android sensor framework and system services
- **iOS Integration**: UIDevice and NSProcessInfo for device monitoring
- **Desktop Support**: Currently uses stub implementations that provide consistent behavior but no actual sensor data (desktop computers typically lack motion sensors and have different power management)

## Usage Examples

### Battery Monitoring

```kotlin
// Get battery info provider from DI
val batteryInfoProvider = get<BatteryInfoProvider>()

// Collect battery updates
coroutineScope.launch {
    batteryInfoProvider.currentBatteryState.collect { state ->
        println("Battery level: ${state.level}%")
        println("Charging: ${state.isCharging}")
        println("Power save mode: ${state.isPowerSaveMode}")
    }
}

// Get current state directly
coroutineScope.launch {
    val currentState = batteryInfoProvider.getCurrentBatteryState()
    println("Current battery level: ${currentState.level}%")
}

// Clean up when done
batteryInfoProvider.cleanup()
```

### Network Data Saver Mode

```kotlin
// Get network saver provider from DI
val networkSaverProvider = get<NetworkSaverModeProvider>()

// Monitor data saver mode
coroutineScope.launch {
    networkSaverProvider.dataSaverModeState.collect { state ->
        if (state.isDataSaverEnabled) {
            // Adjust app behavior for data saving
            println("Data saver enabled, connection: ${state.connectionType}")
        } else {
            println("Normal data usage, connection: ${state.connectionType}")
        }
    }
}

// Clean up when done
networkSaverProvider.cleanup()
```

## TODOs

### Core Sensor Features
- [ ] Add accelerometer sensor support
- [ ] Implement magnetometer access
- [ ] Add ambient light sensor support
- [ ] Implement proximity sensor access
- [ ] Add step counter integration

### Data Processing
- [ ] Add sensor data filtering
- [ ] Implement sensor fusion algorithms
- [ ] Add motion detection capabilities
- [ ] Implement gesture recognition
- [ ] Add orientation detection

### Platform Support
- [ ] Implement native desktop APIs for battery and sensor data where available (JNA/JNI on Windows/Linux/macOS)
- [ ] Add watchOS sensor integration
- [ ] Add sensor mocking for testing
- [ ] Implement sensor data recording/playback

### Advanced Features
- [ ] Add sensor calibration utilities
- [ ] Implement battery-efficient sensor polling
- [ ] Add sensor data aggregation
- [ ] Implement sensor-based activity recognition
- [ ] Add sensor data visualization components