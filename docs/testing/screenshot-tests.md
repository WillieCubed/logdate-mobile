# Screenshot Testing Guide

Screenshot tests compare rendered Compose previews against baseline images to detect unintended visual changes. LogDate uses Google's Compose Preview Screenshot Testing plugin for automated visual regression testing.

## Overview

Screenshot testing captures the visual appearance of your Compose previews and compares them against baseline images. Any visual changes trigger test failures, helping catch unintended UI changes.

**Technology**: Google Compose Preview Screenshot Testing (com.android.compose.screenshot)
**Requirements**: Android Gradle Plugin 8.5.0+, Kotlin 1.9.20+
**Workflows**: Light/dark themes, multiple screen sizes

## Creating Screenshot Tests

### Basic Preview Test

Screenshot tests are Compose previews marked with `@PreviewTest` annotation. Place them in `src/screenshotTest/kotlin/`:

```kotlin
// app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/OnboardingScreenshots.kt
package app.logdate.screenshots

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.ui.theme.LogDateTheme

@PreviewTest
@Preview(showBackground = true)
@Composable
fun OnboardingStart_Default() {
    LogDateTheme {
        OnboardingStartScreen(
            onNext = {},
            onStartFromBackup = {}
        )
    }
}
```

### Light and Dark Variants

Use `@PreviewLightDark` to test both themes automatically:

```kotlin
@PreviewTest
@PreviewLightDark
@Composable
fun EditorScreen_LightDark() {
    LogDateTheme {
        EditorScreenContent(
            state = EditorState.Editing(entry),
            onSave = {},
            onCancel = {}
        )
    }
}
```

This generates two baseline images: one for light theme, one for dark theme.

### Multiple Screen Sizes

Test across device sizes using `@Preview` parameters:

```kotlin
@PreviewTest
@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Composable
fun TimelineScreen_Phone() {
    LogDateTheme {
        TimelineScreenContent()
    }
}

@PreviewTest
@Preview(name = "Tablet", widthDp = 800, heightDp = 1200)
@Composable
fun TimelineScreen_Tablet() {
    LogDateTheme {
        TimelineScreenContent()
    }
}

@PreviewTest
@Preview(name = "Landscape", widthDp = 900, heightDp = 400)
@Composable
fun TimelineScreen_Landscape() {
    LogDateTheme {
        TimelineScreenContent()
    }
}
```

## Test Organization

Organize screenshot tests by feature:

```
app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/
├── EditorScreenshots.kt       # Editor feature screenshots
├── OnboardingScreenshots.kt   # Onboarding flow screenshots
├── TimelineScreenshots.kt     # Timeline browsing screenshots
├── JournalScreenshots.kt      # Journal management screenshots
├── RewindScreenshots.kt       # Rewind feature screenshots
└── ComponentsScreenshots.kt   # Reusable component screenshots

app/wear/src/screenshotTest/kotlin/app/logdate/wear/screenshots/
├── WearScreenshotPreviewMatrix.kt  # Multi-preview for small + large round
├── WearHomeScreenshots.kt          # Home hub states
├── WalkieTalkieScreenshots.kt      # All walkie-talkie phases
├── AudioRecordingScreenshots.kt    # Recording screen states
└── MoodCheckInScreenshots.kt       # Mood check-in flow
```

Each file groups related preview functions for a feature or screen.

## Guidelines for Screenshot Tests

### What to Screenshot

Capture key user-facing screens and states:

```kotlin
// Onboarding journey
@PreviewTest @PreviewLightDark
fun OnboardingWelcome() { }

@PreviewTest @PreviewLightDark
fun OnboardingPermissions() { }

@PreviewTest @PreviewLightDark
fun OnboardingComplete() { }

// Core screens
@PreviewTest @PreviewLightDark
fun TimelineScreen() { }

@PreviewTest @PreviewLightDark
fun EditorEmpty() { }

@PreviewTest @PreviewLightDark
fun JournalDetail() { }

// Important states
@PreviewTest @PreviewLightDark
fun LoadingState() { }

@PreviewTest @PreviewLightDark
fun ErrorState() { }

@PreviewTest @PreviewLightDark
fun EmptyState() { }
```

### What NOT to Screenshot

Skip screenshots for:
- Low-level component variations (use UI tests instead)
- Implementation details
- Every single state combination (select important ones)
- Components that change frequently during development

