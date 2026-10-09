# `:client:screenshot-scenes`

**Screenshot scenes that are shared by the Android, desktop, and iOS screenshot tests.**

## Overview

A screenshot test needs a screen in a known state: fake data, fixed dates, and no network. Writing
that state separately for Android, desktop, and iOS would drift. This module defines each scene once,
in common Kotlin, and every platform's screenshot lane renders the same scene:

| Lane | Renders scenes with | References |
| --- | --- | --- |
| Android | Compose preview screenshot tests in `app/android-main/src/screenshotTest/` | `app/android-main/src/screenshotTestDebug/reference/` |
| Desktop | `DesktopScreenshotTest` in `app/compose-main/src/desktopTest/` | `app/compose-main/src/desktopTest/reference/` |
| iOS | `iosSimulatorArm64Test` in the screenshot host modules | validated only |

## Architecture

Everything lives in `src/commonMain/kotlin/app/logdate/screenshots/shared/SharedScreenshotCatalog.kt`
(and `CampfireScreenshotScenes.kt`).

| Type | What it is |
| --- | --- |
| `SharedScreenshotSceneId` | A stable string id per scene, for example `search-idle`. Baseline file names derive from it, so treat it as permanent. |
| `ScreenshotSceneGroup` | The group a scene belongs to: onboarding, settings, search, and so on |
| `ScreenshotViewportId` | A named viewport: phone, phone dark, landscape, tablet, split, desktop sizes, and foldable book and tabletop postures (with a synthetic 24dp hinge) |
| `ScreenshotSceneVariant` | One viewport a scene must render in |
| `SharedScreenshotSceneSpec` | A scene: id, group, variants, and its composable content |
| `SharedScreenshotCatalog.allScenes` | Every registered scene |
| `SharedScreenshotScene(id)` | Renders one scene |
| `screenshotBaselineName(scene, variant)` | The baseline file name, `<scene-id>--<viewport-suffix>` |

Scenes render the **stateless content composables** of each screen (for example
`AccountContent(uiState, ...)`), never routes or view models. A scene therefore needs no Koin,
database, or network.

## Usage Patterns

### Adding a scene

1. Add an id to `SharedScreenshotSceneId`.
2. Register a `sharedScene(id, group, variants) { ... }` in the right list. Most screens use the
   standard matrix: phone, phone dark, landscape, and tablet.
3. On Android, add a preview function that renders it:

   ```kotlin
   @PreviewTest
   @ScreenshotPreviewMatrix
   @Composable
   fun S01_SearchIdleWithRecents() = SharedSearchScene(SharedScreenshotSceneId.SearchIdle)
   ```

4. Record references on each lane:

   ```bash
   ./gradlew :app:android-main:updateDebugScreenshotTest
   ./gradlew :app:compose-main:updateDesktopScreenshotTest
   ```

5. Commit the scene and its references together.

## Dependencies

The module depends on the modules whose content composables and models it renders:

- **Features:** `:client:feature:core`, `:client:feature:events`, `:client:feature:onboarding`,
  `:client:feature:rewind`, `:client:feature:search`
- **UI:** `:client:ui`
- **Data and services:** `:client:domain`, `:client:repository`, `:client:media`,
  `:client:location`, `:client:sync`, `:client:billing`
- **Compose Multiplatform:** runtime, foundation, and Material 3. Desktop adds
  `compose.desktop.currentOs`.

## Testing

```bash
./gradlew :client:screenshot-scenes:allTests
```

`SharedScreenshotCatalogTest` checks that:

- scene ids are unique;
- every scene has at least one variant;
- baseline names never collide.

See [`docs/testing/screenshot-tests.md`](../../docs/testing/screenshot-tests.md) for the full screenshot
workflow.
