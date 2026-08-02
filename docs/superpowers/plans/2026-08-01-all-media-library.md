# All-Media Library Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` to execute this plan one task at a time, with RED tests, an independent review, and fresh verification before each direct-to-`main` commit.

**Goal:** Make Library a reliable offline view of every photo, video, and audio attachment in the one local LogDate identity, with useful search, accessible selection, trustworthy detail/playback, and screenshot/device evidence that proves the actual media renders.

**Architecture:** Derive one typed Library model from locally durable notes plus indexed metadata. Notes remain the source of user-visible ownership; the index enriches the same canonical media reference and must never hide note-backed items while it catches up. Render each media kind deliberately, expose loading/permission/error states as actionable states instead of empty data, and reuse the existing audio playback provider. Detail and sharing consume the app-private canonical media store from `2026-08-01-android-canonical-media-storage.md`. Search routes into the existing global search experience with media-specific filters rather than maintaining a second search index.

**Tech stack:** Kotlin Multiplatform, Compose Material 3, Navigation 3, Coil, Media3/audio playback abstractions, Koin, kotlinx.coroutines/Flow, AndroidX host-side screenshot tests, Gradle Managed Devices, Kotlin test and MockK.

**Prerequisite:** Complete Tasks 1-3 of `docs/superpowers/plans/2026-08-01-android-canonical-media-storage.md` before wiring final detail sharing and process/gallery-deletion acceptance. Model and state work may begin earlier, but no Library commit may claim durable media until the canonical-store tests are green.

**Launch claim boundary:** This plan proves local all-media discovery, rendering, interaction, restart, and offline behavior on supported Android emulators. Clean-device Cloud restore remains a separate acceptance gate and must be run against staging after both plans land.

---

## Invariants

1. Library reads the one local LogDate identity. It never switches, filters, or partitions data by a remote account.
2. Library is usable without a network connection; local notes and canonical media bytes are sufficient for grid, detail, and playback.
3. Every locally owned photo, video, and audio note appears even if indexing is delayed or failed.
4. Indexed metadata enriches a note-backed item but never creates a duplicate for the same canonical media reference.
5. An upstream repository failure is an error with retry, never an empty library.
6. Permission prompts are shown only for an operation that actually needs permission; viewing app-private media never asks for broad gallery permission.
7. Search affordances always do something. Library search opens the existing search flow already filtered to media captions and transcriptions.
8. Every pointer-only gesture has a keyboard/touch alternative. Multi-select is available through long press and an explicit action.
9. Screenshot content cases use decodable, deterministic media fixtures. A blank or near-uniform content golden is a failing test.
10. No physical Android device or `connected*AndroidTest` task is used.

## Task 1: Replace the image/video boolean with a complete typed media model

**Files:**

- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryMediaSource.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryUiState.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailUiState.kt`
- Modify: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/LibraryViewModelTest.kt`
- Modify: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/MediaDetailViewModelTest.kt`
- Create: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/LibraryMediaSourceTest.kt`

### Step 1: Write failing model tests

Require that:

- `JournalNote.Image`, `JournalNote.Video`, and `JournalNote.Audio` each produce a source with an explicit `LibraryMediaKind`;
- audio retains `durationMs`; `JournalNote.Audio` has no caption or transcript field, so the pure source builder must not invent either;
- image/video captions survive conversion from the note or indexed record when available;
- two notes referencing the same canonical URI collapse to one item while retaining both matching notes;
- indexed image/video metadata wins enrichment without duplicating note-backed media;
- note-only audio remains visible even though `IndexedMedia` currently has no audio subtype;
- ordering is newest-first and deterministic when timestamps tie;
- an indexed-only item remains visible for backward compatibility;
- viewer items retain kind rather than re-deriving MIME from a Boolean.

### Step 2: Run RED