## Running Screenshot Tests

Screenshot tests are host-side preview tests and do not use a connected Android device. For any Android runtime verification you do alongside screenshot work, target the emulator explicitly instead of relying on whichever device is attached:

```bash
export ANDROID_SERIAL=emulator-5554
adb -s emulator-5554 devices
```

### Generate Baseline Images

Create or update reference images for the first time or after intentional changes:

```bash
# Update baselines — phone app
./gradlew :app:android-main:updateDebugScreenshotTest

# Update baselines — Wear OS app
./gradlew :app:wear:updateDebugScreenshotTest

# Update baselines — Desktop app
./gradlew :app:compose-main:updateDesktopScreenshotTest
```

Phone baselines: `app/android-main/src/screenshotTestDebug/reference/`
Wear baselines: `app/wear/src/screenshotTestDebug/reference/`
Desktop baselines: `app/compose-main/src/desktopTest/reference/`

**In CI/CD**: Commit baseline images to version control alongside your code.

### Validate Against Baselines

Check if current screenshots match baselines:

```bash
# Validate — phone app
./gradlew :app:android-main:validateDebugScreenshotTest

# Validate — Wear OS app
./gradlew :app:wear:validateDebugScreenshotTest

# Validate — Desktop app
./gradlew :app:compose-main:validateDesktopScreenshotTest
```

**In CI/CD**: This command runs automatically on pull requests.

### Review Failed Screenshots

When validation fails, check the diff:

```bash
open app/android-main/build/outputs/screenshotTest-results/preview/debug/
```

Inspect:
- `rendered/` - Current screenshots
- `reference/` - Baseline screenshots
- `diffs/` - Difference highlights (if generated)

## Workflow Example

### Initial Setup

1. Write your Compose screen:
   ```kotlin
   @Composable
   fun MyNewScreen() { }
   ```

2. Create screenshot test:
   ```kotlin
   @PreviewTest
   @PreviewLightDark
   fun MyNewScreen_LightDark() {
       LogDateTheme {
           MyNewScreen()
       }
   }
   ```

3. Generate baseline:
   ```bash
   ./gradlew :app:android-main:updateDebugScreenshotTest
   ```

4. Commit baseline image to git:
   ```bash
   git add app/android-main/src/screenshotTestDebug/reference/
   ```

### During Development

1. Make UI changes

2. Run validation to check impact:
   ```bash
   ./gradlew :app:android-main:validateDebugScreenshotTest
   ```

3. If changes are intentional, update baseline:
   ```bash
   ./gradlew :app:android-main:updateDebugScreenshotTest
   ```

4. Review and commit the new baseline

### Code Review

1. Pull request includes UI changes
2. CI runs screenshot validation
3. Reviewer sees generated diff
4. If approved, baseline update is part of commit

## Managing Baseline Images

### Storing Baselines

Baseline images live in version control:
```
app/android-main/src/screenshotTestDebug/reference/
├── app/logdate/screenshots/
│   ├── EditorScreenKt/
│   │   ├── EditorEmpty_light.png
│   │   └── EditorEmpty_dark.png
│   └── TimelineScreenKt/
│       ├── Timeline_light.png
│       └── Timeline_dark.png
```

### Updating Baselines

When a screenshot fails but changes are intentional:

```bash
# Update specific screenshot
./gradlew :app:android-main:updateDebugScreenshotTest

# Commit the new baseline
git add app/android-main/src/screenshotTestDebug/reference/
git commit -m "refactor(ui): update screenshots for new design"
```

### Removing Baselines

When deleting screenshot tests:

```bash
# Remove the @PreviewTest function
# Remove the reference image
git rm app/android-main/src/screenshotTestDebug/reference/path/to/image.png
```

## Advanced Testing

### Testing Complex States

```kotlin
@PreviewTest
@PreviewLightDark
fun EditorWithSuggestionsExpanded() {
    LogDateTheme {
        EditorScreenContent(
            state = EditorState.Editing(
                entry = testEntry(),
                suggestionsVisible = true,
                suggestions = listOf("suggestion1", "suggestion2")
            ),
            onSave = {},
            onCancel = {}
        )
    }
}

@PreviewTest
@PreviewLightDark
fun TimelineWithLoadingError() {
    LogDateTheme {
        TimelineScreenContent(
            state = TimelineState.Error(
                message = "Failed to load entries",
                retryAvailable = true
            ),
            onRetry = {}
        )
    }
}
```

