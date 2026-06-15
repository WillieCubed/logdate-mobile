# Foldables Adaptive Quality Checklist

Source: https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/foldables

Source last updated: 2026-04-10 UTC

Audit created: 2026-06-14

Source rechecked: 2026-06-14 from the Android Developers page. The local `ctx7` lookup was
quota-blocked, so this audit uses the linked official page as the source of truth.

## Scope

This checklist tracks Android adaptive app quality guidance for foldable experiences. Use it when
auditing LogDate screens that render long-form content, media playback, attachments, notifications,
or separate editor windows across folded, unfolded, tabletop, book, split-screen, and multi-window
states.

This audit covers the Android phone/tablet app surfaces that can run on foldable devices. Wear OS,
iOS, desktop, and non-UI service implementations are out of scope except where they expose Android
phone entry points such as notifications, widgets, sync, or connected-device settings.

Agent-driven validation must not target physical Android devices unless the developer explicitly
overrides the repository default in the current conversation. Foldable hardware checks should be
recorded as manual developer evidence, or run on a safe emulator or Gradle Managed Device where the
required posture and windowing capability is available.

## Status Legend

- `[todo]` No validation evidence recorded yet.
- `[partial]` Some behavior exists or evidence is incomplete.
- `[pass]` Requirement is satisfied with evidence linked.
- `[fail]` Requirement is not satisfied.
- `[n/a]` Requirement does not apply to the audited feature.

## Implementation Pointers

- Foldable layout detection: `client/ui/src/androidMain/kotlin/app/logdate/ui/foldable/FoldableDeviceUtils.kt`
- Shared foldable state, hinge, posture, and split model: `client/ui/src/commonMain/kotlin/app/logdate/ui/foldable/FoldableSupport.kt`
- Hinge-aware adaptive pane layout primitive: `client/ui/src/commonMain/kotlin/app/logdate/ui/adaptive/AdaptivePaneLayouts.kt`
- Hinge-aware tabletop split primitive: `client/ui/src/commonMain/kotlin/app/logdate/ui/adaptive/FoldableTabletopLayout.kt`
- Hinge-aware book split primitive: `client/ui/src/commonMain/kotlin/app/logdate/ui/adaptive/FoldableBookLayout.kt`
- Hinge-aware Home scene selection: `app/compose-main/src/commonMain/kotlin/app/logdate/navigation/LogDateNavDisplay.kt`
- Video picture-in-picture support: `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/video/VideoContent.android.kt`
- Separate editor window support: `app/compose-main/src/androidMain/kotlin/app/logdate/client/EditorActivity.kt`
- Multi-window test references: `app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/MultiWindowEditorE2ETest.kt`

## Feature Audit Inventory

Apply this checklist to every user-facing feature below. The inventory is based on the typed
Navigation 3 registry, feature navigation entry files, Android entry points, and feature modules.
Treat the list as a release-audit scope: a row can move to `[n/a]` only when the feature truly has no
foldable, posture, video playback, PiP-if-supported, attachment, notification, or multi-instance
behavior to validate.

The inventory status tracks release/runtime validation. Static audit findings and remediation
methods for every inventory row are recorded in the Feature Findings table below.

### Core Shell And Entry Points

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[todo]` | App launch, splash, lock, unlock, post-restore, and app update prompt | First-run and resume surfaces must fit folded/unfolded windows and not hide blocking UI behind a hinge. | `MainActivity`, `BaseRoute`, `LogDateNavDisplay`, `LockableContent`, `AppUpdatePrompt` |
| `[todo]` | Home shell and adaptive tab scaffold | Hosts Timeline, Library, Journals, Rewind, and Location surfaces, including two-pane home behavior. | `HomeRoute`, `HomeScreen`, `HomeSceneStrategy`, `ListDetailHomeScene` |
| `[todo]` | Deep links and Android 16 handoff | Direct-entry routes must land on the correct folded/unfolded layout without losing arguments. | `DeepLinkResolver`, `NavKey.toWebUrl`, `MainActivity.onHandoffActivityDataRequested` |
| `[complete]` | Search results screen | The expanded search body splits filters/context from recents/results in vertical-hinge book posture, with idle, searching, empty, and results screenshot evidence. | `SearchRoute`, `SearchScreen`; `A30_SearchIdleBookPosture` through `A33_SearchResultsBookPosture`; `validateDebugScreenshotTest --tests '*AdaptiveSearchBookPosture*'` |
| `[todo]` | Search result sharing and return | Search exposes Android share UI, but share-sheet launch/return behavior still needs folded, unfolded, and multi-window validation. | `MainActivity.shareSearchResult` |
| `[complete]` | Sync issues screen | The operational queue uses a vertical-hinge split layout and has book-posture screenshot evidence for summary/counts on one physical pane and retry/discard queue content on the other. | `SyncIssuesRoute`, `SyncIssuesScreen`; `A29_SyncIssuesBookPosture`; `validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'` |
| `[todo]` | Sync issues notification and multi-window entry | Notification/banner entry, fold/unfold return, and retry/discard behavior in multi-window still need validation. | `SyncIssuesRoute`, notification sync entry points |

### Timeline, Location, And Memories

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[todo]` | Timeline overview and day list | Primary daily memory surface; must adapt between single-pane and two-pane home layouts. | `HomeScreen`, `TimelineDetailRoute`, `TimelineDayDetailPanel` |
| `[todo]` | Timeline day detail | Long-form mixed content with notes, people, events, location, audio, and media sections. | `TimelineDetailRoute`, `TimelineDayDetailPanel` |
| `[todo]` | Timeline event and note attachment actions | Attachment flows are explicitly covered by the foldables checklist. | `onAttachNoteToEvent`, `EventDetailRoute`, editor/note routes |
| `[todo]` | Location timeline | Map/list layout can be posture-sensitive and is reachable from tabs, settings, deep links, and notifications. | `LocationTimelineRoute`, `LocationTimelineScreen` |
| `[todo]` | On This Day memory widget entry | Widget opens a specific day and should validate folded/unfolded route restoration. | `OnThisDayWidget`, `NAV_SOURCE_ON_THIS_DAY_WIDGET`, `TimelineDetailRoute` |

### Editor, Capture, And Media Creation

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[todo]` | New entry editor | Core creation surface with text, media, audio, and journal selection. | `EntryEditorRoute`, `NoteEditorScreen`, `EntryEditorContent` |
| `[todo]` | Existing entry and draft editor | Must preserve draft/entry state across posture changes, resize, and separate windows. | `EntryEditorRoute(entryId, draftId)`, `EditorActivity` |
| `[todo]` | Separate editor windows and multi-instance editing | Directly maps to the foldables multi-instance guidance. | `EditorActivity`, `EditorManager`, `MainActivity+MultiWindow.kt` |
| `[todo]` | Incoming share to editor | Covers attachments in folded/unfolded, portrait/landscape, and multi-window mode. | `IncomingShare.kt`, `ACTION_SEND`, `ACTION_SEND_MULTIPLE` |
| `[todo]` | Share-to-journal shortcut entry | Shortcut routes external text/media into a journal-scoped editor instance. | `DynamicShortcutDescriptor.ShareToJournal`, `parseShareToJournalShortcutId` |
| `[todo]` | Audio recording blocks | Tabletop posture and hinge safety matter for recording controls and waveform surfaces. | `AudioRecordingControls`, `AudioRecordingDisplay`, `AudioCaptureState` |
| `[todo]` | Audio playback blocks and immersive audio | Background playback, notification controls, transport controls, and tabletop posture all apply; PiP only applies if minimized audio visuals become a product requirement. | `AudioPlaybackComponent`, `ImmersiveAudioScreen` |
| `[todo]` | Audio transcription and audio tagging UI | Long transcript text must remain readable in book posture and resized windows. | `AudioTranscriptionUi`, speech recognition feature module |
| `[todo]` | Camera/photo/video capture blocks | Camera preview behavior is an explicit checklist category. | `CameraBlockEditor`, `LiveCameraPreview`, `CameraCaptureContent.android.kt` |
| `[todo]` | Media attachment import and block rendering | Attachments must open/close and remain correctly placed across postures. | `EntryEditorViewModel.setInitialAttachments`, media block components |

