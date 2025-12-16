# LogDate Device Identification System

This document outlines the design and implementation of the LogDate device identification system, which provides a reliable way to identify devices across the application lifecycle.

## Core Principles

1. **Unique Identification**: Each device running LogDate generates a persistent unique identifier.
2. **Secure Storage**: Device identifiers are stored securely and privately.
3. **Cross-Platform Consistency**: The system works consistently across Android, iOS, and desktop platforms.
4. **User Transparency**: Users can view and manage their devices.
5. **Local-First**: Device identification works regardless of cloud connectivity.

## Technical Design

### Device ID Generation

1. **Format and Properties**:
   - Device IDs are generated as stable, pseudorandom identifiers
   - Format: SHA-256 hash of platform-specific identifiers + salt
   - Properties: Globally unique, persistent across app updates, resistant to collisions

2. **Generation Timing**:
   - Generated during first app launch after installation
   - Created before user onboarding process begins
   - Persists throughout the application lifecycle

3. **Persistence Strategy**:
   - Stored in platform-specific secure storage
   - Android: EncryptedSharedPreferences backed by Android Keystore
   - iOS: iOS Keychain with appropriate protection class
   - Desktop: OS-specific secure storage (macOS Keychain, Windows Credential Manager, etc.)

### Platform-Specific Approaches

#### Android Implementation

- Uses a combination of Android ID and device hardware information
- Stored in EncryptedSharedPreferences using the Android Keystore
- Fallback to UUID generation if primary methods fail
- Handles edge cases like emulators and development devices

#### iOS Implementation

- Based on identifierForVendor with additional device information
- Stored securely in the iOS Keychain
- Handles reinstallation scenarios appropriately
- Includes fallbacks for simulator and testing environments

#### Desktop Implementation

- Uses platform-specific hardware identifiers (machine-id on Linux, IOPlatformUUID on macOS, etc.)
- Supplemented with OS and hardware information
- Stored in platform-appropriate secure storage
- Handles virtualization scenarios gracefully

### Device Management System

The device management system tracks additional metadata about devices and enables management functionality:

```kotlin
data class DeviceInfo(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val createdAt: Long,
    val lastActive: Long,
    val appVersion: String,
    val isCurrentDevice: Boolean
)

enum class DevicePlatform {
    ANDROID, IOS, MACOS, WINDOWS, LINUX, WEB, UNKNOWN
}
```

Key functionality includes:
- Retrieving the current device ID
- Getting device information
- Tracking device activity
- Registering devices with cloud accounts
- Managing associated devices
- Renaming devices
- Removing devices from accounts

## Device ID Usage Scenarios

### Local-Only Usage (Without LogDate Cloud)

When a user is using LogDate without a cloud account, device IDs serve several important functions:

1. **Content Attribution**:
   - Associates locally created content with its originating device
   - Enables filtering and organization by creation device
   - Maintains metadata about content origin

2. **Settings and Preferences**:
   - Stores device-specific settings separately from user settings
   - Enables customization of UI/UX per device
   - Manages device-specific capabilities (sensors, etc.)

3. **Local Analytics**:
   - Tracks app usage patterns on the device
   - Helps identify performance issues
   - Provides insights for local features

4. **Export/Import Identification**:
   - Includes device ID in exported content metadata
   - Enables verification of content source during import
   - Preserves attribution when sharing between devices

5. **Local Security**:
   - Powers device-specific encryption keys
   - Controls biometric authentication settings
   - Manages local secure storage

### Cloud-Connected Usage

When connected to LogDate Cloud, device IDs enable additional functionality:

1. **Multi-Device Synchronization**:
   - Tracks which content has synced to which devices
   - Enables conflict resolution during content merging
   - Provides origin information for sync audit trails

2. **Push Notification Targeting**:
   - Enables sending notifications to specific devices
   - Powers notification preferences per device
   - Manages notification tokens from FCM/APNS

3. **Device Management**:
   - Shows users all devices linked to their account
   - Enables remote device management (rename, remove)
   - Supports security features like remote logout

4. **Sync Customization**:
   - Allows selective sync of content to specific devices
   - Respects device storage constraints
   - Enables device-specific content filtering

5. **Access Control**:
   - Provides device-level permissions within account
   - Supports limiting sensitive content to trusted devices
   - Enables revoking access from specific devices

6. **Cloud Security**:
   - Powers passkey authentication tied to specific devices
   - Enables secure device attestation
   - Supports trusted device networks for enhanced security

7. **Usage Telemetry**:
   - Provides anonymized usage data for product improvement
   - Tracks feature adoption across platforms
   - Helps identify platform-specific issues

## Implementation Guidelines

### Device Information Storage

1. **Local Storage**:
   - The device ID itself is stored in secure platform storage
   - Basic device info stored in local database for offline access
   - Sensitive device keys stored in secure enclave where available

2. **Cloud Storage**:
   - When using LogDate Cloud, device registry stored in user account
   - Contains basic device metadata (ID, name, platform, timestamps)
   - Does not store security keys or sensitive information

### Security Considerations

1. **ID Protection**:
   - Device ID should be treated as a sensitive identifier
   - Never log full device ID in application logs
   - Transmit only over secure connections (TLS)
   - Store using platform security best practices

