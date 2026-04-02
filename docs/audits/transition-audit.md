# Transition Audit

## Scope

This document is the canonical motion audit for LogDate across Android, desktop, and Wear.

- Goal: inventory current transition behavior, identify inconsistencies, and map opportunities to introduce Android shared-element/shared-bounds motion and predictive back support.
- Search prepass: 51 production Kotlin files matched transition-related motion primitives.
- Excluded from the active catalog:
  - `client/feature/onboarding/src/commonMain/kotlin/app/logdate/feature/onboarding/ui/EntryCreationScreen.kt`
    - Only commented-out transition code.
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/JournalCoverFlowCarousel.kt`
    - `AnimatedContent(true)` is a no-op wrapper; the meaningful motion in this file is continuous card scaling, not an enter/exit transition.

## Device Classes

- Android compact phone
  - Single-pane home/detail flow, bottom navigation, hierarchical push/pop is most visible here.
- Android landscape compact / medium tablet
  - Navigation rail may replace bottom navigation; timeline can already behave like a two-pane layout in landscape compact.
- Android expanded / desktop-size tablet
  - Two-pane detail selection is common; generic full-width push/pop metaphors become less accurate.
- Desktop windowed
  - Separate editor window, no system predictive back.
- Wear
  - State-driven motion, no shared elements and no predictive back expectation.

## Catalog Schema

Every entry below records the same fields.

- Files
- Context
- Enter
- Exit
- Pop / Predictive back
- Shared motion
- Responsive notes
- Findings
- Opportunities

## Android Navigation And Shared Motion

### A01. Global scene transition baseline

- Files:
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/MainNavigationRoot.kt`
- Context:
  - Global `NavDisplay` scene transitions for every Android route that does not provide route-level metadata overrides.
  - Covers tab switching, hierarchical forward navigation, back navigation, and cloud-account modal behavior.
- Enter:
  - Tab to tab: `fadeIn()`
  - Cloud account enter: `slideInVertically(from bottom) + fadeIn()`
  - All other forward navigation: `slideInHorizontally(from right)`
- Exit:
  - Tab to tab: `fadeOut()`
  - Cloud account enter: outgoing scene `fadeOut()`
  - All other forward navigation: `slideOutHorizontally(to left)`
- Pop / Predictive back:
  - Return to main tab: `EnterTransition.None togetherWith fadeOut()`
  - Leave cloud account flow: `fadeIn() togetherWith slideOutVertically(to bottom) + fadeOut()`
  - All other back: `slideInHorizontally(from left) togetherWith slideOutHorizontally(to right)`
  - Predictive back mirrors pop behavior exactly.
- Shared motion:
  - None at the scene level.
- Responsive notes:
  - Good fit for compact single-pane hierarchy.
  - Less accurate for expanded and landscape-compact two-pane routes where the destination can already exist in place.
- Findings:
  - The app has one default hierarchy metaphor, but several flows are not actually full-screen push/pop flows on larger devices.
  - The default predictive back behavior is available broadly, but it is only semantically correct for routes that truly behave like a full-screen stack.
- Opportunities:
  - Keep this as the fallback baseline, but route-level exceptions should increase for shared-bounds and two-pane selection flows.

### A02. Timeline detail now varies by device class: compact keeps the push/pop model, larger layouts use pane activation

- Files:
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/TimelineRoutes.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/scenes/RouteClassification.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/scenes/HomeScene.kt`
- Context:
  - `TimelineDetail` is a two-pane-capable detail route when opened from `TimelineListRoute`.
- Enter:
  - Compact phone: compact detail scene keeps the global forward slide from `A01`.
  - Landscape compact and expanded: route override uses `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`.
- Exit:
  - Compact phone: inherits the global forward exit.
  - Landscape compact and expanded: list pane stays visually stable while the detail pane activates.
- Pop / Predictive back:
  - Compact phone: inherits the global back and predictive-back slide.
  - Landscape compact and expanded: `EnterTransition.None togetherWith fadeOut()`.
- Shared motion:
  - None.
- Responsive notes:
  - Compact phone: remains a full-screen hierarchical detail transition.
  - Landscape compact and expanded: scene strategy now distinguishes compact detail-only mode from the true two-pane list-detail scene so motion can follow the actual layout.
- Findings:
  - The previous mismatch is addressed without degrading phone behavior.
- Opportunities:
  - Consider shared or bounded transitions from day cards to the detail surface only if the list items expose a stable visual anchor.

### A03. Settings, search, postcards, and most utility routes inherit the baseline unchanged

- Files:
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/SearchRoutes.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/PostcardsRoutes.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/AppSettingsRoute.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/RewindRoutes.kt`
- Context:
  - These route families define content but no route-level transition metadata.
  - Current behavior is whatever `A01` provides.