### Journals And Notes

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[complete]` | Journals overview | Grid/list browsing adapts to vertical-hinge book posture, with search/results on one physical pane and the journal carousel/list/filter panel on the other. | `JournalsOverviewRoute`, `JournalsOverviewScreen`; `A40_JournalsOverviewBookPosture` |
| `[complete]` | Empty journals state | First-journal creation affordances now have compact, expanded, and book-posture evidence. | `NoJournalsScreen`; `A42_EmptyJournalsBookPosture`; `A43_EmptyJournalsResponsiveStates` |
| `[complete]` | Journal detail | Long reading and note lists now adapt to vertical-hinge book posture, with journal summary/actions on one physical pane and the entry timeline on the other. | `JournalDetailsRoute`, `JournalDetailScreen`; `A41_JournalDetailBookPosture` |
| `[complete]` | Journal creation | Form input uses saveable local state and now splits title/description from memory selection and finish actions in vertical-hinge book posture. | `JournalCreationRoute`, `JournalCreationScreen`; `A44_JournalCreationBookPosture` |
| `[complete]` | Journal settings and delete flows | Settings, sharing entry, editable fields, and destructive confirmation surfaces now stay reachable and hinge-safe in vertical-hinge book posture. | `JournalSettingsRoute`, `JournalSettingsScreen`; `A45_JournalSettingsBookPosture`; `A46_JournalSettingsDeleteBookPosture` |
| `[complete]` | Share journal flow | Journal preview, sharing explanation, QR/share actions, Instagram action, and nearby-sharing state now fit vertical-hinge book posture. | `ShareJournalRoute`, `ShareJournalScreen`; `A47_ShareJournalBookPosture` |
| `[complete]` | Note viewer | Long text, image notes, video playback surface, audio playback, and immersive audio now have book- and tabletop-posture evidence. Video PiP remains tracked in the PiP-specific checklist rows. | `NoteDetailRoute`, `NoteViewerScreen`; `A48_NoteViewerTextBookPosture` through `A55_NoteViewerAudioTabletopPosture` |

### Library, Media, Rewind, And Postcards

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[complete]` | Library overview | Media grid density and selection adapts across folded/unfolded windows. The thumbnail grid now splits media groups across vertical-hinge book panes, keeps a hinge spacer between panes, computes column density from each physical pane width, and preserves shared selection/open-detail handling across both panes. | `LibraryOverviewRoute`, `LibraryScreen`, `MediaThumbnailGrid`; `:client:feature:library:testAndroidHostTest` |
| `[todo]` | Media detail viewer | Media preview, playback, and attachment opening behavior are foldable-sensitive. | `MediaDetailRoute`, `MediaDetailScreen` |
| `[todo]` | Remote display media presentation | External display presentation can interact with foldable posture and multi-window state. | `AndroidRemoteDisplayManager`, `MediaPresentation` |
| `[complete]` | Rewind overview and past rewinds | Card/list browsing and generated story entry now adapt to book posture. The overview keeps the featured/current rewind immersive on one physical pane and moves past rewinds plus annual generation actions to the other pane, with book-posture screenshot coverage for both overview and past-rewind browsing. | `RewindOverviewRoute`, `RewindOverviewScreen`, `PastRewindsRoute`; `A34_RewindOverviewBookPosture`, `A35_PastRewindsBookPosture` |
| `[complete]` | Rewind detail story | Immersive story playback adapts to tabletop and book posture. Tabletop keeps the active story panel above the horizontal hinge and moves progress/navigation/chrome below it, while book posture keeps the story panel and controls on separate physical panes without shrinking Rewind into a phone-sized window. | `RewindDetailRoute`, `RewindDetailScreen`; `A38_RewindDetailBookPosture`, `A39_RewindDetailTabletopPosture` |
| `[todo]` | Rewind notification detail entry | Notification deep links into Rewind detail still need folded/unfolded route restoration evidence. | `REWIND_NOTIFICATION_TARGET_DETAIL`, `MainActivity.resolveNavKey` |
| `[complete]` | Postcards collection | Canvas/card grid layout now adapts to separating vertical hinges. The collection splits cards across physical book-posture panes, computes card columns from each pane's width, and keeps the selection state plus open/edit/delete callbacks shared across both panes. | `PostcardsCollectionRoute`, `PostcardsCollectionScreen`; `:client:feature:postcards:testAndroidHostTest` |
| `[todo]` | Postcard canvas editor | Canvas, tool palettes, export sheet, and sticker shelf must avoid hinges and resize cleanly. | `PostcardEditorRoute`, `CanvasEditorScreen`, stickers feature module |
| `[todo]` | Postcard viewer and export/share actions | Viewer, export, print, and share surfaces must work in folded/unfolded states. | `PostcardViewerRoute`, `PostcardViewerScreen` |

### Events, People, Profile, And Account

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[todo]` | Event detail | Event notes, place picker, cover image, and linked-note attachments need posture coverage. | `EventDetailRoute`, `EventDetailScreen` |
| `[todo]` | Events calendar | Calendar grids and event navigation need portrait, landscape, and multi-window coverage. | `EventsCalendarRoute`, `EventsCalendarScreen` |
| `[complete]` | Events settings | Events hub uses `FoldableBookLayout` to separate event toggle/smart naming controls from calendar and sync navigation, with generated book-posture screenshot evidence. | `EventsSettingsRoute`, `EventsSettingsScreen`; `A36_EventsSettingsBookPosture` |
| `[complete]` | Calendar sync settings | Calendar sync overview uses `FoldableBookLayout` to separate permission/toggle controls from calendar and activity navigation, with generated book-posture screenshot evidence. | `CalendarSyncSettingsRoute`, `CalendarSyncSettingsScreen`; `A37_CalendarSyncSettingsBookPosture` |
| `[todo]` | Calendar sync calendars and activity | Calendar picker and import activity/detail screens still need posture-specific layout and navigation-return evidence. | `CalendarSyncCalendarsRoute`, `CalendarSyncActivityRoute` |
| `[todo]` | People settings | Permission, contact import, and review entry points need hinge-safe controls. | `PeopleSettingsRoute`, `PeopleSettingsScreen` |
| `[todo]` | People directory | Directory list/search and person navigation must adapt to large/folded layouts. | `PeopleDirectoryRoute`, `PeopleDirectoryScreen` |
| `[todo]` | People inbox | Review queue controls must remain usable in split and folded layouts. | `PeopleInboxRoute`, `PeopleInboxScreen` |
| `[todo]` | Person detail | Profile content and relationship context should fit book and multi-window states. | `PersonDetailRoute`, `PersonDetailScreen` |
| `[todo]` | Profile screen | Profile editing and birthday navigation must survive posture changes. | `ProfileRoute`, `ProfileScreen` |
| `[todo]` | Cloud account setup and sign-in | Multi-step auth UI, passkeys, and email verification must fit compact and expanded windows. | `CloudAccountSetupRoute`, `CloudAccountOnboardingScreen` |
| `[todo]` | Passkey account creation and authentication | Platform auth prompts and recovery states need resized/folded validation. | `PasskeyAccountCreationScreen`, `PasskeyAuthenticationScreen` |

### Onboarding

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[todo]` | Onboarding start and welcome-back flows | First impressions must not depend on phone-only portrait assumptions. | `OnboardingRoute`, `OnboardingStart`, `WelcomeBack` |
| `[todo]` | Personal intro and app overview | Text-heavy and form states should support book posture and split layouts. | `PersonalIntro`, `AppOverview` |
| `[todo]` | Memory import and memory selection | Media selection grids are foldable-sensitive. | `MemoryImport`, `MemorySelection` |
| `[todo]` | Onboarding cloud sync, account, backup plan, sign-in, and recovery phrase | Cloud sync, billing plan selection, auth, and recovery flows must preserve state across posture changes. | `CloudSync`, `BackupSyncScreen`, `AccountCreation`, `SignIn`, `RecoveryPhrase` |
| `[todo]` | Birthday, recommendations, day boundaries, location, and notifications onboarding | Permission and preference flows need readable layouts and reachable actions. | `BirthdayIntro`, `FeatureRecommendations`, `FeatureDayBoundaries`, `FeatureLocationTimeline`, `FeatureNotifications` |
| `[todo]` | Onboarding completion | Final state must render correctly in all supported postures before entering Home. | `OnboardingComplete` |