### Testing Different Data

```kotlin
@PreviewTest
@PreviewLightDark
fun JournalDetailWithManyEntries() {
    LogDateTheme {
        JournalDetail(
            journal = testJournal().copy(
                entries = (1..20).map { testEntry(content = "Entry $it") }
            )
        )
    }
}

@PreviewTest
@PreviewLightDark
fun JournalDetailEmpty() {
    LogDateTheme {
        JournalDetail(
            journal = testJournal().copy(entries = emptyList())
        )
    }
}
```

## Wear OS screenshots

Wear OS screenshot tests render on round watch displays instead of rectangular phone screens.
A custom multi-preview annotation targets both supported form factors:

```kotlin
// app/wear/src/screenshotTest/.../WearScreenshotPreviewMatrix.kt
@Preview(name = "Small Round", device = "id:wearos_small_round", showBackground = true)
@Preview(name = "Large Round", device = "id:wearos_large_round", showBackground = true)
annotation class WearScreenshotPreviewMatrix
```

Use `@WearScreenshotPreviewMatrix` instead of `@PreviewLightDark` for Wear OS screens:

```kotlin
@PreviewTest
@WearScreenshotPreviewMatrix
@Composable
fun S01_WalkieTalkieReady() {
    MaterialTheme {
        ReadyContent(onTouchDown = {}, onTouchUp = {})
    }
}
```

Each preview generates two baselines (small round + large round). Wrap content in Wear
`MaterialTheme` (not phone `LogDateTheme`).

```bash
# Generate Wear OS baselines
./gradlew :app:wear:updateDebugScreenshotTest

# Validate Wear OS screenshots
./gradlew :app:wear:validateDebugScreenshotTest
```

## KMP considerations

Google Compose Screenshot Testing works with Android targets only. LogDate now wraps that limitation in a repo-level universal screenshot lane:

1. **Android and Wear**: real baseline screenshot validation via `com.android.compose.screenshot`
2. **Desktop**: real committed baseline validation via `:app:compose-main:validateDesktopScreenshotTest`
3. **iOS**: simulator-side `iosSimulatorArm64Test` validation for screenshot-covered UI modules

Use the root tasks when you want the platform-agnostic entrypoint:

```bash
# Validate the full universal screenshot lane
./gradlew validateAllScreenshots

# Update every supported baseline lane
./gradlew updateAllScreenshots

# Per-platform entrypoints
./gradlew validateAndroidScreenshots
./gradlew validateDesktopScreenshots
./gradlew validateIosScreenshots
```

Desktop now participates as a real baseline lane. `updateAllScreenshots` updates Android, Wear, and Desktop baselines. iOS remains validate-only until a committed iOS screenshot baseline renderer lands.

```kotlin
// This only runs on Android
@PreviewTest
@PreviewLightDark
fun ComposablePreview() {
    // Android screenshot test
}
```

## CI/CD Integration

### GitHub Actions

```yaml
# .github/workflows/screenshot-tests.yml
name: Screenshot Tests
on:
  pull_request:
    paths:
      - 'app/android-main/**'
      - 'app/wear/**'
      - 'app/compose-main/**'
      - 'client/feature/**'

jobs:
  android-wear-screenshots:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate phone screenshots
        run: ./gradlew :app:android-main:validateDebugScreenshotTest
      - name: Validate Wear OS screenshots
        run: ./gradlew :app:wear:validateDebugScreenshotTest
      - name: Upload results
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: android-wear-screenshot-diffs
          path: |
            app/android-main/build/outputs/screenshotTest-results/
            app/wear/build/outputs/screenshotTest-results/

  desktop-screenshots:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate desktop screenshots
        run: ./gradlew :app:compose-main:validateDesktopScreenshotTest
      - name: Upload results
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: desktop-screenshot-diffs
          path: |
            app/compose-main/build/reports/desktopScreenshotTest/

  ios-screenshot-validation:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate iOS screenshot lane
        run: ./gradlew validateIosScreenshots
```

## Troubleshooting

### "Expected images not found"

Baseline images missing. Generate them:
```bash
# Phone
./gradlew :app:android-main:updateDebugScreenshotTest
git add app/android-main/src/screenshotTestDebug/reference/

# Wear OS
./gradlew :app:wear:updateDebugScreenshotTest
git add app/wear/src/screenshotTestDebug/reference/

# Desktop
./gradlew :app:compose-main:updateDesktopScreenshotTest
git add app/compose-main/src/desktopTest/reference/
```

