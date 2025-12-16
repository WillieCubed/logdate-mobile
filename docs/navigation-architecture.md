# Navigation Architecture in LogDate

## Overview

LogDate implements two different navigation systems across its multiplatform codebase:

1. **Navigation 3 (Navigation Compose 3)** - Used in the Android main Compose app
2. **Jetpack Navigation 2** - Used for shared code across all platforms

This dual approach allows us to leverage Navigation 3's advanced features for Android while maintaining multiplatform compatibility through the mature Navigation 2 system. This document explains both systems, their implementation, and how they coexist in the codebase.

## Navigation 3 (Android-Specific)

### Implementation

Navigation 3 is implemented in the Android-specific code in the main Compose app:

```
app/compose-main/src/androidMain/kotlin/app/logdate/navigation/
```

Key components include:
- `MainNavigationRoot.kt` - Entry point for Navigation 3 implementation
- `MainAppNavigator.kt` - Central navigator that manages the back stack
- Custom "scenes" like `HomeScene.kt` and specialized panes
- Route definitions in the `routes/` directory

### Key Features of Navigation 3

1. **Keys-Based Navigation**
   - Navigation is modeled as a stack of "keys" (NavKey) representing destinations
   - Direct manipulation of the back stack for more precise navigation control
   - Serializable keys for state preservation across configuration changes

2. **Scene-Based Layout System**
   - Scenes represent cohesive UI units that can display multiple navigation entries
   - SceneStrategy determines how navigation entries are organized in the UI
   - Support for complex, adaptive layouts that respond to screen size

3. **Adaptive UI Support**
   - Seamless switching between single-pane and multi-pane layouts
   - `HomeScene` implementation that adapts to device size:
     - Small screens: Bottom navigation + single content pane
     - Medium screens: Side navigation rail + single content pane
     - Large screens: Side navigation rail + two content panes (main + detail)

4. **Flexible Entry Management**
   - NavEntry contains both content and metadata
   - EntryProviderBuilder maps keys to content
   - Support for shared elements and complex transitions

### Navigation 3 Pattern in LogDate

```kotlin
// 1. Define route keys (in routes/core/ directory)
object TimelineListRoute : NavKey
data class TimelineDetail(val day: LocalDate) : NavKey

// 2. Define navigation actions (in route files)
fun MainAppNavigator.openTimelineDetail(day: LocalDate) {
    backStack.add(TimelineDetail(day))
}

// 3. Define entry providers (in route files)
fun EntryProviderBuilder<NavKey>.timelineRoutes(...) {
    entry<TimelineListRoute>(
        metadata = HomeScene.homeScene()
    ) { _ ->
        TimelinePaneScreen(...)
    }
    entry<TimelineDetail> { route ->
        TimelineDetailScreen(...)
    }
}

// 4. Use the navigator in UI components
onOpenDay = { day ->
    mainAppNavigator.openTimelineDetail(day)
}
```

## Jetpack Navigation 2 (Multiplatform)

### Implementation

Jetpack Navigation 2 is used in multiplatform code:

```
client/feature/*/src/commonMain/kotlin/app/logdate/feature/*/navigation/
```

Key implementation patterns:
- Route data classes with `@Serializable` annotation
- Navigation extension functions on `NavController`
- `NavGraphBuilder` extension functions for defining destinations

### Key Features of Navigation 2

1. **Mature, Stable API**
   - Well-documented with extensive community resources
   - Production-ready with predictable behavior

2. **Multiplatform Support**
   - Works across Android, iOS, and Desktop targets
   - Consistent navigation patterns across platforms

3. **Composable-Based Navigation**
   - NavHost and composable() for defining navigation graphs
   - Type-safe route navigation with animated transitions
   - Deep linking support

### Navigation 2 Pattern in LogDate

```kotlin
// 1. Define route data classes
@Serializable
data class JournalDetailsRoute(val journalId: String)

// 2. Define navigation extension functions
fun NavController.navigateToJournalDetails(journalId: String) {
    navigate(JournalDetailsRoute(journalId))
}

// 3. Define destination builders
fun NavGraphBuilder.journalDetailsDestination(
    onGoBack: () -> Unit,
    onEditJournal: (String) -> Unit
) {
    composable<JournalDetailsRoute> { backStackEntry ->
        val journalId = backStackEntry.arguments?.journalId
        JournalDetailScreen(
            journalId = journalId,
            onBack = onGoBack,
            onEditJournal = { onEditJournal(journalId) }
        )
    }
}

// 4. Use in NavHost
NavHost(navController, startDestination = BaseRoute) {
    journalDetailsDestination(
        onGoBack = { navController.popBackStack() },
        onEditJournal = { /* handle edit */ }
    )
}
```

## Integration Strategy

### How the Two Systems Coexist

1. **Platform-Specific Entry Points**
   - Android: `MainNavigationRoot.kt` using Navigation 3
   - Other platforms: `LogDateNavHost.kt` using Navigation 2

2. **Shared UI Components**
   - UI screens are implemented once and used by both navigation systems
   - View models are shared across platforms
   - Navigation parameters are normalized between systems

3. **Feature Modules**
   - Each feature module implements both navigation approaches
   - Common UI code in `commonMain` sources
   - Platform-specific navigation in respective source sets

### Navigation Consistency Guidelines

To maintain consistency across both systems:

1. **Route Naming**
   - Use consistent route naming conventions
   - Mirror route parameters between systems

2. **UI Component Design**
   - Design screens to be navigation-system agnostic
   - Use callbacks for navigation rather than direct dependencies

3. **View Model Approach**
   - View models should not depend on navigation implementation
   - Pass navigation callbacks from the navigation layer

## Decision Rationale

### Why Navigation 3 for Android?

1. **Adaptive Layouts**
   - Superior support for multi-pane layouts and different screen sizes
   - Scene-based approach perfect for tablet and foldable experiences

2. **Flexibility**
   - Direct back stack manipulation for complex navigation patterns
   - Better support for shared element transitions and animations

3. **Performance**
   - More efficient for complex navigation graphs
   - Better handling of deep linking and back navigation

### Why Keep Navigation 2 for Multiplatform?

1. **Maturity**
   - Stable API with production-ready implementation
   - Comprehensive documentation and community resources

2. **Multiplatform Support**
   - Works on all target platforms, not just Android
   - Well-integrated with KMP toolchain

3. **Migration Path**
   - Allows gradual migration as Navigation 3 matures
   - Maintains compatibility with existing code

## Future Direction

As Navigation 3 matures and potentially gains multiplatform support, our strategy will evolve:

1. **Short-term (Current)**
   - Use Navigation 3 for Android-specific UI
   - Use Navigation 2 for multiplatform shared code
   - Maintain consistency between implementations

2. **Mid-term**
   - Refine the Android Navigation 3 implementation
   - Improve integration between the two systems
   - Extend adaptive layout patterns to more features

3. **Long-term**
   - Evaluate Migration to Navigation 3 for all platforms if it gains multiplatform support
   - Otherwise, maintain the dual approach with consistent patterns

## Best Practices for LogDate Contributors

1. **When adding a new screen**
   - Implement the UI component in `commonMain`
   - Add Navigation 2 route in the feature's navigation package
   - Add Navigation 3 entry in the appropriate routes file for Android

2. **When modifying navigation logic**
   - Update both Navigation 2 and Navigation 3 implementations
   - Test on both small and large screens
   - Maintain consistent parameter passing

3. **When working with adaptive layouts**
   - Follow the patterns in `HomeScene` for new scenes
   - Consider both single-pane and multi-pane experiences
   - Test on different screen sizes and orientations