### Settings, Notifications, Widgets, And Connected Devices

| Status | Feature or flow | Why it needs the checklist | Route or code evidence |
| --- | --- | --- | --- |
| `[complete]` | Settings overview | Dense navigation hub uses `FoldableBookLayout`; book-posture screenshot validation passes for the vertical-hinge split. | `SettingsRoute`, `SettingsOverviewScreen`; `A09_SettingsOverviewBookPosture`; `validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*'` |
| `[complete]` | Account and privacy settings | Account and privacy settings use `FoldableBookLayout`; book-posture screenshot validation passes with deterministic passkey timestamps. | `AccountSettingsRoute`, `PrivacySettingsRoute`; `A10_AccountSettingsBookPosture`, `A11_PrivacySettingsBookPosture` |
| `[todo]` | Passkey and recovery phrase settings modals | Recovery phrase modal placement, passkey create/revoke dialogs, and fold/resize return state remain open. | `AccountSettingsRoute`, `PrivacySettingsRoute` |
| `[complete]` | Data settings overview | Data settings uses `FoldableBookLayout` and has book-posture screenshot evidence for the settings hub. | `DataSettingsRoute`; `A12_DataSettingsBookPosture` |
| `[todo]` | Export, restore, cloud backup, and clear/reset data flows | File picker return, export/restore progress, and destructive confirmation surfaces still need folded, unfolded, and multi-window validation. | `ExportSettingsRoute`, `ResetSettingsRoute`, `ClearDataSettingsRoute`, `ResetAppSettingsRoute` |
| `[complete]` | Devices settings | Device list and reset action use `FoldableBookLayout`; book-posture screenshot validation passes. | `DevicesRoute`, `DevicesScreen`; `A14_DevicesSettingsBookPosture` |
| `[todo]` | Device rename, remove, reset, and return flows | Rename, remove, reset, and multi-window return flows remain open. | `DevicesRoute`, `DevicesScreen` |
| `[complete]` | Location settings and sub-settings | Main, tracking options, interval, and advanced location settings screens use `FoldableBookLayout` with book-posture screenshot evidence. | `LocationSettingsRoute`, `LocationTrackingOptionsRoute`, `LocationIntervalRoute`, `LocationAdvancedRoute`; `A30_LocationSettingsBookPosture` through `A33_LocationAdvancedBookPosture` |
| `[todo]` | Location timeline and notification entry return | Location timeline layout plus notification/location-history entry and return behavior remain open. | `LocationTimelineRoute`, location notification entry points |
| `[complete]` | Preference settings pages | Memories, voice notes, streak, timeline, day boundary, sync, library, recommendation, birthday, rewind, and advanced settings split preference summaries, primary opt-ins, dense controls, and supporting actions with `FoldableBookLayout`. | `MemoriesSettingsRoute`, `VoiceNotesSettingsRoute`, `StreakSettingsRoute`, `TimelineSettingsRoute`, `DayBoundarySettingsRoute`, `SyncSettingsRoute`, `LibrarySettingsRoute`, `RecommendationSettingsRoute`, `BirthdaySettingsRoute`, `RewindSettingsRoute`, `AdvancedSettingsRoute`; `A13`, `A15`, `A20` through `A28` book-posture previews |
| `[todo]` | Preference dialog return states | Compact/expanded posture screenshots and dialog return state for recommendation times and birthday editing remain open. | `RecommendationSettingsRoute`, `BirthdaySettingsRoute` |
| `[complete]` | Watch settings and troubleshooting | Watch settings, sync settings, notification settings, and troubleshooting use `FoldableBookLayout` or the shared settings scaffold; book-posture screenshot validation passes for the watch hub and detail pages. | `WatchSettingsRoute`, `WatchSyncSettingsRoute`, `WatchNotificationSettingsRoute`, `WatchTroubleshootingRoute`; `A16` through `A19` book-posture previews |
| `[todo]` | Watch device and action return flows | Sync, notification, association, install, open-on-watch, and troubleshooting return behavior still needs foldable emulator or device-safe manual validation. | `WatchEntries.kt` |
| `[todo]` | Notification settings row and system notification settings launch | Android settings handoff and return behavior should work from split/folded states. | `SettingsOverviewScreen`, `openAppNotificationSettings`, `openChannelNotificationSettings` |
| `[todo]` | App notification entry points | Notification taps deep-link to note, day, location timeline, draft/new entry, event, rewind, export/restore, and watch-sync contexts. | `MainActivity.resolveNavKey`, `AndroidLogDateNotificationCatalog` |
| `[todo]` | On This Day widget UI and configuration | Glance widget sizing and click-through should be audited separately from in-app screens. | `OnThisDayWidget`, `OnThisDayWidgetConfigActivity` |
| `[todo]` | Dynamic launcher shortcuts | Continue draft, today timeline, week rewind, and share-to-journal shortcuts must route correctly in multi-window and foldable states. | `DynamicShortcutDescriptor` |

## Static Audit Findings And Remediation

This section records the current static audit. It does not replace emulator, Gradle Managed Device,
or manual foldable validation. Because no posture-capable runtime evidence is recorded yet, no
feature is marked `[pass]`.

Every feature listed in the Feature Audit Inventory has a matching static finding and remediation
method in this section. Rows remain `[partial]` when behavior exists but foldable runtime evidence is
missing, `[fail]` when the current implementation does not satisfy the guideline, and `[n/a]` when
the product does not expose that capability.

### Cross-Cutting Findings