2. **Access Controls**:
   - Only allow device management by authenticated account owner
   - Implement rate limiting for device operations
   - Verify ownership before device registration/removal

3. **Privacy Considerations**:
   - Include device ID management in privacy policy
   - Allow users to reset device ID if needed
   - Provide transparency about device information usage

### User Interface

1. **Device Management Screen**:
   - List all devices linked to account
   - Show device type, name, last active date
   - Allow renaming devices
   - Provide option to remove devices from account

2. **New Device Registration**:
   - Show clear notification when signing into new device
   - Provide device verification process for high-security use cases
   - Allow user to name device during setup

3. **Security Settings**:
   - Show current device information
   - Allow managing device-specific security settings
   - Provide option to reset device ID if needed

## Handling Edge Cases

### Device ID Resets

If a device ID needs to be reset (e.g., for privacy reasons):

1. **Local Reset Process**:
   - Generate new device ID
   - Update all local content to use new device ID
   - Maintain content attribution and history

2. **Cloud Notification**:
   - If using cloud, register new device ID with account
   - Mark old device ID as deprecated
   - Update server-side mappings to maintain content links

### App Reinstallation

When the app is reinstalled:

1. **New Installation Detection**:
   - Generate new device ID as original is lost
   - Check for existing cloud account during onboarding
   - If user signs in, handle as new device

2. **Content Recovery**:
   - After cloud sign-in, sync content from cloud
   - Restore user settings and preferences
   - Treat as new device for attribution going forward

### Offline to Cloud Transition

When a user creates a cloud account after using the app locally:

1. **Device Registration**:
   - Register existing device ID with new cloud account
   - Upload device metadata to cloud
   - Maintain local device settings

2. **Content Attribution**:
   - Preserve existing device attribution for local content
   - Include device ID in content synced to cloud
   - Maintain consistent identification across transition

## Practical Examples

### Example 1: User with Multiple Devices

1. User installs LogDate on their phone
   - Phone generates device ID: `DEVICE-A`
   - Local content tagged with `DEVICE-A`

2. User creates LogDate Cloud account on phone
   - `DEVICE-A` registered with cloud account
   - Device named "My Phone"

3. User installs LogDate on tablet
   - Tablet generates device ID: `DEVICE-B`
   - Signs into existing cloud account
   - Device registered as "My Tablet"

4. When viewing devices in settings:
   - User sees both "My Phone" and "My Tablet"
   - Can manage both devices
   - Content attribution preserved for both devices

### Example 2: Device Management

1. User notices old device in account
   - Views "Devices" section in settings
   - Sees "Old Phone" hasn't been active for months

2. User removes old device
   - Selects "Remove Device"
   - Confirms removal
   - Device unregistered from account
   - Push tokens for that device invalidated

3. If user later reinstalls on old phone
   - Generates new device ID
   - Requires fresh authentication
   - Registered as new device

## API Design

### Core Interfaces

The system is designed around the following key interfaces:

1. **DeviceIdProvider**
   - Responsible for retrieving or generating device IDs
   - Platform-specific implementations handle different environments
   - Ensures ID persistence across app lifecycles

2. **DeviceInfoProvider**
   - Retrieves metadata about the current device
   - Generates user-friendly device names
   - Provides platform and capability information

3. **DeviceRepository**
   - Manages the collection of user's devices
   - Handles registration with cloud services
   - Provides CRUD operations for device management

### Integration Points

The device identification system integrates with other systems through:

1. **Authentication System**
   - Associates devices with user accounts
   - Verifies device authorization
   - Handles device attestation for security

2. **Sync Framework**
   - Uses device IDs for conflict resolution
   - Tracks sync status per device
   - Enables device-specific sync policies

3. **Notification System**
   - Maps devices to notification tokens
   - Enables targeted notifications
   - Manages notification preferences per device

4. **Content Attribution**
   - Tags content with creation device
   - Preserves attribution through sync
   - Enables filtering by device

## Implementation Roadmap

1. **Phase 1: Basic Device Identification**
   - Implement platform-specific device ID generators
   - Create secure storage for IDs
   - Add ID to content metadata

2. **Phase 2: Local Device Management**
   - Create device info providers
   - Build device settings storage
   - Add device name customization

3. **Phase 3: Cloud Integration**
   - Implement device registration with cloud
   - Build device listing functionality
   - Create device management UI

4. **Phase 4: Enhanced Features**
   - Add selective sync capabilities
   - Implement trusted device network
   - Enable cross-device experiences

5. **Phase 5: Security Enhancements**
   - Add remote device management
   - Implement device attestation
   - Create security alerts for new devices

## Conclusion

The LogDate Device Identification System provides a robust foundation for device management, content attribution, and multi-device experiences. By creating unique, persistent device identifiers and building a comprehensive management system around them, we enable powerful features while respecting user privacy and maintaining the local-first principles of the application.

The system works seamlessly whether users choose to connect to LogDate Cloud or use the app in local-only mode, with additional capabilities unlocked when cloud connectivity is available. This flexible approach ensures that all users benefit from device identification while respecting different usage patterns and privacy preferences.