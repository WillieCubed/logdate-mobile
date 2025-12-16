# `:client:feature:location-timeline`

**Location history tracking and visualization**

## Overview

Provides location timeline tracking and visualization capabilities, showing users their movement history with contextual information about places visited and time spent at each location.

## Architecture

```
Location Timeline Feature
├── Timeline UI
├── Location History Management
├── Place Resolution
└── Permission Handling
```

## Key Components

### Core Components

- `LocationTimelineScreen.kt` - Main location history interface
- `LocationTimelineViewModel.kt` - Location timeline state management
- `LocationTimelineUiState.kt` - UI state representation
- `LocationTimelineModule.kt` - Dependency injection

### UI Components

- `LocationTimelineCard` - Individual location entry display
- `CurrentLocationCard` - Current location visualization
- `EmptyLocationTimeline` - Empty state handling
- `LocationPermissionRequiredScreen` - Permission request interface

## Features

### Location Timeline

- **Location History**: Chronological display of visited locations
- **Current Location**: Real-time location tracking and display
- **Place Recognition**: Automatic resolution of coordinates to place names
- **Stay Duration**: Time spent at each location
- **Address Display**: Detailed address information for each location
- **Timeline Navigation**: Scrollable history of locations

### Location Management

- **History Tracking**: Automatic tracking of significant location changes
- **Location Deletion**: Ability to remove individual location entries
- **Permission Management**: Proper handling of location permissions
- **Error Handling**: Graceful handling of location service failures
- **Empty States**: User guidance when no history is available

### Privacy Features

- **Permission Controls**: Clear permission requesting and handling
- **Data Control**: User ability to delete location entries
- **Transparent UI**: Clear indication of location data usage
- **Location Resolution**: On-device place name resolution

## Dependencies

### Core Dependencies

- `:client:domain` - Business logic
- `:client:ui` - Shared UI components
- `:client:repository` - Data access
- `:client:location` - Location services
- `:client:permissions` - Permission handling
- **Compose Multiplatform**: UI framework
- **Material 3**: Design components
- **Kotlinx DateTime**: Date and time handling

## Usage Patterns

### Location Timeline Display

```kotlin
@Composable
fun LocationTimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: LocationTimelineViewModel = koinViewModel()
) {
    val locationPermissionState = rememberLocationPermissionState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Permission handling
    if (!locationPermissionState.hasPermission) {
        LocationPermissionRequiredScreen(
            onPermissionGranted = { viewModel.refreshData() },
            modifier = modifier
        )
        return
    }
    
    // Timeline content display
    LocationTimelineContent(
        uiState = uiState,
        onDeleteLocation = viewModel::deleteLocationEntry,
        modifier = Modifier.fillMaxSize()
    )
}
```

### Location Card Implementation

```kotlin
@Composable
private fun LocationTimelineCard(
    locationItem: LocationTimelineItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Location icon
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = locationItem.placeName)
                Text(text = locationItem.address)
                Text(text = locationItem.timeAgo)
                
                if (locationItem.duration != null) {
                    Text(text = "Stayed for ${locationItem.duration}")
                }
            }
            
            // Delete button
        }
    }
}
```

## Dependency Injection

```kotlin
val locationTimelineModule = module {
    viewModel { LocationTimelineViewModel(get(), get(), get()) }
    
    // Additional location timeline dependencies
}
```

## TODOs

### Core Features
- [ ] Add map visualization of location history
- [ ] Implement timeline filtering capabilities
- [ ] Add search functionality for locations
- [ ] Implement location categorization
- [ ] Add location statistics and insights

### Location Quality Improvements
- [ ] Improve place name resolution accuracy
- [ ] Add user-defined place naming
- [ ] Implement favorite/important locations
- [ ] Add visit frequency tracking
- [ ] Improve duration calculation for stays

### Privacy Enhancements
- [ ] Add location history export capability
- [ ] Implement bulk deletion options
- [ ] Add location data retention controls
- [ ] Implement location privacy dashboard
- [ ] Add granular location tracking preferences

### UI Improvements
- [ ] Add timeline grouping by day/week/month
- [ ] Implement map-timeline integration
- [ ] Add location details expanded view
- [ ] Improve location card visualization
- [ ] Add animations for timeline interactions