### "Images do not match"

Visual changes detected. Options:

1. If changes are unintended: Fix the UI code
2. If changes are intentional: Update baseline
   ```bash
   ./gradlew :app:android-main:updateDebugScreenshotTest  # phone
   ./gradlew :app:wear:updateDebugScreenshotTest           # wear
   ./gradlew :app:compose-main:updateDesktopScreenshotTest # desktop
   ```

### Screenshot filename confusion

Filenames are auto-generated from:
- Package: `app.logdate.screenshots`
- Class: `EditorScreenshotsKt`
- Function: `EditorEmpty`
- Theme: `_light.png` or `_dark.png`

Example:
```
app/logdate/screenshots/EditorScreenshotsKt/EditorEmpty_light.png
app/logdate/screenshots/EditorScreenshotsKt/EditorEmpty_dark.png
```

## Best Practices

1. **Screenshot Critical Journeys Only**: Focus on key screens
2. **Use Light/Dark Variants**: Always test both themes
3. **Keep Baselines in Git**: Version control your visual expectations
4. **Review Diffs in PR**: Let reviewers see what changed
5. **Document State**: Include comments for non-obvious test setup
6. **Update Regularly**: Keep baselines current during development
7. **Combine with UI Tests**: Screenshots for appearance, UI tests for behavior

## Resources

- [Compose Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)
- [Google Issue #402137754](https://issuetracker.google.com/issues/402137754) - KMP compatibility
- [Visual Regression Testing Best Practices](https://www.smashingmagazine.com/2021/06/visual-regression-testing/)
## Desktop Screenshot Operations

Desktop screenshot validation for `app/compose-main` runs through the normal desktop unit test task and does not require any Android device, emulator, `adb`, or connected-device task.

Core tasks:

```bash
# Validate committed desktop baselines
./gradlew :app:compose-main:desktopTest \
  --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared_catalog_matches_baselines'

# Convenience alias for the same validation lane
./gradlew :app:compose-main:validateDesktopScreenshotTest

# Refresh committed desktop baselines
./gradlew :app:compose-main:updateDesktopScreenshotTest
```

Default behavior:

- Desktop screenshot tests now run in hidden mode by default.
- The harness creates a displayable off-screen surface without forcing a visible foreground window.
- Hidden mode avoids stealing focus from the current desktop window.
- Hidden mode also avoids hover and focus contamination from the mouse pointer during screenshot capture.

Visible mode:

Use visible mode only when you explicitly want to watch screenshots render live on the desktop.

```bash
./gradlew -Dlogdate.desktopScreenshots.visible=true \
  :app:compose-main:desktopTest \
  --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared_catalog_matches_baselines'
```

Scene filtering:

Use `logdate.desktopScreenshots.sceneFilter` to run only a subset of the shared screenshot catalog. The filter matches `SharedScreenshotSceneId.value` substrings.

```bash
# Run only onboarding-start scenes
./gradlew -Dlogdate.desktopScreenshots.sceneFilter=onboarding-start \
  :app:compose-main:desktopTest \
  --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared_catalog_matches_baselines'

# Run only settings scenes and show them live
./gradlew \
  -Dlogdate.desktopScreenshots.visible=true \
  -Dlogdate.desktopScreenshots.sceneFilter=settings \
  :app:compose-main:desktopTest \
  --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared_catalog_matches_baselines'
```

Desktop baseline locations:

- References: `app/compose-main/src/desktopTest/reference`
- Actual renders: `app/compose-main/build/reports/desktopScreenshotTest/actual`
- Pixel diffs: `app/compose-main/build/reports/desktopScreenshotTest/diff`

Recommended workflow for desktop screenshot changes:

1. Narrow the scope with `logdate.desktopScreenshots.sceneFilter` while iterating on a scene.
2. Run hidden mode by default to avoid desktop interference.
3. Use visible mode only when you need to inspect transitions or layout behavior live.
4. Refresh baselines with `:app:compose-main:updateDesktopScreenshotTest` after the render path is deterministic.
5. Re-run `:app:compose-main:desktopTest --tests 'app.logdate.screenshots.DesktopScreenshotTest.shared_catalog_matches_baselines'` before committing.