| Status | Area | Current evidence | Remediation method |
| --- | --- | --- | --- |
| `[partial]` | Width-based adaptation | The app has `WindowSizeClass`, `LocalWindowInfo`, `AdaptivePaneLayout`, and `ListDetailHomeScene` usage in Home, Location Timeline, Library, Media Detail, Rewind, Postcards, Event place picking, and selected onboarding/account screens. `FoldableLayoutInfo` now combines AndroidX Window display features with posture, hinge bounds, occlusion type, and safe split panes. | Continue migrating feature layouts from width-only breakpoints to the shared foldable split model, especially surfaces that do not use `AdaptivePaneLayout` or the Home scene. |
| `[partial]` | Hinge avoidance | Shared foldable layout info exposes hinge bounds, separating hinges, posture, and computed pane regions. `AdaptivePaneLayout` avoids vertical and horizontal separating hinges, and the Home list/detail scene avoids vertical hinges. | Migrate major panes, toolbars, bottom sheets, snackbars, FABs, and media controls to place content outside hinge bounds; add runtime foldable screenshots before marking pass. |
| `[partial]` | Tabletop posture | `FoldableTabletopLayout` now splits horizontal-hinge windows into top and bottom physical panes. Audio recording controls, active recording, immersive audio playback, Android camera capture, note viewer, library media detail, and Rewind story playback use it so previews/waveforms/content stay above the hinge and controls/metadata/chrome move below it. | Add posture-capable runtime screenshots before marking pass. |
| `[partial]` | Book posture | Home list/detail and shared `AdaptivePaneLayout` now have vertical-hinge split handling. `FoldableBookLayout` provides reusable vertical-hinge panes. Search, journals overview, journal detail, note viewer, library media detail, Rewind detail, sync issues, settings overview, account settings, privacy settings, data settings, devices settings, location settings and sub-settings, and the major preference settings pages use it to keep navigation, metadata, filters, toggles, queues, or controls on one physical pane and primary content on the other. Many reading-heavy screens still only constrain width or use width-based panes. | Add book-posture reader layouts for timeline day detail, onboarding, and long transcript surfaces; add runtime foldable screenshots before marking pass. |
| `[partial]` | State continuity | Some flows use ViewModels, `rememberSaveable`, draft autosave, and editor auto-save; this is not validated against fold/unfold, split-screen resize, or multi-instance transitions. | Add posture-change and resize tests for scroll position, editor drafts, selected media, audio playback, camera review, dialog state, and route arguments. |
| `[partial]` | Camera | Camera capture has an inline `PreviewView`, camera switching, aspect ratio controls, photo/video review, and tabletop control placement. LogDate does not expose simultaneous front/back foldable screen preview, so that conditional guideline is out of scope. Fold/unfold runtime evidence is still missing. | Add posture runtime validation, rotation verification, and review-state validation for the supported in-app camera preview. |
| `[partial]` | Video playback | Journal note viewing, library media detail, and editor video blocks render video through Android `VideoPlayerContent`, backed by Media3 `ExoPlayer` and `PlayerView`. Video playback now has explicit Android PiP entry and keeps active playback visible through PiP or multi-window pause transitions. Note viewer and library media detail move viewing content above the tabletop hinge with navigation/actions/metadata below it. | Add runtime evidence for video playback in folded/unfolded and multi-window states, including editor video blocks. |
| `[partial]` | PiP | Video playback now exposes explicit Android PiP entry from the shared `VideoPlayerContent`, keeps playback alive when the host activity enters or is already in PiP, avoids pausing active playback during multi-window focus changes, and enables Android 12+ auto-enter while playing. Audio no longer exposes stale PiP callbacks. | Add runtime validation for note videos, library videos, and editor video blocks in folded, unfolded, portrait, landscape, and multi-window states; add custom PiP playback actions if product testing shows platform media controls are insufficient. |
| `[partial]` | Attachments and notification entry | Incoming share, shortcuts, widgets, audio playback notifications, location/history notifications, rewind notifications, and deep links route to app destinations. Runtime foldable validation is missing. | Build a notification/attachment entry matrix and validate each entry from folded, unfolded, portrait, landscape, and multi-window mode. |
| `[partial]` | Multi-instance | `EditorActivity` is resizable, document-launched, and uses `FLAG_ACTIVITY_NEW_DOCUMENT` plus `FLAG_ACTIVITY_MULTIPLE_TASK`. Existing E2E tests verify intent flags and split-screen support, but not foldable postures. | Extend the editor multi-window tests to fold/unfold and resize scenarios, and pass `WindowLayoutInfo` or derived foldable layout info into editor UI instead of only logging window metrics. |
| `[partial]` | Runtime validation | Book/tabletop screenshot validation now covers settings, location settings, events settings, calendar sync settings, sync issues, search, journals overview, journal detail, and Rewind detail/overview postures. Folded/unfolded emulator, PiP, attachment, and multi-instance evidence remain open. | Keep expanding posture screenshots and add safe emulator/managed-device commands plus manual hardware notes. Do not use physical devices from agent work without an explicit current-conversation override. |

### Feature Findings