- Enter:
  - Search, settings, postcard collection/viewer/editor, and rewind detail all inherit the global forward transition.
- Exit:
  - Inherit the global forward exit behavior.
- Pop / Predictive back:
  - Inherit the global back and predictive-back behavior.
- Shared motion:
  - None at the route layer.
- Responsive notes:
  - Search and settings are plausible as generic pushes.
  - Rewind detail is always fullscreen and immersive, so the generic slide is mechanically correct but visually unopinionated.
  - Postcard routes still need visual groundwork before shared motion makes sense: collection cards are first-photo thumbnails, while viewer/editor surfaces are freeform canvases.
- Findings:
  - These flows are consistent with the baseline, but they do not have route-specific semantics.
- Opportunities:
  - Search: low-priority opportunity to feel more like transient exploration than hierarchy if product direction changes.
  - Postcards: medium-priority opportunity only after the collection and destination surfaces share an actual postcard-shaped anchor.
  - Rewind detail: decide whether immersive story entry should keep the baseline slide or gain a more media-driven transition.

### A04. Library media detail already treats shared bounds as the semantic transition

- Files:
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/LibraryRoutes.kt`
  - `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/components/MediaThumbnailItem.kt`
- Context:
  - Library grid thumbnail to media detail.
- Enter:
  - Route override: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`
- Exit:
  - Thumbnail side uses shared bounds from `MediaThumbnailItem`.
  - Detail side keeps the originating scene in place until the morph completes.
- Pop / Predictive back:
  - Pop: `EnterTransition.None togetherWith fadeOut()`
  - Predictive pop: `EnterTransition.None togetherWith fadeOut()`
- Shared motion:
  - Yes. Shared-bounds key `${TransitionKeys.LIBRARY_MEDIA_TRANSITION}-${mediaId}` on both source and destination.
  - Custom `MediaBoundsTransform` with 400ms tween and `FastOutSlowInEasing`.
- Responsive notes:
  - Strong fit on compact.
  - Also a good fit on expanded layouts because the shared morph works even when the library pane remains visible.
- Findings:
  - This is the cleanest example of a route-specific override matching the underlying shared-bounds semantics.
  - Predictive back exists, but the predictive override currently only preserves the fade/no-enter behavior, not a gesture-driven shared-bounds scrub.
- Opportunities:
  - Use this route as the standard for future shared-bounds route work.
  - If Navigation 3 and shared transitions allow better progress coupling later, this is the route to upgrade first.

### A05. FAB to editor now suppresses the global slide when the FAB morph is the semantic transition

- Files:
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/EditorRoute.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/scenes/HomeSceneComponents.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/EntryEditorContent.kt`
- Context:
  - Home FAB opens the entry editor via shared bounds using the `fab_to_editor` key.
- Enter:
  - Route override checks the originating route class.
  - From a main tab route: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`
  - From any other route: falls back to the global hierarchical slide from `A01`.
- Exit:
  - Source and destination expose shared bounds with the same `fab_to_editor` key.
- Pop / Predictive back:
  - Back to a main tab route: `EnterTransition.None togetherWith fadeOut()`
  - Back to any other route: falls back to the global slide from `A01`.
  - Inside the editor, local predictive back exists for expanded-block and immersive chrome transitions.
- Shared motion:
  - Yes. Shared-bounds key `fab_to_editor` with 350ms tween and `FastOutSlowInEasing`.
- Responsive notes:
  - Compact phone: the FAB morph now owns the route transition when entering from home tabs.
  - Expanded layouts: the route no longer layers the generic slide on top of the shared-bounds morph.
- Findings:
  - This inconsistency is addressed for the home-tab entry path.
  - The editor now matches the library-detail model: keep the source scene stable and let the shared bounds define the motion.