```bash
./gradlew \
  :client:feature:library:desktopTest --tests '*LibraryMediaSourceTest' \
  :client:feature:library:desktopTest --tests '*LibraryViewModelTest' \
  :client:feature:library:desktopTest --tests '*MediaDetailViewModelTest' \
  --console=plain
```

Expected: audio assertions fail and the current `isVideo` model cannot express all three kinds.

### Step 3: Implement the minimal typed model

- Add `enum class LibraryMediaKind { IMAGE, VIDEO, AUDIO }` or a sealed equivalent.
- Give `LibraryMediaSource`, `LibraryMediaItem`, and `MediaViewerItem` a `kind` field.
- Carry only real domain metadata: canonical reference, capture timestamp, duration, caption/transcription, indexed enrichment, and matching notes.
- Normalize deduplication keys consistently. Do not merge distinct byte objects merely because their display names match.
- Keep the conversion pure and deterministic so it remains exhaustively unit-testable.

### Step 4: Run GREEN and review

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :client:feature:library:compileKotlinDesktop \
  :client:feature:library:ktlintCheck \
  --console=plain
```

Fresh review must inspect audio completeness, deduplication, stable ordering, nullable metadata, and source-of-truth semantics.

### Step 5: Commit

```text
fix(library): include every journal attachment
```

## Task 2: Make loading, error, retry, permission, and search behavior truthful

**Files:**

- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryUiState.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryViewModel.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryContent.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryScreen.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryTopBar.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/navigation/LibraryNavRoute.kt`
- Modify: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt`
- Modify: `client/feature/library/src/commonMain/composeResources/values/strings.xml`
- Modify: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/LibraryViewModelTest.kt`
- Create or modify focused Compose tests under `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/`

### Step 1: Write failing state and navigation tests

Require that:

- repository exceptions produce `LibraryUiState.Error` with a retry action, not `Empty`;
- retry creates a fresh collection attempt and can recover to content;
- loading renders visible progress placeholders with semantics;
- permission-required state names the exact operation and invokes a supplied permission/settings callback;
- ordinary app-private Library viewing never emits permission-required;
- tapping or submitting the search field invokes `onOpenSearch` exactly once;
- both the Home-embedded Library and standalone `LibraryOverviewRoute` pass a real search callback;
- the resulting `SearchRoute` carries filters for media caption and transcription results;
- all user-visible copy is in Compose resources and mentions photos, videos, and audio.

### Step 2: Run RED

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :app:compose-main:desktopTest \
  --console=plain
```

Expected: error currently collapses to empty, retry does not exist, the loading surface is blank, and Library search is a no-op in at least one route.

### Step 3: Implement truthful states

- Model `Error(message, canRetry)` separately from `Empty`.
- Drive retries through an explicit refresh/retry trigger combined with repository flows; do not create duplicate collectors per tap.
- Render a small Material 3 skeleton grid or progress treatment that preserves final layout geometry.
- Keep `PermissionRequired` only for explicit import/export paths and provide the corresponding action callback.
- Route search through the existing global search screen with media-caption and transcription type filters. Extend `SearchRoute` only through a backward-compatible default if needed.
- Use test tags/semantics for loading, retry, permission action, and search without exposing implementation detail.

### Step 4: Run GREEN and review

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :app:compose-main:desktopTest \
  :client:feature:library:ktlintCheck \
  :app:compose-main:ktlintCheck \
  --console=plain
```

Fresh review must verify that failures cannot masquerade as no memories, search filters are not silently dropped, repeated retry does not leak collectors, and no local view depends on network or gallery permission.

### Step 5: Commit

```text
fix(library): make loading and recovery actionable
```

## Task 3: Render polished photo, video, and audio tiles with accessible selection

**Files:**

- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/components/MediaThumbnailGrid.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/components/MediaThumbnailItem.kt`
- Create: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/components/AudioLibraryItem.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryContent.kt`
- Modify: `client/feature/library/src/commonMain/composeResources/values/strings.xml`
- Modify: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/components/MediaThumbnailGridTest.kt`
- Create: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/components/AudioLibraryItemTest.kt`
- Create focused Android Compose tests under `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/`

