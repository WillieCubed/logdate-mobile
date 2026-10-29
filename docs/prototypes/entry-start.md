# Entry editor explorations

## Current editor continuity contract

The production entry page keeps one attached surface and input owner for each memory while moving
between overview and focus. The actual surface bounds, foreground safe spacing and corners follow
one interruptible focus clock. Predictive Back starts from the currently displayed position; cancelling
restores focus, while committing returns the memory to the overview. A gesture invalidated by a new
input owner cannot dismiss its successor. Text selection, scroll bookmarks and memory identity stay
with the input rather than being recreated in a second presentation tree.

**Done** returns from a focused memory and retains its content in the entry. **Save entry** commits the
entry. Draft status reports the real draft-persistence state. The default capture preference belongs
in Settings > Memories; choosing a tool in the current entry does not change that preference. The
mode bar is available only for an empty, unowned input. Existing content and capture ownership
prevent silent type replacement.

Home creation sources carry a per-visit identity and their actual shape into the editor transition.
A leading Timeline pull previews the New entry source; retreat cancels, and an armed release creates
one editor visit. No editor or capture is created during the preview. The committed route visibility
also drives the mode bar's reveal and interaction eligibility. This is a local pull preview followed
by the supported navigation transition, not an externally seeked editor before commitment.

`EntryDocumentContinuityTest`, `RetainedEditorFocusMotionTest`, `WorkspaceEntryPullTest`,
`WorkspaceEntryPullInterruptionTest` and `HomeEntryCreationAdmissionTest` cover attachment, intermediate
bounds, interruption and source admission. Native `EditorEntrySwitchVideoTest` separately checks input
semantics through real touches. `EditorHomePullVideoTest` captures the pull and mode flow using the
native frame clock and public accessibility, including the IME and resized emulator windows. Those windows do not
establish physical hinge or foldable-hardware behavior. The explorations below retain historical
prototype context and are not current production acceptance claims.

## Compose entry page study

`EntryPagePreviews.kt` contains interactive Android Studio previews built from `EntryStartPrototype`,
`MainEditorContent`, the actual earlier-entry context component, and the original handwriting canvas
presentation. It includes phone, tablet, expanded earlier content, handwriting focus, dark theme, book,
and tabletop fixtures. `EntryPagePreviewScenesTest` renders those same components offscreen; these
renders are layout evidence, not Android keyboard, persistence, physical-hinge, or haptic acceptance.

The continuous-overview study has one scroll owner for earlier content and current blocks. The page
fills its viewport and scrolls underneath floating close, journal, and save controls. Its initial top
spacing belongs to the scroll content; there is no reserved opaque app-bar row. Phone block surfaces
reach both screen edges, with text inset inside them. In book posture, the same scroll owns both
columns: earlier content sits on the left and the working entry on the right, clear of the hinge.
Tapping a block opens its existing editor. Short text blocks fit their content, and handwriting keeps
the same shared surface and original strokes. The back control returns to the overview without a
visible “Entry,” “Text,” or “Focus” label. Flat tablet focus fills the canvas; book and tabletop focus
retain useful adjacent entry context.

`EntryPageStudyChrome` owns one persistent `StoriesCaptureModes` selector. It follows an anchor at
the end of the scrolling page and moves to the lower edge during focus. There is no separate primary
creation-button strip. Switching modes while focused preserves existing blocks. Drawing tools are
positioned above this same selector. The earlier-content heading is plain text; its adjacent tonal
history control explicitly opens browsing.

The shared handwriting element owns the whole block surface, including its paper and inset content,
and matches the focused drawing area. The workspace behind it keeps its normal color while the paper
expands or contracts. Focused text supplies a surface inside its shared bounds as well. The focus
surface reaches the entire window behind the floating controls rather than becoming an inset rectangle.
Book lists retain framing at the hinge while their outside edges fill the available pane.

The card owns its clipped Material press indication. Its inner canvas retains the focus action without
painting a second rectangular state layer. The paper uses the same `surfaceContainerLow` token in
overview and focus; no intermediate surface recoloring is needed. Passive ink previews use that same
paper role. Earlier-day browsing reuses the existing primary-container accent for its date identity
and groups complete entries on the same working surfaces as the editor, rather than presenting a
divider-separated report. A separating book hinge keeps those reading groups in separate columns.