- Opportunities:
  - Tune compact vs expanded behavior separately if the editor opens from different shell contexts on larger devices.
  - Review non-home editor entry points to decide whether any of them also deserve route-specific motion instead of the fallback slide.

### A06. Journal cover to journal detail now uses a route-level shared-motion override from the journals overview

- Files:
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/JournalCover.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailScreen.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/JournalRoutes.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/scenes/RouteClassification.kt`
- Context:
  - Journal cover card opens journal detail.
  - Both source and destination use `journal-container-${journal.id}`.
- Enter:
  - From `JournalList`: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`
  - From any other route: falls back to the global forward slide from `A01`.
- Exit:
  - Journal cover and journal detail scaffold share the same journal container key.
- Pop / Predictive back:
  - Back to `JournalList`: `EnterTransition.None togetherWith fadeOut()`
  - Back to any other route: falls back to the global back and predictive-back transitions from `A01`.
- Shared motion:
  - Yes. Shared element on the journal cover and on the journal detail scaffold container.
- Responsive notes:
  - Compact phone: the cover morph now owns the transition when detail opens from the journals overview.
  - Expanded and landscape-compact: the journals surface can remain visually stable underneath the detail morph.
- Findings:
  - This inconsistency is addressed for the journals overview entry path.
  - Non-overview journal-detail entry points intentionally keep the fallback hierarchy slide for now.
- Opportunities:
  - Revisit larger-screen behavior to decide whether split-pane journal layouts should use an even more pane-oriented reveal.
  - Audit other journal-detail origins to decide whether they should stay on the baseline slide or gain their own semantic overrides.

### A07. Note viewer now supports shared bounds from journal detail and location memory cards, while other origins still use the baseline

- Files:
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/NoteViewerScreen.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/detail/JournalDetailScreen.kt`
  - `client/feature/location-timeline/src/commonMain/kotlin/app/logdate/feature/location/timeline/ui/LocationTimelineScreen.kt`
  - `client/ui/src/commonMain/kotlin/app/logdate/ui/common/transitions/TransitionKeys.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/routes/NoteViewerRouteTransitions.kt`
  - `app/compose-main/src/androidMain/kotlin/app/logdate/navigation/MainNavigationRoot.kt`
- Context:
  - Journal detail note cards, location memory preview cards, and note viewer now share a note-specific bounds key where the source surface is spatially explicit.
- Enter:
  - From `JournalDetail`: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`
  - From `LocationRoute`: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`
  - From all other origins: falls back to the global forward slide from `A01`.
- Exit:
  - Journal detail note cards attach shared bounds to the card surface only, excluding timestamps and membership badges.
  - Location memory preview cards attach shared bounds to the bottom-sheet card surface.
  - Note viewer attaches the matching shared bounds to text, image, video, and audio presentations.
- Pop / Predictive back:
  - Back to `JournalDetail`: `EnterTransition.None togetherWith fadeOut()`
  - Back to `LocationRoute`: `EnterTransition.None togetherWith fadeOut()`
  - Back to all other origins: falls back to the global back and predictive-back transitions from `A01`.
- Shared motion:
  - Yes for journal-detail and location-timeline origins.
  - Shared-bounds key `note-viewer-${noteId}` is present on both source card surfaces and the note viewer destination.
- Responsive notes:
  - Compact phone: both journal-detail -> note-viewer and location-memory -> note-viewer now behave like true shared-container transitions.
  - Expanded layouts: the note viewer still opens from several different contexts, so source-specific handling remains important.
- Findings:
  - The journal-detail origin is now addressed, including audio notes.
  - The location timeline origin is now addressed through the place-detail bottom sheet.
  - Other origins still behave as plain hierarchy routes because they do not expose matching source surfaces yet.
- Opportunities:
  - Add source-side shared motion for any remaining note-viewer origins only if those surfaces can expose stable visual anchors.
  - Explore gesture-coupled predictive shared bounds if Navigation 3 and Compose shared transitions make progress-driven scrubbing practical later.

### A08. Legacy journal Navigation Compose routes now define explicit pop transitions, but still remain a separate navigation system

- Files:
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/navigation/JournalCreationRoute.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/navigation/JournalDetailsRoute.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/navigation/JournalSettingsRoute.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/navigation/NoteDetailRoute.kt`
- Context:
  - Deprecated Navigation Compose graph still declares explicit route transitions.