### Step 1: Write failing interaction tests

Require that:

- photo and video items expose kind, capture date, and caption through semantics;
- video has a high-contrast duration/play badge that remains legible on light and dark frames;
- audio renders a deliberate waveform/audio-card treatment with duration, an “Audio entry” label, optional transcript summary supplied by UI state, and play/pause action;
- audio uses `LocalAudioPlaybackState` so only one clip plays at a time and lifecycle stop pauses it;
- a long press enters selection without opening detail;
- an explicit overflow/select action provides the same operation without long press;
- selected state is announced and visible beyond color alone;
- keyboard Enter opens detail and the context/menu action remains reachable by keyboard;
- stable keys preserve playback and selection state during regrouping;
- large text does not clip the action row or obscure another media group.

### Step 2: Run RED

```bash
./gradlew \
  :client:feature:library:desktopTest --tests '*MediaThumbnailGridTest' \
  :client:feature:library:desktopTest --tests '*AudioLibraryItemTest' \
  :app:android-main:compileDebugAndroidTestKotlin \
  --console=plain
```

Expected: audio and long-press behavior do not exist; current selection is pointer-context-menu dependent.

### Step 3: Implement the grid

- Keep image/video previews square and use the existing shared-bounds transition only for visual media.
- Render audio as a Material 3 card sized to the same grid rhythm without pretending it has an image thumbnail.
- Reuse the existing playback provider/controller; extract only the smallest shared UI primitive if the timeline component cannot be depended on without a module cycle.
- Use `combinedClickable` for click/long-click and expose an explicit selection action in the item menu/top-level selection affordance.
- Provide minimum 48 dp interactive targets, visible focus, selected icon/check semantics, and localized content descriptions.
- Keep hover scaling subtle and disable transformations that cause clipping or motion issues under reduced-motion policy if the project exposes one.

### Step 4: Run GREEN, GMD interaction proof, and review

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :client:feature:library:ktlintCheck \
  :app:android-main:compileDebugAndroidTestKotlin \
  --console=plain
```

Run the focused emulator-only interaction class on the repository's API 36 phone and API 35 tablet Gradle Managed Devices. The test must tap, long-press, use the explicit alternative, play/pause audio, increase font scale, and rotate where supported.

Fresh review must inspect touch targets, TalkBack semantics, keyboard focus, single-playback ownership, recomposition stability, and MD3 color/shape hierarchy.

### Step 5: Commit

```text
feat(library): browse and play every media type
```

## Task 4: Add complete media detail, recovery, and sharing behavior

**Files:**

- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailUiState.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailViewModel.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/detail/MediaDetailScreen.kt`
- Modify: `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/di/LibraryFeatureModule.kt`
- Modify: `client/feature/library/src/commonMain/composeResources/values/strings.xml`
- Modify: `client/feature/library/src/commonTest/kotlin/app/logdate/feature/library/ui/MediaDetailViewModelTest.kt`
- Create focused common/Android UI tests for detail recovery and audio playback
- Modify: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/LibraryMediaDetailVideoE2ETest.kt`
- Create: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/LibraryAllMediaDetailE2ETest.kt`

### Step 1: Write failing detail tests

Require that:

- audio detail observes `TranscriptionRepository` for the selected note and displays a completed local transcription when present, plus duration, journals, location, and playback controls;
- photo and video detail display caption plus existing date/location/journal/EXIF metadata;
- a missing/unreadable canonical object reports a specific local-media error with retry and, when a remote mapping exists, a restore action that queues while offline;
- decode/prepare failures are distinguishable from a missing Library record;
- retry reloads the same media ID rather than silently selecting the first item;
- viewer paging preserves explicit media kind and never presents audio as an image;
- sharing uses the canonical-store FileProvider contract and grants read access;
- an unavailable external presenter does not expose a dead action;
- back remains available in every state.

### Step 2: Run RED

