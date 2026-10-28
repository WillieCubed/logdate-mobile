# Android safe areas and editor chrome

Run the focused acceptance tests on a repository-declared Gradle Managed Device:

```sh
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorSafeAreaAcceptanceTest,app.logdate.client.e2e.SurfaceSafeAreaAcceptanceTest \
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
