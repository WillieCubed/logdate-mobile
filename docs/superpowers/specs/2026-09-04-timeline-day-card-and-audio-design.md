# Timeline Day Card and Audio Design

## Goal

The timeline must read as a record of the user's days rather than a stack of app chrome. Photos, writing, and recorded voice carry the visual weight. Structural furniture that repeats information already on screen, labels content that announces itself, or reserves space without rendering is removed.

Recorded voice is presented by one component system across the product, and that system already exists.

## Problem

### A unified audio system was built and never connected

`client/feature/editor/.../ui/audio/expansion/` defines a four-state model for audio presentation. Its own documentation states where each state belongs:

- `COLLAPSED` — "Default compact state in timeline. Shows: mini waveform, duration, play button."
- `SPATIAL_EXPANDED` — "In-place expansion within the timeline flow." Rendered by `SpatialExpandedAudioBlock`.
- `ELEVATED` — floating preview on long press. Rendered by `ElevatedAudioCard`.
- `IMMERSIVE` — full-screen. Rendered by `ImmersiveAudioScreen`.

`AudioExpansionController` implements the transition map, including `expandOnPlayback`. `BezierAudioWaveform` draws real amplitudes with a playhead, segment markers, and scrubbing. `AudioPalette` derives a per-recording color palette from the recording's daylight period.

Only `IMMERSIVE` is reachable in production, through `NoteViewerScreen`. `COLLAPSED` has no composable at all — its layout exists inlined in `AudioBlockContent`'s collapsed row and was never extracted. The timeline, which the system was designed for, instead grew `AudioSection` and `AudioMomentCard`: near-duplicate cards differing only in surface color and corner radius, neither playable, both drawing `AudioWaveBars` — a list of eight hardcoded heights, identical for every recording in the product. A third language, `AudioNoteSnippet`, sits one tap away in the day detail, where a full-width filled "Convert to Text" button is the loudest element on screen and the recording's own content is absent.

This work finishes the existing system rather than adding a fourth presentation beside it.

### The goldens render a path production does not use

`TimelineDayUiStateFactory` exposes two builders. `createSemanticTimelineDayUiState` is what production uses; `TimelineViewModel` and `HomeScreen` are its only non-test callers. `createTimelineDayUiState` has no production callers — only `TimelineDayUiStateFactoryTest` and four screenshot suites — yet it owns the nine hardcoded section labels ("Captured" twice, "Heard back", "Went through", "From the day", "Noted", "Held onto", "Moved through", "Said out loud"), the hero and supporting section model, `DayMetaPill`, and `TimelineRecapStrip`.

Every checked-in timeline golden therefore depicts a path users never see, and none depict the path they do. `S01_TimelinePopulated_*` is additionally orphaned: the golden files exist with no corresponding preview function.

`TimelineDayListItem` selects between renderers on `item.moments.isNotEmpty()`. `InferMomentsUseCase` returns an empty list when a day has no entries, so a day with places but no notes still reaches the legacy renderer in production.

### Waveform amplitudes are not written anywhere

`WaveformStorage.load()` is a cache read with no backfill. The only two `save()` call sites are inside `AudioContextProcessor`, both lazy write-on-read. Nothing writes amplitudes during recording or import, so a recording carries amplitudes only if the user has already opened it in the note viewer or the editor. `RewindPanelUiState` documents this gap. Reading `WaveformStorage` directly from the timeline would render a flat line for most recordings.

### The rail

`TimelineDayRail` sizes its spine with `fillMaxHeight()` inside a `Row` inside a `LazyColumn` item, where the incoming height constraint is unbounded and the modifier resolves to nothing. It renders a day number, a month abbreviation, and a dot with no connecting line while reserving 72dp or 88dp of width. Three call sites indent past it with a hardcoded `start = 80.dp` that matches the rail at none of its three widths. On phones the semantic path already bypasses the rail through `InlineDateHeader`, so this affects tablets only, as does the resulting duplicate date.

