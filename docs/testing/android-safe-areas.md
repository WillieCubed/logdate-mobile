# Android safe areas and editor chrome

Run the focused acceptance tests on a repository-declared Gradle Managed Device:

```sh
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorSafeAreaAcceptanceTest,app.logdate.client.e2e.SurfaceSafeAreaAcceptanceTest,app.logdate.client.e2e.EditorGeometryAcceptanceTest \
  -Plogdate.androidTestCoverage=false \
  -Plogdate.androidTestOrchestrator=false
```

The production editor journey types into the real Android IME, shows and hides it,
rotates between portrait and landscape, and repeats with gesture and three-button
navigation. It temporarily enables the image's double-cutout overlay and restores
the previous overlay, navigation mode, keyboard setting, and default entry mode.
It checks unclipped control and writing bounds, then interrupts both directions
of the focus transition using pointer input. Settings and sheet tests exercise
the shared production components in a real edge-to-edge Android window.

Captures are bounded to 15 PNGs in the managed device's additional output:

```text
app/android-main/build/intermediates/managed_device_android_test_additional_output/
  debugAndroidTest/phoneApi34DebugAndroidTest/
    editor-safe-area/
    surface-safe-area/
```

Inspect those images. Bounds and semantics alone do not prove a control was painted
above an animation overlay or that a writing line is visible. In particular, check
the landscape keyboard image, the transition frames, and the sheet's bottom action.
These focused scenes do not establish a complete app visual or accessibility audit.

Headless regression tests independently cover inset consumption, keyboard-sized
viewports, header paint order and pointer delivery during interrupted transitions,
and recording transport ownership. Run the affected suites with:

```sh
./gradlew :client:ui:desktopTest :client:feature:editor:desktopTest
```

The frame owns system insets and persistent chrome. Shared transition overlays belong
to `EditorContentLayer`, below that chrome. A recording or stopping take owns its
transport area; the capture selector returns when that ownership ends. In short
keyboard viewports both chrome rows use 48dp targets to preserve space for writing.

## Memory geometry and continuity

Inline memory surfaces have a shared 120dp minimum, including empty, loading,
failed and ready content. This reuses the editor surface's established minimum;
48dp is an interactive target size, not a document block height. Taller content
can grow. Focused content adapts to the available keyboard viewport instead of
forcing the inline minimum into a shorter writing area.

Overview memory surfaces and mode-selector ends share a centered rail capped at
640dp with 16dp framing inside the safe area. The journal chooser centers on the
safe header using equal reservations for the measured leading and trailing
actions. Below 360dp, Save uses an accessible icon button so the chooser retains
at least a 48dp target; long titles ellipsize without overlapping controls.

`EntryGeometryContractTest` measures both edges, chooser center and target size
(including 280dp, 200% text and RTL), the surface minimum through content changes
and sampled focus frames, and stable semantics identity through capture-state
updates and insertion of another memory. Overview compositions key memories by
block ID, independently of capture state and recording elapsed time.

`EditorGeometryAcceptanceTest` measures the production block height, chooser
center and both rail edges in portrait/landscape at normal and 200% Android text.
It exercises the real IME and captures six bounded images in `editor-geometry/`,
including the two large-text keyboard states. Inspect their pixels separately
from the measurements. Native runs do not cover a 280dp window or RTL; those are
headless layout regressions.