| Status | Feature or flow | Static finding | Remediation method |
| --- | --- | --- | --- |
| `[partial]` | App launch, splash, lock, unlock, post-restore, and app update prompt | Entry UI uses full-screen Compose and width-limited update prompts, but no hinge-safe blocking overlay evidence exists. | Route all blocking launch/lock/update surfaces through a hinge-aware overlay container and add folded/unfolded screenshots. |
| `[partial]` | Home shell and adaptive tab scaffold | Home can switch to `ListDetailHomeScene` based on window size and now passes a vertical foldable split into the list/detail scene when both physical panes are usable. | Add runtime screenshots for compact, expanded, book posture, and small-pane fallback; keep horizontal/tabletop posture out of Home dual-pane unless a product layout is designed. |
| `[partial]` | Deep links and Android 16 handoff | Deep links and handoff restore typed routes; layout behavior is delegated to destination screens. | Add deep-link tests for every addressable destination under compact, expanded, folded, and multi-window configurations. |
| `[complete]` | Search results screen | `SearchScreenContent` uses `FoldableBookLayout` in vertical-hinge book posture so filters/context stay on one physical pane while recents, progress, empty states, or result buckets stay on the other. Book-posture screenshot validation passes for `A30_SearchIdleBookPosture` through `A33_SearchResultsBookPosture`. | `AdaptiveSearchBookPostureScreenshots.kt`; `./gradlew :app:android-main:validateDebugScreenshotTest --tests '*AdaptiveSearchBookPosture*' --console=plain --no-build-cache`. |
| `[partial]` | Search result sharing and return | Search can share results through Android chooser, but share-sheet launch/return and result-to-detail route restoration are not foldable-tested. | Add share-sheet return validation and result-to-detail route restoration from folded/unfolded and multi-window modes. |
| `[complete]` | Sync issues screen | Operational list screen uses `FoldableBookLayout` so the issue summary/counts stay on one physical pane and the retry/discard queue stays on the other. Book-posture screenshot validation passes for `A29_SyncIssuesBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `./gradlew :app:android-main:validateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*' --console=plain --no-build-cache`. |
| `[partial]` | Sync issues notification and multi-window entry | Sync issues can be reached from operational entry points, but folded/unfolded entry and multi-window retry/discard return evidence is missing. | Add folded/unfolded entry validation from the banner/notification path and multi-window retry/discard return screenshots. |
| `[partial]` | Timeline overview and day list | Home/timeline can participate in two-pane scene selection, and Home scene placement can avoid a vertical hinge. Timeline-specific selected-day and scroll restoration across posture changes is not yet validated. | Split timeline list/detail by hinge region where it does not already flow through Home, and preserve selected day/scroll state on posture change. |
| `[partial]` | Timeline day detail | Detail panel contains mixed long content and attachment actions; no book/tabletop-specific behavior. | Add a book-posture detail layout with readable text width, section navigation, and hinge-safe action placement. |
| `[partial]` | Timeline event and note attachment actions | Event and note attachments route across editor/detail flows; no folded/multi-window attachment matrix exists. | Validate attach/open/return for event notes from timeline in folded, unfolded, portrait, landscape, and split-screen modes. |
| `[partial]` | Location timeline | Uses `AdaptivePaneLayout` for map plus list; the shared layout now computes safe physical panes for separating vertical and horizontal hinges. Runtime map/list evidence is still missing. | Verify map/list placement on each physical pane in book posture and define whether tabletop should split map/list or keep a single-pane fallback. |
| `[partial]` | On This Day memory widget entry | Widget opens a specific day route; Glance sizing and route restoration are not foldable-tested. | Add widget size/config validation and click-through screenshots for folded/unfolded destination restoration. |
| `[partial]` | New entry editor | Editor supports text/media/audio/camera blocks and normal Compose state, but no posture-aware editor scaffold. | Add an editor adaptive shell that moves block toolbar, journal picker, camera/audio controls, and save actions away from hinge regions. |
| `[partial]` | Existing entry and draft editor | Draft/entry loading and pause autosave exist, but fold/unfold state continuity is not proven. | Add tests for dirty draft, focused block, selected journals, attachments, and scroll restoration across recreation and resize. |
| `[partial]` | Separate editor windows and multi-instance editing | Separate `EditorActivity` and task flags exist; foldable-specific multi-instance behavior is unvalidated. | Extend `MultiWindowEditorE2ETest` or emulator-safe manual scripts to launch two editor windows folded/unfolded and verify distinct state and recents entries. |
| `[partial]` | Incoming share to editor | `ACTION_SEND` and `ACTION_SEND_MULTIPLE` import text/images/videos into new editor windows. | Add attachment import tests from folded/unfolded and split-screen source apps, including return-to-source behavior. |
| `[partial]` | Share-to-journal shortcut entry | Per-journal shortcut IDs route incoming content to selected journals. | Verify share-sheet shortcut targets in multi-window and folded layouts; record expected fallback when journal shortcut is stale. |
| `[partial]` | Audio recording blocks | Recording UI and foreground notification support exist. `AudioRecordingControls` and `ActiveRecordingDisplay` now use `FoldableTabletopLayout` so waveform/transcript/status remain above a horizontal hinge and microphone/actions stay below it. | Add fold/unfold runtime validation for active recording, paused recording, restart, finish, input selection, and notification continuity. |
| `[partial]` | Audio playback blocks and immersive audio | Playback services and immersive audio UI exist. `ImmersiveAudioScreen` now uses `FoldableTabletopLayout` so waveform/context remain above a horizontal hinge and scrubber/transport/output controls stay below it, and `FoldableBookLayout` so waveform/context and transport/output controls occupy separate vertical-hinge panes. Audio PiP callback scaffolding has been removed because PiP is scoped to video playback. | Validate notification controls and return from background playback on a foldable emulator or manual hardware. |
| `[partial]` | Audio transcription and audio tagging UI | Transcript/tag UI exists as audio content but has no book-posture reading treatment. | Add transcript reader mode with constrained line length and pane-aware transcript/waveform placement. |
| `[partial]` | Camera/photo/video capture blocks | Inline CameraX preview, camera switch, aspect ratios, and review states exist. Android camera capture now uses `FoldableTabletopLayout` so the live preview stays above a horizontal hinge and camera/microphone/capture controls stay below it. Simultaneous front/back foldable screen preview is not a LogDate feature. | Add posture runtime validation, rotation verification, and review-state validation for the supported in-app camera preview. |
| `[partial]` | Media attachment import and block rendering | Attachments are imported into editor blocks. Video blocks use the shared Android `VideoPlayerContent`, including PiP entry and active-playback preservation through PiP or multi-window pause transitions. Placement/resizing across hinge states is unverified. | Add attachment block screenshot states for folded, unfolded, landscape, editor multi-window, and editor video PiP return. |
| `[complete]` | Journals overview | `JournalsOverviewScreenContent` uses `FoldableBookLayout` so search/results stay on one vertical-hinge pane and the journal list/filter panel fills the other pane. The populated overview now has generated book-posture screenshot coverage. | `AdaptiveAuditScreenshots.kt`; `A40_JournalsOverviewBookPosture`; `./gradlew :client:feature:journal:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*A40_JournalsOverviewBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Empty journals state | `NoJournalsScreen` remains visible and actionable in compact, expanded, and vertical-hinge book layouts. The empty-state CTA now has generated screenshot evidence for split-medium, tablet portrait, tablet landscape, desktop window, and book-posture devices. | `AdaptiveAuditScreenshots.kt`; `A42_EmptyJournalsBookPosture`; `A43_EmptyJournalsResponsiveStates`; `./gradlew :app:android-main:updateDebugScreenshotTest --tests '*A42_EmptyJournalsBookPosture*' --console=plain --no-build-cache`; `./gradlew :app:android-main:updateDebugScreenshotTest --tests '*A43_EmptyJournalsResponsiveStates*' --console=plain --no-build-cache`. |
| `[complete]` | Journal detail | `JournalDetailScreenContent` now uses `FoldableBookLayout` so vertical-hinge book posture keeps journal title, sort state, share/settings/delete actions, and summary on one physical pane while the entry timeline or media gallery fills the other pane. | `AdaptiveAuditScreenshots.kt`; `A41_JournalDetailBookPosture`; `./gradlew :client:feature:journal:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*A41_JournalDetailBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Journal creation | `JournalCreationScreenContent` preserves title, description, and note-picker state with `rememberSaveable`; selected notes/media remain ViewModel-provided route state. The screen now uses `FoldableBookLayout` so title/description stay on one physical pane while memory selection and finish actions stay on the other in vertical-hinge book posture. | `JournalCreationScreen.kt`; `AdaptiveAuditScreenshots.kt`; `A44_JournalCreationBookPosture`; `./gradlew :client:feature:journal:ktlintCheck :client:feature:journal:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*A44_JournalCreationBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Journal settings and delete flows | `JournalSettingsScreenContent` now uses `FoldableBookLayout` so the journal cover/share affordance and insights stay on one physical pane while editable fields, privacy copy, save, and delete actions stay on the other. The delete confirmation uses a foldable-specific overlay that constrains the confirmation card to one physical pane instead of spanning the hinge. | `JournalSettingsScreen.kt`; `AdaptiveAuditScreenshots.kt`; `A45_JournalSettingsBookPosture`; `A46_JournalSettingsDeleteBookPosture`; `./gradlew :client:feature:journal:ktlintCheck :client:feature:journal:compileAndroidMain :app:android-main:validateDebugScreenshotTest --tests '*A45_JournalSettingsBookPosture*' --tests '*A46_JournalSettingsDeleteBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Share journal flow | `ShareJournalContent` now uses `FoldableBookLayout` so the share card and explanation occupy one physical pane while QR, system share, Instagram, and nearby-sharing actions occupy the other pane. | `ShareJournalScreen.kt`; `AdaptiveAuditScreenshots.kt`; `A47_ShareJournalBookPosture`; `./gradlew :client:feature:journal:ktlintCheck :client:feature:journal:compileAndroidMain :app:android-main:validateDebugScreenshotTest --tests '*A47_ShareJournalBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Note viewer | `NoteViewerScaffoldContent` uses `FoldableTabletopLayout` so text/image/video note content stays above a horizontal hinge and back/share/add-to-journal/previous/next controls move below it. The standard non-tabletop path uses `FoldableBookLayout` so vertical-hinge book posture keeps note content on one physical pane and navigation/actions on the other. Audio notes route through `ImmersiveAudioScreen`, which now supports both tabletop and book posture. Video uses the shared `VideoPlayerContent`, including Android video PiP entry. | `NoteViewerScreen.kt`; `ImmersiveAudioScreen.kt`; `AdaptiveAuditScreenshots.kt`; `A48_NoteViewerTextBookPosture`; `A49_NoteViewerImageBookPosture`; `A50_NoteViewerVideoBookPosture`; `A51_NoteViewerAudioBookPosture`; `A52_NoteViewerTextTabletopPosture`; `A53_NoteViewerImageTabletopPosture`; `A54_NoteViewerVideoTabletopPosture`; `A55_NoteViewerAudioTabletopPosture`; `./gradlew :client:feature:editor:ktlintCheck :client:feature:editor:compileAndroidMain :client:feature:journal:ktlintCheck :client:feature:journal:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*A51_NoteViewerAudioBookPosture*' --tests '*A55_NoteViewerAudioTabletopPosture*' --console=plain --no-build-cache`. Video PiP entry/return remains covered by `T-Foldables_PiP`. |
| `[complete]` | Library overview | `MediaThumbnailGrid` uses `rememberFoldableLayoutInfo()` and `calculateFoldableSplitLayout()` to render separate left/right thumbnail grids around a separating vertical hinge. Book-posture column counts are derived from each physical pane width, and both panes share the same `MultiSelectState` plus item click callback so selection and open-detail behavior remain consistent. | `MediaThumbnailGridTest` covers book-pane group splitting and pane-width column calculation; `./gradlew :client:feature:library:ktlintCheck :client:feature:library:compileAndroidMain --console=plain --no-build-cache` and `./gradlew :client:feature:library:testAndroidHostTest --console=plain --no-build-cache`. |
| `[partial]` | Media detail viewer | Expanded media detail uses side-by-side metadata based on width and renders videos through `VideoPlayerContent`, including Android video PiP entry. Library media detail now uses `FoldableTabletopLayout` to place media preview above a horizontal hinge and metadata/share/presentation controls below it, and `FoldableBookLayout` to keep media playback on one physical pane with metadata/share/presentation controls on the other in vertical-hinge book posture. | Validate video PiP entry/return, pager behavior, share, and external presentation from library media detail in folded, unfolded, tabletop, book, and multi-window states. |
| `[partial]` | Remote display media presentation | Remote display manager/presentation exist; no foldable lifecycle evidence. | Validate start/stop presentation through fold/unfold, external display attach/detach, and media detail resize. |
| `[complete]` | Rewind overview and past rewinds | `FloatingRewindCardList` now uses `FoldableBookLayout` in vertical-hinge book posture. The start pane preserves the active/current rewind card as an immersive focused surface, while the end pane provides a scrollable past-rewinds browser plus any annual rewind action. Standard layouts keep the original full-screen floating-card flow. | `AdaptiveRewindBookPostureScreenshots.kt`; `A34_RewindOverviewBookPosture`; `A35_PastRewindsBookPosture`; `./gradlew :client:feature:rewind:ktlintCheck :client:feature:rewind:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveRewindBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Rewind detail story | `RewindStoryView` uses `FoldableTabletopLayout` so the active story panel stays above a horizontal hinge while progress, share, reply, delete, close, previous, and next controls move below it. The standard non-tabletop path uses `FoldableBookLayout` so vertical-hinge book posture keeps the story panel on one physical pane and chrome/navigation on the other. Rewind remains immersive across screen sizes, with only a large-screen max width to keep the story stage and chrome readable. | `AdaptiveRewindBookPostureScreenshots.kt`; `A38_RewindDetailBookPosture`; `A39_RewindDetailTabletopPosture`; `./gradlew :client:feature:rewind:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveRewindBookPosture*' --console=plain --no-build-cache`. |
| `[partial]` | Rewind notification detail entry | Rewind notification targets route to detail screens, but there is no folded/unfolded notification tap and route-restoration evidence yet. | Add notification deep-link validation into folded Rewind detail. |
| `[complete]` | Postcards collection | `PostcardsCollectionScreen` uses `FoldableBookLayout` so postcard cards render in separate left/right grids around a separating vertical hinge. Each book pane derives its grid count from the physical pane width, while both panes share one `MultiSelectState` and the same open/edit/delete callbacks. The FAB remains in Scaffold's end action slot, away from the hinge in book posture. | `PostcardsCollectionLayoutTest` covers book-pane card splitting and pane-width column calculation; `./gradlew :client:feature:postcards:ktlintCheck :client:feature:postcards:compileAndroidMain :client:feature:postcards:testAndroidHostTest --console=plain --no-build-cache`. |
| `[partial]` | Postcard canvas editor | Uses medium-width layout to switch tool placement; no hinge-aware canvas/tool rail. | Split canvas and tool shelf around hinge, keep selection handles out of hinge bounds, and test export sheet placement. |
| `[partial]` | Postcard viewer and export/share actions | Viewer/export/share flows exist; export dialog changes at expanded widths only. | Add hinge-aware viewer actions and validate share/save/print return state in split-screen. |
| `[partial]` | Event detail | Event detail has linked notes/media/place picker; some subcomponents use width breakpoints. | Add event detail list/supporting pane layout and foldable attachment validation. |
| `[partial]` | Events calendar | Calendar and navigation exist; no large/foldable calendar evidence recorded. | Add month/week calendar layouts for book and landscape; validate event jumps preserve selected date. |
| `[complete]` | Events settings | `EventsSettingsContent` uses `FoldableBookLayout` so vertical-hinge book posture keeps the event enablement and smart-naming controls on one physical pane while calendar and sync navigation stay on the other. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A36_EventsSettingsBookPosture`; `./gradlew :client:screenshot-scenes:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*' --console=plain --no-build-cache`. |
| `[complete]` | Calendar sync settings | `CalendarSyncSettingsContent` uses `FoldableBookLayout` so permission/toggle controls stay on one physical pane and calendar/activity navigation stays on the other. The shared screenshot fixture covers the granted + enabled state with selected calendar counts. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A37_CalendarSyncSettingsBookPosture`; `./gradlew :client:screenshot-scenes:compileAndroidMain :app:android-main:updateDebugScreenshotTest --tests '*AdaptiveSettingsBookPosture*' --console=plain --no-build-cache`. |
| `[partial]` | Calendar sync calendars and activity | Calendar picker and import activity screens exist, but there is no posture-specific layout or foldable navigation-return evidence yet. | Add split list/detail activity layout and validate event-detail navigation from folded multi-window mode. |
| `[partial]` | People settings | Permission/contact import flow exists; no hinge-safe permission/import evidence. | Use shared settings scaffold and validate permission sheet/contact picker return in split-screen. |
| `[partial]` | People directory | Directory and person navigation exist; no foldable list/detail evidence. | Add list/detail People layout in book posture and preserve search/filter state on resize. |
| `[partial]` | People inbox | Review queue exists; no foldable review controls evidence. | Add review queue split layout with current item/actions on separate physical panes. |
| `[partial]` | Person detail | Detail screen exists; no book/multi-window evidence. | Use shared detail scaffold and add directory-to-detail folded screenshots. |
| `[partial]` | Profile screen | Profile editing exists; no fold/resize restoration evidence. | Add form-state preservation tests and hinge-safe date/profile dialogs. |
| `[partial]` | Cloud account setup and sign-in | Account onboarding uses compact/split width behavior; no hinge/posture support. | Feed foldable layout info into account setup and validate passkey/email steps across fold/unfold. |
| `[partial]` | Passkey account creation and authentication | Passkey flows exist, but platform prompt behavior in split/folded state is unvalidated. | Add emulator/manual validation notes for passkey prompts, cancellation, and return-to-app route state. |
| `[partial]` | Onboarding start and welcome-back flows | Start/welcome-back screens constrain width but are not posture-aware. | Add onboarding host scaffold with hinge-safe actions and screenshot baselines for compact, book, and tabletop. |
| `[partial]` | Personal intro and app overview | Overview can split at 700dp and intro constrains width; state preservation partly exists. | Add fold/unfold tests for text entry and overview split layout using `rememberSaveable` or ViewModel-backed state. |
| `[partial]` | Memory import and memory selection | Memory selection has media grid/expanded preview; no hinge-aware selection evidence. | Split media grid and detail preview around hinge and add selected-media restoration tests. |
| `[partial]` | Onboarding cloud sync, account, backup plan, sign-in, and recovery phrase | Cloud sync, backup plan selection, account, sign-in, and recovery screens exist; recovery words, billing-plan cards, and auth dialog placement are not hinge-aware. | Use shared account adaptive layout and validate cloud sync plan selection, purchase-return state, recovery phrase visibility, and auth actions in book posture and split-screen. |
| `[partial]` | Birthday, recommendations, day boundaries, location, and notifications onboarding | Preference/permission screens constrain width and some state is saveable; no foldable runtime evidence. | Add common onboarding preference layout with hinge-safe permission prompts and folded/unfolded screenshots. |
| `[partial]` | Onboarding completion | Completion screen exists with width constraints only. | Add screenshot coverage for completion in compact, unfolded, book, and tabletop sizes. |
| `[complete]` | Settings overview | Dense navigation hub uses `FoldableBookLayout` to split personal, privacy/security, and data/storage groups across vertical hinges. Book-posture screenshot validation passes for `A09_SettingsOverviewBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A09_SettingsOverviewBookPosture`. |
| `[complete]` | Account and privacy settings | Account and privacy screens use `FoldableBookLayout`. Book-posture screenshot validation passes for `A10_AccountSettingsBookPosture` and `A11_PrivacySettingsBookPosture` after pinning passkey timestamps in shared screenshot data. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A10_AccountSettingsBookPosture`, `A11_PrivacySettingsBookPosture`. |
| `[partial]` | Passkey and recovery phrase settings modals | Recovery phrase, passkey create/revoke, and resize return state flows exist but are not hinge-placement validated. | Add recovery phrase modal placement, passkey dialog return, and fold/resize state validation. |
| `[complete]` | Data settings overview | Data settings uses `FoldableBookLayout`. Book-posture screenshot validation passes for `A12_DataSettingsBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A12_DataSettingsBookPosture`. |
| `[partial]` | Export, restore, cloud backup, and clear/reset data flows | Export/restore and destructive reset routes exist, but picker return, progress UI, sheet/dialog state, and confirmations are not foldable-tested. | Validate file picker return, sheet/dialog state, export/restore progress UI, and destructive confirmation placement. |
| `[complete]` | Devices settings | Device list and reset action use `FoldableBookLayout`. Book-posture screenshot validation passes for `A14_DevicesSettingsBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A14_DevicesSettingsBookPosture`. |
| `[partial]` | Device rename, remove, reset, and return flows | Device actions exist, but rename/remove/reset and multi-window return behavior is not foldable-tested. | Validate rename, remove, reset, folded, and multi-window return behavior. |
| `[complete]` | Location settings and sub-settings | Location settings use `FoldableBookLayout` for the main hub, tracking options, interval, and advanced settings. Generated book-posture baselines cover `A30_LocationSettingsBookPosture` through `A33_LocationAdvancedBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A30_LocationSettingsBookPosture` through `A33_LocationAdvancedBookPosture`. |
| `[partial]` | Location timeline and notification entry return | Location timeline and notification/location-history entry flows exist, but layout and return behavior are not foldable-tested. | Validate location timeline layout, notification/location-history entry return, and folded/multi-window runtime behavior. |
| `[complete]` | Preference settings pages | Memories, voice notes, streak, timeline, day boundary, sync, library, recommendation, birthday, rewind, and advanced settings split preference summaries, primary opt-ins, dense controls, and supporting actions with `FoldableBookLayout`. Book-posture screenshot validation passes for `A13_MemoriesSettingsBookPosture`, `A15_RewindSettingsBookPosture`, and `A20_StreakSettingsBookPosture` through `A28_SyncSettingsBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A13`, `A15`, and `A20` through `A28` book-posture previews. |
| `[partial]` | Preference dialog return states | Recommendation time and birthday edit dialog paths exist, but compact/expanded posture screenshots and dialog return state are not validated. | Add compact/expanded posture screenshots and dialog return state for recommendation times and birthday editing. |
| `[complete]` | Watch settings and troubleshooting | Watch routes are registered through `watchEntries`. Book-posture screenshot validation passes for `A16_WatchSettingsBookPosture`, `A17_WatchSyncSettingsBookPosture`, `A18_WatchNotificationSettingsBookPosture`, and `A19_WatchTroubleshootingBookPosture`. | `AdaptiveSettingsBookPostureScreenshots.kt`; `A16_WatchSettingsBookPosture` through `A19_WatchTroubleshootingBookPosture`. |
| `[partial]` | Watch device and action return flows | Watch sync, notification, association, install, open-on-watch, and troubleshooting actions exist but are not return-tested in foldable layouts. | Validate sync, notification, association, install, open-on-watch, and troubleshooting return behavior on a foldable emulator or device-safe manual run. |
| `[partial]` | Notification settings row and system notification settings launch | Notification row is optional and system settings launch helpers exist; return behavior is not foldable-tested. | Validate settings handoff and return from split-screen; keep system-routing copy if system settings opens in another window. |
| `[partial]` | App notification entry points | `MainActivity.resolveNavKey` maps notification extras to note, day, location, editor, event, and rewind routes. | Add per-channel tap tests and screenshots for destination restoration in folded/unfolded and multi-window states. |
| `[partial]` | On This Day widget UI and configuration | Widget and config activity exist; sizing and folded click-through are unvalidated. | Add widget-size screenshots, config activity resize tests, and folded/unfolded click-through evidence. |
| `[partial]` | Dynamic launcher shortcuts | Shortcut descriptors exist for continue draft, today timeline, week rewind, and share-to-journal. | Add shortcut launch matrix for folded/unfolded and separate-window editor behavior. |

