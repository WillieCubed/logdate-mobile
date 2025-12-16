# `:client:location`

**Location services and context-aware features**

## Overview

Handles location tracking, place detection, and location-based features for automatic entry context. Provides privacy-focused location services with intelligent place recognition.

## Architecture

```
Location Module
├── Location Providers
├── Place Detection
├── Geofencing
├── Privacy Controls
└── Platform Implementations
```

## Key Components

### Core Location
- `ClientLocationProvider.kt` - Common location interface
- `AndroidLocationProvider.kt` - Android location implementation
- Platform-specific location services

### Location Features
- Real-time location tracking
- Place detection and recognition
- Location history management
- Geofencing for automatic triggers

## Features

### Location Tracking
- GPS and network-based positioning
- Battery-efficient location updates
- Configurable accuracy and frequency
- Background location tracking

### Place Detection
- Automatic place recognition
- Custom place creation and management
- Place visit detection
- Significant location changes

### Privacy Controls
- Granular location permissions
- Location data anonymization
- Local-only location storage option
- Location sharing controls

## Dependencies

### Platform Dependencies
- **Android**: Google Play Services Location
- **iOS**: Core Location framework
- **Desktop**: IP-based location (limited)

### Core Dependencies
- `:client:database` - Location data storage
- **Kotlinx Coroutines**: Reactive location updates

## Usage Patterns

### Location Tracking
```kotlin
val locationProvider = ClientLocationProvider()
locationProvider.locationUpdates.collect { location ->
    // Handle location update
    processLocationChange(location)
}
```

### Place Detection
```kotlin
val places = locationProvider.detectNearbyPlaces(location)
val currentPlace = places.firstOrNull { it.isWithinBounds(location) }
```

## Privacy Considerations

### Data Minimization
- Collect only necessary location data
- Automatic data retention policies
- Location precision adjustment
- Anonymous location analytics

### User Control
- Explicit location permission requests
- Granular privacy settings
- Location data export/deletion
- Transparent data usage disclosure

## Platform Features

### Android
- **Fused Location Provider**: Battery-efficient positioning
- **Geofencing API**: Location-based triggers
- **Places API**: Place detection and details
- **Activity Recognition**: Movement detection

### iOS
- **Core Location**: Native location services
- **MapKit**: Place search and details
- **Region Monitoring**: Geofencing capabilities
- **Visit Monitoring**: Significant location visits

### Desktop
- **IP Geolocation**: Approximate location
- **Manual Location**: User-specified locations
- **Time Zone Detection**: Automatic time zone updates

## Known Issues

### Location Logging Failures
- **Silent failures**: When location logging fails permanently (after 5 retry attempts), users are never notified
- **Data loss**: Failed location logs result in permanent loss of location context for journal entries
- **No user feedback**: Users have no visibility into retry status or permanent failures
- **No manual recovery**: No option for users to manually trigger location capture for failed entries
- **Non-persistent retries**: Pending location retries are lost if the app is killed or restarted

### Recommended Improvements
- [ ] Add UI indicators for location retry status
- [ ] Implement user notifications for permanent location logging failures
- [ ] Add manual location tagging as fallback option
- [ ] **Implement persistent retry queue**: Database-backed retry queue that survives app restarts and device reboots
- [ ] Implement graceful degradation with lower-accuracy location sources
- [ ] Add settings to configure retry behavior and user preferences

## TODOs

### Core Location Features
- [ ] Implement privacy-focused location tracking
- [ ] Add offline place detection capabilities
- [ ] Implement comprehensive location history
- [ ] Add location-based entry reminders
- [ ] Implement battery-efficient tracking algorithms
- [ ] Add custom place management UI
- [ ] Implement location sharing with contacts
- [ ] Add location-based entry suggestions

### Advanced Features
- [ ] Implement indoor location tracking
- [ ] Add location clustering and analysis
- [ ] Implement location-based insights
- [ ] Add travel pattern recognition
- [ ] Implement location-based mood correlation
- [ ] Add location photo integration
- [ ] Implement location weather integration
- [ ] Add location-based social features

### Privacy & Security
- [ ] Implement location data encryption
- [ ] Add location spoofing detection
- [ ] Implement location data anonymization
- [ ] Add location audit trails
- [ ] Implement location access monitoring
- [ ] Add location data backup/restore
- [ ] Implement location compliance features
- [ ] Add location security alerts