The proposed boundary interactions have two stages. At the bottom, pulling first reveals capture
options; further travel arms the user's configured default, and release creates and focuses a block
in the current entry. At the top, pulling reveals the earlier-day heading behind the page; further
travel arms earlier-day browsing, which settles into its own reading view on release. Retreating
before release cancels either action. Motion, the revealed content, and selector emphasis communicate
the transition without visible pull/release instructions.
Normal scrolling must finish before a boundary gesture claims input. Detent feedback uses the
existing haptics abstraction, and visible controls must offer the same actions without gestures.
The study implements these boundary gestures using local state, including retreat before release and
a call to the shared detent haptic. Its default mode is a preview parameter; it does not read or change
the user's saved preference. Earlier-day browsing shows full earlier entries and omits current draft
blocks. Its floating “Back to writing” action and system Back restore the same draft and scroll position.
This is an interaction proposal, not a production gesture integration
or native haptic acceptance. Folding must preserve the draft, selected block, modality, and position;
it only changes placement.

`EntryPageScrollFlowTest` records a continuous phone flow with Text and Handwriting as separate
preview defaults: scroll to creation controls, pull and hold at the armed detent, release into focus,
return to the same scroll position, scroll to the beginning, then pull and release into earlier-day
browsing. Pointer events drive every step. Pull progress does not change the list's measured height
or move the arming threshold. The overview owns scroll and
earlier-content state across focus transitions. The recordings use local preview preferences and do
not read or modify the installed app's saved settings.

`EntryPageAdaptiveFlowTest` records the tablet flow and a book-to-closed-phone viewport handoff in a
single composition. It types into a newly created text block, switches to the existing ink, closes the
foldable while ink is focused, resumes the same text block, and browses earlier entries. Block IDs,
typed text, original strokes, and one capture selector are checked throughout. Returning after a
width change anchors the focused block in the reflowed overview; returning at the same width retains
the exact scroll offset. Manual scrolling replaces that return anchor. Window-coordinate anchors and
hinge bounds are converted to the editor's measured local coordinates, including nested viewports.
These are simulated Compose posture and resizing checks, not physical-device or process-restoration
acceptance. Large frame sequences are temporary outputs under the guarded external artifact root;
compact recordings and selected stills are retained with the prototype captures.

## Original editor with handwriting

The entry-start preview uses the original editor components: `ImmersiveEditorLayout`, `NoteEditorToolbar`, `MainEditorContent`, the capture grid, block sequence, and `EditorBottomContent`. The flat entry-start redesign is superseded. The original journal selector, block surfaces, spacing, and contextual add controls remain intact.

The initial state combines a compact earlier-today preview with the capture grid. Type and Handwrite are peer actions inside the writing tile; handwriting is no longer an appended row. A blank shared canvas under the Handwrite action expands into drawing focus, then returns as an ordinary block. The earlier-today preview remains above the block list and leaves the screen during focus. The scrollable initial composition measures the preview before allocating the picker, retaining access in short windows or with larger text.