## Audit Checklist

| Status | Guideline ID | Checklist item | Evidence |
| --- | --- | --- | --- |
| `[partial]` | Foldables_Postures | Major LogDate surfaces render without clipped content, inaccessible controls, or hinge-obscured UI in folded, unfolded, tabletop, and book postures. | Shared foldable split primitives now exist and are consumed by Home and `AdaptivePaneLayout`; posture-capable runtime evidence is still missing. |
| `[partial]` | Foldables_Postures | Tabletop posture moves media playback, recording, or review controls to the reachable horizontal screen area instead of spanning the fold. | Implemented for audio recording controls, active recording, immersive audio playback, Android camera capture, note viewer, library media detail, and Rewind story playback through `FoldableTabletopLayout`; runtime posture evidence is still missing. |
| `[partial]` | Foldables_Postures | Book posture gives long text and reading-heavy flows a readable layout, such as single-side reading, dual-pane reading/detail, or hinge-aware gutters. | Home, `AdaptivePaneLayout`, and `FoldableBookLayout` can split around vertical hinges. Search, journals overview, note viewer, library media detail, Rewind detail, settings overview, account settings, privacy settings, data settings, devices settings, location settings and sub-settings, and the major preference settings pages now use `FoldableBookLayout` for content/action separation; other reader-heavy feature screens and non-book runtime evidence are still missing. |
| `[partial]` | Foldables_Postures | Posture changes preserve task state, scroll position, selections, drafts, playback state, and focused inputs. | Some ViewModel/saveable/autosave support exists; fold/unfold validation is missing. |
| `[partial]` | Foldables_Postures | Hinge bounds are treated as unavailable space for primary content, touch targets, sheet handles, snackbars, and navigation affordances. | Home list/detail and `AdaptivePaneLayout` consume computed hinge-safe panes; modal, toolbar, media, and screen-specific controls still need migration. |
| `[partial]` | Foldables_Camera | If LogDate uses an in-app camera preview, the preview is correctly sized, oriented, and cropped in folded and unfolded states. | Inline camera preview exists; folded/unfolded preview evidence is missing. |
| `[partial]` | Foldables_Camera | If LogDate uses an in-app camera preview, portrait and landscape rotations do not produce stretched, mirrored, blank, or stale preview frames. | Preview switch timeout and aspect ratio logic exist; posture/rotation validation is missing. |
| `[n/a]` | Foldables_Camera | If LogDate supports front and back screen camera preview on unfolded devices, both previews render correctly and keep capture controls reachable. | LogDate does not expose simultaneous front/back foldable screen preview; supported camera behavior is single-preview capture with camera switching. |
| `[partial]` | Foldables_Multitasking_Scenarios | Picture-in-picture entry and exit work while the device is folded and unfolded. | Android video PiP entry is implemented in `VideoPlayerContent`; folded/unfolded runtime evidence is still missing. |
| `[partial]` | Foldables_Multitasking_Scenarios | Picture-in-picture entry and exit work in portrait, landscape, and multi-window mode without losing playback, current media, or navigation state. | Android video PiP entry is implemented and playback is not paused while the activity is in PiP; portrait/landscape/multi-window runtime evidence is still missing. |
| `[partial]` | Foldables_Multitasking_Scenarios | While PiP is active in multi-window mode, resizing the app window does not break layout, playback state, or return-from-PiP behavior. | Android video PiP entry is implemented; resize and return-from-PiP evidence is still missing. |
| `[partial]` | Foldables_Multitasking_Scenarios | Media, note, journal, and share attachments can open and close in folded and unfolded states. | Attachment routes and incoming share exist; folded/unfolded evidence is missing. |
| `[partial]` | Foldables_Multitasking_Scenarios | Attachment and notification entry points work in portrait, landscape, and multi-window mode without duplicating content or dropping the target route. | Notification/deep-link routing exists; posture and multi-window validation is missing. |
| `[partial]` | Foldables_PiP | PiP controls exposed by LogDate are interactive, accessible, and synchronized with the underlying playback or media state. | Video surfaces expose an accessible PiP entry button; runtime validation and any needed custom playback actions remain open. |
| `[partial]` | Foldables_PiP | PiP controls remain correct after fold, unfold, rotate, resize, background, foreground, and return-to-app transitions. | Video PiP entry is implemented; transition evidence is still missing. |
| `[partial]` | Foldables_Multi-Instance | LogDate can launch supported multiple-instance flows in separate windows while folded and unfolded. | Editor multi-instance exists; folded/unfolded validation is missing. |
| `[partial]` | Foldables_Multi-Instance | Multiple instances preserve distinct task identity, route arguments, attachments, and draft state. | Intent flags and E2E coverage exist; draft/attachment folded evidence is missing. |
| `[partial]` | Foldables_Multi-Instance | Multiple instances work in portrait, landscape, and multi-window mode without task collisions, overwritten extras, or incorrect recents behavior. | Existing tests cover flags/split-screen; foldable posture coverage is missing. |

