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
// app/compose-main/src/screenshotTest/kotlin/app/logdate/screenshots/OnboardingScreenshots.kt
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
app/compose-main/src/screenshotTest/kotlin/app/logdate/screenshots/
├── EditorScreenshots.kt       # Editor feature screenshots
├── OnboardingScreenshots.kt   # Onboarding flow screenshots
├── TimelineScreenshots.kt     # Timeline browsing screenshots
├── JournalScreenshots.kt      # Journal management screenshots
├── RewindScreenshots.kt       # Rewind feature screenshots
└── ComponentsScreenshots.kt   # Reusable component screenshots
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

### Generate Baseline Images

Create or update reference images for the first time or after intentional changes:

```bash
# Update all baselines
./gradlew :app:compose-main:updateDebugScreenshotTest

# This generates images in: app/compose-main/src/screenshotTestDebug/reference/
```

**In CI/CD**: Commit baseline images to version control alongside your code.

### Validate Against Baselines

Check if current screenshots match baselines:

```bash
# Validate all screenshots
./gradlew :app:compose-main:validateDebugScreenshotTest

# Validate specific screenshot (if supported)
./gradlew :app:compose-main:validateDebugScreenshotTest --tests "OnboardingScreenshots.onboardingStart"
```

**In CI/CD**: This command runs automatically on pull requests.

### Review Failed Screenshots

When validation fails, check the diff:

```bash
open app/compose-main/build/outputs/screenshotTest-results/preview/debug/
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
   ./gradlew :app:compose-main:updateDebugScreenshotTest
   ```

4. Commit baseline image to git:
   ```bash
   git add app/compose-main/src/screenshotTestDebug/reference/
   ```

### During Development

1. Make UI changes

2. Run validation to check impact:
   ```bash
   ./gradlew :app:compose-main:validateDebugScreenshotTest
   ```

3. If changes are intentional, update baseline:
   ```bash
   ./gradlew :app:compose-main:updateDebugScreenshotTest
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
app/compose-main/src/screenshotTestDebug/reference/
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
./gradlew :app:compose-main:updateDebugScreenshotTest

# Commit the new baseline
git add app/compose-main/src/screenshotTestDebug/reference/
git commit -m "refactor(ui): update screenshots for new design"
```

### Removing Baselines

When deleting screenshot tests:

```bash
# Remove the @PreviewTest function
# Remove the reference image
git rm app/compose-main/src/screenshotTestDebug/reference/path/to/image.png
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

## KMP Considerations

Google Compose Screenshot Testing works with Android targets only. For iOS/Desktop:

1. **Android (Screenshot Tests)**: Use Google's screenshot plugin
2. **iOS/Desktop**: Use separate screenshot tools or manual testing

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
      - 'app/compose-main/**'
      - 'client/feature/**'

jobs:
  screenshot-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate Screenshots
        run: ./gradlew :app:compose-main:validateDebugScreenshotTest
      - name: Upload Results
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: screenshot-diffs
          path: app/compose-main/build/outputs/screenshotTest-results/
```

## Troubleshooting

### "Expected images not found"

Baseline images missing. Generate them:
```bash
./gradlew :app:compose-main:updateDebugScreenshotTest
git add app/compose-main/src/screenshotTestDebug/reference/
```

### "Images do not match"

Visual changes detected. Options:

1. If changes are unintended: Fix the UI code
2. If changes are intentional: Update baseline
   ```bash
   ./gradlew :app:compose-main:updateDebugScreenshotTest
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