Earlier entries are read-only design fixtures, inspired by [Add to LogDate - Continuing](https://www.figma.com/design/AJpRHAh7QNdcytwbpjlXaS?node-id=167-3567). They are supplied through the editor's optional context slot and never become draft blocks or saved content. `showEarlierEntries = false` previews the first-entry-of-the-day state. Loading actual earlier entries is outside this local prototype.

Handwriting uses the real editor's block presentation, ordinary block surface, and overflow menu. Its inline canvas has a transparent background, a bounded writing width, and the same minimum block height as text. The canvas expands through the existing true `sharedElement` focus/return transition; full-screen paper extends behind system bars. These presentation changes also apply to real drafts and saved entries. The preview keeps state locally and disables Save; durable editing remains available through the real editor host (`--ez real_editor true`).

Android runs use the repository's Gradle Managed Devices. The durable editor journey runs with `:app:android-main:flagshipPhoneApi37DebugAndroidTest` or `:app:android-main:largeScreenTabletApi37DebugAndroidTest`, filtered with `-Plogdate.androidTestClass=app.logdate.client.e2e.EntryFocusDraftJourneyTest`. Run these within the required capacity guard; do not deploy to physical devices.

`EntryStartPrototypeScreenshotsTest` renders empty and populated phone/tablet scenes, dark phone, and full-screen handwriting. Its interaction checks open handwriting and return to the same page. `EntryStartWritingFlowTest` checks Type creation and continuous canvas bounds from the writing control into focus and back to a normal block. `HandwritingSharedElementTest` checks opaque ink in both transition directions and edge-to-edge focused paper through this preview's real block presentation. Current captures live under `docs/screenshots/prototypes/entry-start/day-context/`; generated renders live in the editor module's `build/reports/entry-start-prototype/`. Other capture folders retain earlier design iterations.

## Entry and Focus exploration

The Android debug host’s `--ez entry_stories true` route now uses the production editor components through `EntryStartPrototype(focusAnyBlock = true)`. Entry is the complete block sequence. Focus expands a selected block using its existing editor: text, handwriting, audio, photo, video, or camera. Each overview block has an explicit expand action. The compact Entry control returns the same block to its place in the sequence; there is no intermediate Review screen or Done row. Save is in the overview header and remains disabled in this local design probe.

The overview shows earlier entries directly as compact vertical time-and-text rows. Capture tools sit in one bottom strip. Choosing a tool from Entry creates a block and focuses it; switching tools while focused reuses an existing matching block when available. Horizontal gestures on the header or tool strip can change tools. Drawing gestures remain reserved for handwriting, and active recording prevents a tool switch. Camera and handwriting keep their native immersive controls.

The same block IDs, editor state, and original stroke history back both modes. Handwriting retains its true shared-element expansion and return, with themed edge-to-edge paper and contrast adaptation that does not alter stored strokes. The generic focus option is opt-in for this probe; the production editor’s default presentation is unchanged.

The existing editor layout owns measured window constraints, keyboard and system insets, fold posture, and native media layouts. Overview content remains bounded on wide windows. Handwriting can occupy the whole standard tablet window; supporting context is optional. The probe is verified with native desktop Compose interactions and populated light/dark captures, not Android camera or microphone acceptance. The Android host uses real media editors and services; desktop audio rendering uses the production recorder controls with a static UI fixture. Earlier entries are still fixtures, and this probe does not persist drafts. The real editor’s durable handwriting path remains separate.

The adaptive comparison uses `EntryFocusScenesTest`: populated entry overview, handwriting, text, photo, and recorder focus at compact phone, landscape, medium, and tablet sizes, plus book/tabletop fixtures, dark theme, RTL, and 200% text. The fold-safe header uses the same book-layout primitive as the editor rather than placing its title over a separating hinge. Capture controls retain accessible names when large text makes icon-only controls necessary. Recorder actions remain bounded in wide windows, and handwriting begins below the floating controls. `EntryFocusAdaptiveTest` checks block identity and strokes through same-session resizing, focus/return for every block type, hinge-safe header bounds, and bounded recorder actions. Headless renders establish layout and these interactions only; native window, keyboard, camera, microphone, and Android dynamic-color acceptance remain separate.

## Current-main integration constraints

Entry and Focus are presentation states over the same draft and block identities. Capture lifecycle is independent: starting, recording, processing, and recovery stay within the selected block's native editor. A presentation change must not implicitly stop a recorder or create a second owner. Native recording and camera paths require their real draft-persistence callbacks before capture starts; the local UI probe is not a durable capture host. Integration should use `EntryEditorContent` and the current block-scoped controllers rather than copying the probe's local state into a second editor.

The current personal-space note contract does not support handwriting. The mapper rejects that upload explicitly instead of sending strokes as plain text. Legacy encrypted storage, archive export, and restore retain original ink and capture metadata, but personal-space support and a usable handwriting Share action still need implementation. Keep `HANDWRITING_ENTRIES` disabled pending that work and native acceptance.

## Android editor and durable draft journey

The standard Android `EditorActivity` accepts a debug-only `entry_focus` opt-in for the two-mode presentation. It uses `NoteEditorScreen`, the real editor ViewModel, and the existing draft and note repositories. The regular release presentation remains unchanged. The debug real-editor host also uses this presentation; the standalone design probe still keeps only local state.

`EntryFocusDraftJourneyTest` starts without seeded entries, types through the editor with the Android IME visible, and injects six stylus strokes after the canvas accepts input. It exits through Save draft, opens a fresh editor, and selects that draft from Entry options > Drafts. It requires both text and original strokes to survive reopening, then saves and reopens the published handwriting and text notes. Published blocks currently reopen individually; this journey does not assert a grouped published-entry model. Gradle Managed Device additional output includes the screen recording and each workflow state.

Ordinary content blocks use a shared 12dp corner radius rather than deriving their shape from the display corners. The handwriting shared-element clip starts with that same radius and expands to edge-to-edge paper. Capture controls keep their separate shapes.

The native phone and tablet walkthroughs, including the Android keyboard, focus transitions, Save draft dialog, draft picker, and reopened content, are retained in `docs/screenshots/prototypes/entry-start/entry-focus/android-runtime/phone/` and `tablet/`. Each folder includes `draft-journey.mp4` and the corresponding screen captures.