Every day is styled by `defaultDayStyle()`, which resolves to `primary` unconditionally on every device. `TimelineDayCardLayout.style()`, which maps media-led to primary, voice-led to secondary, and place-led to tertiary, is reachable only from the legacy renderer and the loading skeletons.

## Product contract

- A day occupies the full content width at every breakpoint. No column of the viewport is permanently reserved for chronological furniture.
- The date is rendered once per day, in one treatment, in one place, and remains visible while that day's content is on screen.
- The generated day summary is present but subordinate to the user's own content.
- Recorded voice presents its transcript as body content at reading size. The transcript is the moment; playback is secondary to it.
- Audio is playable wherever it is displayed, and every audio surface draws the recording's own amplitudes. No audio is represented by a fixed decorative shape.
- Audio presentation escalates through one state machine — collapsed, spatially expanded, elevated, immersive — rather than through per-screen components.
- Transcription is initiated in the background. The user is never asked to press a primary action to make a recording legible; a failed transcription offers a quiet inline retry.
- A moment is labeled only where the label carries information the content cannot. `InferMomentsUseCase` already enforces this rule: "Only place-based labels are shown. Bare time-of-day labels are always suppressed."
- A run of days is visually differentiated by what those days contained.
- Breakpoints change how many moments sit side by side. They do not change the structure of a day.
- Every timeline golden renders the path production renders.

## Architecture

### Day card

`TimelineDayRail` is deleted, along with `TimelineDayStyle.railColor` and its five initializers, the rail column and spine in `TimelineDaySkeleton`, and the three `start = 80.dp` paddings on the time-gap, append-loading, and append-error rows.

The date becomes a keyed `LazyColumn` sticky header carrying date and weekday, following the existing pattern in `SearchScreen`. `InlineDateHeader` becomes that header, reduced from `headlineMedium`; the summary moves beneath it at body scale. The COMPACT, MEDIUM, and EXPANDED branches of `TimelineDayListItem` collapse into one structure, with layout mode governing only how many moments flow per row.

`TimelineList` currently maps visible list items back to days with `itemInfo.index - 1`. Sticky headers change the item count per day, so this moves to matching `itemInfo.key`, which is already the day's date. The mapping feeds `onVisibleAudioNoteIdsChanged` and therefore transcription prefetch; left as index arithmetic it would resolve to the wrong notes rather than fail visibly.

### Removing the legacy renderer

`createSemanticTimelineDayUiState` is made total: it guarantees at least one moment, synthesizing a single unlabeled moment from the day's notes and places when the caller supplies none, and sets `layout` from the day's content.

`createTimelineDayUiState` is then deleted with the nine label strings, `TimelineDaySectionUiState` and its four subtypes, `TimelineDayRecapUiState`, and the composables that exist only to render them: `TimelineDayHeader`, `TimelineDayContent`, `TimelineSupportingFlow`, `TimelineSection`, `TimelineRecapStrip`, `DayMetaPill`, `TextSnippetSection`, `MediaSection`, `PlaceSection`, and `AudioSection`.

The four screenshot suites building state through the legacy builder migrate to the semantic builder with representative moments, including a voice-led day.

### Relocating the audio system

`AudioContextProcessor` depends only on `client.awareness` and `app.logdate.ui.audio.WaveformStorage`, both already available to `client/ui`, so the move requires no dependency inversion. Moved from `client/feature/editor` to `client/ui`: `AudioContextProcessor`, `AmplitudeExtractor` with its platform source sets, `SegmentDetector`, `PaletteGenerator`, the `AudioPalette`/`AudioSegment` models, `BezierAudioWaveform`, `WaveformPathGenerator`, the expansion state machine, and `SpatialExpandedAudioBlock`. Koin bindings move with them. `feature/editor` and `feature/journal` update imports; both already depend on `client/ui`.