- Enter:
  - All four routes use `slideIntoContainer(towards = Left)`.
- Exit:
  - All four routes use `slideOutOfContainer(towards = Right)`.
- Pop / Predictive back:
  - All four routes now define explicit pop transitions:
    - `popEnterTransition = slideIntoContainer(towards = Right)`
    - `popExitTransition = slideOutOfContainer(towards = Left)`
  - There is still no predictive-back-specific behavior.
- Shared motion:
  - None at the navigation layer.
- Responsive notes:
  - This stack is single-pane and does not adapt to the Navigation 3 two-pane rules.
- Findings:
  - Forward and back semantics are now explicit and symmetric within the legacy stack.
  - The remaining inconsistency is architectural: this deprecated graph still uses a separate navigation system from the Navigation 3 app shell.
- Opportunities:
  - Preferred: migrate these routes to Navigation 3 and inherit the app-wide scene model intentionally.
  - Predictive back support should be considered part of migration, not bolted onto this legacy graph.

## Android Shell, Inline Motion, And Responsive Surfaces

### B01. Navigation chrome animates independently of scene transitions

- Files:
  - `client/ui/src/commonMain/kotlin/app/logdate/ui/scaffold/ResponsiveScaffold.kt`
- Context:
  - Navigation rail show/hide and bottom navigation show/hide.
- Enter:
  - Rail: `slideInHorizontally(from left) + fadeIn()`
  - Bottom navigation: `slideInVertically(from bottom) + fadeIn()`
- Exit:
  - Rail: `slideOutHorizontally(to left) + fadeOut()`
  - Bottom navigation: `slideOutVertically(to bottom) + fadeOut()`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - This is one of the few places the app already varies motion by device class.
- Findings:
  - Shell motion is coherent on its own, but it is not referenced by the route-level transition system.
- Opportunities:
  - When route overrides are added for expanded layouts, ensure shell motion and scene motion do not compete.

### B02. Library panel crossfades loading and content states

- Files:
  - `client/feature/library/src/commonMain/kotlin/app/logdate/feature/library/ui/LibraryContent.kt`
- Context:
  - Loading placeholder vs panel content, plus placeholder visibility.
- Enter:
  - Content: `fadeIn(tween(200))`
  - Placeholder: `fadeIn()`
- Exit:
  - Content: `fadeOut(tween(200))`
  - Placeholder: `fadeOut()`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None at the panel level.
- Responsive notes:
  - Good on all Android sizes.
- Findings:
  - Clean local-state motion, consistent with the library feature's calm motion direction.
- Opportunities:
  - None urgent.

### B03. Journal list panel mixes loading fades, layout crossfade, and responsive shape morphing