```bash
./gradlew \
  :client:feature:library:desktopTest --tests '*MediaDetailViewModelTest' \
  :app:android-main:compileDebugAndroidTestKotlin \
  --console=plain
```

Expected: audio detail is unrepresentable and the current error only offers “Go back.”

### Step 3: Implement detail behavior

- Add a first-class `AudioContent` state and typed viewer items.
- Inject `TranscriptionRepository` through the Library Koin module and observe only the currently selected audio note; cancel that collector when selection changes so Library does not create one perpetual flow per recording.
- Share one metadata panel across kinds while keeping kind-specific primary content.
- Reuse audio output selection and playback state; do not instantiate an independent player per recomposition.
- Report local availability separately from repository lookup failure.
- Keep restoration idempotent and offline-queued through the existing sync/media mapping boundary; never replace a good local object with a remote response.
- Do not fall back to another item when the requested ID is missing. Preserve the requested identity and show recovery.
- Convert canonical `file://` references through the FileProvider implementation from the canonical media plan.

### Step 4: Run GREEN and GMD proof

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :client:feature:library:ktlintCheck \
  :app:android-main:compileDebugAndroidTestKotlin \
  :app:android-main:ktlintCheck \
  --console=plain
```

Run `LibraryMediaDetailVideoE2ETest` and `LibraryAllMediaDetailE2ETest` on API 36 phone and API 35 tablet GMD targets. Delete the picker/gallery source before opening each detail, disable networking, restart the app process, and assert image decode, video prepare, audio prepare, metadata, retry semantics, and FileProvider sharing.

Fresh review must inspect audio lifecycle, error identity, offline queueing, FileProvider grants, process recreation, and compact/expanded/foldable layout behavior.

### Step 5: Commit

```text
feat(library): complete media detail and recovery
```

## Task 5: Replace fake screenshot URIs with real fixtures and make blank goldens fail

**Files:**

- Modify: `app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/components/library/LibraryScreenshotData.kt`
- Modify: `app/android-main/src/screenshotTest/kotlin/app/logdate/screenshots/components/library/LibraryScreenshots.kt`
- Add deterministic photo, video-poster, and audio fixtures under the appropriate debug/screenshot resources
- Modify generated references under `app/android-main/src/screenshotTestDebug/reference/app/logdate/screenshots/components/library/LibraryScreenshotsKt/`
- Create: `app/android-main/src/test/kotlin/app/logdate/screenshots/LibraryScreenshotSanityTest.kt` or the repository-equivalent screenshot host-test source set

### Step 1: Write a failing screenshot sanity test

For every screenshot whose name contains `Content`, `MediaDetail`, or an explicit media kind:

- decode the PNG;
- reject a missing/zero-size file;
- reject a near-uniform image using a conservative pixel-variance/unique-color threshold;
- inspect defined media-content regions and reject an all-black/all-transparent placeholder;
- print the exact failing reference path and metric, without rewriting it.

Run the sanity test before changing fixtures and record the current blank Library grid/detail failures.

### Step 2: Use deterministic renderable fixtures

- Use the existing `app/android-main/src/debug/res/drawable-nodpi/sample_note_photo.jpg` where appropriate.
- Add a small rights-safe deterministic video poster/clip and audio clip if no repository fixture exists.
- Resolve fixtures with `android.resource://` or a repository-supported file/provider URI that Coil and playback actually open in screenshot tests.
- Keep media files small, non-sensitive, deterministic, and documented with provenance.
- Never use a fabricated `content://` URI as visual proof.

### Step 3: Record the complete MD3 matrix

Capture at minimum:

- loading, content, empty, error, and actionable permission states;
- mixed photo/video/audio content and selection mode;
- photo, video, and audio detail;
- audio paused and playing;
- compact phone, expanded tablet, landscape, foldable book/tabletop when harnessed;
- light and dark schemes;
- 200% font scale;
- RTL;
- import/recovery error states that can occur in this flow.

