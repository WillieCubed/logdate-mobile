# Handwritten entry block prototype

A native Compose handwriting prototype hosted by the actual entry editor shell, block sequence, toolbar, journal selector, and shared focus transition. It preserves original ink rather than sending it to the keyboard for text recognition.

## Try it

Build the debug app with `./gradlew :app:android-main:assembleDebug`. The debug manifest alone registers `app.logdate.client.prototype.HandwritingPrototypeActivity`.

Use an emulator only. First inspect `adb devices -l` and select a serial beginning with `emulator-`. Install the debug APK with `adb -s <verified-emulator-serial> install -r app/android-main/build/outputs/apk/debug/android-main-debug.apk`, then launch:

```sh
adb -s <verified-emulator-serial> shell am start \
  -n studio.hypertext.logdate.debug/app.logdate.client.prototype.HandwritingPrototypeActivity
```

Tap the handwriting block itself to focus it, write with the pen, then use **Back** to return to the entry. Tapping the block again restores the same page. Pen, highlighter, whole-stroke eraser, undo, redo, three ink colors, and three pen widths are available. Drawing options contains ruled-page and touch-drawing switches. Pen-only input is the default. Dedicated handwriting focus uses the camera-like immersive editor presentation: the canvas reaches the mobile screen edges, the journal selector and entry Save action disappear, and a bounded drawing palette floats above the page. Back restores the entry through its shared block morph and retains list position. On wide windows, real neighboring entry content can be shown beside the canvas. Fingers scroll taller pages in pen-only mode; Draw with touch explicitly changes fingers to drawing input. The Material 3 Expressive floating toolbar gives the selected tool a filled shape and label. Undo/redo sits in a separate floating group at the top edge. Wide windows show ink colors directly; an ink swatch opens color and width choices on any screen. The bottom toolbar stays in one 56dp row. Compact windows use tool icons; wide windows can show the selected tool label. Tool buttons use 40dp visual height while preserving 48dp touch regions. Leading and trailing scroll clearance allow ink to move clear of both control groups without changing the initial edge-to-edge canvas.

**Save entry** uses the real editor Save affordance and explicitly writes the editable text plus original stroke geometry, pressure, colors, tools, and page dimensions to the debug app's private `handwriting-entry-practice.json` with `AtomicFile`. Existing ink-only `handwriting-practice.json` is read as a starting page and remains untouched. It can be reopened after process restart. Unsaved ink survives Android configuration changes through the host ViewModel, but is not guaranteed after process death. This practice file is separate from journal data.

## Real drafts and saved entries

The normal editor supports a first-class handwriting block behind `HANDWRITING_ENTRIES` (off by default). Existing handwriting remains readable when creation is disabled. Original ink is stored in `HandwritingDocument`, a versioned shared model containing page dimensions and ordered strokes with stable IDs, tools, colors, widths, transforms, and pressure-bearing points. The editor keeps undo history and tool preferences separate from the authoritative document.

Completed strokes update `EntryEditorViewModel` directly and participate in its ordinary autosave, background save, Save as draft, and Save entry operations. Leaving through Save as draft waits for the complete draft snapshot to persist. Loading a draft or saved note reconstructs the same page in the real editor; it does not read the standalone prototype file. Saved-note capture location, device, and sync version survive editor conversion.

Room schema 55 adds an explicit `content_format` discriminator to the existing note-document storage table. Existing rows remain plain text. Handwriting rows hydrate as `JournalNote.Handwriting`; their vector JSON is excluded from text search and prose inference. The ordinary note queries, pagination, journal associations, duplicate protection, deletion, export, and sync bookkeeping include these records. Reading surfaces render the original strokes. Unreadable stored documents retain their identity and complete original payload, appear as explicitly unavailable records, and cannot be edited as empty pages. Stroke tools, colors, geometry, and pressure are validated before ingestion.

The cloud content contract uses `HANDWRITING` with the encrypted document in its content field. The rich draft contract has `SerializableHandwritingBlock`; older clients retain unsupported remote records through the existing recovery inbox rather than interpreting them as text. Local API tests establish serialization, not a deployed multi-device sync result. Export and restore preserve structured ink in both archive formats.

To exercise the real editor on a verified emulator, launch the existing debug activity with `--ez real_editor true`. Add handwriting using the normal empty editor or Add to entry control. Use `--ez real_editor true --ez resume_draft true` to reopen the latest real draft, or `--ez real_editor true --ez resume_saved true` to reopen the newest saved handwriting note. These switches are debug validation affordances and do not register a release entry point.

## Standalone prototype boundary