- Files:
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/JournalListPanel.kt`
  - `client/feature/journal/src/commonMain/kotlin/app/logdate/feature/journals/ui/JournalListPlaceholder.kt`
- Context:
  - Journal overview loading state, layout mode switch, and adaptive panel corner changes.
- Enter:
  - Content after loading: `fadeIn(tween(200))`
  - Layout mode switch: `fadeIn(tween(200))`
  - Placeholder: `fadeIn(tween(200))`
- Exit:
  - Content after loading: `fadeOut(tween(200))`
  - Layout mode switch: `fadeOut(tween(200))`
  - Placeholder: `fadeOut(tween(500))`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None at the panel transition level.
- Responsive notes:
  - Panel corner radii animate across compact, landscape, and expanded layouts using `animateDpAsState`.
- Findings:
  - Good local consistency, but the panel's responsive shape morph is separate from the route-level journal cover/detail shared element story.
- Opportunities:
  - Low-priority opportunity to align panel shape changes with route-level journal detail transitions on larger devices.

### B04. Timeline shell uses inline reveal motion for utility affordances

- Files:
  - `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/TimelinePane.kt`
  - `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/newstuff/TimelineList.kt`
  - `client/ui/src/commonMain/kotlin/app/logdate/ui/timeline/NewTimelineItem.kt`
  - `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/Timeline.kt`
  - `client/feature/timeline/src/commonMain/kotlin/app/logdate/feature/timeline/ui/details/PeopleEncounteredSection.kt`
- Context:
  - Scroll-to-top button, suggestion block, item detail-level swaps, pinchable container, people section visibility.
- Enter:
  - Scroll-to-top: `fadeIn() + slideInVertically(from bottom)`
  - Suggestion block: `expandVertically(from top) + fadeIn()`
  - New timeline item detail switch: implicit `AnimatedContent`
  - Pinchable container: implicit `AnimatedContent`
  - People list: one implicit `AnimatedVisibility`, one explicit `fadeIn()` for the empty state.
- Exit:
  - Scroll-to-top: `fadeOut() + slideOutVertically(to bottom)`
  - Suggestion block: `shrinkVertically(towards top) + fadeOut()`
  - New timeline item detail switch: implicit `AnimatedContent`
  - Pinchable container: implicit `AnimatedContent`
  - People list: one implicit `AnimatedVisibility`, one explicit `fadeOut()` for the empty state.
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Mostly independent of device class.
- Findings:
  - Timeline local motion is dominated by inline reveal/collapse rather than spatial navigation metaphors.
  - Several sites rely on implicit `AnimatedContent` or implicit `AnimatedVisibility`, which weakens the motion contract.
- Opportunities:
  - Low-priority: add explicit content specs to the implicit timeline swaps if they become visually important.

### B05. Location timeline swaps placeholder and current-location card with short fades

- Files:
  - `client/feature/location-timeline/src/commonMain/kotlin/app/logdate/feature/location/timeline/ui/LocationTimelineScreen.kt`
- Context:
  - Current location loaded vs loading state.
- Enter:
  - `fadeIn(tween(200))`
- Exit:
  - `fadeOut(tween(150))`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Clean, local status transition.
- Opportunities:
  - None urgent.

### B06. Restore and export sheets use simple crossfade content swaps

- Files:
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/restore/RestoreBottomSheet.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/export/ExportOptionsBottomSheet.kt`
- Context:
  - Bottom sheet internal state changes.
- Enter:
  - `fadeIn()`
- Exit:
  - `fadeOut()`
- Pop / Predictive back:
  - Bottom sheet dismissal is separate from these internal swaps.
- Shared motion:
  - None.
- Responsive notes:
  - Good on compact and expanded surfaces.
- Findings:
  - Consistent local-state motion.
- Opportunities:
  - None urgent.

### B07. Profile, data transfer, and settings surfaces use small-state visibility patterns

- Files:
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/profile/ui/ProfileScreen.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/common/DataTransferComposables.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/BirthdaySettingsScreen.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/QuotaUsageBlock.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/ServerSelectionCard.kt`
  - `client/feature/core/src/commonMain/kotlin/app/logdate/feature/core/settings/ui/ServerSelectionSection.kt`
- Context:
  - Progress bars, status messages, expandable details, custom-server inputs, warnings.
- Enter:
  - Profile progress: `fadeIn()`
  - Data transfer message: `expandVertically()`
  - Birthday age message: `fadeIn() + expandVertically()`
  - Quota detail: `expandVertically()` with spring
  - Server selection warning and custom URL field: implicit `AnimatedVisibility` defaults
- Exit:
  - Profile progress: `fadeOut()`
  - Data transfer message: `shrinkVertically()`
  - Birthday age message: `fadeOut() + shrinkVertically()`
  - Quota detail: `shrinkVertically()` with spring
  - Server selection warning and custom URL field: implicit `AnimatedVisibility` defaults
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Mostly device agnostic.
- Findings:
  - Local patterns are sensible, but settings surfaces mix explicit and implicit visibility contracts.
- Opportunities:
  - Low-priority cleanup: prefer explicit enter/exit specs for important settings visibility changes.

## Android Editor Motion

### C01. Entry editor already has internal predictive-back logic

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/EntryEditorContent.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/common/PlatformPredictiveBackHandler.kt`
  - `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/common/PlatformPredictiveBackHandler.android.kt`
- Context:
  - Editor chrome and expanded-block back behavior.
- Enter:
  - Not a screen enter/exit definition; this is internal gesture handling and chrome interpolation.
- Exit:
  - Not a screen exit definition; internal only.