The content set must visibly distinguish every media kind and contain enough metadata to expose truncation and hierarchy problems.

### Step 4: Inspect every changed image

- Record screenshots with the repository's AndroidX host-side `updateDebugScreenshotTest` task.
- Run screenshot verification plus the sanity test.
- Open every changed reference at original resolution and inspect cropping, text overflow, contrast, alignment, touch-target spacing, false placeholders, and fold/hinge occlusion.
- Fix code/fixtures and repeat; do not bless accidental blank output.

### Step 5: Verify and commit

```bash
./gradlew \
  :app:android-main:validateDebugScreenshotTest \
  :app:android-main:testDebugUnitTest --tests '*LibraryScreenshotSanityTest' \
  :app:android-main:ktlintCheck \
  --console=plain
```

Fresh reviewer must compare each reference to the named state and report whether the screenshot materially proves that state. Commit code, fixtures, and reviewed goldens together.

```text
test(library): prove real media rendering across layouts
```

## Task 6: Prove offline restart and canonical-media survival end to end

**Files:**

- Create or modify focused Android tests under `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/`
- Modify test-only fixture/helpers required to seed notes and canonical media
- Update the launch evidence/runbook document selected by the parent launch plan

### Step 1: Build the acceptance test around real user behavior

On a clean API 36 phone and API 35 tablet Gradle Managed Device:

1. Start with networking disabled.
2. Create one text note plus photo, video, and audio entries through user-visible flows.
3. Kill and recreate the app process.
4. Delete or invalidate the original picker/gallery sources.
5. Open Library and assert exactly the three media items appear in the one identity.
6. Open each detail and prove readable bytes/playback plus captions and metadata.
7. Search a media caption and audio transcription and open the correct detail.
8. Enter and exit selection through both long press and the explicit alternative.
9. Restart again while offline and repeat the core read/play assertions.
10. Re-enable networking only after local proof; confirm reconnect does not duplicate, hide, or replace the items.

### Step 2: Add failure-path acceptance

- Corrupt or remove one test object and assert only that item reports recoverable failure.
- Make the repository flow fail and assert Library shows Error, then retry to recover.
- Deny optional gallery/export permission and assert app-private items remain browsable.
- Interrupt a restore/import and assert no half-published item appears.

### Step 3: Run the exact emulator-only lanes

Use the repository's GMD test filtering property and name the exact test classes. Do not use `connectedDebugAndroidTest`, `install*`, or a physical-device serial.

Run the feature/unit/lint/screenshot gates again after the device lane. Record per-device pass/skip/failure counts and retain the test reports/screenshots as launch evidence.

### Step 4: Independent launch review

The final reviewer must verify:

- the test begins from clean app state on each managed device;
- networking is genuinely unavailable during local creation/restart assertions;
- source deletion/grant loss occurs before Library/detail proof;
- media checks assert readable bytes or player-ready state, not just row presence;
- no remote account or origin is used to partition the Library;
- failures cannot be interpreted as an empty Library;
- screenshot references contain real rendered media.

Resolve all Critical and Important findings, rerun every affected gate, then commit the acceptance evidence.

```text
test(library): verify offline all-media survival
```

## Final verification bundle

After all tasks and canonical media prerequisites land, run fresh from direct `main`:

```bash
./gradlew \
  :client:feature:library:desktopTest \
  :client:feature:library:ktlintCheck \
  :app:compose-main:desktopTest \
  :app:compose-main:ktlintCheck \
  :app:android-main:testDebugUnitTest \
  :app:android-main:validateDebugScreenshotTest \
  :app:android-main:compileDebugAndroidTestKotlin \
  :app:android-main:assembleDebug \
  --console=plain
```

Then run the two exact GMD acceptance classes on both configured API 36 phone and API 35 tablet devices and record per-device results. A green repository bundle is necessary but insufficient for Cloud restore; the separate staging backup/sync test must next restore these same three media kinds onto a clean managed device.