## Test Checklist

| Status | Test ID | Test procedure | Evidence |
| --- | --- | --- | --- |
| `[todo]` | T-Foldables_Postures | View each audited screen in folded, unfolded, tabletop, and book postures. Verify that primary content and controls move to usable locations and avoid the hinge. | Add posture matrix screenshots or manual notes. |
| `[todo]` | T-Foldables_Postures | Change posture while editing, scrolling, selecting, recording, playing media, or viewing long text. Verify state continuity after each transition. | Add before/after notes or automated test link. |
| `[todo]` | T-Foldables_Camera | If an audited feature activates an in-app camera, verify preview correctness when folded, unfolded, portrait, and landscape. | Add camera evidence or mark `[n/a]`. |
| `[n/a]` | T-Foldables_Camera | If front and back screen preview are supported while unfolded, verify both previews and capture controls. | LogDate does not expose simultaneous front/back foldable screen preview. |
| `[todo]` | T-Foldables_PiP | With the device folded and unfolded, enter and exit PiP from portrait, landscape, and multi-window mode. | Validate note video, library video, and editor video block playback. |
| `[todo]` | T-Foldables_PiP | While PiP is active in multi-window mode, resize the window and verify playback, controls, and return-to-app behavior. | Validate note video, library video, and editor video block playback. |
| `[todo]` | T-Foldables_PiP | Interact with every custom PiP control and verify that app state updates consistently. | Validate platform PiP controls and add custom actions if they are insufficient for video playback. |
| `[todo]` | T-Foldables_Attachments | With the device folded and unfolded, open and close note, media, share, and notification attachments in portrait and landscape. | Add attachment matrix evidence. |
| `[todo]` | T-Foldables_Attachments | Repeat attachment and notification entry from multi-window mode, including returning to the source window. | Add manual notes or E2E result. |
| `[todo]` | T-Foldables_Multi-Instance | With the device folded and unfolded, launch multiple supported LogDate instances in separate windows. | Add editor or route-specific validation evidence. |
| `[todo]` | T-Foldables_Multi-Instance | Repeat multi-instance launch in portrait, landscape, and multi-window mode; verify separate recents entries and independent route state. | Add manual notes or E2E result. |