- Pop / Predictive back:
  - Editor intercepts predictive back for expanded blocks and immersive chrome.
  - Plain route exit is intentionally left to Nav3.
- Shared motion:
  - Works alongside the FAB shared-bounds pair.
- Responsive notes:
  - Valuable on compact and expanded because the editor has deep local states.
- Findings:
  - Predictive back is already strong inside the editor, which makes the missing route-level override more obvious.
- Opportunities:
  - Route-level editor motion should be upgraded to match this internal sophistication.

### C02. Main editor content treats shared bounds as the semantic transition

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/MainEditorContent.kt`
- Context:
  - Picker/list/expanded block transitions inside the editor.
- Enter:
  - `EnterTransition.None`
- Exit:
  - `fadeOut(snap())`
- Pop / Predictive back:
  - `SeekableTransitionState` and `PlatformPredictiveBackHandler` scrub the transition.
- Shared motion:
  - Yes. Block surfaces use `sharedBounds("block_surface_$id")`.
- Responsive notes:
  - Device agnostic.
- Findings:
  - This is another excellent example of the shared-bounds overlay being the real transition while the content swap is deliberately suppressed.
- Opportunities:
  - Use this pattern as the internal benchmark for future Android shared-bounds route work.

### C03. Empty editor picker and footer seed shared-bounds origins for block creation

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/content/EmptyEditorStateContent.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/content/EditorContentFooter.kt`
- Context:
  - Picker tiles and footer actions are origins for block-surface shared bounds.
- Enter:
  - Shared bounds only; no separate enter transition.
- Exit:
  - Shared bounds only; no separate exit transition.
- Pop / Predictive back:
  - Handled by editor-local predictive back machinery.
- Shared motion:
  - Yes. Shared-bounds keys for text, image, audio, video, and camera block surfaces.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Internal editor creation flows already have a coherent shared-bounds grammar.
- Opportunities:
  - None urgent beyond keeping route-level motion consistent with it.

### C04. Journal selector has the strongest local motion contract in the app

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/common/JournalSelectorDropdown.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/common/JournalSelectorModifiers.kt`
- Context:
  - Collapsed trigger to expanded picker, checkmark appearance, predictive-back clipping.
- Enter:
  - Expanded picker: `expandVertically(from bottom) + fadeIn()`
  - Checkmark: `scaleIn() + fadeIn()`
  - Inner content swap: `fadeIn()`
- Exit:
  - Expanded picker: `shrinkVertically(towards bottom) + fadeOut()`
  - Checkmark: `scaleOut() + fadeOut()`
  - Inner content swap: `fadeOut()`
- Pop / Predictive back:
  - Yes. Custom predictive-back clipping keeps the list anchored to the bottom edge.
- Shared motion:
  - Yes. Shared bounds for selector surface, shared elements for journal titles.
- Responsive notes:
  - Explicit height capping already considers compact screens.
- Findings:
  - This is the most decision-complete local motion system in the repo.
- Opportunities:
  - None urgent; use it as a standard for future explicit motion contracts.

### C05. Audio block transitions are shared-element rich but rely on implicit outer content swapping

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioBlockContent.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AnimatedPlayPauseButton.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/AudioTranscriptionUi.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/audio/expansion/ImmersiveAudioScreen.kt`
- Context:
  - Collapsed audio block to expanded audio block, play/pause icon changes, transcript state, immersive controls.
- Enter:
  - Outer collapsed/expanded block swap: implicit `AnimatedContent`
  - Play/pause icon: `fadeIn() + scaleIn()`
  - Transcript state: `fadeIn()`
  - Immersive controls: `fadeIn()`
- Exit:
  - Outer collapsed/expanded block swap: implicit `AnimatedContent`
  - Play/pause icon: `fadeOut() + scaleOut()`
  - Transcript state: `fadeOut()`
  - Immersive controls: `fadeOut()`
- Pop / Predictive back:
  - No route-level predictive back here; local editor predictive back can still interact with expanded states upstream.
- Shared motion:
  - Yes. Play button and waveform use shared elements between collapsed and expanded layouts.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Strong shared-element primitives, but the outer content swap remains implicit.
- Opportunities:
  - Low-to-medium priority: make the outer collapsed/expanded content transform explicit if the current implicit default feels too generic.