The scene composes `ImmersiveEditorLayout`, `MainEditorContent`, `MemoryBlockSurface`, `TextBlockContent`, `NoteEditorToolbar`, and `EditorBottomContent`. A host-supplied `EditorBlockPresentation` attaches ink to a prototype-only marker; the marker never enters the repository. Default production presentations and creation controls are unchanged. The fixed practice entry suppresses unrelated block-creation controls. It reuses Postcards' `CanvasElement.Ink`, `InkPoint`, `InkTool`, and live `InkCaptureState`. Page coordinates remain stable across focused/inline views, resizing, and showing entry context. The inline thumbnail fits the whole page, including ink near its bottom edge. Undo records completed gestures, including whole-stroke erasure. Ink is serialized as vector data, not a bitmap or recognized text.

The standalone scene uses its own local Save callback; use the real-editor switch above to exercise `JournalNote.Handwriting`, real drafts, and journal persistence. The editor has no prototype-specific footer or separate continue-writing button; save results and errors appear transiently. No prototype host is registered in the release manifest. The standalone scene's neighboring text block is editable and included in its local save. Its surface holds one handwriting page; the stored extent grows to retain writing below the initial page height.

Remaining acceptance before enabling handwriting creation for everyone:

- Validate deployed server/client compatibility, multi-device sync, backup/restore, and older-client recovery with retained ink.
- Continue sharing Postcards capture support while keeping persisted ink independent of feature UI.
- Complete Android cancellation/palm rejection, hardware eraser, pressure calibration, tilt, latency/prediction, hover, zoom, accessibility, and foldable acceptance. The prototype filters finger input and tracks the active pen pointer; it does not claim complete hardware palm rejection or low-latency ink acceptance.
- Review page growth, multiple handwriting blocks, text scaling, RTL, dark-page treatment, and keyboard shortcuts. The current canvas is white even in dark theme.

The toolbar direction follows [Android’s creativity gallery](https://developer.android.com/large-screens/archive/gallery/creativity) and [Material 3 Expressive toolbars](https://m3.material.io/components/toolbars/overview): contextual tools around the creative work surface, with entry context remaining optional.

The paper and original ink expand and return through a 480ms Compose `sharedElement` transition rooted above the editor shell. Matching block IDs share only the canvas, so Compose renders the target canvas in the moving overlay without crossfading two ink images. Floating tools and optional entry context remain outside that shared element. Ink interpolates from the complete inline page to the focused page coordinates, corners resolve to the edge-to-edge canvas, and drawing controls enter after the paper settles. The focused paper background extends behind system bars and cutouts; controls retain safe insets. System Back and predictive Back drive the same transition progress.

## Verification and visual review

```sh
./gradlew :client:feature:editor:desktopTest
./gradlew :client:feature:editor:ktlintCheck :client:feature:editor:detekt
./gradlew :app:compose-main:ktlintAndroidMainSourceSetCheck :app:compose-main:detekt
./gradlew :app:android-main:assembleDebug
```

`HandwritingSharedElementTest` checks opaque ink throughout focus/return and edge-to-edge tablet paper. `HandwritingPageTest` checks original-ink serialization, segment-aware erasure, undo/redo, and redo invalidation. `HandwritingPrototypeTest` exercises actual Compose input, focus/return, full-page preview visibility, full-screen drawing bounds, touch filtering, finger scrolling, and coordinate stability when entry context changes the canvas width. `HandwritingPrototypeScreenshotsTest` produces populated shared Compose renders in `client/feature/editor/build/reports/handwriting-prototype/` at phone, tablet, dark tablet, and landscape sizes, including wide entry context.

Review captures under `docs/screenshots/prototypes/handwriting/` distinguish Android emulator screenshots from shared desktop Compose renders. `motion/entry-focus-return.mp4` records the actual editor shell transitioning from a populated entry into handwriting focus and back, with the compact toolbar and continuous paper transition. Frame checks cover ink alignment in both directions, including tablet context retained between focus sessions. The `integrated/` captures exercise the real editor, showing a draft reopened after process restart and the same ink published and reopened from the normal encrypted note database. Canonical draft documents are compared separately from changing save timestamps. Emulator captures show injected stylus input saved as original ink and reopened after process restart; they do not establish physical-pen feel or latency. Review the comparison sheet alongside the interaction tests, not as a substitute for input or durability verification.

Integration coverage includes `HandwritingEntryIntegrationTest` (fresh editor draft/saved reopen and failed draft writes), `HandwritingStorageRestartTest` (actual disk store and Room restart, pagination, unreadable-record isolation, and search exclusion), `HandwritingNoteMigrationTest` (retained text upgrade), `HandwritingContentSyncTest`, `HandwritingClientServerE2ETest` (local HTTP contract), and `HandwritingArchiveRestoreTest` (ink drafts without media attachments). These tests supplement the input and motion checks above.
