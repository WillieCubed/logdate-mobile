# Android safe areas and editor chrome

Run the focused acceptance tests on a repository-declared Gradle Managed Device:

```sh
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorSafeAreaAcceptanceTest,app.logdate.client.e2e.SurfaceSafeAreaAcceptanceTest,app.logdate.client.e2e.EditorGeometryAcceptanceTest,app.logdate.client.e2e.EditorLongPageAcceptanceTest \
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

The frame owns persistent chrome and provides safe insets to the inner document
and control layout. Full-window surfaces and imagery paint behind the system bars;
do not pad or clip the whole transition layer to the safe rectangle. Shared transition overlays belong
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
640dp with 16dp framing inside the safe area. The final scroll target reserves the selector
height, one 16dp bottom design margin, and the necessary OS inset together. There
is no second footer padding; scrolling that target into view clears the last line.
The journal chooser centers on the
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

The overview selector follows its document anchor until it reaches the safe bottom
of the viewport, including the IME boundary. Long documents cannot push it into
system navigation. The creation slot and bottom spacing still let the last block
scroll fully above the selector. `EntryCapturePositionTest` checks short, long and
returning anchors with pointer input. `EditorLongPageAcceptanceTest` measures the
actual Android safe bounds at the start and end of a long document in both
navigation modes and orientations, and captures eight bounded images in
`editor-long-page/`. Its end-state assertion checks that content clears the bar;
presence of the selector alone is insufficient.

`EntryEdgeToEdgeTest` asserts actual focused-surface pixels behind all four
synthetic system-bar boundaries, independently from the inner writing bounds.
It also measures equal 16dp horizontal and bottom design margins at 400/700/900dp,
retaining the 640dp rail on larger windows. Native focus assertions separately
require the painted surface to fill the window while writing and controls remain
inside the system/IME safe bounds. Inspect device captures for continuous paint;
three-button navigation may add an Android contrast scrim independently of app
painting, so a platform scrim is not evidence of app padding.

Scrolling text, audio controls, media captions and block menus use an inner
window-safe clipping layer. The owning Surface and imagery remain uncut.
The clip blocks pointer input as well as ink; fully clipped content is hidden
from accessibility without replacing its semantics identity. Reported layout
rectangles remain layout geometry, so tests check actual pointer delivery,
accessibility hiding and pixel output rather than assuming the rectangles shrink.
The native long-page end capture checks that the center status strip contains
only continuous surface color, excluding Android's clock and status icons.
Native TalkBack operation, camera capture and foldable posture are not covered
by these two text-focused journeys.