### C06. Editor chrome uses restrained fade and expand/shrink patterns

- Files:
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/layout/ImmersiveEditorLayout.kt`
  - `client/feature/editor/src/commonMain/kotlin/app/logdate/feature/editor/ui/common/NoteEditorToolbar.kt`
  - `client/feature/editor/src/androidMain/kotlin/app/logdate/feature/editor/ui/camera/CameraCaptureContent.android.kt`
- Context:
  - Bottom content visibility, toolbar actions, capture overlays.
- Enter:
  - Bottom content: `fadeIn() + expandVertically()`
  - Toolbar actions: `fadeIn()`
  - Camera overlays: `fadeIn()`
- Exit:
  - Bottom content: `fadeOut() + shrinkVertically()`
  - Toolbar actions: `fadeOut()`
  - Camera overlays: `fadeOut()`
- Pop / Predictive back:
  - Editor-local predictive back scrubs chrome elsewhere; these are inline visibility transitions.
- Shared motion:
  - None at this layer.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Consistent local chrome grammar.
- Opportunities:
  - None urgent.

## Android Onboarding Motion

### D01. Onboarding start relies on implicit AnimatedContent defaults

- Files:
  - `client/feature/onboarding/src/commonMain/kotlin/app/logdate/feature/onboarding/ui/OnboardingStartScreen.kt`
- Context:
  - Splash to landing content and delayed subheading reveal.
- Enter:
  - Implicit `AnimatedContent` defaults.
- Exit:
  - Implicit `AnimatedContent` defaults.
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Same behavior on all device sizes.
- Findings:
  - Onboarding opens with the weakest explicit motion contract in the onboarding flow.
- Opportunities:
  - Low priority: define an intentional opening transition if onboarding needs a stronger branded motion identity.

### D02. Personal intro is a three-stage motion system

- Files:
  - `client/feature/onboarding/src/commonMain/kotlin/app/logdate/feature/onboarding/ui/PersonalIntroScreen.kt`
- Context:
  - Processing bar, name/bio step navigation, LLM response reveal.
- Enter:
  - Progress bar: `fadeIn()`
  - Step content: `slideInHorizontally(from right)` with spring
  - LLM response content: `fadeIn() + scaleIn(initialScale = 0.8f)`
- Exit:
  - Progress bar: `fadeOut()`
  - Step content: `slideOutHorizontally(to left)` with spring
  - LLM response content: `fadeOut() + scaleOut(targetScale = 0.8f)`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Clear, intentional motion language.
- Opportunities:
  - None urgent.

### D03. Memory selection already uses local shared elements effectively

- Files:
  - `client/feature/onboarding/src/commonMain/kotlin/app/logdate/feature/onboarding/ui/MemorySelectionScreen.kt`
- Context:
  - Thumbnail long-press to expanded overlay, loading and auxiliary visibility.
- Enter:
  - Auxiliary visibility: `fadeIn()`
  - Thumbnail to overlay: shared element with `rememberSharedContentState("memory-${memory.uri}")`
- Exit:
  - Auxiliary visibility: `fadeOut()`
  - Overlay dismiss: reverse shared element
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - Yes. Shared element between grid thumbnail and expanded overlay.
- Responsive notes:
  - Works on all sizes because the overlay owns the focus.
- Findings:
  - Good local shared-element usage outside the main Android navigation system.
- Opportunities:
  - Useful reference for future Android overlay/shared-element work.

### D04. Onboarding completion uses an unusually long crossfade

- Files:
  - `client/feature/onboarding/src/commonMain/kotlin/app/logdate/feature/onboarding/ui/OnboardingCompletionScreen.kt`
- Context:
  - Streak screen to final completion message.
- Enter:
  - `fadeIn(tween(3000))`
- Exit:
  - `fadeOut(tween(3000))`
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Device agnostic.
- Findings:
  - Three-second crossfades are much longer than most of the app's motion.
- Opportunities:
  - Medium-priority polish item if the onboarding flow is revisited.

## Desktop

### X01. Desktop editor window exposes shared-transition locals but no meaningful window transition

- Files:
  - `app/compose-main/src/desktopMain/kotlin/app/logdate/desktop/EditorWindow.kt`
- Context:
  - Separate editor window.
- Enter:
  - `AnimatedVisibility(true)` around editor content; effectively no stateful enter transition.
- Exit:
  - Window close is immediate.
- Pop / Predictive back:
  - No predictive back on desktop.
- Shared motion:
  - SharedTransitionLayout is present for editor-internal motion, not for window open/close.
- Responsive notes:
  - Desktop-specific.
- Findings:
  - Desktop inherits editor-local motion but has no desktop-specific window transition story.
- Opportunities:
  - Low priority: if desktop becomes a larger focus, define explicit window open/close or content reveal behavior.

## Wear

### W01. Wear home is state-driven, not enter/exit-driven

- Files:
  - `app/wear/src/main/kotlin/app/logdate/wear/presentation/home/WearHomeScreen.kt`
- Context:
  - Bottom action row visibility and record surface phase changes.
- Enter:
  - Bottom actions: alpha animates toward visible using `animateFloatAsState`
  - Record surface: scale, background color, and icon tint animate toward recording/saved states
- Exit:
  - Same property animations reverse back to idle states.
- Pop / Predictive back:
  - Not applicable.
- Shared motion:
  - None.
- Responsive notes:
  - Wear-specific motion model.
- Findings:
  - Wear has no navigation transition problem here; it is a compact state machine with property animation.
- Opportunities:
  - None urgent for shared elements or predictive back.

## Android Opportunity Map

### Highest Priority

1. `EntryEditor` route
   - Why: shared-bounds pair already exists, internal predictive back already exists, but screen navigation still slides generically.
   - Action: add route-level forward/pop/predictive overrides so the FAB morph owns the transition.

2. `JournalDetail` route
   - Why: source and destination already share the `journal-container-${journal.id}` key.
   - Action: promote journal cover to detail into a route-level shared-element transition with custom pop and predictive-pop behavior.

3. Legacy journal Navigation Compose routes
   - Why: explicit forward-only transitions, implicit back behavior, no predictive-back story, different system from the rest of Android.
   - Action: migrate to Navigation 3 or at minimum define explicit pop transitions as a stopgap.

### Medium Priority

4. `PostcardViewerRoute` and `PostcardEditorRoute`
   - Why: current behavior is baseline-only, and the collection thumbnails do not match the viewer/editor canvas surface closely enough for a clean morph.
   - Action: revisit only after the collection and destination surfaces share a postcard-shaped visual anchor.

### Lower Priority

5. `NoteViewerRoute` non-journal origins
   - Why: journal-detail and location origins are now covered, but any remaining non-spatial note entry points still use the baseline hierarchy transition.
   - Action: only add source-side shared motion where those surfaces can expose stable anchors without compromising clarity.

6. `SearchRoute`
   - Why: current global transitions are acceptable, but the route could eventually behave more like transient exploration than hierarchy.

7. `RewindDetailRoute`
   - Why: always fullscreen and immersive; the baseline slide works but is not tailored to story-style media.

8. Local implicit `AnimatedContent` / `AnimatedVisibility` sites
   - Why: mostly fine today, but explicit specs would improve readability and auditability in timeline and settings surfaces.

## Responsive Transition Notes

- Compact phone
  - Keep directional push/pop as the default for true full-screen hierarchy.
  - Prefer shared bounds over slides when a stable origin exists.
- Landscape compact / medium tablet
  - Audit every route that can become list-detail or rail-based. Pane activation should not look like a full-screen page push.
- Expanded / desktop-size tablet
  - Favor detail-surface reveal or shared morphs over full-width slides for list-detail features.
  - Library detail already models this well.
- Desktop
  - Do not force Android predictive-back assumptions into desktop windows.
- Wear
  - Keep the state-machine/property-animation approach; shared elements and predictive back are not priorities here.

## Recommended Follow-Up Order

1. Evaluate postcards again only after the feature exposes matching postcard-shaped anchors across collection and destination surfaces.
2. Revisit any remaining note-viewer origins and add source contracts only where the origin surface is spatially clear.
3. Revisit search and rewind if product direction calls for more opinionated route motion.
4. Normalize local `AnimatedContent` and `AnimatedVisibility` sites where implicit defaults are obscuring motion intent.