`ElevatedAudioCard` and `ImmersiveAudioScreen` stay in the editor: they depend on `ImmersiveSystemBarEffect`, whose `jvm` actual has no corresponding source set in `client/ui`. They are the overlay states, which the timeline does not enter, and they compile against the moved types from where they are. `MediaDurationFormatter` likewise stays; it is an expect/actual that exists only to reach `String.format`, so `client/ui` gains a single pure-Kotlin `formatAudioDuration` instead and the seven competing formatters are left for separate cleanup.

This lands as a behavior-preserving move so that the subsequent change is legible in review.

### Connecting the timeline

The missing `COLLAPSED` renderer is extracted from `AudioBlockContent`'s collapsed row into a `CollapsedAudioCard`.

The inline states gain the transcript, which no expansion component currently displays: `COLLAPSED` shows the excerpt from `buildTranscriptExcerpt`, `SPATIAL_EXPANDED` the full text. `AudioMomentCard` is replaced by `CollapsedAudioCard` driven by an `AudioExpansionController` with `expandOnPlayback` enabled.

Amplitudes are sourced through `AudioContextProcessor`, which loads from cache or extracts and caches, rather than through `WaveformStorage` directly. Composables obtain an `AudioContext` via `produceState`.

The processor reaches the card through `LocalAudioContextProcessor`, supplied by the app-wide `AudioPlaybackProvider`, rather than being injected inside the card. Audio cards render in previews and screenshot tests where no Koin graph exists, and injecting there fails hard enough to remove the entire surrounding section — observed as the day detail losing its notes list. With no processor available the card still draws and still plays; it has no waveform until one can be produced.

`AudioNoteSnippet` reduces to the same component plus output-device routing when its note is active. Its icon tile, "Audio recording" title, and "Convert to Text" button are removed. `AudioPalette` supplies per-recording color.

### Color

`TimelineDayCardLayout.style()` is wired into the semantic renderer and `defaultDayStyle()` is removed.

## Error handling and observability

A missing or unreadable waveform is not an error state. The component renders a flat baseline, remains playable, and logs nothing. Extraction failure is logged once at the existing `AudioContextProcessor` boundary and leaves the baseline in place.

Transcription failure surfaces as inline text with a retry sized as a text action, not a filled button. Existing transcription state, error, and retry plumbing in `LocalTranscriptionState` is reused unchanged; only its presentation changes. No transcript text or audio sample data is logged.

## Verification

- Unit tests cover the synthesized-moment fallback, `layout` selection in `createSemanticTimelineDayUiState`, and the collapsed-state transcript excerpt. Existing `buildTranscriptExcerpt` and `collectLazyTimelineAudioNoteIds` coverage continues to pass.
- `TimelineDayUiStateFactoryTest` loses its three legacy-builder cases and gains the above.
- Screenshot goldens are regenerated with `./gradlew :app:android-main:updateDebugScreenshotTest`. Because the suites move to the semantic builder, the regenerated timeline goldens differ substantially from the current ones by design; they are the first to depict the shipping timeline, and the rendered pixels decide whether the design succeeded.
- `./gradlew test allTests`, `./gradlew ktlintCheck`, `./scripts/check_hardcoded_compose_literals.sh`, and `./gradlew :app:android-main:assembleDebug` pass.
- `TimelineAudioSnippetE2ETest` and `TimelineTranscriptE2ETest` assert strings and components this change removes. Neither runs in CI — there is no instrumented job — so both are updated and run locally on an emulator or Gradle Managed Device.

## Non-goals

- No change to the hero and supporting rhythm within a day, to the media grid, or to the `Spacing` scale.
- No change to audio capture, storage, sync, or the transcription engine. Amplitudes remain extracted lazily rather than written at capture time.
- No change to `InferMomentsUseCase` or moment inference quality.
- No consolidation of the remaining audio surfaces outside the timeline — the library detail, Wear card, and journal inline card keep their own presentation for now.
- No change to the suggestion block, the app bar, or the FAB.