## Validation Matrix

Record each audited feature against the relevant device/window states instead of marking the app as a
single global pass. Use the Feature Audit Inventory as the source list; the starter rows below are
high-risk groups that should be expanded or duplicated as evidence is collected.

| Feature or flow | Folded | Unfolded | Tabletop | Book | Portrait | Landscape | Multi-window | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Search results screen | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveSearchBookPostureScreenshots.kt`; `A30_SearchIdleBookPosture` through `A33_SearchResultsBookPosture`. |
| Search result sharing and return | `[todo]` | `[todo]` | `[n/a]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Share-sheet launch/return evidence remains open. |
| Sync issues screen | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveSettingsBookPostureScreenshots.kt`; `A29_SyncIssuesBookPosture`. |
| Journals overview | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A40_JournalsOverviewBookPosture`. |
| Empty journals state | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[complete]` | `[complete]` | `[complete]` | `AdaptiveAuditScreenshots.kt`; `A42_EmptyJournalsBookPosture`; `A43_EmptyJournalsResponsiveStates`. |
| Timeline and day detail | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |
| Note editor and separate editor windows | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |
| Journal detail | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A41_JournalDetailBookPosture`. |
| Journal creation | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A44_JournalCreationBookPosture`. |
| Journal settings and delete flows | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A45_JournalSettingsBookPosture`; `A46_JournalSettingsDeleteBookPosture`. |
| Share journal flow | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A47_ShareJournalBookPosture`. |
| Note viewer | `[todo]` | `[todo]` | `[complete]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveAuditScreenshots.kt`; `A48_NoteViewerTextBookPosture`; `A49_NoteViewerImageBookPosture`; `A50_NoteViewerVideoBookPosture`; `A51_NoteViewerAudioBookPosture`; `A52_NoteViewerTextTabletopPosture`; `A53_NoteViewerImageTabletopPosture`; `A54_NoteViewerVideoTabletopPosture`; `A55_NoteViewerAudioTabletopPosture`. PiP return evidence remains tracked in `T-Foldables_PiP`. |
| Library media viewer | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |
| Audio recording and playback | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |
| Rewind overview and past rewinds | `[todo]` | `[todo]` | `[n/a]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveRewindBookPostureScreenshots.kt`; `A34_RewindOverviewBookPosture`, `A35_PastRewindsBookPosture`. |
| Rewind detail story | `[todo]` | `[todo]` | `[complete]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveRewindBookPostureScreenshots.kt`; `A38_RewindDetailBookPosture`, `A39_RewindDetailTabletopPosture`. |
| Rewind notification detail entry | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Notification tap and route-restoration evidence remains open. |
| Incoming share and attachment entry | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |
| Settings screens | `[todo]` | `[todo]` | `[todo]` | `[complete]` | `[todo]` | `[todo]` | `[todo]` | `AdaptiveSettingsBookPostureScreenshots.kt`; `A09` through `A33`, plus `A36` and `A37`, cover settings, location settings, events settings, calendar sync settings, and sync issues. |
| Settings handoffs and dialogs | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | System settings handoff, recovery phrase, passkey, export/restore, destructive confirmation, device actions, and preference dialog return evidence remains open. |
| Onboarding | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | `[todo]` | Add evidence. |

## Audit Notes

- Re-check the source page before using this checklist for release certification.
- Prefer emulator or Gradle Managed Device validation for agent-run checks. Do not use physical
  Android devices from agent-driven work without an explicit current-conversation override.
- PiP is scoped to Android video playback. The shared video player exposes explicit PiP entry,
  Android 12+ auto-enter while playing, and lifecycle handling that keeps video playing while the
  host activity is in PiP. Audio PiP remains out of scope.
- Video playback is still in scope even though PiP is not. Video note playback, library media
  playback, and external display presentation need posture, resize, lifecycle, and controls
  validation.
- When a row passes, replace placeholder evidence with a file path, test task, screenshot baseline,
  screen recording, or manual validation note.
